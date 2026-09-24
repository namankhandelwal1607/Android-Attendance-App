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
    val timeFrom: String? = null, // HH:mm
    val timeTo: String? = null    // HH:mm
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun queryAttendance(
        userQuestion: String,
        allStaff: List<Staff>,
        allRecords: List<AttendanceRecord>
    ): AgentQueryResult = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val staffRoster = allStaff.joinToString(", ") { "${it.name} (${it.employeeId})" }

        // If no API key is provided, perform smart local heuristic fallback
        if (apiKey.isBlank()) {
            return@withContext localFallbackQuery(
                userQuestion,
                allStaff,
                allRecords,
                "Groq API key not configured. Showing local search results."
            )
        }

        try {
            val systemPrompt = """
                You are the SmartAttendance AI Assistant. Your job is to convert natural language queries about employee attendance into a JSON filter.
                Today's Date: $todayStr
                Registered Staff Roster: [$staffRoster]
                
                You must return a single JSON object with this exact schema:
                {
                  "staffName": "Name or employee ID if mentioned, or null",
                  "dateFrom": "YYYY-MM-DD or null",
                  "dateTo": "YYYY-MM-DD or null",
                  "timeFrom": "HH:mm (24-hour) or null",
                  "timeTo": "HH:mm (24-hour) or null",
                  "aiAnswer": "A concise, natural-language explanation of what was asked and what criteria are being filtered"
                }
                
                Examples:
                - "Who marked attendance today?" -> {"staffName": null, "dateFrom": "$todayStr", "dateTo": "$todayStr", "timeFrom": null, "timeTo": null, "aiAnswer": "Filtering all check-in records for today ($todayStr)."}
                - "Show Rohan between 9 AM and 10 AM" -> {"staffName": "Rohan", "dateFrom": null, "dateTo": null, "timeFrom": "09:00", "timeTo": "10:00", "aiAnswer": "Filtering check-ins for Rohan between 9:00 AM and 10:00 AM."}
                
                Return ONLY valid raw JSON. Do not wrap in markdown quotes or code fences.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
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
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                return@withContext localFallbackQuery(
                    userQuestion,
                    allStaff,
                    allRecords,
                    "Groq API returned $errorBody. Showing local search fallback."
                )
            }

            val responseString = response.body?.string() ?: ""
            val responseJson = JSONObject(responseString)
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext localFallbackQuery(userQuestion, allStaff, allRecords, "Empty response from Groq.")
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

            AgentQueryResult(
                userQuery = userQuestion,
                structuredFilter = filter,
                aiAnswer = aiAnswer,
                matchingRecords = filteredRecords,
                isSuccess = true
            )
        } catch (e: Exception) {
            localFallbackQuery(
                userQuestion,
                allStaff,
                allRecords,
                "AI query error (${e.localizedMessage ?: "Network error"}). Showing local search fallback."
            )
        }
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

        try {
            val prompt = """
                You are an executive HR and attendance analyst. Generate a concise 3-4 bullet point executive summary of today's attendance.
                - Total registered staff: $totalStaff
                - Total checked in: $presentCount
                - Check-in records: [$checkInList]
                - Not marked / absent staff: [$absentList]
                
                Provide 3-4 concise, professional bullet points highlighting presence, absence, and any timing insights.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an executive attendance analytics assistant. Respond only with concise bullet points.")
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
            if (!response.isSuccessful) {
                return@withContext DailySummaryResult(
                    summaryMarkdown = "• **Status**: $presentCount of $totalStaff staff marked attendance today.\n• **Checked in**: ${if (checkInList.isNotBlank()) checkInList else "None"}\n• **Pending**: ${if (absentList.isNotBlank()) absentList else "None"}",
                    isSuccess = true,
                    errorMessage = "Groq summary fallback used (HTTP ${response.code})"
                )
            }

            val body = response.body?.string() ?: ""
            val choices = JSONObject(body).optJSONArray("choices")
            val summaryText = choices?.getJSONObject(0)?.getJSONObject("message")?.getString("content")
                ?: "• $presentCount of $totalStaff staff marked today."

            DailySummaryResult(summaryMarkdown = summaryText.trim(), isSuccess = true)
        } catch (e: Exception) {
            DailySummaryResult(
                summaryMarkdown = "• **Status**: $presentCount of $totalStaff staff marked attendance today.\n• **Checked in**: ${if (checkInList.isNotBlank()) checkInList else "None"}\n• **Pending**: ${if (absentList.isNotBlank()) absentList else "None"}",
                isSuccess = true,
                errorMessage = "Offline summary fallback: ${e.localizedMessage}"
            )
        }
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

        val filter = StructuredFilter(
            staffName = matchedStaff?.name,
            dateFrom = if (isToday) todayStr else null,
            dateTo = if (isToday) todayStr else null
        )

        val filtered = applyFilter(allRecords, filter)
        val answer = if (matchedStaff != null) {
            "Filtered records for ${matchedStaff.name} (${matchedStaff.employeeId})."
        } else if (isToday) {
            "Filtered attendance records for today ($todayStr)."
        } else {
            "Found ${filtered.size} records matching your query."
        }

        return AgentQueryResult(
            userQuery = userQuestion,
            structuredFilter = filter,
            aiAnswer = answer,
            matchingRecords = filtered,
            isSuccess = false,
            errorMessage = notice
        )
    }

    companion object {
        fun applyFilter(records: List<AttendanceRecord>, filter: StructuredFilter): List<AttendanceRecord> {
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

            return records.filter { record ->
                // 1. Staff Name / ID filter
                if (!filter.staffName.isNullOrBlank()) {
                    val target = filter.staffName.trim().lowercase(Locale.getDefault())
                    val nameMatch = record.staffName.lowercase(Locale.getDefault()).contains(target)
                    val idMatch = record.employeeId.lowercase(Locale.getDefault()).contains(target)
                    if (!nameMatch && !idMatch) return@filter false
                }

                // 2. Date filter
                val recordDateStr = sdfDate.format(Date(record.timestamp))
                if (!filter.dateFrom.isNullOrBlank() && recordDateStr < filter.dateFrom) {
                    return@filter false
                }
                if (!filter.dateTo.isNullOrBlank() && recordDateStr > filter.dateTo) {
                    return@filter false
                }

                // 3. Time filter (within day)
                val recordTimeStr = sdfTime.format(Date(record.timestamp))
                if (!filter.timeFrom.isNullOrBlank() && recordTimeStr < filter.timeFrom) {
                    return@filter false
                }
                if (!filter.timeTo.isNullOrBlank() && recordTimeStr > filter.timeTo) {
                    return@filter false
                }

                true
            }
        }
    }
}
