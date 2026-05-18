package com.lumiere.player.enhancement

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.*

/**
 * Runs MediaPipe FaceDetector on video frames (sampled every N frames)
 * and returns face bounding boxes in UV coordinates [0,1].
 */
class FaceEnhancementManager(private val context: Context) {

    private var detector: FaceDetector? = null
    private var frameCounter = 0
    private val DETECT_EVERY_N = 15 // run face detection every 15 frames

    // Last known face regions in UV space: FloatArray of [x1,y1,x2,y2]
    var lastFaceRegions: Array<FloatArray> = emptyArray()
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init() {
        try {
            val opts = FaceDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_detection_short_range.tflite")
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .setMinSuppressionThreshold(0.3f)
                .build()
            detector = FaceDetector.createFromOptions(context, opts)
        } catch (e: Exception) {
            // Model not bundled — face detection disabled gracefully
            detector = null
        }
    }

    fun processFrame(bitmap: Bitmap, frameWidth: Int, frameHeight: Int) {
        frameCounter++
        val det = detector ?: return

        scope.launch {
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = det.detect(mpImage)
                val regions = mutableListOf<FloatArray>()
                result.detections().take(4).forEach { detection ->
                    val box = detection.boundingBox()
                    val x1 = box.left   / frameWidth.toFloat()
                    val y1 = box.top    / frameHeight.toFloat()
                    val x2 = box.right  / frameWidth.toFloat()
                    val y2 = box.bottom / frameHeight.toFloat()
                    regions.add(floatArrayOf(
                        x1.coerceIn(0f,1f),
                        y1.coerceIn(0f,1f),
                        x2.coerceIn(0f,1f),
                        y2.coerceIn(0f,1f)
                    ))
                }
                lastFaceRegions = regions.toTypedArray()
            } catch (e: Exception) {
                // Silently ignore per-frame errors
            }
        }
    }

    fun release() {
        scope.cancel()
        detector?.close()
        detector = null
    }
}