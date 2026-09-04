package com.tapedeck.dsp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val trackName: String? = null,
    val albumName: String? = null,
    val albumArt: android.graphics.Bitmap? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val vuLeft: Float = 0f,
    val vuRight: Float = 0f,
    val tapeAge: Float = 0.3f,
    val dustDirt: Float = 0.2f,
    val tapeType: TapeType = TapeType.TYPE_I,
    val playlist: List<PlaylistTrack> = emptyList(),
    val currentTrackIndex: Int = -1,
    val error: String? = null,
)

/**
 * Owns the native audio engine and runs as a foreground service so playback
 * (and the native Oboe stream) survives the hosting Activity being destroyed
 * or the app being backgrounded. Exposes a MediaSession-backed notification
 * with prev/play-pause/next controls, and its state as a StateFlow that
 * PlayerViewModel binds to and mirrors into PlayerUiState.
 */
class PlaybackService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val engine = AudioEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private lateinit var mediaSession: MediaSessionCompat
    private var isForeground = false

    // True only while paused because the active output device (wired or
    // Bluetooth headphones) disconnected mid-playback - as opposed to a
    // deliberate user pause - so we know it's safe to auto-resume once a
    // route reappears, without ever auto-resuming a pause the user chose.
    private var pausedDueToRouteChange = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && engine.isPlaying) {
                pausedDueToRouteChange = true
                pause()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if (!pausedDueToRouteChange) return
            if (addedDevices.any { it.type in RESUMABLE_OUTPUT_DEVICE_TYPES }) {
                resume()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine.setTapeAge(_state.value.tapeAge)
        engine.setDustDirt(_state.value.dustDirt)
        engine.setTapeType(_state.value.tapeType)

        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "TapeDeckDSP").apply {
            setCallback(sessionCallback)
            isActive = true
        }

        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        startMeterLoop()
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = resume()
        override fun onPause() = pause()
        override fun onSkipToNext() = playNextTrack()
        override fun onSkipToPrevious() = playPreviousTrack()
        override fun onStop() = stopPlayback()
        override fun onSeekTo(pos: Long) {
            engine.seekToMs(pos)
            _state.update { it.copy(positionMs = pos) }
            updateMediaSessionPlaybackState()
        }

        // Handle the raw hardware key ourselves rather than relying on the
        // library's default translation - wired (USB-C/3.5mm) headset
        // buttons and some Bluetooth AVRCP stacks deliver a KeyEvent here,
        // and OEM-specific default handling for MEDIA_PREVIOUS in particular
        // isn't reliably "skip to previous track" across devices.
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val keyEvent = mediaButtonIntent.parcelableExtraCompat<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return super.onMediaButtonEvent(mediaButtonIntent)
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return true

            return when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> { playNextTrack(); true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { playPreviousTrack(); true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { resume(); true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { pause(); true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { togglePlayPause(); true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { stopPlayback(); true }
                else -> super.onMediaButtonEvent(mediaButtonIntent)
            }
        }
    }

    fun loadTrack(uri: Uri, displayName: String?, autoPlay: Boolean = false) {
        serviceScope.launch {
            engine.stop()
            _state.update {
                it.copy(isLoading = true, error = null, isPlaying = false, albumName = null, albumArt = null)
            }
            try {
                val metadataDeferred = async { AudioMetadataReader.read(this@PlaybackService, uri) }
                engine.load(this@PlaybackService, uri)
                if (autoPlay) {
                    pausedDueToRouteChange = false
                    engine.play()
                    ensureForeground()
                }

                val metadata = metadataDeferred.await()
                val fallbackTitle = displayName?.substringBeforeLast('.')
                _state.update { current ->
                    // Playlist entries are titled from the M3U/filename at
                    // parse time (no per-track tag read, so loading a large
                    // playlist stays fast) - patch in the real ID3 title for
                    // the track that just finished loading, now that we have
                    // it, so the playlist bar matches the header above it.
                    val playlist = if (metadata.title != null && current.currentTrackIndex in current.playlist.indices) {
                        current.playlist.toMutableList().apply {
                            this[current.currentTrackIndex] = this[current.currentTrackIndex].copy(title = metadata.title)
                        }
                    } else {
                        current.playlist
                    }
                    current.copy(
                        isLoading = false,
                        trackName = metadata.title ?: fallbackTitle,
                        albumName = metadata.album,
                        albumArt = metadata.albumArt,
                        durationMs = engine.durationMs,
                        positionMs = 0,
                        isPlaying = autoPlay,
                        playlist = playlist,
                    )
                }
                updateMediaSessionMetadata()
                updateMediaSessionPlaybackState()
                updateNotification()
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, error = t.message ?: "Failed to load track") }
            }
        }
    }

    fun playQueue(tracks: List<PlaylistTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        _state.update { it.copy(playlist = tracks, currentTrackIndex = -1) }
        playTrackAt(startIndex)
    }

    fun playTrackAt(index: Int) {
        val tracks = _state.value.playlist
        if (index !in tracks.indices) return
        _state.update { it.copy(currentTrackIndex = index) }
        loadTrack(tracks[index].uri, tracks[index].title, autoPlay = true)
    }

    fun playNextTrack() {
        val s = _state.value
        if (s.currentTrackIndex + 1 < s.playlist.size) playTrackAt(s.currentTrackIndex + 1)
    }

    // "Previous" restarts the current track on a single press - unless
    // we're already near the start of it (restarting would do nothing
    // useful), or this press follows another one within a couple seconds,
    // in which case it actually skips back a track. Matches the convention
    // most music players use for the previous-track control.
    private var lastPreviousPressAtMs = 0L

    fun playPreviousTrack() {
        val now = SystemClock.elapsedRealtime()
        val isDoublePress = now - lastPreviousPressAtMs <= PREVIOUS_DOUBLE_PRESS_WINDOW_MS
        lastPreviousPressAtMs = now

        val s = _state.value
        val hasPreviousTrack = s.currentTrackIndex - 1 >= 0
        val nearStartOfTrack = s.positionMs < PREVIOUS_RESTART_THRESHOLD_MS

        if (hasPreviousTrack && (nearStartOfTrack || isDoublePress)) {
            playTrackAt(s.currentTrackIndex - 1)
        } else {
            engine.seekToMs(0)
            _state.update { it.copy(positionMs = 0) }
            updateMediaSessionPlaybackState()
        }
    }

    fun togglePlayPause() {
        if (_state.value.trackName == null) return
        if (engine.isPlaying) pause() else resume()
    }

    private fun resume() {
        pausedDueToRouteChange = false
        engine.play()
        _state.update { it.copy(isPlaying = true) }
        ensureForeground()
        updateMediaSessionPlaybackState()
        updateNotification()
    }

    private fun pause() {
        engine.pause()
        _state.update { it.copy(isPlaying = false) }
        updateMediaSessionPlaybackState()
        updateNotification()
    }

    fun stopPlayback() {
        engine.stop()
        _state.update { it.copy(isPlaying = false, positionMs = 0) }
        updateMediaSessionPlaybackState()
        stopForegroundAndRemoveNotification()
    }

    fun seekBy(deltaMs: Long) {
        val target = (engine.positionMs + deltaMs).coerceIn(0, _state.value.durationMs)
        engine.seekToMs(target)
        _state.update { it.copy(positionMs = target) }
        updateMediaSessionPlaybackState()
    }

    fun setTapeAge(value01: Float) {
        engine.setTapeAge(value01)
        _state.update { it.copy(tapeAge = value01) }
    }

    fun setDustDirt(value01: Float) {
        engine.setDustDirt(value01)
        _state.update { it.copy(dustDirt = value01) }
    }

    fun setTapeType(type: TapeType) {
        engine.setTapeType(type)
        _state.update { it.copy(tapeType = type) }
    }

    private fun startMeterLoop() {
        serviceScope.launch {
            var wasPlaying = false
            while (isActive) {
                val enginePlaying = engine.isPlaying
                val position = engine.positionMs
                val duration = _state.value.durationMs

                val trackFinished = wasPlaying && !enginePlaying && duration > 0 && position >= duration - 60
                wasPlaying = enginePlaying

                _state.update {
                    it.copy(
                        isPlaying = enginePlaying,
                        positionMs = position,
                        vuLeft = engine.vuLeft,
                        vuRight = engine.vuRight,
                    )
                }
                updateMediaSessionPlaybackState()

                if (trackFinished && _state.value.playlist.isNotEmpty()) {
                    playNextTrack()
                }

                delay(33)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNextTrack()
            ACTION_PREV -> playPreviousTrack()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground() {
        if (isForeground) return
        isForeground = true
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun stopForegroundAndRemoveNotification() {
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification() {
        if (!isForeground) return
        if (!hasNotificationPermission()) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
            description = "TapeDeck DSP playback controls"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val state = _state.value

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseIcon = if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val playPauseLabel = if (state.isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.trackName ?: "TapeDeck DSP")
            .setContentText(state.albumName ?: "")
            .setLargeIcon(state.albumArt)
            .setContentIntent(contentIntent)
            .setDeleteIntent(servicePendingIntent(ACTION_STOP))
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_notif_skip_previous, "Previous", servicePendingIntent(ACTION_PREV))
            .addAction(playPauseIcon, playPauseLabel, servicePendingIntent(ACTION_PLAY_PAUSE))
            .addAction(R.drawable.ic_notif_skip_next, "Next", servicePendingIntent(ACTION_NEXT))
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateMediaSessionMetadata() {
        val state = _state.value
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.trackName ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, state.albumName ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
            .apply {
                state.albumArt?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
            }
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updateMediaSessionPlaybackState() {
        val state = _state.value
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.positionMs,
                1.0f,
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    override fun onDestroy() {
        unregisterReceiver(becomingNoisyReceiver)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        serviceScope.cancel()
        mediaSession.release()
        engine.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tapedeck_playback"
        private const val NOTIFICATION_ID = 1001
        private const val PREVIOUS_DOUBLE_PRESS_WINDOW_MS = 3000L
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 10_000L
        private val RESUMABLE_OUTPUT_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )
        private const val ACTION_PLAY_PAUSE = "com.tapedeck.dsp.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.tapedeck.dsp.action.NEXT"
        private const val ACTION_PREV = "com.tapedeck.dsp.action.PREV"
        private const val ACTION_STOP = "com.tapedeck.dsp.action.STOP"
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
