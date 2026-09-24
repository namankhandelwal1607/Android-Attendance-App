package com.attendance.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.attendance.app.ai.AgentQueryResult
import com.attendance.app.ai.AttendanceQueryAgent
import com.attendance.app.ai.DailySummaryResult
import com.attendance.app.ai.GroqAttendanceQueryAgent
import com.attendance.app.data.local.AdminUserDao
import com.attendance.app.data.local.AttendanceDao
import com.attendance.app.data.local.StaffDao
import com.attendance.app.data.model.AdminUser
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.model.DailyAttendancePair
import com.attendance.app.data.model.Staff
import com.attendance.app.location.LocationData
import com.attendance.app.ml.FaceDetectorHelper
import com.attendance.app.ml.FaceNetModelHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class StaffAttendanceTodayStatus(
    val staff: Staff,
    val checkIn: AttendanceRecord?,
    val checkOut: AttendanceRecord?,
    val hasOpenCheckIn: Boolean,
    val canCheckIn: Boolean,
    val canCheckOut: Boolean,
    val hoursWorked: Double
) {
    val formattedCheckInTime: String get() = checkIn?.formattedShortTime ?: "--:--"
    val formattedCheckOutTime: String get() = checkOut?.formattedShortTime ?: "--:--"
    val formattedHours: String
        get() = if (hoursWorked > 0.0) {
            val h = hoursWorked.toInt()
            val m = ((hoursWorked - h) * 60).toInt()
            "${h}h ${m}m"
        } else "--:--"

    val expectedAction: String
        get() = if (canCheckOut) AttendanceRecord.TYPE_CHECK_OUT else AttendanceRecord.TYPE_CHECK_IN
}

sealed class KioskIdentificationState {
    object Idle : KioskIdentificationState()
    object Processing : KioskIdentificationState()
    object NoFaceDetected : KioskIdentificationState()
    object NoStaffRegistered : KioskIdentificationState()
    data class Unrecognized(val highestScore: Float) : KioskIdentificationState()
    data class Identified(
        val staff: Staff,
        val matchPercentage: Int,
        val status: StaffAttendanceTodayStatus,
        val selfieBitmap: Bitmap
    ) : KioskIdentificationState()
    data class Recorded(
        val staff: Staff,
        val record: AttendanceRecord,
        val status: StaffAttendanceTodayStatus,
        val actionType: String,
        val matchPercentage: Int
    ) : KioskIdentificationState()
    data class Error(val message: String) : KioskIdentificationState()
}

// Backward compatibility alias
typealias KioskAttendanceResult = KioskIdentificationState

class AttendanceRepository(
    private val staffDao: StaffDao,
    private val attendanceDao: AttendanceDao,
    private val adminUserDao: AdminUserDao,
    private val faceNetHelper: FaceNetModelHelper,
    private val faceDetectorHelper: FaceDetectorHelper,
    private val aiAgent: AttendanceQueryAgent = GroqAttendanceQueryAgent(),
    private val context: Context
) {
    val allStaff: Flow<List<Staff>> = staffDao.getAllStaff()
    val allRecords: Flow<List<AttendanceRecord>> = attendanceDao.getAllRecords()
    val staffCount: Flow<Int> = staffDao.getStaffCount()

    fun getRecordsForStaff(staffId: Long): Flow<List<AttendanceRecord>> {
        return attendanceDao.getRecordsForStaff(staffId)
    }

    fun getTodayAttendanceCount(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return attendanceDao.getTodayAttendanceCount(calendar.timeInMillis)
    }

    fun getTodayRecords(): Flow<List<AttendanceRecord>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return attendanceDao.getTodayRecords(calendar.timeInMillis)
    }

    suspend fun seedDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        val count = adminUserDao.getAdminCount()
        if (count == 0) {
            adminUserDao.insertAdmin(
                AdminUser(
                    username = "admin",
                    password = "admin123"
                )
            )
        }
    }

    suspend fun authenticateAdmin(u: String, p: String): Boolean = withContext(Dispatchers.IO) {
        val admin = adminUserDao.authenticateAdmin(u, p)
        admin != null
    }

    suspend fun authenticateStaff(identifier: String, p: String): Staff? = withContext(Dispatchers.IO) {
        staffDao.authenticateStaff(identifier, p)
    }

    suspend fun registerStaff(
        name: String,
        employeeId: String,
        username: String,
        password: String,
        faceEmbedding: FloatArray,
        photoBitmap: Bitmap
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val photoPath = saveBitmapToFile(photoBitmap, "staff_${employeeId}_${System.currentTimeMillis()}.jpg")
            val newStaff = Staff(
                name = name.trim(),
                employeeId = employeeId.trim(),
                username = username.trim().lowercase(),
                password = password.trim(),
                faceEmbedding = Staff.embeddingToString(faceEmbedding),
                photoPath = photoPath,
                enrolledAt = System.currentTimeMillis()
            )
            val insertedId = staffDao.insertStaff(newStaff)
            Result.success(insertedId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compute today's attendance status for a staff member:
     * Check if they have an open CHECK_IN (not yet checked out), latest CHECK_OUT, and hours worked.
     */
    suspend fun getStaffTodayStatus(staff: Staff): StaffAttendanceTodayStatus = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        val todayRecords = attendanceDao.getTodayRecordsForStaffSync(staff.id, startOfDay)
        val checkIn = todayRecords.filter { it.isCheckIn }.maxByOrNull { it.timestamp }
        val checkOut = todayRecords.filter { it.isCheckOut }.maxByOrNull { it.timestamp }

        val hasOpenCheckIn = checkIn != null && (checkOut == null || checkIn.timestamp > checkOut.timestamp)
        val canCheckIn = !hasOpenCheckIn
        val canCheckOut = hasOpenCheckIn

        val hoursWorked = if (checkOut != null && checkOut.hoursWorked > 0.0) {
            checkOut.hoursWorked
        } else if (checkIn != null && checkOut != null && checkOut.timestamp > checkIn.timestamp) {
            (checkOut.timestamp - checkIn.timestamp) / (1000.0 * 60 * 60)
        } else {
            0.0
        }

        StaffAttendanceTodayStatus(
            staff = staff,
            checkIn = checkIn,
            checkOut = checkOut,
            hasOpenCheckIn = hasOpenCheckIn,
            canCheckIn = canCheckIn,
            canCheckOut = canCheckOut,
            hoursWorked = hoursWorked
        )
    }

    /**
     * KIOSK STEP 1: 1:N Facial Identification
     * Identifies staff member and retrieves their current day status (Check In vs Check Out readiness).
     */
    suspend fun identifyStaffMember(selfieBitmap: Bitmap): KioskIdentificationState = withContext(Dispatchers.IO) {
        try {
            // 1. Detect and crop face
            val croppedFace = faceDetectorHelper.cropPrimaryFace(selfieBitmap)
                ?: return@withContext KioskIdentificationState.NoFaceDetected

            // 2. Extract 192-d embedding
            val queryEmbedding = faceNetHelper.getFaceEmbedding(croppedFace)

            // 3. Retrieve all staff
            val allStaffList = staffDao.getAllStaffSync()
            if (allStaffList.isEmpty()) {
                return@withContext KioskIdentificationState.NoStaffRegistered
            }

            // 4. 1:N Cosine Similarity
            var bestMatch: Staff? = null
            var highestScore = -1.0f

            for (staff in allStaffList) {
                val enrolled = staff.getEmbeddingArray()
                if (enrolled.isEmpty()) continue
                val score = faceNetHelper.calculateCosineSimilarity(enrolled, queryEmbedding)
                if (score > highestScore) {
                    highestScore = score
                    bestMatch = staff
                }
            }

            // 5. Match threshold 0.70
            if (bestMatch != null && highestScore >= FaceNetModelHelper.MATCH_THRESHOLD) {
                val matchPercentage = (highestScore * 100).toInt()
                val status = getStaffTodayStatus(bestMatch)
                return@withContext KioskIdentificationState.Identified(
                    staff = bestMatch,
                    matchPercentage = matchPercentage,
                    status = status,
                    selfieBitmap = selfieBitmap
                )
            } else {
                return@withContext KioskIdentificationState.Unrecognized(highestScore.coerceAtLeast(0f))
            }
        } catch (e: Exception) {
            return@withContext KioskIdentificationState.Error(e.localizedMessage ?: "Failed to process face identification")
        }
    }

    /**
     * KIOSK STEP 2: Record Attendance Action (CHECK_IN or CHECK_OUT)
     * Enforces validation rules and calculates hours worked on CHECK_OUT.
     */
    suspend fun recordAttendanceAction(
        staff: Staff,
        actionType: String,
        selfieBitmap: Bitmap,
        location: LocationData,
        matchPercentage: Int = 95
    ): KioskIdentificationState = withContext(Dispatchers.IO) {
        try {
            val status = getStaffTodayStatus(staff)

            if (actionType == AttendanceRecord.TYPE_CHECK_IN) {
                if (!status.canCheckIn) {
                    return@withContext KioskIdentificationState.Error(
                        "Already checked in today at ${status.formattedCheckInTime}. You must Check Out before checking in again."
                    )
                }

                val selfiePath = saveBitmapToFile(selfieBitmap, "in_${staff.employeeId}_${System.currentTimeMillis()}.jpg")
                val record = AttendanceRecord(
                    staffId = staff.id,
                    staffName = staff.name,
                    employeeId = staff.employeeId,
                    timestamp = System.currentTimeMillis(),
                    selfiePath = selfiePath,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = location.readableAddress,
                    confidenceScore = (matchPercentage / 100f),
                    type = AttendanceRecord.TYPE_CHECK_IN,
                    hoursWorked = 0.0
                )
                val id = attendanceDao.insertRecord(record)
                val savedRecord = record.copy(id = id)
                val updatedStatus = getStaffTodayStatus(staff)

                return@withContext KioskIdentificationState.Recorded(
                    staff = staff,
                    record = savedRecord,
                    status = updatedStatus,
                    actionType = AttendanceRecord.TYPE_CHECK_IN,
                    matchPercentage = matchPercentage
                )
            } else {
                // CHECK_OUT
                if (!status.canCheckOut || status.checkIn == null) {
                    return@withContext KioskIdentificationState.Error(
                        "No open check-in found for today. Please Check In first."
                    )
                }

                val checkOutTime = System.currentTimeMillis()
                val checkInTime = status.checkIn.timestamp
                val diffHours = ((checkOutTime - checkInTime).coerceAtLeast(0L)) / (1000.0 * 60 * 60)

                val selfiePath = saveBitmapToFile(selfieBitmap, "out_${staff.employeeId}_$checkOutTime.jpg")
                val outRecord = AttendanceRecord(
                    staffId = staff.id,
                    staffName = staff.name,
                    employeeId = staff.employeeId,
                    timestamp = checkOutTime,
                    selfiePath = selfiePath,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = location.readableAddress,
                    confidenceScore = (matchPercentage / 100f),
                    type = AttendanceRecord.TYPE_CHECK_OUT,
                    pairedRecordId = status.checkIn.id,
                    hoursWorked = diffHours
                )
                val outId = attendanceDao.insertRecord(outRecord)
                val savedOutRecord = outRecord.copy(id = outId)

                // Update the open check-in record to pair with this checkout
                attendanceDao.updateRecord(
                    status.checkIn.copy(
                        pairedRecordId = outId,
                        hoursWorked = diffHours
                    )
                )

                val updatedStatus = getStaffTodayStatus(staff)

                return@withContext KioskIdentificationState.Recorded(
                    staff = staff,
                    record = savedOutRecord,
                    status = updatedStatus,
                    actionType = AttendanceRecord.TYPE_CHECK_OUT,
                    matchPercentage = matchPercentage
                )
            }
        } catch (e: Exception) {
            return@withContext KioskIdentificationState.Error(e.localizedMessage ?: "Failed to save attendance record")
        }
    }

    /**
     * Legacy single-call method: identifies and auto-selects valid action (Check In or Check Out)
     */
    suspend fun identifyAndMarkAttendance(
        selfieBitmap: Bitmap,
        location: LocationData
    ): KioskIdentificationState = withContext(Dispatchers.IO) {
        val idResult = identifyStaffMember(selfieBitmap)
        if (idResult is KioskIdentificationState.Identified) {
            val action = idResult.status.expectedAction
            recordAttendanceAction(idResult.staff, action, selfieBitmap, location, idResult.matchPercentage)
        } else {
            idResult
        }
    }

    /**
     * Group flat list of AttendanceRecord into DailyAttendancePair (Check In + Check Out + Hours Worked)
     */
    fun groupRecordsIntoDailyPairs(records: List<AttendanceRecord>): List<DailyAttendancePair> {
        val grouped = records.groupBy { "${it.staffId}_${it.formattedDayKey}" }

        return grouped.values.mapNotNull { dayRecords ->
            val first = dayRecords.firstOrNull() ?: return@mapNotNull null
            val checkIn = dayRecords.filter { it.isCheckIn }.minByOrNull { it.timestamp }
            val checkOut = dayRecords.filter { it.isCheckOut }.maxByOrNull { it.timestamp }

            val totalHours = if (checkOut != null && checkOut.hoursWorked > 0.0) {
                checkOut.hoursWorked
            } else if (checkIn != null && checkOut != null && checkOut.timestamp > checkIn.timestamp) {
                (checkOut.timestamp - checkIn.timestamp) / (1000.0 * 60 * 60)
            } else {
                0.0
            }

            DailyAttendancePair(
                staffId = first.staffId,
                staffName = first.staffName,
                employeeId = first.employeeId,
                dayKey = first.formattedDayKey,
                formattedDate = first.formattedDate,
                checkIn = checkIn,
                checkOut = checkOut,
                totalHours = totalHours
            )
        }.sortedByDescending { it.checkIn?.timestamp ?: it.checkOut?.timestamp ?: 0L }
    }

    suspend fun queryAiAttendance(question: String): AgentQueryResult = withContext(Dispatchers.IO) {
        val staffList = staffDao.getAllStaffSync()
        val records = attendanceDao.getAllRecordsSync()
        aiAgent.queryAttendance(question, staffList, records)
    }

    suspend fun generateDailySummary(): DailySummaryResult = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val staffList = staffDao.getAllStaffSync()
        val todayRecords = attendanceDao.getTodayRecordsSync(calendar.timeInMillis)
        aiAgent.generateDailySummary(staffList, todayRecords)
    }

    suspend fun deleteStaff(staff: Staff) = withContext(Dispatchers.IO) {
        staff.photoPath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        staffDao.deleteStaff(staff)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, filename: String): String {
        val dir = File(context.filesDir, "attendance_photos")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }
}
