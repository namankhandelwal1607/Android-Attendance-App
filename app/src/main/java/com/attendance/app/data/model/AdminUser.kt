package com.attendance.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_users")
data class AdminUser(
    @PrimaryKey
    val username: String,
    val password: String
)
