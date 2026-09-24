package com.attendance.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.attendance.app.data.model.AdminUser

@Dao
interface AdminUserDao {
    @Query("SELECT * FROM admin_users WHERE username = :username AND password = :password LIMIT 1")
    suspend fun authenticateAdmin(username: String, password: String): AdminUser?

    @Query("SELECT COUNT(*) FROM admin_users")
    suspend fun getAdminCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: AdminUser)
}
