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

sealed class KioskAttendanceResult {
    object Idle : KioskAttendanceResult()
    object Processing : KioskAttendanceResult()
    object NoFaceDetected : KioskAttendanceResult()
    object NoStaffRegistered : KioskAttendanceResult()
    data class Unrecognized(val highestScore: Float) : KioskAttendanceResult()
    data class Success(
        val staff: Staff,
        val record: AttendanceRecord,
        val matchPercentage: Int
    ) : KioskAttendanceResult()
    data class Error(val message: String) : KioskAttendanceResult()
}

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

    suspend fun getStaffById(id: Long): Staff? = withContext(Dispatchers.IO) {
        staffDao.getStaffById(id)
    }

    suspend fun getStaffByEmployeeId(empId: String): Staff? = withContext(Dispatchers.IO) {
        staffDao.getStaffByEmployeeId(empId)
    }

    suspend fun authenticateAdmin(username: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        if (username.equals("admin", ignoreCase = true) && pass == "admin123") {
            return@withContext true
        }
        adminUserDao.authenticateAdmin(username, pass) != null
    }

    suspend fun authenticateStaff(usernameOrEmpId: String, pass: String): Staff? = withContext(Dispatchers.IO) {
        staffDao.authenticateStaff(usernameOrEmpId.trim(), pass.trim())
    }

    /**
     * Seeds ONLY the Admin account on first launch.
     * All staff accounts must be registered at runtime by the Admin.
     */
    suspend fun seedDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        if (adminUserDao.getAdminCount() == 0) {
            adminUserDao.insertAdmin(AdminUser(username = "admin", password = "admin123"))
        }
    }

    /**
     * Registers a new staff member.
     * Requires Full Name, Employee ID, Username, Password, and Face Enrolment.
     */
    suspend fun registerStaff(
        name: String,
        employeeId: String,
        username: String,
        pass: String,
        faceEmbedding: FloatArray,
        photoBitmap: Bitmap
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            if (name.isBlank() || employeeId.isBlank() || username.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("All fields (Name, ID, Username, Password) are required"))
            }

            if (faceEmbedding.isEmpty()) {
                return@withContext Result.failure(Exception("Face enrolment selfie is required to complete registration"))
            }

            val existingId = staffDao.getStaffByEmployeeId(employeeId.trim())
            if (existingId != null) {
                return@withContext Result.failure(Exception("Employee ID '${employeeId.trim()}' is already registered"))
            }

            val existingUsername = staffDao.getStaffByUsername(username.trim())
            if (existingUsername != null) {
                return@withContext Result.failure(Exception("Username '${username.trim()}' is already taken"))
            }

            val photoPath = saveBitmapToFile(photoBitmap, "staff_${employeeId.trim()}_${System.currentTimeMillis()}.jpg")
            val staff = Staff(
                name = name.trim(),
                employeeId = employeeId.trim(),
                username = username.trim(),
                password = pass.trim(),
                faceEmbedding = Staff.embeddingToString(faceEmbedding),
                photoPath = photoPath,
                enrolledAt = System.currentTimeMillis()
            )
            val id = staffDao.insertStaff(staff)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * KIOSK 1:N FACE IDENTIFICATION ATTENDANCE:
     * NOTE: This changes face matching from 1:1 verification (against a pre-selected user)
     * to 1:N identification (searching across all registered staff members in Room).
     * Compares the query embedding against every enrolled staff embedding via Cosine Similarity loop.
     * If best similarity >= 0.70 threshold, attendance is automatically marked for that person.
     */
    suspend fun identifyAndMarkAttendance(
        selfieBitmap: Bitmap,
        location: LocationData
    ): KioskAttendanceResult = withContext(Dispatchers.IO) {
        try {
            // 1. Detect and crop face from live selfie
            val croppedFace = faceDetectorHelper.cropPrimaryFace(selfieBitmap)
                ?: return@withContext KioskAttendanceResult.NoFaceDetected

            // 2. Extract 192-d facial embedding
            val queryEmbedding = faceNetHelper.getFaceEmbedding(croppedFace)

            // 3. Retrieve all registered staff from database
            val allStaffList = staffDao.getAllStaffSync()
            if (allStaffList.isEmpty()) {
                return@withContext KioskAttendanceResult.NoStaffRegistered
            }

            // 4. 1:N Cosine Similarity comparison over all enrolled staff
            var bestMatch: Staff? = null
            var highestScore = -1.0f

            for (staff in allStaffList) {
                val enrolledEmbedding = staff.getEmbeddingArray()
                if (enrolledEmbedding.isEmpty()) continue

                val similarity = faceNetHelper.calculateCosineSimilarity(enrolledEmbedding, queryEmbedding)
                if (similarity > highestScore) {
                    highestScore = similarity
                    bestMatch = staff
                }
            }

            // 5. Check threshold (0.70)
            if (bestMatch != null && highestScore >= FaceNetModelHelper.MATCH_THRESHOLD) {
                val selfiePath = saveBitmapToFile(selfieBitmap, "attendance_${bestMatch.employeeId}_${System.currentTimeMillis()}.jpg")
                val record = AttendanceRecord(
                    staffId = bestMatch.id,
                    staffName = bestMatch.name,
                    employeeId = bestMatch.employeeId,
                    timestamp = System.currentTimeMillis(),
                    selfiePath = selfiePath,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = location.readableAddress,
                    confidenceScore = highestScore
                )
                val recordId = attendanceDao.insertRecord(record)
                val finalRecord = record.copy(id = recordId)
                val matchPercentage = (highestScore * 100).toInt()

                return@withContext KioskAttendanceResult.Success(bestMatch, finalRecord, matchPercentage)
            } else {
                return@withContext KioskAttendanceResult.Unrecognized(highestScore.coerceAtLeast(0f))
            }
        } catch (e: Exception) {
            return@withContext KioskAttendanceResult.Error(e.localizedMessage ?: "Failed to process face identification")
        }
    }

    /**
     * AI Agent: Natural Language Query over Attendance Data
     */
    suspend fun queryAiAttendance(question: String): AgentQueryResult = withContext(Dispatchers.IO) {
        val staffList = staffDao.getAllStaffSync()
        val records = attendanceDao.getAllRecordsSync()
        aiAgent.queryAttendance(question, staffList, records)
    }

    /**
     * AI Agent: Generate Daily Attendance Executive Summary
     */
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
        val photosDir = File(context.filesDir, "attendance_photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        val file = File(photosDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }
}
