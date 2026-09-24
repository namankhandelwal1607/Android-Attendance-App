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

    @Query("SELECT COUNT(*) FROM attendance_records WHERE timestamp >= :startOfDay")
    fun getTodayAttendanceCount(startOfDay: Long): Flow<Int>
}
