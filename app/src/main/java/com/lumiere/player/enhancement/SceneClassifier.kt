package com.lumiere.player.enhancement

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Classifies video scenes using TFLite MobileNet-based model.
 * Falls back to heuristic (average brightness/saturation analysis)
 * if TFLite model is not bundled.
 */
class SceneClassifier(private val context: Context) {

    companion object {
        const val SCENE_NORMAL  = 0
        const val SCENE_OUTDOOR = 1
        const val SCENE_INDOOR  = 2
        const val SCENE_NIGHT   = 3

        private const val INPUT_SIZE = 224
        private const val MODEL_FILE = "scene_classification.tflite"
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var currentScene: Int = SCENE_NORMAL
        private set

    private var frameCounter = 0
    private val CLASSIFY_EVERY_N = 60 // classify every 2 seconds at 30fps

    fun init() {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                try {
                    gpuDelegate = GpuDelegate()
                    addDelegate(gpuDelegate!!)
                } catch (e: Exception) {
                    // GPU delegate not available, use CPU
                }
                numThreads = 2
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            interpreter = null // use heuristic fallback
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun processFrame(bitmap: Bitmap) {
        frameCounter++
        if (frameCounter % CLASSIFY_EVERY_N != 0) return

        scope.launch {
            currentScene = if (interpreter != null) {
                classifyWithModel(bitmap)
            } else {
                classifyWithHeuristic(bitmap)
            }
        }
    }

    private fun classifyWithModel(bitmap: Bitmap): Int {
        val interp = interpreter ?: return SCENE_NORMAL
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        pixels.forEach { pixel ->
            inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 255f - 0.485f) / 0.229f)
            inputBuffer.putFloat(((pixel shr 8  and 0xFF) / 255f - 0.456f) / 0.224f)
            inputBuffer.putFloat(((pixel        and 0xFF) / 255f - 0.406f) / 0.225f)
        }

        val output = Array(1) { FloatArray(4) }
        interp.run(inputBuffer, output)

        val scores = output[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
        return when (maxIdx) {
            0 -> SCENE_OUTDOOR
            1 -> SCENE_INDOOR
            2 -> SCENE_NIGHT
            else -> SCENE_NORMAL
        }
    }

    /**
     * Heuristic fallback: analyse pixel statistics to guess scene type.
     */
    private fun classifyWithHeuristic(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        small.getPixels(pixels, 0, 32, 0, 0, 32, 32)

        var totalR = 0L; var totalG = 0L; var totalB = 0L
        var darkCount = 0
        pixels.forEach { p ->
            val r = (p shr 16 and 0xFF); val g = (p shr 8 and 0xFF); val b = (p and 0xFF)
            totalR += r; totalG += g; totalB += b
            val brightness = (0.2126 * r + 0.7152 * g + 0.0722 * b)
            if (brightness < 50) darkCount++
        }
        val n = pixels.size.toFloat()
        val avgR = totalR / n; val avgG = totalG / n; val avgB = totalB / n
        val avgBright = 0.2126 * avgR + 0.7152 * avgG + 0.0722 * avgB
        val darkRatio = darkCount / n

        return when {
            darkRatio > 0.4 || avgBright < 60 -> SCENE_NIGHT
            avgG > avgR && avgG > avgB && avgBright > 100 -> SCENE_OUTDOOR
            avgBright in 60.0..140.0 -> SCENE_INDOOR
            else -> SCENE_NORMAL
        }
    }

    fun release() {
        scope.cancel()
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}
