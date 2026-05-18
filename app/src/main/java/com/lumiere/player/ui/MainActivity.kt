package com.lumiere.player.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.lumiere.player.R
import com.lumiere.player.databinding.ActivityMainBinding
import com.lumiere.player.db.LumiereDatabase
import com.lumiere.player.db.WatchHistory
import com.lumiere.player.enhancement.AudioEqualizer
import com.lumiere.player.enhancement.EnhanceParams
import com.lumiere.player.enhancement.SceneClassifier
import com.lumiere.player.player.PlayerManager
import com.lumiere.player.utils.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playerManager: PlayerManager
    private lateinit var gestureController: GestureController
    private val handler = Handler(Looper.getMainLooper())
    private val db by lazy { LumiereDatabase.getInstance(this) }

    private var controlsVisible = false
    private var isFullscreen = false
    private var currentUri: Uri? = null
    private var playbackSpeed = 1.0f
    private var aspectRatioIndex = 0
    private val aspectRatios = listOf("Fit", "Fill", "4:3", "16:9", "21:9")

    // Panels
    private var enhancePanelVisible = false
    private var eqPanelVisible = false

    // Auto-hide
    private val hideControlsRunnable = Runnable { if (playerManager.player.isPlaying) hideControls() }

    // File picker
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadVideo(it) }
    }
    private val multiFilePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            playerManager.loadPlaylist(uris)
            currentUri = uris.first()
            showVideoUI()
        }
    }
    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { playerManager.addExternalSubtitle(it) }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) filePicker.launch("video/*") else
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        playerManager = PlayerManager(this).apply { init() }
        binding.playerView.player = playerManager.player

        setupPlayerCallbacks()
        setupControls()
        setupGestures()
        setupEnhancePanel()
        setupEqPanel()

        // Handle intent (open from file manager)
        intent?.data?.let { loadVideo(it) }

        binding.emptyState.setOnClickListener { requestFileOpen() }
    }

    // ─── VIDEO LOADING ───────────────────────────────────────

    private fun loadVideo(uri: Uri) {
        currentUri = uri
        lifecycleScope.launch {
            val history = db.watchHistoryDao().getByUri(uri.toString())
            playerManager.loadUri(uri, history?.lastPosition ?: 0L)
            showVideoUI()
            // Update filename display
            val name = uri.lastPathSegment ?: "Video"
            binding.tvFileName.text = name
        }
    }

    private fun showVideoUI() {
        binding.emptyState.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE
        showControls()
    }

    // ─── PLAYER CALLBACKS ────────────────────────────────────

    private fun setupPlayerCallbacks() {
        playerManager.onPlaybackStateChanged = { state ->
            when (state) {
                Player.STATE_READY   -> { updatePlayPauseIcon(playerManager.player.isPlaying); showControls() }
                Player.STATE_ENDED   -> { updatePlayPauseIcon(false); saveWatchHistory() }
                Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
                else -> {}
            }
            if (state != Player.STATE_BUFFERING) binding.progressBar.visibility = View.GONE
        }
        playerManager.onIsPlayingChanged = { isPlaying ->
            updatePlayPauseIcon(isPlaying)
            if (isPlaying) scheduleHideControls() else handler.removeCallbacks(hideControlsRunnable)
        }
        playerManager.onPositionChanged = { pos, dur ->
            if (dur > 0) {
                binding.seekBar.value = (pos.toFloat() / dur * 1000f).coerceIn(0f, 1000f)
                binding.tvTime.text = "${formatTime(pos)} / ${formatTime(dur)}"
            }
        }
        playerManager.onError = { error ->
            Toast.makeText(this, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── CONTROLS SETUP ──────────────────────────────────────

    private fun setupControls() {
        // Play/Pause
        binding.btnPlayPause.setOnClickListener { togglePlay() }
        // Skip
        binding.btnSkipBack.setOnClickListener { playerManager.player.seekTo((playerManager.player.currentPosition - 10000).coerceAtLeast(0)); showControls() }
        binding.btnSkipFwd.setOnClickListener  { playerManager.player.seekTo(playerManager.player.currentPosition + 10000); showControls() }
        // Prev/Next
        binding.btnPrev.setOnClickListener { playerManager.skipToPrevious() }
        binding.btnNext.setOnClickListener { playerManager.skipToNext() }
        // Seek bar
        binding.seekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val pos = (value / 1000f * playerManager.player.duration).toLong()
                playerManager.player.seekTo(pos)
            }
        }
        // Open file
        binding.btnOpen.setOnClickListener { requestFileOpen() }
        // Open playlist
        binding.btnPlaylist.setOnClickListener { openPlaylistPicker() }
        // Fullscreen
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        // Enhance
        binding.btnEnhance.setOnClickListener { toggleEnhancePanel() }
        // EQ
        binding.btnEq.setOnClickListener { toggleEqPanel() }
        // Speed
        binding.btnSpeed.setOnClickListener { showSpeedDialog() }
        // Aspect ratio
        binding.btnAspect.setOnClickListener { cycleAspectRatio() }
        // Audio tracks
        binding.btnAudio.setOnClickListener { showAudioTrackDialog() }
        // Subtitles
        binding.btnSub.setOnClickListener { showSubtitleDialog() }
        // Screenshot
        binding.btnScreenshot.setOnClickListener { takeScreenshot() }
        // Library
        binding.btnLibrary.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        // Settings
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        // Lock screen
        binding.btnLock.setOnClickListener { toggleControlsLock() }
    }

    // ─── GESTURES ────────────────────────────────────────────

    private fun setupGestures() {
        gestureController = GestureController(
            context = this,
            view = binding.playerView,
            onSeek = { delta ->
                val newPos = (playerManager.player.currentPosition + delta).coerceAtLeast(0)
                playerManager.player.seekTo(newPos)
            },
            onVolumeChange = { delta -> gestureController.adjustSystemVolume(delta) },
            onBrightnessChange = { delta ->
                val cur = window.attributes.screenBrightness.takeIf { it > 0 } ?: 0.5f
                gestureController.setScreenBrightness(this, cur + delta)
            },
            onSingleTap = { toggleControls() },
            onDoubleTap = { togglePlay() },
            onShowOverlay = { type, value, label ->
                showGestureOverlay(label, value)
            }
        )

        binding.playerView.setOnTouchListener { _, event ->
            gestureController.onTouchEvent(event)
            false
        }
    }

    // ─── ENHANCE PANEL ───────────────────────────────────────

    private fun setupEnhancePanel() {
        val p = playerManager.params

        // Master toggle
        binding.enhancePanel.switchEnhance.isChecked = p.enabled
        binding.enhancePanel.switchEnhance.setOnCheckedChangeListener { _, checked ->
            p.enabled = checked
            binding.enhanceBadge.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // Face enhance
        binding.enhancePanel.switchFace.isChecked = p.faceEnhance
        binding.enhancePanel.switchFace.setOnCheckedChangeListener { _, c -> p.faceEnhance = c }

        // Scene aware
        binding.enhancePanel.switchScene.isChecked = p.sceneAware
        binding.enhancePanel.switchScene.setOnCheckedChangeListener { _, c -> p.sceneAware = c }

        // HDR sim
        binding.enhancePanel.switchHdr.isChecked = p.hdrSim
        binding.enhancePanel.switchHdr.setOnCheckedChangeListener { _, c -> p.hdrSim = c }

        // Deinterlace
        binding.enhancePanel.switchDeint.isChecked = p.deinterlace
        binding.enhancePanel.switchDeint.setOnCheckedChangeListener { _, c -> p.deinterlace = c }

        // Sliders
        setupEnhanceSlider(binding.enhancePanel.sliderSharpness, "Sharpness", 0f, 1f, p.sharpness) { p.sharpness = it }
        setupEnhanceSlider(binding.enhancePanel.sliderNoise,     "Noise Reduce", 0f, 1f, p.noise) { p.noise = it }
        setupEnhanceSlider(binding.enhancePanel.sliderContrast,  "Contrast", 0.8f, 1.6f, p.contrast) { p.contrast = it }
        setupEnhanceSlider(binding.enhancePanel.sliderBrightness,"Brightness", 0.7f, 1.4f, p.brightness) { p.brightness = it }
        setupEnhanceSlider(binding.enhancePanel.sliderSaturation,"Saturation", 0f, 2.5f, p.saturation) { p.saturation = it }
        setupEnhanceSlider(binding.enhancePanel.sliderWarmth,    "Warmth", -1f, 1f, p.warmth) { p.warmth = it }
        setupEnhanceSlider(binding.enhancePanel.sliderShadow,    "Shadow Lift", 0f, 0.2f, p.shadow) { p.shadow = it }

        // Presets
        EnhanceParams.ALL_PRESETS.forEach { (name, preset) ->
            val chip = layoutInflater.inflate(R.layout.preset_chip, binding.enhancePanel.presetsRow, false) as TextView
            chip.text = name
            chip.setOnClickListener {
                p.copyFrom(preset)
                syncEnhanceSlidersToParams()
                clearPresetSelection()
                chip.isSelected = true
            }
            binding.enhancePanel.presetsRow.addView(chip)
        }
    }

    private fun setupEnhanceSlider(slider: Slider, label: String, min: Float, max: Float, initial: Float, onChange: (Float) -> Unit) {
        slider.valueFrom = min
        slider.valueTo = max
        slider.value = initial.coerceIn(min, max)
        slider.addOnChangeListener { _, value, fromUser -> if (fromUser) onChange(value) }
    }

    private fun syncEnhanceSlidersToParams() {
        val p = playerManager.params
        binding.enhancePanel.sliderSharpness.value  = p.sharpness.coerceIn(0f, 1f)
        binding.enhancePanel.sliderNoise.value      = p.noise.coerceIn(0f, 1f)
        binding.enhancePanel.sliderContrast.value   = p.contrast.coerceIn(0.8f, 1.6f)
        binding.enhancePanel.sliderBrightness.value = p.brightness.coerceIn(0.7f, 1.4f)
        binding.enhancePanel.sliderSaturation.value = p.saturation.coerceIn(0f, 2.5f)
        binding.enhancePanel.sliderWarmth.value     = p.warmth.coerceIn(-1f, 1f)
        binding.enhancePanel.sliderShadow.value     = p.shadow.coerceIn(0f, 0.2f)
    }

    private fun clearPresetSelection() {
        for (i in 0 until binding.enhancePanel.presetsRow.childCount) {
            binding.enhancePanel.presetsRow.getChildAt(i).isSelected = false
        }
    }

    // ─── EQ PANEL ────────────────────────────────────────────

    private fun setupEqPanel() {
        val eq = playerManager.equalizer ?: return
        val bands = listOf(binding.eqPanel.band60, binding.eqPanel.band230,
            binding.eqPanel.band910, binding.eqPanel.band3600, binding.eqPanel.band14000)
        val labels = listOf("60Hz","230Hz","910Hz","3.6kHz","14kHz")

        bands.forEachIndexed { i, slider ->
            val min = eq.getMinBandLevel(); val max = eq.getMaxBandLevel()
            slider.valueFrom = min; slider.valueTo = max; slider.value = 0f
            slider.addOnChangeListener { _, value, fromUser -> if (fromUser) eq.setBand(i, value) }
        }

        // EQ presets
        AudioEqualizer.ALL_PRESETS.forEach { (name, levels) ->
            val chip = layoutInflater.inflate(R.layout.preset_chip, binding.eqPanel.eqPresetsRow, false) as TextView
            chip.text = name
            chip.setOnClickListener {
                eq.applyPreset(levels)
                bands.forEachIndexed { i, s -> s.value = levels[i].coerceIn(s.valueFrom, s.valueTo) }
            }
            binding.eqPanel.eqPresetsRow.addView(chip)
        }

        // Volume boost
        binding.eqPanel.sliderVolumeBoost.addOnChangeListener { _, v, fromUser ->
            if (fromUser) eq.setVolumeBoost(v.toInt())
        }

        // Surround
        binding.eqPanel.sliderSurround.addOnChangeListener { _, v, fromUser ->
            if (fromUser) eq.setVirtualSurround(v.toInt())
        }

        // Bass boost
        binding.eqPanel.sliderBassBoost.addOnChangeListener { _, v, fromUser ->
            if (fromUser) eq.setBassBoost(v.toInt())
        }
    }

    // ─── DIALOGS ─────────────────────────────────────────────

    private fun showSpeedDialog() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val labels = speeds.map { if (it == 1.0f) "Normal" else "${it}x" }.toTypedArray()
        val current = speeds.indexOfFirst { it == playbackSpeed }.takeIf { it >= 0 } ?: 3

        android.app.AlertDialog.Builder(this, R.style.DialogDark)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                playbackSpeed = speeds[which]
                playerManager.setPlaybackSpeed(playbackSpeed)
                binding.btnSpeed.text = if (playbackSpeed == 1.0f) "1×" else "${playbackSpeed}×"
                dialog.dismiss()
            }.show()
    }

    private fun showAudioTrackDialog() {
        val tracks = playerManager.getAudioTracks()
        if (tracks.isEmpty()) { Toast.makeText(this, "No audio tracks found", Toast.LENGTH_SHORT).show(); return }
        val arr = tracks.toTypedArray()
        android.app.AlertDialog.Builder(this, R.style.DialogDark)
            .setTitle("Audio Track")
            .setItems(arr) { _, which -> playerManager.setAudioTrack(which) }
            .show()
    }

    private fun showSubtitleDialog() {
        val items = mutableListOf("Off", "Load external (.srt, .ass)")
        items.addAll(playerManager.getSubtitleTracks())
        android.app.AlertDialog.Builder(this, R.style.DialogDark)
            .setTitle("Subtitles")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> playerManager.setSubtitleTrack(-1)
                    1 -> subtitlePicker.launch("*/*")
                    else -> playerManager.setSubtitleTrack(which - 2)
                }
            }.show()
    }

    // ─── CONTROLS VISIBILITY ─────────────────────────────────

    private fun toggleControls() { if (controlsVisible) hideControls() else showControls() }
    private fun togglePlay() {
        val p = playerManager.player
        if (p.isPlaying) p.pause() else p.play()
        showControls()
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlsOverlay.visibility = View.VISIBLE
        binding.controlsOverlay.animate().alpha(1f).setDuration(200).start()
        binding.bottomBar.visibility = View.VISIBLE
        binding.bottomBar.animate().alpha(1f).setDuration(200).start()
        scheduleHideControls()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlsOverlay.animate().alpha(0f).setDuration(400).withEndAction {
            binding.controlsOverlay.visibility = View.INVISIBLE
        }.start()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 4000)
    }

    private fun toggleEnhancePanel() {
        enhancePanelVisible = !enhancePanelVisible
        binding.enhancePanelContainer.visibility = if (enhancePanelVisible) View.VISIBLE else View.GONE
        if (enhancePanelVisible) eqPanelVisible = false.also { binding.eqPanelContainer.visibility = View.GONE }
    }

    private fun toggleEqPanel() {
        eqPanelVisible = !eqPanelVisible
        binding.eqPanelContainer.visibility = if (eqPanelVisible) View.VISIBLE else View.GONE
        if (eqPanelVisible) enhancePanelVisible = false.also { binding.enhancePanelContainer.visibility = View.GONE }
    }

    private var controlsLocked = false
    private fun toggleControlsLock() {
        controlsLocked = !controlsLocked
        binding.btnLock.setImageResource(if (controlsLocked) R.drawable.ic_lock else R.drawable.ic_lock_open)
        if (controlsLocked) {
            binding.controlsRow.visibility = View.GONE
            binding.seekBar.isEnabled = false
        } else {
            binding.controlsRow.visibility = View.VISIBLE
            binding.seekBar.isEnabled = true
        }
    }

    // ─── ASPECT RATIO ────────────────────────────────────────

    private fun cycleAspectRatio() {
        aspectRatioIndex = (aspectRatioIndex + 1) % aspectRatios.size
        binding.btnAspect.text = aspectRatios[aspectRatioIndex]
        when (aspectRatioIndex) {
            0 -> binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            3 -> binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            4 -> binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    // ─── SCREENSHOT ──────────────────────────────────────────

    private fun takeScreenshot() {
        lifecycleScope.launch {
            try {
                val bitmap = Bitmap.createBitmap(
                    binding.playerView.width, binding.playerView.height, Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                binding.playerView.draw(canvas)
                val uri = ScreenshotHelper.saveFrame(this@MainActivity, bitmap)
                withContext(Dispatchers.Main) {
                    if (uri != null) Toast.makeText(this@MainActivity, "Screenshot saved", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this@MainActivity, "Screenshot failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Screenshot error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── PICTURE IN PICTURE ──────────────────────────────────

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerManager.player.isPlaying) {
            val rational = Rational(16, 9)
            val params = PictureInPictureParams.Builder().setAspectRatio(rational).build()
            enterPictureInPictureMode(params)
        }
    }

    // ─── FULLSCREEN ──────────────────────────────────────────

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) hideSystemUI() else showSystemUI()
        binding.btnFullscreen.setImageResource(
            if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            window.insetsController?.show(WindowInsets.Type.systemBars())
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // ─── OVERLAY ─────────────────────────────────────────────

    private fun showGestureOverlay(label: String, value: Float) {
        binding.gestureOverlay.tvOverlayLabel.text = label
        binding.gestureOverlay.progressOverlay.progress = (value * 100).toInt().coerceIn(0, 100)
        binding.gestureOverlay.root.visibility = View.VISIBLE
        binding.gestureOverlay.root.alpha = 1f
        handler.removeCallbacksAndMessages("overlay")
        handler.postDelayed({
            binding.gestureOverlay.root.animate().alpha(0f).setDuration(300).withEndAction {
                binding.gestureOverlay.root.visibility = View.GONE
            }.start()
        }, 800)
    }

    // ─── HELPERS ─────────────────────────────────────────────

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val h = m / 60
        return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60) else "%d:%02d".format(m, s % 60)
    }

    private fun requestFileOpen() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)

        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })
            filePicker.launch("video/*")
        else permLauncher.launch(perms)
    }

    private fun openPlaylistPicker() { multiFilePicker.launch("video/*") }

    private fun saveWatchHistory() {
        val uri = currentUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            db.watchHistoryDao().upsert(WatchHistory(
                uriString    = uri.toString(),
                fileName     = uri.lastPathSegment ?: "Video",
                lastPosition = playerManager.player.currentPosition,
                duration     = playerManager.player.duration
            ))
        }
    }

    // ─── LIFECYCLE ───────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        saveWatchHistory()
        if (!isInPictureInPictureMode) playerManager.player.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        playerManager.release()
    }

    override fun onBackPressed() {
        when {
            enhancePanelVisible -> toggleEnhancePanel()
            eqPanelVisible      -> toggleEqPanel()
            isFullscreen        -> toggleFullscreen()
            else                -> super.onBackPressed()
        }
    }
}
