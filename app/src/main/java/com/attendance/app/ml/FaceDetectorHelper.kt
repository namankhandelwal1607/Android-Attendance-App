package com.attendance.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

class FaceDetectorHelper {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        detector = FaceDetection.getClient(options)
    }

    /**
     * Detects faces in the given bitmap.
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (continuation.isActive) {
                    continuation.resume(faces)
                }
            }
            .addOnFailureListener { e ->
                if (continuation.isActive) {
                    continuation.resume(emptyList())
                }
            }
    }

    /**
     * Detects and crops the primary (largest) face from the bitmap with padding.
     * Returns null if no face is detected.
     */
    suspend fun cropPrimaryFace(bitmap: Bitmap): Bitmap? {
        val faces = detectFaces(bitmap)
        if (faces.isEmpty()) return null

        // Pick largest face by area
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        return cropFaceWithPadding(bitmap, primaryFace.boundingBox)
    }

    private fun cropFaceWithPadding(bitmap: Bitmap, box: Rect): Bitmap {
        // Add 15% padding around the bounding box
        val paddingX = (box.width() * 0.15f).toInt()
        val paddingY = (box.height() * 0.15f).toInt()

        val left = max(0, box.left - paddingX)
        val top = max(0, box.top - paddingY)
        val right = min(bitmap.width, box.right + paddingX)
        val bottom = min(bitmap.height, box.bottom + paddingY)

        val width = max(1, right - left)
        val height = max(1, bottom - top)

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    fun close() {
        detector.close()
    }
}
