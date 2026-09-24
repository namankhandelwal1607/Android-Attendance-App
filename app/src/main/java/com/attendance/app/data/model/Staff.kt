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
    val faceEmbedding: String? = null, // Comma-separated float values of 192-d vector; null until face enrolled
    val photoPath: String? = null,
    val enrolledAt: Long = System.currentTimeMillis(),
    val username: String = "",
    val password: String = ""
) {
    fun getEmbeddingArray(): FloatArray {
        val embedding = faceEmbedding
        return if (embedding.isNullOrBlank()) {
            FloatArray(0)
        } else {
            embedding.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
        }
    }

    val isFaceEnrolled: Boolean
        get() = !faceEmbedding.isNullOrBlank()

    companion object {
        fun embeddingToString(array: FloatArray): String {
            return array.joinToString(",")
        }
    }
}
