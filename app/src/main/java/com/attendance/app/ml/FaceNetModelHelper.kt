package com.attendance.app.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FaceNetModelHelper(private val context: Context) {

    private var interpreter: Interpreter? = null

    companion object {
        private const val MODEL_FILE = "mobilefacenet.tflite"
        const val INPUT_IMAGE_SIZE = 112
        const val EMBEDDING_SIZE = 192
        // Threshold for cosine similarity (0.0 to 1.0). >= 0.70 is considered a strong match.
        const val MATCH_THRESHOLD = 0.70f
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isModelLoaded(): Boolean = interpreter != null

    /**
     * Extracts a 192-dimensional L2-normalized embedding vector from a cropped face bitmap.
     */
    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val currentInterpreter = interpreter ?: throw IllegalStateException("TFLite Interpreter is not initialized")

        // 1. Resize to model input size (112x112)
        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true)

        // 2. Preprocess into Float32 ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        resizedBitmap.getPixels(intValues, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            // Normalize (value - 128) / 128
            byteBuffer.putFloat((r - 128f) / 128f)
            byteBuffer.putFloat((g - 128f) / 128f)
            byteBuffer.putFloat((b - 128f) / 128f)
        }

        // 3. Run inference
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        currentInterpreter.run(byteBuffer, output)

        // 4. L2 Normalize the embedding vector
        val rawEmbedding = output[0]
        return l2Normalize(rawEmbedding)
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in embedding) {
            sumSquares += v * v
        }
        val norm = sqrt(max(sumSquares, 1e-10f))
        val normalized = FloatArray(embedding.size)
        for (i in embedding.indices) {
            normalized[i] = embedding[i] / norm
        }
        return normalized
    }

    /**
     * Calculates Cosine Similarity between two L2-normalized embedding vectors.
     * Returns a value between -1.0 and 1.0 (clamped to 0.0 to 1.0 for similarity percentage).
     */
    fun calculateCosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
        if (emb1.isEmpty() || emb2.isEmpty() || emb1.size != emb2.size) return 0f
        var dotProduct = 0f
        for (i in emb1.indices) {
            dotProduct += emb1[i] * emb2[i]
        }
        return min(max(dotProduct, 0f), 1f)
    }

    /**
     * Returns true if similarity >= MATCH_THRESHOLD.
     */
    fun isMatch(emb1: FloatArray, emb2: FloatArray, threshold: Float = MATCH_THRESHOLD): Boolean {
        val similarity = calculateCosineSimilarity(emb1, emb2)
        return similarity >= threshold
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
