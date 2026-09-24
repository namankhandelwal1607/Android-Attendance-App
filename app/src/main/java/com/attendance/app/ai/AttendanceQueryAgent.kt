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

enum class QueryIntent {
    FILTER_RECORDS,
    COUNT_STAFF,
    CURRENTLY_CHECKED_IN,
    CHECKED_OUT_TODAY,
    HOURS_WORKED_TODAY,
    GENERAL_STATS,
    CLARIFY
}

data class StructuredFilter(
    val staffName: String? = null,
    val dateFrom: String? = null, // YYYY-MM-DD
    val dateTo: String? = null,   // YYYY-MM-DD
    val timeFrom: String? = null, // HH:mm or e.g. 9:00 AM
    val timeTo: String? = null,   // HH:mm or e.g. 10:00 AM
    val eventType: String? = null, // "CHECK_IN", "CHECK_OUT", or "EITHER"
    val hourThreshold: Double = 8.0,
    val intent: QueryIntent = QueryIntent.FILTER_RECORDS
)

data class AgentQueryResult(
    val userQuery: String,
    val intent: QueryIntent,
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
                "Groq API key not configured. Using smart on-device analytics."
            )
        }

        // STEP 1: Intent Classification Prompt
        val classificationPrompt = """
            You are the SmartAttendance AI Intent Classifier.
            Today's Date: $todayStr
            Registered Staff Roster: [$staffRoster]
            
            Classify the user's question into EXACTLY ONE of these intents:
            - "COUNT_STAFF": asking how many staff exist/registered (e.g. "how many users are registered", "total staff count")
            - "CURRENTLY_CHECKED_IN": asking who is currently present/checked in right now (e.g. "who is currently checked in", "who is in the office")
            - "CHECKED_OUT_TODAY": asking who has completed checkout today (e.g. "who has checked out today", "who left today")
            - "HOURS_WORKED_TODAY": asking about hours worked (e.g. "who worked 8 hours today", "how many people worked 8+ hours", "who worked 6 hours")
            - "GENERAL_STATS": general summaries, averages, or attendance overviews
            - "FILTER_RECORDS": specific record lookups (e.g. "show Alice's attendance", "who checked in between 9 and 10 AM", "attendance this week")
            - "CLARIFY": completely ambiguous, nonsensical, or unrelated query
            
            Return JSON with this schema:
            {
              "intent": "COUNT_STAFF" | "CURRENTLY_CHECKED_IN" | "CHECKED_OUT_TODAY" | "HOURS_WORKED_TODAY" | "GENERAL_STATS" | "FILTER_RECORDS" | "CLARIFY",
              "staffName": "Name or employee ID if specified, or null",
              "dateFrom": "YYYY-MM-DD or null",
              "dateTo": "YYYY-MM-DD or null",
              "timeFrom": "HH:mm or null",
              "timeTo": "HH:mm or null",
              "eventType": "CHECK_IN" | "CHECK_OUT" | "EITHER",
              "hourThreshold": 8.0,
              "clarifyingQuestion": "Question to user if CLARIFY, or null"
            }
        """.trimIndent()

        var parsedFilter: StructuredFilter? = null
        var intent = QueryIntent.FILTER_RECORDS
        var clarifyingQuestion: String? = null

        // Try candidate models for Step 1
        for (model in candidateModels) {
            try {
                val reqJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", classificationPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userQuestion)
                        })
                    })
                    put("temperature", 0.1)
                    put("response_format", JSONObject().put("type", "json_object"))
                }

                val req = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(reqJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val bodyStr = resp.body?.string() ?: ""
                val choices = JSONObject(bodyStr).optJSONArray("choices")
                if (choices == null || choices.length() == 0) continue

                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val json = JSONObject(content)

                val intentStr = json.optString("intent", "FILTER_RECORDS").uppercase()
                intent = try {
                    QueryIntent.valueOf(intentStr)
                } catch (_: Exception) {
                    QueryIntent.FILTER_RECORDS
                }

                val staffName = json.optString("staffName").takeIf { it.isNotBlank() && it != "null" }
                val dateFrom = json.optString("dateFrom").takeIf { it.isNotBlank() && it != "null" }
                val dateTo = json.optString("dateTo").takeIf { it.isNotBlank() && it != "null" }
                val timeFrom = json.optString("timeFrom").takeIf { it.isNotBlank() && it != "null" }
                val timeTo = json.optString("timeTo").takeIf { it.isNotBlank() && it != "null" }
                val eventType = json.optString("eventType", "EITHER")
                val threshold = json.optDouble("hourThreshold", 8.0)
                clarifyingQuestion = json.optString("clarifyingQuestion").takeIf { it.isNotBlank() && it != "null" }

                parsedFilter = StructuredFilter(
                    staffName = staffName,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    timeFrom = timeFrom,
                    timeTo = timeTo,
                    eventType = eventType,
                    hourThreshold = if (threshold.isNaN() || threshold <= 0.0) 8.0 else threshold,
                    intent = intent
                )
                break
            } catch (_: Exception) {}
        }

        if (parsedFilter == null) {
            return@withContext localFallbackQuery(userQuestion, allStaff, allRecords, "Groq connection issue. Showing local search.")
        }

        if (intent == QueryIntent.CLARIFY && clarifyingQuestion != null) {
            return@withContext AgentQueryResult(
                userQuery = userQuestion,
                intent = QueryIntent.CLARIFY,
                structuredFilter = parsedFilter,
                aiAnswer = clarifyingQuestion,
                matchingRecords = emptyList(),
                isSuccess = true
            )
        }

        // STEP 2: Execute Real Room SQLite Query On-Device
        val executionResult = executeIntentQuery(intent, parsedFilter, allStaff, allRecords)
        val computedData = executionResult.computedSummary
        val matchingRecords = executionResult.records

        // STEP 3: Second call to Groq to phrase the real computed data naturally
        val narrativeAnswer = phraseComputedDataWithAi(userQuestion, computedData, executionResult.defaultNarrative)

        AgentQueryResult(
            userQuery = userQuestion,
            intent = intent,
            structuredFilter = parsedFilter,
            aiAnswer = narrativeAnswer,
            matchingRecords = matchingRecords,
            isSuccess = true
        )
    }

    private data class IntentExecution(
        val computedSummary: String,
        val defaultNarrative: String,
        val records: List<AttendanceRecord>
    )

    private fun executeIntentQuery(
        intent: QueryIntent,
        filter: StructuredFilter,
        allStaff: List<Staff>,
        allRecords: List<AttendanceRecord>
    ): IntentExecution {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val todayRecords = allRecords.filter { it.timestamp >= startOfDay }

        return when (intent) {
            QueryIntent.COUNT_STAFF -> {
                val total = allStaff.size
                val activeWithRecords = allStaff.count { s -> allRecords.any { it.staffId == s.id } }
                val inactive = total - activeWithRecords
                val summary = "Total registered staff: $total. Staff with attendance history: $activeWithRecords. Staff without records: $inactive."
                val narrative = "There are **$total staff member(s)** registered in the system ($activeWithRecords active with attendance history)."
                IntentExecution(summary, narrative, emptyList())
            }

            QueryIntent.CURRENTLY_CHECKED_IN -> {
                // Find staff whose latest record today is CHECK_IN
                val checkedInStaff = mutableListOf<Pair<Staff, AttendanceRecord>>()
                for (staff in allStaff) {
                    val staffToday = todayRecords.filter { it.staffId == staff.id }
                    val latest = staffToday.maxByOrNull { it.timestamp }
                    if (latest != null && latest.isCheckIn) {
                        checkedInStaff.add(staff to latest)
                    }
                }
                val summary = "Currently checked in: ${checkedInStaff.size} staff member(s): " +
                        checkedInStaff.joinToString(", ") { "${it.first.name} (${it.second.formattedShortTime})" }
                val narrative = if (checkedInStaff.isEmpty()) {
                    "No staff members are currently checked in today."
                } else {
                    "Currently, **${checkedInStaff.size} staff member(s)** are checked in: " +
                            checkedInStaff.joinToString(", ") { "**${it.first.name}** (${it.second.formattedShortTime})" } + "."
                }
                IntentExecution(summary, narrative, checkedInStaff.map { it.second })
            }

            QueryIntent.CHECKED_OUT_TODAY -> {
                val checkedOutStaff = mutableListOf<Pair<Staff, AttendanceRecord>>()
                for (staff in allStaff) {
                    val staffToday = todayRecords.filter { it.staffId == staff.id }
                    val checkOut = staffToday.filter { it.isCheckOut }.maxByOrNull { it.timestamp }
                    if (checkOut != null) {
                        checkedOutStaff.add(staff to checkOut)
                    }
                }
                val summary = "Checked out today: ${checkedOutStaff.size} staff member(s): " +
                        checkedOutStaff.joinToString(", ") { "${it.first.name} (${it.second.formattedShortTime}, ${it.second.formattedHours})" }
                val narrative = if (checkedOutStaff.isEmpty()) {
                    "No staff members have checked out yet today."
                } else {
                    "**${checkedOutStaff.size} staff member(s)** have checked out today: " +
                            checkedOutStaff.joinToString(", ") { "**${it.first.name}** at ${it.second.formattedShortTime} (${it.second.formattedHours} worked)" } + "."
                }
                IntentExecution(summary, narrative, checkedOutStaff.map { it.second })
            }

            QueryIntent.HOURS_WORKED_TODAY -> {
                val threshold = filter.hourThreshold
                val qualifying = mutableListOf<Pair<Staff, AttendanceRecord>>()
                for (staff in allStaff) {
                    val staffToday = todayRecords.filter { it.staffId == staff.id }
                    val checkOut = staffToday.filter { it.isCheckOut }.maxByOrNull { it.timestamp }
                    if (checkOut != null && checkOut.hoursWorked >= threshold) {
                        qualifying.add(staff to checkOut)
                    }
                }
                val summary = "${qualifying.size} staff member(s) worked >= ${threshold}h today: " +
                        qualifying.joinToString(", ") { "${it.first.name} (${it.second.formattedHours})" }
                val narrative = if (qualifying.isEmpty()) {
                    "No staff members have worked **${threshold.toInt()}+ hours** today yet."
                } else {
                    "**${qualifying.size} staff member(s)** worked **${threshold.toInt()}+ hours** today: " +
                            qualifying.joinToString(", ") { "**${it.first.name}** (${it.second.formattedHours})" } + "."
                }
                IntentExecution(summary, narrative, qualifying.map { it.second })
            }

            QueryIntent.GENERAL_STATS -> {
                val totalStaff = allStaff.size
                val checkInsToday = todayRecords.count { it.isCheckIn }
                val checkOutsToday = todayRecords.count { it.isCheckOut }
                val totalHoursToday = todayRecords.filter { it.isCheckOut }.sumOf { it.hoursWorked }
                val avgHours = if (checkOutsToday > 0) totalHoursToday / checkOutsToday else 0.0
                val summary = "Total registered: $totalStaff. Check-ins today: $checkInsToday. Check-outs today: $checkOutsToday. Avg hours worked: %.1fh.".format(avgHours)
                val narrative = "Today, **$checkInsToday staff** checked in and **$checkOutsToday** checked out (average shift: **%.1f hours**).".format(avgHours)
                IntentExecution(summary, narrative, todayRecords)
            }

            QueryIntent.FILTER_RECORDS, QueryIntent.CLARIFY -> {
                val matching = applyFilter(allRecords, filter)
                val summary = "Found ${matching.size} matching attendance record(s)."
                val narrative = if (matching.isEmpty()) {
                    if (filter.timeFrom != null && filter.timeTo != null) {
                        "No staff marked attendance between **${filter.timeFrom}** and **${filter.timeTo}**."
                    } else if (filter.staffName != null) {
                        "No attendance records found for **${filter.staffName}**."
                    } else {
                        "No records matched your search criteria."
                    }
                } else {
                    "Found **${matching.size} matching record(s)**."
                }
                IntentExecution(summary, narrative, matching)
            }
        }
    }

    private suspend fun phraseComputedDataWithAi(
        userQuestion: String,
        computedData: String,
        fallbackNarrative: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext fallbackNarrative

        val prompt = """
            User asked: "$userQuestion"
            Actual verified application data: "$computedData"
            
            Instruction: State the answer clearly in 1 or 2 friendly sentences for the Admin.
            - Base your answer strictly on the provided data numbers.
            - Do not invent names or statistics not in the data.
            - Format key numbers or staff names with **bold**.
        """.trimIndent()

        for (model in candidateModels) {
            try {
                val reqJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are an executive HR assistant. Answer strictly according to the facts.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.2)
                    put("max_tokens", 150)
                }

                val req = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(reqJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val body = resp.body?.string() ?: ""
                val choices = JSONObject(body).optJSONArray("choices")
                val text = choices?.getJSONObject(0)?.getJSONObject("message")?.getString("content")
                if (!text.isNullOrBlank()) {
                    return@withContext text.trim()
                }
            } catch (_: Exception) {}
        }

        fallbackNarrative
    }

    override suspend fun generateDailySummary(
        allStaff: List<Staff>,
        todayRecords: List<AttendanceRecord>
    ): DailySummaryResult = withContext(Dispatchers.IO) {
        val totalStaff = allStaff.size
        val checkInRecords = todayRecords.filter { it.isCheckIn }
        val checkOutRecords = todayRecords.filter { it.isCheckOut }
        val presentCount = checkInRecords.map { it.staffId }.distinct().size
        val absentStaff = allStaff.filter { staff -> checkInRecords.none { it.staffId == staff.id } }

        val checkInList = checkInRecords.joinToString("; ") {
            "${it.staffName} (${it.formattedShortTime}, ${it.address.take(25)})"
        }
        val checkOutList = checkOutRecords.joinToString("; ") {
            "${it.staffName} (${it.formattedShortTime}, ${it.formattedHours})"
        }
        val absentList = absentStaff.joinToString(", ") { it.name }

        if (apiKey.isBlank()) {
            return@withContext DailySummaryResult(
                summaryMarkdown = "• **Attendance Rate**: $presentCount / $totalStaff present today.\n" +
                        "• **Check-ins**: ${if (checkInList.isNotBlank()) checkInList else "None"}\n" +
                        "• **Check-outs**: ${if (checkOutList.isNotBlank()) checkOutList else "None"}\n" +
                        "• **Absent / Pending**: ${if (absentList.isNotBlank()) absentList else "Everyone present!"}",
                isSuccess = true
            )
        }

        val prompt = """
            You are an executive HR and attendance analyst. Generate a concise 3-4 bullet point executive summary of today's attendance.
            - Total registered staff: $totalStaff
            - Total checked in: $presentCount
            - Check-in logs: [$checkInList]
            - Check-out logs: [$checkOutList]
            - Unmarked / absent staff: [$absentList]
            
            Provide 3-4 concise, professional bullet points highlighting attendance rate, arrivals, departures, and hours worked.
        """.trimIndent()

        for (model in candidateModels) {
            try {
                val reqJson = JSONObject().apply {
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

                val req = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(reqJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val body = resp.body?.string() ?: ""
                val choices = JSONObject(body).optJSONArray("choices")
                val summaryText = choices?.getJSONObject(0)?.getJSONObject("message")?.getString("content")
                if (!summaryText.isNullOrBlank()) {
                    return@withContext DailySummaryResult(summaryMarkdown = summaryText.trim(), isSuccess = true)
                }
            } catch (_: Exception) {}
        }

        // Offline fallback
        DailySummaryResult(
            summaryMarkdown = "• **Attendance Rate**: $presentCount / $totalStaff present today.\n" +
                    "• **Check-ins**: ${if (checkInList.isNotBlank()) checkInList else "None"}\n" +
                    "• **Check-outs**: ${if (checkOutList.isNotBlank()) checkOutList else "None"}\n" +
                    "• **Pending**: ${if (absentList.isNotBlank()) absentList else "Everyone present!"}",
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

        val intent = when {
            q.contains("how many") && (q.contains("user") || q.contains("staff") || q.contains("registered")) -> QueryIntent.COUNT_STAFF
            q.contains("currently") || (q.contains("who is") && q.contains("checked in")) -> QueryIntent.CURRENTLY_CHECKED_IN
            q.contains("checked out") -> QueryIntent.CHECKED_OUT_TODAY
            q.contains("hour") && (q.contains("worked") || q.contains("8") || q.contains("6")) -> QueryIntent.HOURS_WORKED_TODAY
            else -> QueryIntent.FILTER_RECORDS
        }

        val matchedStaff = allStaff.find {
            q.contains(it.name.lowercase(Locale.getDefault())) ||
            q.contains(it.employeeId.lowercase(Locale.getDefault()))
        }

        val isToday = q.contains("today") || q.contains("now")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val timeRegex = Regex("""(?:between|from)?\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s*(?:to|and|-)\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)""", RegexOption.IGNORE_CASE)
        val timeMatch = timeRegex.find(userQuestion)
        val timeFrom = timeMatch?.groupValues?.get(1)?.trim()
        val timeTo = timeMatch?.groupValues?.get(2)?.trim()

        // Extract hour threshold if asking about hours
        val hourRegex = Regex("""(\d+)\s*(?:\+|plus)?\s*hours?""", RegexOption.IGNORE_CASE)
        val hourMatch = hourRegex.find(userQuestion)
        val threshold = hourMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 8.0

        val filter = StructuredFilter(
            staffName = matchedStaff?.name,
            dateFrom = if (isToday) todayStr else null,
            dateTo = if (isToday) todayStr else null,
            timeFrom = timeFrom,
            timeTo = timeTo,
            hourThreshold = threshold,
            intent = intent
        )

        val exec = executeIntentQuery(intent, filter, allStaff, allRecords)

        return AgentQueryResult(
            userQuery = userQuestion,
            intent = intent,
            structuredFilter = filter,
            aiAnswer = exec.defaultNarrative,
            matchingRecords = exec.records,
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

                // 4. Event type filter (CHECK_IN, CHECK_OUT, or EITHER)
                if (!filter.eventType.isNullOrBlank() && filter.eventType != "EITHER") {
                    if (record.type != filter.eventType) {
                        return@filter false
                    }
                }

                true
            }
        }
    }
}
