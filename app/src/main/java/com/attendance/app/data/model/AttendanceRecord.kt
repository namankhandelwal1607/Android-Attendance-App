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
    indices = [Index("staffId"), Index("timestamp"), Index("type")]
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
    val confidenceScore: Float,
    val type: String = TYPE_CHECK_IN, // "CHECK_IN" or "CHECK_OUT"
    val pairedRecordId: Long? = null,
    val hoursWorked: Double = 0.0 // Hours worked if this is CHECK_OUT or paired
) {
    companion object {
        const val TYPE_CHECK_IN = "CHECK_IN"
        const val TYPE_CHECK_OUT = "CHECK_OUT"
    }

    val isCheckIn: Boolean get() = type == TYPE_CHECK_IN
    val isCheckOut: Boolean get() = type == TYPE_CHECK_OUT

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

    val formattedDayKey: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val formattedHours: String
        get() = if (hoursWorked > 0.0) {
            val h = hoursWorked.toInt()
            val m = ((hoursWorked - h) * 60).toInt()
            "${h}h ${m}m"
        } else {
            "--:--"
        }
}

/**
 * Paired representation of a staff member's day: one check-in + one check-out
 */
data class DailyAttendancePair(
    val staffId: Long,
    val staffName: String,
    val employeeId: String,
    val dayKey: String, // YYYY-MM-DD
    val formattedDate: String, // e.g. Wed, 24 Sep 2026
    val checkIn: AttendanceRecord?,
    val checkOut: AttendanceRecord?,
    val totalHours: Double = checkOut?.hoursWorked ?: 0.0
) {
    val formattedHours: String
        get() = if (totalHours > 0.0) {
            val h = totalHours.toInt()
            val m = ((totalHours - h) * 60).toInt()
            "${h}h ${m}m"
        } else {
            "--:--"
        }

    val statusText: String
        get() = when {
            checkIn != null && checkOut != null -> "Completed"
            checkIn != null && checkOut == null -> "Checked In"
            else -> "No Record"
        }
}
