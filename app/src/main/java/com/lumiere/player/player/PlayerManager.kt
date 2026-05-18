package com.lumiere.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.lumiere.player.enhancement.*
import kotlinx.coroutines.*

class PlayerManager(private val context: Context) {

    lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

    val params = EnhanceParams()
    private var videoEffect: LumiereVideoEffect? = null
    var faceManager: FaceEnhancementManager? = null
    var sceneClassifier: SceneClassifier? = null
    var equalizer: AudioEqualizer? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Playlist
    val playlist = mutableListOf<Uri>()
    var currentIndex = 0

    // Callbacks
    var onPlaybackStateChanged: ((Int) -> Unit)? = null
    var onIsPlayingChanged: ((Boolean) -> Unit)? = null
    var onPositionChanged: ((Long, Long) -> Unit)? = null
    var onTracksChanged: ((Tracks) -> Unit)? = null
    var onError: ((PlaybackException) -> Unit)? = null

    fun init() {
        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("en"))
        }

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        onPlaybackStateChanged?.invoke(state)
                        if (state == Player.STATE_READY) setupEffects()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onIsPlayingChanged?.invoke(isPlaying)
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        onTracksChanged?.invoke(tracks)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        onError?.invoke(error)
                    }
                })
            }

        // Position update loop
        scope.launch {
            while (isActive) {
                if (::player.isInitialized) {
                    onPositionChanged?.invoke(player.currentPosition, player.duration)
                }
                delay(500)
            }
        }

        // Init AI components
        faceManager = FaceEnhancementManager(context).apply { init() }
        sceneClassifier = SceneClassifier(context).apply { init() }
    }

    fun loadUri(uri: Uri, startPosition: Long = 0L) {
        val item = MediaItem.fromUri(uri)
        player.setMediaItem(item)
        player.prepare()
        if (startPosition > 0) player.seekTo(startPosition)
        player.playWhenReady = true
    }

    fun loadPlaylist(uris: List<Uri>, startIndex: Int = 0) {
        val items = uris.map { MediaItem.fromUri(it) }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        playlist.clear()
        playlist.addAll(uris)
        currentIndex = startIndex
    }

    private fun setupEffects() {
        try {
            val effect = LumiereVideoEffect(params)
            videoEffect = effect
            player.setVideoEffects(listOf(effect))
        } catch (e: Exception) { /* fallback handled in MainActivity */ }

        val sessionId = player.audioSessionId
        if (sessionId != AudioAttributes.DEFAULT.let { 0 }) {
            equalizer = AudioEqualizer(sessionId).apply { init() }
        }
    }

    fun updateShaderFaces(regions: Array<FloatArray>) {
        videoEffect?.shader?.faceRegions = regions
    }

    fun updateShaderScene(sceneType: Int) {
        videoEffect?.shader?.sceneType = sceneType
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackParameters(PlaybackParameters(speed))
    }

    fun setAudioTrack(index: Int) {
        val tracks = player.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (index < audioGroups.size) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            )
        }
    }

    fun setSubtitleTrack(index: Int) {
        val params = trackSelector.buildUponParameters()
        if (index < 0) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context)
        }
        trackSelector.setParameters(params)
    }

    fun addExternalSubtitle(uri: Uri) {
        val current = player.currentMediaItem ?: return
        val updated = current.buildUpon()
            .setSubtitleConfigurations(
                listOf(MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build())
            ).build()
        val pos = player.currentPosition
        player.setMediaItem(updated, pos)
        player.prepare()
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    fun getAudioTracks(): List<String> {
        val result = mutableListOf<String>()
        player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .forEachIndexed { i, group ->
                val format = group.getTrackFormat(0)
                result.add("Track ${i+1}: ${format.language ?: "Unknown"} (${format.sampleMimeType ?: ""})")
            }
        return result
    }

    fun getSubtitleTracks(): List<String> {
        val result = mutableListOf<String>()
        player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .forEachIndexed { i, group ->
                val format = group.getTrackFormat(0)
                result.add("Sub ${i+1}: ${format.language ?: "Unknown"}")
            }
        return result
    }

    fun release() {
        scope.cancel()
        equalizer?.release()
        faceManager?.release()
        sceneClassifier?.release()
        player.release()
    }
}
