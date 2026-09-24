package com.attendance.app.data.local

import androidx.room.*
import com.attendance.app.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE staffId = :staffId ORDER BY timestamp DESC")
    fun getRecordsForStaff(staffId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayRecords(startOfDay: Long): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecord): Long

    @Update
    suspend fun updateRecord(record: AttendanceRecord)

    @Query("SELECT * FROM attendance_records WHERE id = :id LIMIT 1")
    suspend fun getRecordByIdSync(id: Long): AttendanceRecord?

    @Query("SELECT COUNT(*) FROM attendance_records WHERE timestamp >= :startOfDay AND type = 'CHECK_IN'")
    fun getTodayAttendanceCount(startOfDay: Long): Flow<Int>

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    suspend fun getAllRecordsSync(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE staffId = :staffId ORDER BY timestamp DESC")
    suspend fun getRecordsForStaffSync(staffId: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    suspend fun getTodayRecordsSync(startOfDay: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE staffId = :staffId AND timestamp >= :startOfDay ORDER BY timestamp ASC")
    suspend fun getTodayRecordsForStaffSync(staffId: Long, startOfDay: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE staffId = :staffId AND timestamp >= :startOfDay AND type = 'CHECK_IN' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCheckInForStaffTodaySync(staffId: Long, startOfDay: Long): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE staffId = :staffId AND timestamp >= :startOfDay AND type = 'CHECK_OUT' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCheckOutForStaffTodaySync(staffId: Long, startOfDay: Long): AttendanceRecord?
}
