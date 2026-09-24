package com.attendance.app.data.local

import androidx.room.*
import com.attendance.app.data.model.Staff
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY name ASC")
    fun getAllStaff(): Flow<List<Staff>>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getStaffById(id: Long): Staff?

    @Query("SELECT * FROM staff WHERE employeeId = :employeeId")
    suspend fun getStaffByEmployeeId(employeeId: String): Staff?

    @Query("SELECT * FROM staff WHERE username = :username LIMIT 1")
    suspend fun getStaffByUsername(username: String): Staff?

    @Query("SELECT * FROM staff WHERE (username = :query OR employeeId = :query) AND password = :password LIMIT 1")
    suspend fun authenticateStaff(query: String, password: String): Staff?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: Staff): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(staffList: List<Staff>)

    @Update
    suspend fun updateStaff(staff: Staff)

    @Delete
    suspend fun deleteStaff(staff: Staff)

    @Query("SELECT COUNT(*) FROM staff")
    fun getStaffCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM staff")
    suspend fun getStaffCountSync(): Int
}
