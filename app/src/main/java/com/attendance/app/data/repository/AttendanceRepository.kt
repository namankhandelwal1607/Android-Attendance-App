package com.attendance.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.attendance.app.data.local.AttendanceDao
import com.attendance.app.data.local.StaffDao
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.model.Staff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class AttendanceRepository(
    private val staffDao: StaffDao,
    private val attendanceDao: AttendanceDao,
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

    suspend fun enrollStaff(
        name: String,
        employeeId: String,
        faceEmbedding: FloatArray,
        photoBitmap: Bitmap
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val existing = staffDao.getStaffByEmployeeId(employeeId)
            if (existing != null) {
                return@withContext Result.failure(Exception("Employee ID '$employeeId' already exists"))
            }

            // Save photo to app private storage
            val photoPath = saveBitmapToFile(photoBitmap, "staff_${employeeId}_${System.currentTimeMillis()}.jpg")
            val staff = Staff(
                name = name.trim(),
                employeeId = employeeId.trim(),
                faceEmbedding = Staff.embeddingToString(faceEmbedding),
                photoPath = photoPath
            )
            val id = staffDao.insertStaff(staff)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordAttendance(
        staff: Staff,
        selfieBitmap: Bitmap,
        latitude: Double,
        longitude: Double,
        address: String,
        confidence: Float
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val selfiePath = saveBitmapToFile(selfieBitmap, "attendance_${staff.employeeId}_${System.currentTimeMillis()}.jpg")
            val record = AttendanceRecord(
                staffId = staff.id,
                staffName = staff.name,
                employeeId = staff.employeeId,
                timestamp = System.currentTimeMillis(),
                selfiePath = selfiePath,
                latitude = latitude,
                longitude = longitude,
                address = address,
                confidenceScore = confidence
            )
            val id = attendanceDao.insertRecord(record)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
