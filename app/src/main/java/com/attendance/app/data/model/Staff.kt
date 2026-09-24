package com.attendance.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "staff",
    indices = [Index(value = ["employeeId"], unique = true)]
)
data class Staff(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val employeeId: String,
    val faceEmbedding: String, // Comma-separated float values of 192-d vector
    val photoPath: String? = null,
    val enrolledAt: Long = System.currentTimeMillis()
) {
    fun getEmbeddingArray(): FloatArray {
        return if (faceEmbedding.isBlank()) {
            FloatArray(0)
        } else {
            faceEmbedding.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
        }
    }

    companion object {
        fun embeddingToString(array: FloatArray): String {
            return array.joinToString(",")
        }
    }
}
