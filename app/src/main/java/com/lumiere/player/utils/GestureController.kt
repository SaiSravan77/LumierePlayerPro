package com.lumiere.player.utils

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Handles swipe gestures on the player surface:
 *  - Left half vertical swipe  → brightness
 *  - Right half vertical swipe → volume
 *  - Horizontal swipe          → seek
 *  - Double tap                → play/pause
 *  - Single tap                → show/hide controls
 */
class GestureController(
    private val context: Context,
    private val view: View,
    private val onSeek: (deltaMs: Long) -> Unit,
    private val onVolumeChange: (delta: Float) -> Unit,
    private val onBrightnessChange: (delta: Float) -> Unit,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onShowOverlay: (type: Int, value: Float, label: String) -> Unit,
    private val getCurrentPosition: () -> Long
) {

    companion object {
        const val OVERLAY_VOLUME = 0
        const val OVERLAY_BRIGHTNESS = 1
        const val OVERLAY_SEEK = 2
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var currentBrightness = 0.5f
    private var seekBasePosition = 0L
    private var isSeeking = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap(); return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap(); return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            isSeeking = false
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            val e1n = e1 ?: return false
            val dx = e2.x - e1n.x
            val dy = e2.y - e1n.y

            return if (abs(dx) > abs(dy) * 1.5f || isSeeking) {
                // Horizontal: seek
                if (!isSeeking) {
                    isSeeking = true
                    seekBasePosition = getCurrentPosition()
                }
                val seekDelta = (dx * 150).toLong() // 150ms per pixel
                onSeek(seekBasePosition + seekDelta - getCurrentPosition())
                val secs = abs(dx * 150).toLong() / 1000
                onShowOverlay(OVERLAY_SEEK, dx / view.width, if (dx > 0) "+${secs}s" else "-${secs}s")
                true
            } else {
                // Vertical: left=brightness, right=volume
                val normalized = -dy / view.height
                if (e1n.x < view.width / 2f) {
                    // Brightness
                    onBrightnessChange(normalized * 0.05f)
                    val lp = (context as? android.app.Activity)?.window?.attributes
                    val cur = lp?.screenBrightness ?: 0.5f
                    onShowOverlay(OVERLAY_BRIGHTNESS, cur.coerceIn(0f, 1f), "☀ ${(cur.coerceIn(0f, 1f) * 100).toInt()}%")
                } else {
                    // Volume
                    onVolumeChange(normalized * 0.1f)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    onShowOverlay(OVERLAY_VOLUME, curVol / maxVol, "🔊 ${(curVol / maxVol * 100).toInt()}%")
                }
                true
            }
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    fun adjustSystemVolume(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol = (cur + (delta * max)).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
    }

    fun setScreenBrightness(activity: android.app.Activity, brightness: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1f)
        activity.window.attributes = lp
        currentBrightness = brightness
    }
}