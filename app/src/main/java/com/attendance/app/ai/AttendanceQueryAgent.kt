package com.attendance.app.ai

import com.attendance.app.BuildConfig
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.model.Staff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class StructuredFilter(
    val staffName: String? = null,
    val dateFrom: String? = null, // YYYY-MM-DD
    val dateTo: String? = null,   // YYYY-MM-DD
    val timeFrom: String? = null, // HH:mm or e.g. 9:00 AM
    val timeTo: String? = null    // HH:mm or e.g. 10:00 AM
)

data class AgentQueryResult(
    val userQuery: String,
    val structuredFilter: StructuredFilter?,
    val aiAnswer: String,
    val matchingRecords: List<AttendanceRecord>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class DailySummaryResult(
    val summaryMarkdown: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

interface AttendanceQueryAgent {
    suspend fun queryAttendance(
        userQuestion: String,
        allStaff: List<Staff>,
        allRecords: List<AttendanceRecord>
    ): AgentQueryResult

    suspend fun generateDailySummary(
        allStaff: List<Staff>,
        todayRecords: List<AttendanceRecord>
    ): DailySummaryResult
}

class GroqAttendanceQueryAgent(
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) : AttendanceQueryAgent {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Priority ordered models supported by Groq API
    private val candidateModels = listOf(
        "openai/gpt-oss-20b",
        "openai/gpt-oss-120b",
        "qwen/qwen3.8-27b",
        "llama-3.3-70b-versatile"
    )

    override suspend fun queryAttendance(
        userQuestion: String,
        allStaff: List<Staff>,
        allRecords: List<AttendanceRecord>
    ): AgentQueryResult = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val staffRoster = allStaff.joinToString(", ") { "${it.name} (${it.employeeId})" }

        if (apiKey.isBlank()) {
            return@withContext localFallbackQuery(
                userQuestion,
                allStaff,
                allRecords,
                "Groq API key not configured. Using smart on-device search."
            )
        }

        val systemPrompt = """
            You are the SmartAttendance AI Assistant. Your job is to convert natural language queries about employee attendance into a JSON filter.
            Today's Date: $todayStr
            Registered Staff Roster: [$staffRoster]
            
            You must return a single JSON object with this exact schema:
            {
              "staffName": "Name or employee ID if mentioned, or null",
              "dateFrom": "YYYY-MM-DD or null",
              "dateTo": "YYYY-MM-DD or null",
              "timeFrom": "HH:mm (24-hour format like 09:00) or null",
              "timeTo": "HH:mm (24-hour format like 10:00) or null",
              "aiAnswer": "A concise, natural-language explanation of what was asked"
            }
            
            Examples:
            - "Who marked attendance today?" -> {"staffName": null, "dateFrom": "$todayStr", "dateTo": "$todayStr", "timeFrom": null, "timeTo": null, "aiAnswer": "Filtering check-in records for today ($todayStr)."}
            - "who checked in between 9 am to 10 am" -> {"staffName": null, "dateFrom": "$todayStr", "dateTo": "$todayStr", "timeFrom": "09:00", "timeTo": "10:00", "aiAnswer": "Filtering check-ins between 09:00 AM and 10:00 AM."}
            - "Did Alice check in this morning?" -> {"staffName": "Alice", "dateFrom": "$todayStr", "dateTo": "$todayStr", "timeFrom": "06:00", "timeTo": "12:00", "aiAnswer": "Filtering check-ins for Alice this morning."}
            
            Return ONLY valid raw JSON.
        """.trimIndent()

        var lastError = ""

        // Try candidate models in order
        for (model in candidateModels) {
            try {
                val requestBodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userQuestion)
                        })
                    })
                    put("temperature", 0.1)
                    put("response_format", JSONObject().put("type", "json_object"))
                }

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    lastError = "Model $model returned $err"
                    continue
                }

                val responseString = response.body?.string() ?: ""
                val responseJson = JSONObject(responseString)
                val choices = responseJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    lastError = "Empty choices from $model"
                    continue
                }

                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val filterJson = JSONObject(content)

                val staffName = filterJson.optString("staffName").takeIf { it.isNotBlank() && it != "null" }
                val dateFrom = filterJson.optString("dateFrom").takeIf { it.isNotBlank() && it != "null" }
                val dateTo = filterJson.optString("dateTo").takeIf { it.isNotBlank() && it != "null" }
                val timeFrom = filterJson.optString("timeFrom").takeIf { it.isNotBlank() && it != "null" }
                val timeTo = filterJson.optString("timeTo").takeIf { it.isNotBlank() && it != "null" }
                val aiAnswer = filterJson.optString("aiAnswer", "Found matching attendance records based on your query.")

                val filter = StructuredFilter(
                    staffName = staffName,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    timeFrom = timeFrom,
                    timeTo = timeTo
                )

                val filteredRecords = applyFilter(allRecords, filter)

                val finalAnswer = if (filteredRecords.isEmpty()) {
                    if (timeFrom != null && timeTo != null) {
                        "No check-ins recorded between $timeFrom and $timeTo."
                    } else if (staffName != null) {
                        "No attendance records found for $staffName."
                    } else {
                        "No attendance records matched your query."
                    }
                } else {
                    aiAnswer
                }

                return@withContext AgentQueryResult(
                    userQuery = userQuestion,
                    structuredFilter = filter,
                    aiAnswer = finalAnswer,
                    matchingRecords = filteredRecords,
                    isSuccess = true
                )
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network error"
            }
        }

        // Fallback if all models failed or network is down
        localFallbackQuery(
            userQuestion,
            allStaff,
            allRecords,
            "Groq AI fallback ($lastError). Showing on-device query results."
        )
    }

    override suspend fun generateDailySummary(
        allStaff: List<Staff>,
        todayRecords: List<AttendanceRecord>
    ): DailySummaryResult = withContext(Dispatchers.IO) {
        val totalStaff = allStaff.size
        val presentCount = todayRecords.map { it.staffId }.distinct().size
        val absentStaff = allStaff.filter { staff -> todayRecords.none { it.staffId == staff.id } }

        val checkInList = todayRecords.joinToString("; ") {
            "${it.staffName} (${it.formattedShortTime}, ${it.address.take(30)})"
        }
        val absentList = absentStaff.joinToString(", ") { it.name }

        if (apiKey.isBlank()) {
            return@withContext DailySummaryResult(
                summaryMarkdown = "• **Attendance Rate**: $presentCount / $totalStaff present today.\n" +
                        "• **Present**: ${if (checkInList.isNotBlank()) checkInList else "None so far"}\n" +
                        "• **Not Yet Marked**: ${if (absentList.isNotBlank()) absentList else "Everyone marked!"}",
                isSuccess = true
            )
        }

        val prompt = """
            You are an executive HR and attendance analyst. Generate a concise 3-4 bullet point executive summary of today's attendance.
            - Total registered staff: $totalStaff
            - Total checked in: $presentCount
            - Check-in records: [$checkInList]
            - Not marked / absent staff: [$absentList]
            
            Provide 3-4 concise, professional bullet points highlighting presence, absence, and any timing insights.
        """.trimIndent()

        for (model in candidateModels) {
            try {
                val requestBodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are an executive attendance analytics assistant. Respond only with concise, formatted bullet points.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.3)
                    put("max_tokens", 350)
                }

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: ""
                val choices = JSONObject(body).optJSONArray("choices")
                val summaryText = choices?.getJSONObject(0)?.getJSONObject("message")?.getString("content")
                if (!summaryText.isNullOrBlank()) {
                    return@withContext DailySummaryResult(summaryMarkdown = summaryText.trim(), isSuccess = true)
                }
            } catch (_: Exception) {}
        }

        // Offline fallback
        DailySummaryResult(
            summaryMarkdown = "• **Status**: $presentCount of $totalStaff staff marked attendance today.\n" +
                    "• **Checked in**: ${if (checkInList.isNotBlank()) checkInList else "None"}\n" +
                    "• **Pending**: ${if (absentList.isNotBlank()) absentList else "None"}",
            isSuccess = true,
            errorMessage = "Offline summary fallback used"
        )
    }

    private fun localFallbackQuery(
        userQuestion: String,
        allStaff: List<Staff>,
        allRecords: List<AttendanceRecord>,
        notice: String
    ): AgentQueryResult {
        val q = userQuestion.lowercase(Locale.getDefault())

        val matchedStaff = allStaff.find {
            q.contains(it.name.lowercase(Locale.getDefault())) ||
            q.contains(it.employeeId.lowercase(Locale.getDefault()))
        }

        val isToday = q.contains("today") || q.contains("now")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Parse time range if asked, e.g. "between 9 am to 10 am", "9 to 10 am", "9:00 to 10:00"
        val timeRegex = Regex("""(?:between|from)?\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s*(?:to|and|-)\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)""", RegexOption.IGNORE_CASE)
        val timeMatch = timeRegex.find(userQuestion)
        val timeFrom = timeMatch?.groupValues?.get(1)?.trim()
        val timeTo = timeMatch?.groupValues?.get(2)?.trim()

        val filter = StructuredFilter(
            staffName = matchedStaff?.name,
            dateFrom = if (isToday) todayStr else null,
            dateTo = if (isToday) todayStr else null,
            timeFrom = timeFrom,
            timeTo = timeTo
        )

        val filtered = applyFilter(allRecords, filter)
        val answer = if (filtered.isEmpty()) {
            if (timeFrom != null && timeTo != null) {
                "No check-ins found between $timeFrom and $timeTo."
            } else if (matchedStaff != null) {
                "No records found for ${matchedStaff.name}."
            } else {
                "No attendance records matched your query."
            }
        } else if (matchedStaff != null) {
            "Filtered ${filtered.size} record(s) for ${matchedStaff.name} (${matchedStaff.employeeId})."
        } else if (timeFrom != null && timeTo != null) {
            "Filtered ${filtered.size} check-in(s) between $timeFrom and $timeTo."
        } else if (isToday) {
            "Filtered ${filtered.size} attendance record(s) for today ($todayStr)."
        } else {
            "Found ${filtered.size} records matching your query."
        }

        return AgentQueryResult(
            userQuery = userQuestion,
            structuredFilter = filter,
            aiAnswer = answer,
            matchingRecords = filtered,
            isSuccess = true,
            errorMessage = notice
        )
    }

    companion object {
        fun parseTimeToMinutes(timeStr: String?): Int? {
            if (timeStr.isNullOrBlank()) return null
            val clean = timeStr.trim().lowercase(Locale.getDefault())

            val formats = listOf(
                SimpleDateFormat("HH:mm", Locale.getDefault()),
                SimpleDateFormat("H:mm", Locale.getDefault()),
                SimpleDateFormat("hh:mm a", Locale.getDefault()),
                SimpleDateFormat("h:mm a", Locale.getDefault()),
                SimpleDateFormat("h a", Locale.getDefault()),
                SimpleDateFormat("ha", Locale.getDefault())
            )

            for (sdf in formats) {
                try {
                    val date = sdf.parse(clean)
                    if (date != null) {
                        val cal = Calendar.getInstance().apply { time = date }
                        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    }
                } catch (_: Exception) {}
            }

            // Regex fallback for "9", "9am", "2pm", "14:30"
            val regex = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""")
            val match = regex.find(clean)
            if (match != null) {
                var hour = match.groupValues[1].toIntOrNull() ?: return null
                val min = match.groupValues[2].toIntOrNull() ?: 0
                val ampm = match.groupValues[3]
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                return hour * 60 + min
            }

            return null
        }

        fun applyFilter(records: List<AttendanceRecord>, filter: StructuredFilter): List<AttendanceRecord> {
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val filterFromMin = parseTimeToMinutes(filter.timeFrom)
            val filterToMin = parseTimeToMinutes(filter.timeTo)

            return records.filter { record ->
                // 1. Staff Name / ID filter
                if (!filter.staffName.isNullOrBlank()) {
                    val target = filter.staffName.trim().lowercase(Locale.getDefault())
                    val nameMatch = record.staffName.lowercase(Locale.getDefault()).contains(target)
                    val idMatch = record.employeeId.lowercase(Locale.getDefault()).contains(target)
                    if (!nameMatch && !idMatch) return@filter false
                }

                // 2. Date filter (yyyy-MM-dd)
                val recordDateStr = sdfDate.format(Date(record.timestamp))
                if (!filter.dateFrom.isNullOrBlank() && recordDateStr < filter.dateFrom) {
                    return@filter false
                }
                if (!filter.dateTo.isNullOrBlank() && recordDateStr > filter.dateTo) {
                    return@filter false
                }

                // 3. Time filter (minutes from midnight 0..1439)
                if (filterFromMin != null || filterToMin != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                    val recordMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

                    if (filterFromMin != null && recordMin < filterFromMin) {
                        return@filter false
                    }
                    if (filterToMin != null && recordMin > filterToMin) {
                        return@filter false
                    }
                }

                true
            }
        }
    }
}
