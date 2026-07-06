package com.sentinelshield.services.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Phase 4: TensorFlow Lite-based app behavior classifier.
 * Uses a pre-trained model to classify app behavior patterns as
 * normal, suspicious, or malicious based on feature vectors.
 *
 * Features analyzed:
 * - Permission count (normalized)
 * - Network activity frequency
 * - Background wake-ups
 * - Data transmission volume
 * - Battery drain rate
 * - CPU usage pattern
 * - Sensor access frequency
 * - Inter-process communication frequency
 */
class AppBehaviorClassifier(private val context: Context) {

    companion object {
        private const val TAG = "AppBehaviorClassifier"
        private const val MODEL_FILE = "behavior_model.tflite"
        private const val NUM_FEATURES = 8
        private const val NUM_CLASSES = 3  // normal, suspicious, malicious

        // Classification thresholds
        const val CLASS_NORMAL = 0
        const val CLASS_SUSPICIOUS = 1
        const val CLASS_MALICIOUS = 2
    }

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    // Fallback heuristic weights when TFLite model is not available
    private val heuristicWeights = floatArrayOf(
        0.15f,  // permission_count weight
        0.20f,  // network_activity weight
        0.15f,  // background_wakeups weight
        0.20f,  // data_transmission weight
        0.10f,  // battery_drain weight
        0.08f,  // cpu_usage weight
        0.07f,  // sensor_access weight
        0.05f   // ipc_frequency weight
    )

    init {
        loadModel()
    }

    /**
     * Load the TFLite model from assets.
     * Falls back to heuristic classification if model is unavailable.
     */
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            if (modelBuffer != null) {
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                interpreter = Interpreter(modelBuffer, options)
                isModelLoaded = true
                Log.i(TAG, "TFLite model loaded successfully")
            } else {
                Log.w(TAG, "Model file not found, using heuristic fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}")
            isModelLoaded = false
        }
    }

    /**
     * Load model file from assets directory.
     */
    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Classify app behavior based on feature vector.
     * Returns a BehaviorClassification result.
     */
    fun classify(features: BehaviorFeatures): BehaviorClassification {
        val normalizedFeatures = normalizeFeatures(features)

        return if (isModelLoaded) {
            classifyWithModel(normalizedFeatures)
        } else {
            classifyWithHeuristics(normalizedFeatures)
        }
    }

    /**
     * Classify using TFLite model inference.
     */
    private fun classifyWithModel(features: FloatArray): BehaviorClassification {
        val inputBuffer = ByteBuffer.allocateDirect(NUM_FEATURES * 4).apply {
            order(ByteOrder.nativeOrder())
            features.forEach { putFloat(it) }
        }

        val outputBuffer = ByteBuffer.allocateDirect(NUM_CLASSES * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        try {
            interpreter?.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val probabilities = FloatArray(NUM_CLASSES)
            for (i in 0 until NUM_CLASSES) {
                probabilities[i] = outputBuffer.float
            }

            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val confidence = probabilities[maxIndex]

            return BehaviorClassification(
                classification = maxIndex,
                confidence = confidence,
                probabilities = probabilities,
                method = "tflite"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Model inference failed, falling back to heuristics", e)
            return classifyWithHeuristics(features)
        }
    }

    /**
     * Fallback heuristic classification when model is unavailable.
     * Uses weighted sum of features to determine risk level.
     */
    private fun classifyWithHeuristics(features: FloatArray): BehaviorClassification {
        // Calculate weighted risk score
        var riskScore = 0f
        for (i in features.indices) {
            riskScore += features[i] * heuristicWeights[i]
        }

        // Apply non-linear scaling for better separation
        riskScore = riskScore * riskScore  // Square to amplify high values

        val classification = when {
            riskScore > 0.6f -> CLASS_MALICIOUS
            riskScore > 0.3f -> CLASS_SUSPICIOUS
            else -> CLASS_NORMAL
        }

        // Generate pseudo-probabilities
        val probabilities = when (classification) {
            CLASS_MALICIOUS -> floatArrayOf(0.1f, 0.2f, 0.7f)
            CLASS_SUSPICIOUS -> floatArrayOf(0.2f, 0.6f, 0.2f)
            else -> floatArrayOf(0.8f, 0.15f, 0.05f)
        }

        return BehaviorClassification(
            classification = classification,
            confidence = probabilities[classification],
            probabilities = probabilities,
            method = "heuristic"
        )
    }

    /**
     * Normalize raw features to 0-1 range for model input.
     */
    private fun normalizeFeatures(features: BehaviorFeatures): FloatArray {
        return floatArrayOf(
            (features.permissionCount / 30f).coerceIn(0f, 1f),
            (features.networkActivityPerHour / 100f).coerceIn(0f, 1f),
            (features.backgroundWakeupsPerHour / 50f).coerceIn(0f, 1f),
            (features.dataTxMbPerHour / 50f).coerceIn(0f, 1f),
            (features.batteryDrainPercentPerHour / 10f).coerceIn(0f, 1f),
            (features.cpuUsagePercent / 100f).coerceIn(0f, 1f),
            (features.sensorAccessPerHour / 30f).coerceIn(0f, 1f),
            (features.ipcFrequencyPerHour / 200f).coerceIn(0f, 1f)
        )
    }

    /**
     * Release model resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
}

/**
 * Raw behavior features collected from app monitoring.
 */
data class BehaviorFeatures(
    val permissionCount: Float = 0f,
    val networkActivityPerHour: Float = 0f,
    val backgroundWakeupsPerHour: Float = 0f,
    val dataTxMbPerHour: Float = 0f,
    val batteryDrainPercentPerHour: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val sensorAccessPerHour: Float = 0f,
    val ipcFrequencyPerHour: Float = 0f
)

/**
 * Classification result from the behavior model.
 */
data class BehaviorClassification(
    val classification: Int,       // 0=normal, 1=suspicious, 2=malicious
    val confidence: Float,         // 0.0 to 1.0
    val probabilities: FloatArray, // [normal, suspicious, malicious]
    val method: String             // "tflite" or "heuristic"
) {
    val label: String
        get() = when (classification) {
            AppBehaviorClassifier.CLASS_NORMAL -> "Normal"
            AppBehaviorClassifier.CLASS_SUSPICIOUS -> "Suspicious"
            AppBehaviorClassifier.CLASS_MALICIOUS -> "Malicious"
            else -> "Unknown"
        }

    val isThreat: Boolean
        get() = classification != AppBehaviorClassifier.CLASS_NORMAL

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BehaviorClassification) return false
        return classification == other.classification &&
                confidence == other.confidence &&
                probabilities.contentEquals(other.probabilities) &&
                method == other.method
    }

    override fun hashCode(): Int {
        var result = classification
        result = 31 * result + confidence.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        result = 31 * result + method.hashCode()
        return result
    }
}
