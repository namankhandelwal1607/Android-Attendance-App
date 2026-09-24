package com.attendance.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = Staff::class,
            parentColumns = ["id"],
            childColumns = ["staffId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("staffId"), Index("timestamp")]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val staffId: Long,
    val staffName: String,
    val employeeId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val selfiePath: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val confidenceScore: Float
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val formattedShortTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
