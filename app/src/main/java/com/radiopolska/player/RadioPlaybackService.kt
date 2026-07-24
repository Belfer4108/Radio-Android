package com.radiopolska.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle
import com.radiopolska.alarm.RadioAlarmScheduler
import com.radiopolska.alarm.RadioAlarmStore
import com.radiopolska.MainActivity
import com.radiopolska.data.PolishRadioStations
import com.radiopolska.data.RadioStation
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class RadioPlayerState(
    val currentStation: RadioStation? = null,
    val volume: Float = 1f,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val status: String = "Wybierz stację",
    val streamIndex: Int = 0,
    val dataUsedBytes: Long = 0L,
    val wifiDataUsedBytes: Long = 0L,
    val mobileDataUsedBytes: Long = 0L,
    val otherDataUsedBytes: Long = 0L,
    val currentNetworkLabel: String = "Nieznana sieć",
    val sleepTimerEndAtMillis: Long? = null,
    val remainingSleepSeconds: Long = 0L,
    val isRecording: Boolean = false,
    val recordingFileName: String? = null,
    val recordingBytes: Long = 0L,
    val rdsText: String = "Oczekiwanie na RDS",
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Flat",
    val equalizerBands: List<Int> = listOf(0, 0, 0, 0, 0),
    val colorofonEnabled: Boolean = false,
    val colorofonIntensity: Int = 0,
    val colorofonBass: Int = 70,
    val colorofonMid: Int = 35,
    val colorofonTreble: Int = 45,
    val colorofonUseTorch: Boolean = true,
    val colorofonBassLevel: Int = 0,
    val colorofonMidLevel: Int = 0,
    val colorofonTrebleLevel: Int = 0,
    val alarmActive: Boolean = false,
    val alarmStationId: String? = null,
)

@UnstableApi
class RadioPlaybackService : MediaSessionService() {
    private lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var activeStation: RadioStation? = null
    private var activeUrls: List<String> = emptyList()
    private var activeIndex: Int = 0
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var torchCameraId: String? = null
    private var torchStrengthMaxLevel: Int = 1
    private var currentTorchStrengthLevel: Int = 0
    private var torchOn = false
    private var lastTorchChangeAt = 0L
    private var torchOffRunnable: Runnable? = null
    private var volumeRampRunnable: Runnable? = null
    private var reconnectRunnable: Runnable? = null
    private var reconnectAttempts = 0
    @Volatile private var recordingActive = false
    private var recordingThread: Thread? = null
    private var recordingConnection: HttpURLConnection? = null
    private var recordingTargetFile: File? = null
    private var recordingStartedAt: String? = null
    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsTicker = object : Runnable {
        override fun run() {
            tickPlaybackStats()
            statsHandler.postDelayed(this, STATS_TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: creating single ExoPlayer instance")
        setListener(
            object : MediaSessionService.Listener {
                override fun onForegroundServiceStartNotAllowedException() {
                    Log.e(TAG, "Foreground service start was blocked by Android")
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            status = "Android zablokowal odtwarzanie w tle. Otworz aplikacje i wlacz radio ponownie.",
                        )
                    }
                }
            },
        )
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK)
        createPlaybackNotificationChannel()
        setMediaNotificationProvider(
            object : MediaNotification.Provider {
                override fun createNotification(
                    mediaSession: MediaSession,
                    mediaButtonPreferences: ImmutableList<CommandButton>,
                    actionFactory: MediaNotification.ActionFactory,
                    onNotificationChangedCallback: MediaNotification.Provider.Callback,
                ): MediaNotification =
                    MediaNotification(
                        MEDIA_NOTIFICATION_ID,
                        buildPlaybackNotification(
                            mediaSession = mediaSession,
                            actionFactory = actionFactory,
                            title = mediaSession.player.mediaMetadata.title?.toString()
                                ?: activeStation?.name
                                ?: "Radio Polska",
                            text = mediaSession.player.mediaMetadata.artist?.toString()
                                ?: activeStation?.city
                                ?: "Odtwarzanie radia",
                            showPause = !shouldShowPlayButton(mediaSession.player),
                        ),
                    )

                override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false
            },
        )
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityIntent)
            .build()
        attachPlayerDiagnostics()
        statsHandler.post(statsTicker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: action=${intent?.action}, startId=$startId")
        when (intent?.action) {
            ACTION_PLAY -> {
                val stationId = intent.getStringExtra(EXTRA_STATION_ID)
                val station = PolishRadioStations.firstOrNull { it.id == stationId }
                if (station != null) {
                    ensurePlaybackForegroundStarted(station.name)
                    play(station)
                } else {
                    Log.e(TAG, "play requested for unknown stationId=$stationId")
                }
            }
            ACTION_TOGGLE -> toggle()
            ACTION_STOP -> stopPlayback()
            ACTION_NEXT -> playRelative(offset = 1)
            ACTION_PREVIOUS -> playRelative(offset = -1)
            ACTION_SET_SLEEP_TIMER -> setSleepTimer(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0))
            ACTION_CANCEL_SLEEP_TIMER -> cancelSleepTimer()
            ACTION_RESET_TRANSFER -> resetTransfer()
            ACTION_TOGGLE_RECORDING -> toggleRecording()
            ACTION_SET_VOLUME -> setVolume(intent.getFloatExtra(EXTRA_VOLUME, 1f))
            ACTION_SET_EQUALIZER_ENABLED -> setEqualizerEnabled(intent.getBooleanExtra(EXTRA_ENABLED, false))
            ACTION_SET_EQUALIZER_PRESET -> setEqualizerPreset(intent.getStringExtra(EXTRA_PRESET).orEmpty())
            ACTION_SET_EQUALIZER_BAND -> setEqualizerBand(intent.getIntExtra(EXTRA_BAND_INDEX, 0), intent.getIntExtra(EXTRA_BAND_LEVEL, 0))
            ACTION_SET_COLOROFON_ENABLED -> setColorofonEnabled(intent.getBooleanExtra(EXTRA_ENABLED, false))
            ACTION_SET_COLOROFON_INTENSITY -> setColorofonIntensity(intent.getIntExtra(EXTRA_INTENSITY, 0))
            ACTION_SET_COLOROFON_BAND -> setColorofonBand(intent.getStringExtra(EXTRA_BAND).orEmpty(), intent.getIntExtra(EXTRA_BAND_LEVEL, 50))
            ACTION_ALARM_SNOOZE -> snoozeAlarm()
            ACTION_ALARM_DISMISS -> dismissAlarm(keepPlaying = intent.getBooleanExtra(EXTRA_KEEP_PLAYING, false))
            ACTION_ALARM_PLAY -> {
                val stationId = intent.getStringExtra(EXTRA_STATION_ID).orEmpty()
                val station = PolishRadioStations.firstOrNull { it.id == stationId }
                if (station != null) {
                    ensurePlaybackForegroundStarted(station.name)
                    playAlarm(
                        station = station,
                        rampVolume = intent.getBooleanExtra(EXTRA_RAMP_VOLUME, true),
                        autoOffMinutes = intent.getIntExtra(EXTRA_AUTO_OFF_MINUTES, 60),
                        alarmVolumePercent = intent.getIntExtra(EXTRA_ALARM_VOLUME_PERCENT, 80),
                    )
                } else {
                    Log.e(TAG, "alarm requested unknown stationId=$stationId")
                }
            }
        }
        return START_STICKY
    }

    private fun ensurePlaybackForegroundStarted(stationName: String) {
        createPlaybackNotificationChannel()
        val notification = mediaSession?.let { session ->
            buildPlaybackNotification(
                mediaSession = session,
                actionFactory = null,
                title = stationName,
                text = "Odtwarzanie radia",
                showPause = true,
            )
        } ?: NotificationCompat.Builder(this, MEDIA_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(stationName)
            .setContentText("Uruchamianie odtwarzania")
            .setContentIntent(sessionActivityPendingIntent())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            MEDIA_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun buildPlaybackNotification(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory?,
        title: String,
        text: String,
        showPause: Boolean,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, MEDIA_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(mediaSession.sessionActivity ?: sessionActivityPendingIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        addPlaybackNotificationAction(builder, actionFactory, mediaSession, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, android.R.drawable.ic_media_previous, "Poprzednia")
        addPlaybackNotificationAction(
            builder,
            actionFactory,
            mediaSession,
            Player.COMMAND_PLAY_PAUSE,
            if (showPause) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (showPause) "Pauza" else "Odtworz",
        )
        addPlaybackNotificationAction(builder, actionFactory, mediaSession, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, android.R.drawable.ic_media_next, "Nastepna")
        addPlaybackNotificationAction(builder, actionFactory, mediaSession, Player.COMMAND_STOP, android.R.drawable.ic_menu_close_clear_cancel, "Stop")

        return builder
            .setStyle(MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun addPlaybackNotificationAction(
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory?,
        mediaSession: MediaSession,
        command: Int,
        iconRes: Int,
        title: String,
    ) {
        val action = actionFactory?.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(this, iconRes),
            title,
            command,
        ) ?: NotificationCompat.Action(iconRes, title, pendingIntentForPlaybackCommand(command))
        builder.addAction(action)
    }

    private fun pendingIntentForPlaybackCommand(command: Int): PendingIntent {
        val action = when (command) {
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> ACTION_PREVIOUS
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> ACTION_NEXT
            Player.COMMAND_STOP -> ACTION_STOP
            else -> ACTION_TOGGLE
        }
        return PendingIntent.getService(
            this,
            command,
            Intent(this, RadioPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createPlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(MEDIA_NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                MEDIA_NOTIFICATION_CHANNEL_ID,
                "Odtwarzanie radia",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun sessionActivityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun shouldShowPlayButton(player: Player): Boolean =
        !player.playWhenReady || player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: releasing ExoPlayer and MediaSession")
        statsHandler.removeCallbacks(statsTicker)
        stopRecording()
        releaseAudioEffects()
        volumeRampRunnable?.let(statsHandler::removeCallbacks)
        volumeRampRunnable = null
        reconnectRunnable?.let(statsHandler::removeCallbacks)
        reconnectRunnable = null
        setTorch(false)
        mediaSession?.release()
        mediaSession = null
        exoPlayer.release()
        _state.value = RadioPlayerState()
        super.onDestroy()
    }

    private fun attachPlayerDiagnostics() {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (!isPlaying) setTorch(false)
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    Log.d(TAG, "onAudioSessionIdChanged: audioSessionId=$audioSessionId")
                    this@RadioPlaybackService.audioSessionId = audioSessionId
                    rebuildAudioEffects()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(TAG, "onPlaybackStateChanged: ${playbackState.toReadableState()}")
                    _state.update {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> it.copy(isBuffering = true, status = "Łączenie ze strumieniem")
                            Player.STATE_READY -> it.copy(isBuffering = false, status = if (exoPlayer.isPlaying) "Odtwarzanie" else "Gotowe")
                            Player.STATE_ENDED -> it.copy(isBuffering = false, isPlaying = false, status = "Zatrzymano")
                            else -> it
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "onPlayerError: station=${activeStation?.id}, index=$activeIndex, message=${error.message}", error)
                    val nextIndex = activeIndex + 1
                    if (nextIndex < activeUrls.size) {
                        playAt(nextIndex)
                    } else {
                        _state.update {
                            it.copy(isPlaying = false, isBuffering = false, status = "Nie udalo sie odtworzyc strumienia")
                        }
                        scheduleReconnect()
                    }
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title?.toString()?.trim().orEmpty()
                    updateRdsText(title)
                }

                override fun onMetadata(metadata: Metadata) {
                    for (index in 0 until metadata.length()) {
                        val entry = metadata[index]
                        if (entry is IcyInfo) {
                            updateRdsText(entry.title?.trim().orEmpty())
                        }
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val stationId = mediaItem?.mediaId.orEmpty()
                    val station = PolishRadioStations.firstOrNull { it.id == stationId } ?: return
                    if (station.id == activeStation?.id) return
                    Log.d(TAG, "onMediaItemTransition: reason=$reason, station=${station.id}")
                    activeStation = station
                    activeUrls = station.streamUrls.ifEmpty {
                        listOfNotNull(station.streamUrl, station.fallbackStreamUrl)
                    }.distinct()
                    activeIndex = 0
                    _state.update {
                        it.copy(
                            currentStation = station,
                            isBuffering = true,
                            status = "Laczenie ze strumieniem",
                            streamIndex = 0,
                            rdsText = "Oczekiwanie na RDS",
                        )
                    }
                }
            },
        )
    }

    private fun play(station: RadioStation) {
        Log.d(TAG, "play: station=${station.id}, name=${station.name}")
        reconnectRunnable?.let(statsHandler::removeCallbacks)
        reconnectRunnable = null
        reconnectAttempts = 0
        activeStation = station
        activeUrls = station.streamUrls.ifEmpty {
            listOfNotNull(station.streamUrl, station.fallbackStreamUrl)
        }.distinct()
        activeIndex = 0
        _state.update {
            it.copy(
                currentStation = station,
                isBuffering = true,
                status = "Laczenie ze strumieniem",
                streamIndex = 0,
                rdsText = "Oczekiwanie na RDS",
            )
        }
        val stationIndex = PolishRadioStations.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        exoPlayer.setMediaItems(PolishRadioStations.map(::stationMediaItem), stationIndex, C.TIME_UNSET)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun playAlarm(station: RadioStation, rampVolume: Boolean, autoOffMinutes: Int, alarmVolumePercent: Int) {
        Log.d(TAG, "playAlarm: station=${station.id}, rampVolume=$rampVolume, autoOffMinutes=$autoOffMinutes, alarmVolumePercent=$alarmVolumePercent")
        volumeRampRunnable?.let(statsHandler::removeCallbacks)
        volumeRampRunnable = null
        _state.update { it.copy(alarmActive = true, alarmStationId = station.id) }
        setMusicStreamVolume(alarmVolumePercent)
        exoPlayer.volume = if (rampVolume) 0.15f else 1f
        play(station)
        if (rampVolume) startVolumeRamp()
        if (autoOffMinutes > 0) setSleepTimer(autoOffMinutes)
    }

    private fun setMusicStreamVolume(percent: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetVolume = ((percent.coerceIn(5, 100) / 100f) * maxVolume).roundToInt().coerceIn(1, maxVolume)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        }.onFailure { error ->
            Log.e(TAG, "setMusicStreamVolume failed: ${error.message}", error)
        }
    }

    private fun snoozeAlarm() {
        val config = RadioAlarmStore.load(this)
        Log.d(TAG, "snoozeAlarm: minutes=${config.snoozeMinutes}")
        volumeRampRunnable?.let(statsHandler::removeCallbacks)
        volumeRampRunnable = null
        stopPlayback()
        _state.update { it.copy(alarmActive = false, alarmStationId = null) }
        RadioAlarmScheduler.scheduleSnooze(this, config.snoozeMinutes)
    }

    private fun dismissAlarm(keepPlaying: Boolean) {
        Log.d(TAG, "dismissAlarm: keepPlaying=$keepPlaying")
        volumeRampRunnable?.let(statsHandler::removeCallbacks)
        volumeRampRunnable = null
        exoPlayer.volume = 1f
        _state.update { it.copy(alarmActive = false, alarmStationId = null) }
        if (!keepPlaying) stopPlayback()
    }

    private fun startVolumeRamp() {
        val startedAt = System.currentTimeMillis()
        val durationMs = 90_000L
        val ramp = object : Runnable {
            override fun run() {
                val progress = ((System.currentTimeMillis() - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
                exoPlayer.volume = 0.15f + progress * 0.85f
                if (progress < 1f) {
                    statsHandler.postDelayed(this, 2000L)
                } else {
                    volumeRampRunnable = null
                }
            }
        }
        volumeRampRunnable = ramp
        statsHandler.post(ramp)
    }

    private fun toggle() {
        if (exoPlayer.isPlaying) {
            Log.d(TAG, "pause: station=${activeStation?.id}")
            exoPlayer.pause()
            _state.update { it.copy(isPlaying = false, status = "Pauza") }
        } else if (activeStation != null) {
            Log.d(TAG, "resume: station=${activeStation?.id}")
            exoPlayer.play()
            _state.update { it.copy(status = "Odtwarzanie") }
        } else {
            Log.d(TAG, "toggle ignored: no active station")
        }
    }

    private fun stopPlayback() {
        Log.d(TAG, "stop: station=${activeStation?.id}")
        reconnectRunnable?.let(statsHandler::removeCallbacks)
        reconnectRunnable = null
        reconnectAttempts = 0
        exoPlayer.stop()
        _state.update { it.copy(isPlaying = false, isBuffering = false, status = "Zatrzymano", alarmActive = false, alarmStationId = null) }
    }

    private fun scheduleReconnect() {
        val station = activeStation ?: return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "scheduleReconnect: giving up station=${station.id}")
            _state.update { it.copy(status = "Strumien niedostepny") }
            return
        }
        reconnectAttempts += 1
        val delayMs = reconnectAttempts * 5_000L
        Log.d(TAG, "scheduleReconnect: attempt=$reconnectAttempts, delayMs=$delayMs, station=${station.id}")
        val reconnect = Runnable {
            reconnectRunnable = null
            _state.update { it.copy(isBuffering = true, status = "Ponowne laczenie ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}") }
            playAt(0)
        }
        reconnectRunnable = reconnect
        statsHandler.postDelayed(reconnect, delayMs)
    }

    private fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        Log.d(TAG, "setSleepTimer: minutes=$minutes, endAt=$endAt")
        _state.update {
            it.copy(
                sleepTimerEndAtMillis = endAt,
                remainingSleepSeconds = ((endAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
            )
        }
    }

    private fun setVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        Log.d(TAG, "setVolume: volume=$volume")
        exoPlayer.volume = volume
        _state.update { it.copy(volume = volume) }
    }

    private fun cancelSleepTimer() {
        Log.d(TAG, "cancelSleepTimer")
        _state.update { it.copy(sleepTimerEndAtMillis = null, remainingSleepSeconds = 0L) }
    }

    private fun resetTransfer() {
        Log.d(TAG, "resetTransfer")
        _state.update {
            it.copy(
                dataUsedBytes = 0L,
                wifiDataUsedBytes = 0L,
                mobileDataUsedBytes = 0L,
                otherDataUsedBytes = 0L,
            )
        }
    }

    private fun setEqualizerEnabled(enabled: Boolean) {
        Log.d(TAG, "setEqualizerEnabled: enabled=$enabled")
        _state.update { it.copy(equalizerEnabled = enabled) }
        rebuildAudioEffects()
    }

    private fun setEqualizerPreset(name: String) {
        val preset = EQUALIZER_PRESETS[name] ?: EQUALIZER_PRESETS.getValue("Flat")
        Log.d(TAG, "setEqualizerPreset: preset=$name")
        _state.update {
            it.copy(
                equalizerEnabled = true,
                equalizerPreset = EQUALIZER_PRESETS.entries.first { entry -> entry.value == preset }.key,
                equalizerBands = preset,
            )
        }
        rebuildAudioEffects()
    }

    private fun setEqualizerBand(index: Int, level: Int) {
        val bounded = level.coerceIn(-12, 12)
        val bands = _state.value.equalizerBands.toMutableList()
        if (index !in bands.indices) return
        bands[index] = bounded
        Log.d(TAG, "setEqualizerBand: index=$index, level=$bounded")
        _state.update {
            it.copy(
                equalizerEnabled = true,
                equalizerPreset = "Manual",
                equalizerBands = bands,
            )
        }
        applyEqualizerLevels()
    }

    private fun setColorofonEnabled(enabled: Boolean) {
        Log.d(TAG, "setColorofonEnabled: enabled=$enabled")
        _state.update { it.copy(colorofonEnabled = enabled) }
        rebuildVisualizer()
        if (!enabled) {
            torchOffRunnable?.let(statsHandler::removeCallbacks)
            torchOffRunnable = null
            setTorch(false)
        }
    }

    private fun setColorofonIntensity(intensity: Int) {
        val bounded = intensity.coerceIn(-100, 100)
        Log.d(TAG, "setColorofonIntensity: intensity=$bounded")
        _state.update { it.copy(colorofonIntensity = bounded) }
    }

    private fun setColorofonBand(band: String, level: Int) {
        val bounded = level.coerceIn(0, 100)
        Log.d(TAG, "setColorofonBand: band=$band, level=$bounded")
        _state.update {
            when (band) {
                "bass" -> it.copy(colorofonBass = bounded)
                "mid" -> it.copy(colorofonMid = bounded)
                "treble" -> it.copy(colorofonTreble = bounded)
                else -> it
            }
        }
    }

    private fun rebuildAudioEffects() {
        rebuildEqualizer()
        rebuildVisualizer()
    }

    private fun releaseAudioEffects() {
        equalizer?.release()
        equalizer = null
        visualizer?.release()
        visualizer = null
        torchOffRunnable?.let(statsHandler::removeCallbacks)
        torchOffRunnable = null
    }

    private fun rebuildEqualizer() {
        equalizer?.release()
        equalizer = null
        val sessionId = audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        val state = _state.value
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = state.equalizerEnabled
            }
            applyEqualizerLevels()
        }.onFailure { error ->
            Log.e(TAG, "equalizer unavailable: ${error.message}", error)
            equalizer = null
        }
    }

    private fun applyEqualizerLevels() {
        val eq = equalizer ?: return
        val state = _state.value
        runCatching {
            eq.enabled = state.equalizerEnabled
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            val count = eq.numberOfBands.toInt()
            state.equalizerBands.forEachIndexed { index, db ->
                if (index < count) {
                    eq.setBandLevel(index.toShort(), (db * 100).coerceIn(minLevel, maxLevel).toShort())
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "equalizer apply failed: ${error.message}", error)
        }
    }

    private fun rebuildVisualizer() {
        visualizer?.release()
        visualizer = null
        val sessionId = audioSessionId
        if (!_state.value.colorofonEnabled || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        runCatching {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            handleColorofonFft(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true,
                )
                enabled = true
            }
        }.onFailure { error ->
            Log.e(TAG, "visualizer unavailable: ${error.message}", error)
            _state.update { it.copy(colorofonEnabled = false) }
            setTorch(false)
        }
    }

    private fun handleColorofonFft(fft: ByteArray?) {
        val data = fft ?: return
        if (!exoPlayer.isPlaying || data.size < 16) {
            setTorch(false)
            return
        }

        fun bandEnergy(startBin: Int, endBin: Int): Double {
            val lastBin = ((data.size / 2) - 1).coerceAtLeast(1)
            val start = startBin.coerceIn(1, lastBin)
            val end = endBin.coerceIn(start, lastBin)
            var sum = 0.0
            var count = 0
            for (bin in start..end) {
                val real = data[bin * 2].toInt()
                val imag = data[bin * 2 + 1].toInt()
                sum += kotlin.math.sqrt((real * real + imag * imag).toDouble())
                count++
            }
            return if (count == 0) 0.0 else sum / count
        }

        val state = _state.value
        val bass = bandEnergy(1, 7) * (0.25 + state.colorofonBass / 100.0)
        val mid = bandEnergy(8, 28) * (0.25 + state.colorofonMid / 100.0)
        val treble = bandEnergy(29, 90) * (0.25 + state.colorofonTreble / 100.0)
        val sensitivity = state.colorofonIntensity / 100.0
        val bassThreshold = 82.0 - sensitivity * 42.0
        val midThreshold = 68.0 - sensitivity * 34.0
        val trebleThreshold = 62.0 - sensitivity * 32.0
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                colorofonBassLevel = ((bass / bassThreshold) * 100.0).toInt().coerceIn(0, 100),
                colorofonMidLevel = ((mid / midThreshold) * 100.0).toInt().coerceIn(0, 100),
                colorofonTrebleLevel = ((treble / trebleThreshold) * 100.0).toInt().coerceIn(0, 100),
            )
        }

        val pulseMs = when {
            bass > bassThreshold && bass > mid * 1.08 && bass > treble * 1.04 -> 150L
            treble > trebleThreshold && treble > bass * 0.75 -> 40L
            mid > midThreshold && now - lastTorchChangeAt > 105L -> 65L
            else -> 0L
        }

        if (pulseMs > 0L && now - lastTorchChangeAt > 35L) {
            pulseTorch(pulseMs)
            lastTorchChangeAt = now
        }
    }

    private fun pulseTorch(durationMs: Long) {
        torchOffRunnable?.let(statsHandler::removeCallbacks)
        if (supportsTorchStrengthControl()) {
            setTorchStrength(torchStrengthMaxLevel)
        } else {
            setTorch(true)
        }
        val off = Runnable {
            if (supportsTorchStrengthControl()) {
                setTorchStrength(1)
            } else {
                setTorch(false)
            }
        }
        torchOffRunnable = off
        statsHandler.postDelayed(off, durationMs)
    }

    private fun supportsTorchStrengthControl(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && torchStrengthMaxLevel > 1

    private fun setTorchStrength(level: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setTorch(level > 0)
            return
        }
        val bounded = level.coerceIn(1, torchStrengthMaxLevel.coerceAtLeast(1))
        if (torchOn && currentTorchStrengthLevel == bounded) return
        runCatching {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = resolveTorchCameraId(manager) ?: return
            manager.turnOnTorchWithStrengthLevel(cameraId, bounded)
            torchOn = true
            currentTorchStrengthLevel = bounded
        }.onFailure { error ->
            Log.e(TAG, "torch strength unavailable: ${error.message}", error)
            setTorch(level > 0)
        }
    }

    private fun setTorch(enabled: Boolean) {
        if (torchOn == enabled) return
        runCatching {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = resolveTorchCameraId(manager) ?: return
            manager.setTorchMode(cameraId, enabled)
            torchOn = enabled
            currentTorchStrengthLevel = if (enabled) torchStrengthMaxLevel.coerceAtLeast(1) else 0
        }.onFailure { error ->
            Log.e(TAG, "torch unavailable: ${error.message}", error)
            torchOn = false
            currentTorchStrengthLevel = 0
        }
    }

    private fun resolveTorchCameraId(manager: CameraManager): String? {
        torchCameraId?.let { return it }
        for (cameraId in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                torchCameraId = cameraId
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    torchStrengthMaxLevel = characteristics
                        .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                        ?.coerceAtLeast(1)
                        ?: 1
                }
                return cameraId
            }
        }
        return null
    }

    private fun toggleRecording() {
        if (_state.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val station = activeStation
        val url = activeUrls.getOrNull(activeIndex)
        if (station == null || url.isNullOrBlank()) {
            Log.d(TAG, "startRecording ignored: no active stream")
            _state.update { it.copy(status = "Wybierz stację przed nagrywaniem") }
            return
        }

        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "${station.name.toSafeFileName()}-$timestamp.mp3"
        val target = File(dir, fileName)
        recordingTargetFile = target
        recordingStartedAt = timestamp
        recordingActive = true
        _state.update { it.copy(isRecording = true, recordingFileName = fileName, recordingBytes = 0L) }
        Log.d(TAG, "startRecording: station=${station.id}, file=${target.absolutePath}")

        recordingThread = Thread {
            var bytesWritten = 0L
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "RadioPolskaAndroid/1.0")
                }
                recordingConnection = connection
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (recordingActive) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            bytesWritten += read
                            _state.update { it.copy(recordingBytes = bytesWritten) }
                        }
                    }
                }
                Log.d(TAG, "recording finished: file=$fileName, bytes=$bytesWritten")
            } catch (error: Exception) {
                if (recordingActive) {
                    Log.e(TAG, "recording error: file=$fileName, message=${error.message}", error)
                    _state.update { it.copy(status = "Blad nagrywania") }
                } else {
                    Log.d(TAG, "recording stopped by user: file=$fileName, bytes=$bytesWritten")
                }
            } finally {
                recordingConnection?.disconnect()
                recordingConnection = null
                recordingActive = false
                val finalFile = runCatching {
                    renameRecordingWithRds(target, station, timestamp)
                }.onFailure { error ->
                    Log.e(TAG, "recording finalization failed: file=$fileName, message=${error.message}", error)
                }.getOrDefault(target)
                if (recordingThread === Thread.currentThread()) recordingThread = null
                recordingTargetFile = null
                recordingStartedAt = null
                runCatching {
                    _state.update {
                        it.copy(
                            isRecording = false,
                            recordingFileName = finalFile.name,
                            recordingBytes = bytesWritten,
                            status = if (it.isPlaying) "Odtwarzanie" else it.status,
                        )
                    }
                }.onFailure { error ->
                    Log.e(TAG, "recording state finalization failed: ${error.message}", error)
                }
            }
        }.apply {
            name = "RadioRecordingThread"
            start()
        }
    }

    private fun stopRecording() {
        if (!recordingActive && recordingThread == null) return
        Log.d(TAG, "stopRecording")
        recordingActive = false
        recordingConnection?.disconnect()
        recordingConnection = null
        recordingThread?.interrupt()
        _state.update { it.copy(isRecording = false) }
    }

    private fun renameRecordingWithRds(file: File, station: RadioStation, timestamp: String): File {
        if (!file.exists()) return file
        val rds = _state.value.rdsText
        val cleanRds = rds
            .removePrefix("RDS:")
            .trim()
            .takeIf { it.isNotBlank() && it != "Oczekiwanie na RDS" }

        val (artist, title) = cleanRds?.parseArtistTitle() ?: ("nieznany wykonawca" to null)
        val baseName = buildString {
            append(artist.toRecordingFileNamePart())
            if (!title.isNullOrBlank()) {
                append("-")
                append(title.toRecordingFileNamePart())
            }
            append("-")
            append(station.name.toRecordingFileNamePart())
            append("-")
            append(timestamp)
        }.ifBlank { "nieznany-wykonawca-${station.name.toRecordingFileNamePart()}-$timestamp" }

        var target = File(file.parentFile, "$baseName.mp3")
        var index = 2
        while (target.exists() && target.absolutePath != file.absolutePath) {
            target = File(file.parentFile, "$baseName-$index.mp3")
            index++
        }
        return if (target.absolutePath == file.absolutePath || file.renameTo(target)) {
            Log.d(TAG, "recording renamed: ${file.name} -> ${target.name}")
            target
        } else {
            Log.e(TAG, "recording rename failed: ${file.absolutePath} -> ${target.absolutePath}")
            file
        }
    }

    private fun playRelative(offset: Int) {
        val currentId = activeStation?.id
        val currentIndex = PolishRadioStations.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + offset + PolishRadioStations.size) % PolishRadioStations.size
        } else {
            0
        }
        val station = PolishRadioStations[nextIndex]
        Log.d(TAG, "playRelative: offset=$offset, from=$currentId, to=${station.id}")
        play(station)
    }

    private fun playAt(index: Int) {
        val station = activeStation ?: return
        val url = activeUrls.getOrNull(index) ?: return
        val previousState = _state.value
        Log.d(TAG, "playAt: station=${station.id}, index=$index, url=$url")
        activeIndex = index
        _state.value = previousState.copy(
            currentStation = station,
            isBuffering = true,
            status = if (index == 0) "Łączenie ze strumieniem" else "Próba źródła zapasowego ${index + 1}/${activeUrls.size}",
            streamIndex = index,
            rdsText = "Oczekiwanie na RDS",
        )
        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist("${station.city} | ${station.bitrate} kbps")
                    .setAlbumTitle(station.region ?: "Radio online")
                    .setArtworkUri(station.iconUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build(),
            )
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun stationMediaItem(station: RadioStation): MediaItem =
        MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(stationPrimaryUrl(station))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist("${station.city} | ${station.bitrate} kbps")
                    .setAlbumTitle(station.region ?: "Radio online")
                    .setArtworkUri(station.iconUrl.takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse))
                    .build(),
            )
            .build()

    private fun stationPrimaryUrl(station: RadioStation): String =
        station.streamUrls.firstOrNull().orEmpty().ifBlank { station.streamUrl }

    private fun updateRdsText(rawText: String) {
        val stationName = activeStation?.name.orEmpty()
        val text = rawText
            .trim()
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
        val lower = text.lowercase(Locale.ROOT)
        val ignored = lower == stationName.lowercase(Locale.ROOT) ||
            lower == "unknown" ||
            lower == "untitled" ||
            lower == "streamtitle" ||
            lower.contains("no title") ||
            lower.contains("brak informacji") ||
            lower.contains("oczekiwanie")
        if (text.isBlank() || ignored) return
        Log.d(TAG, "rds metadata: $text")
        _state.update { it.copy(rdsText = text) }
    }

    private fun tickPlaybackStats() {
        val now = System.currentTimeMillis()
        val snapshot = _state.value
        val timerEndAt = snapshot.sleepTimerEndAtMillis
        val remainingSeconds = timerEndAt?.let { ((it - now) / 1000L).coerceAtLeast(0L) } ?: 0L

        if (timerEndAt != null && now >= timerEndAt) {
            Log.d(TAG, "sleep timer elapsed: stopping playback")
            stopPlayback()
            cancelSleepTimer()
            return
        }

        val networkType = currentNetworkType()
        val incrementBytes = if (exoPlayer.isPlaying) {
            ((activeStation?.bitrate ?: 0) * 1000L / 8L).coerceAtLeast(0L)
        } else {
            0L
        }

        if (incrementBytes > 0L || remainingSeconds != snapshot.remainingSleepSeconds || networkType.label != snapshot.currentNetworkLabel) {
            _state.update {
                it.copy(
                    dataUsedBytes = it.dataUsedBytes + incrementBytes,
                    wifiDataUsedBytes = it.wifiDataUsedBytes + if (networkType == NetworkType.Wifi) incrementBytes else 0L,
                    mobileDataUsedBytes = it.mobileDataUsedBytes + if (networkType == NetworkType.Mobile) incrementBytes else 0L,
                    otherDataUsedBytes = it.otherDataUsedBytes + if (networkType == NetworkType.Other) incrementBytes else 0L,
                    currentNetworkLabel = networkType.label,
                    remainingSleepSeconds = remainingSeconds,
                )
            }
        }
    }

    private fun currentNetworkType(): NetworkType {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkType.Other
        val network = manager.activeNetwork ?: return NetworkType.Other
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkType.Other
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.Mobile
            else -> NetworkType.Other
        }
    }

    private fun Int.toReadableState(): String = when (this) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($this)"
    }

    private enum class NetworkType(val label: String) {
        Wifi("Wi‑Fi"),
        Mobile("Dane komórkowe"),
        Other("Inna sieć"),
    }

    companion object {
        private const val TAG = "RadioPlaybackService"
        private const val ACTION_PLAY = "com.radiopolska.action.PLAY"
        private const val ACTION_TOGGLE = "com.radiopolska.action.TOGGLE"
        private const val ACTION_STOP = "com.radiopolska.action.STOP"
        private const val ACTION_NEXT = "com.radiopolska.action.NEXT"
        private const val ACTION_PREVIOUS = "com.radiopolska.action.PREVIOUS"
        private const val ACTION_SET_SLEEP_TIMER = "com.radiopolska.action.SET_SLEEP_TIMER"
        private const val ACTION_CANCEL_SLEEP_TIMER = "com.radiopolska.action.CANCEL_SLEEP_TIMER"
        private const val ACTION_RESET_TRANSFER = "com.radiopolska.action.RESET_TRANSFER"
        private const val ACTION_TOGGLE_RECORDING = "com.radiopolska.action.TOGGLE_RECORDING"
        private const val ACTION_SET_VOLUME = "com.radiopolska.action.SET_VOLUME"
        private const val ACTION_SET_EQUALIZER_ENABLED = "com.radiopolska.action.SET_EQUALIZER_ENABLED"
        private const val ACTION_SET_EQUALIZER_PRESET = "com.radiopolska.action.SET_EQUALIZER_PRESET"
        private const val ACTION_SET_EQUALIZER_BAND = "com.radiopolska.action.SET_EQUALIZER_BAND"
        private const val ACTION_SET_COLOROFON_ENABLED = "com.radiopolska.action.SET_COLOROFON_ENABLED"
        private const val ACTION_SET_COLOROFON_INTENSITY = "com.radiopolska.action.SET_COLOROFON_INTENSITY"
        private const val ACTION_SET_COLOROFON_BAND = "com.radiopolska.action.SET_COLOROFON_BAND"
        private const val ACTION_ALARM_PLAY = "com.radiopolska.action.ALARM_PLAY"
        private const val ACTION_ALARM_SNOOZE = "com.radiopolska.action.ALARM_SNOOZE"
        private const val ACTION_ALARM_DISMISS = "com.radiopolska.action.ALARM_DISMISS"
        private const val EXTRA_STATION_ID = "station_id"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_SLEEP_MINUTES = "sleep_minutes"
        private const val EXTRA_RAMP_VOLUME = "ramp_volume"
        private const val EXTRA_AUTO_OFF_MINUTES = "auto_off_minutes"
        private const val EXTRA_ALARM_VOLUME_PERCENT = "alarm_volume_percent"
        private const val EXTRA_KEEP_PLAYING = "keep_playing"
        private const val EXTRA_ENABLED = "enabled"
        private const val EXTRA_PRESET = "preset"
        private const val EXTRA_BAND_INDEX = "band_index"
        private const val EXTRA_BAND = "band"
        private const val EXTRA_BAND_LEVEL = "band_level"
        private const val EXTRA_INTENSITY = "intensity"
        private const val STATS_TICK_MS = 1000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val MEDIA_NOTIFICATION_ID = 1001
        private const val MEDIA_NOTIFICATION_CHANNEL_ID = "default_channel_id"
        val EqualizerPresets: Map<String, List<Int>> = mapOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Bass" to listOf(8, 5, 1, 0, -2),
            "Rock" to listOf(5, 2, -1, 3, 6),
            "Pop" to listOf(-1, 3, 5, 3, -1),
            "Vocal" to listOf(-3, -1, 6, 4, 1),
            "Dance" to listOf(6, 4, 0, 4, 6),
            "Manual" to listOf(0, 0, 0, 0, 0),
        )
        private val EQUALIZER_PRESETS = EqualizerPresets

        private val _state = MutableStateFlow(RadioPlayerState())
        val state: StateFlow<RadioPlayerState> = _state

        fun play(context: Context, station: RadioStation) {
            Log.d(TAG, "command play: station=${station.id}")
            context.startPlaybackService(
                Intent(context, RadioPlaybackService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_STATION_ID, station.id),
            )
        }

        fun playAlarm(context: Context, stationId: String, rampVolume: Boolean, autoOffMinutes: Int, alarmVolumePercent: Int) {
            Log.d(TAG, "command playAlarm: station=$stationId")
            val intent = Intent(context, RadioPlaybackService::class.java)
                .setAction(ACTION_ALARM_PLAY)
                .putExtra(EXTRA_STATION_ID, stationId)
                .putExtra(EXTRA_RAMP_VOLUME, rampVolume)
                .putExtra(EXTRA_AUTO_OFF_MINUTES, autoOffMinutes)
                .putExtra(EXTRA_ALARM_VOLUME_PERCENT, alarmVolumePercent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun snoozeAlarm(context: Context) {
            Log.d(TAG, "command snoozeAlarm")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_ALARM_SNOOZE))
        }

        fun dismissAlarm(context: Context, keepPlaying: Boolean) {
            Log.d(TAG, "command dismissAlarm: keepPlaying=$keepPlaying")
            context.startService(
                Intent(context, RadioPlaybackService::class.java)
                    .setAction(ACTION_ALARM_DISMISS)
                    .putExtra(EXTRA_KEEP_PLAYING, keepPlaying),
            )
        }

        fun toggle(context: Context) {
            Log.d(TAG, "command toggle")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_TOGGLE))
        }

        fun stop(context: Context) {
            Log.d(TAG, "command stop")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_STOP))
        }

        fun next(context: Context) {
            Log.d(TAG, "command next")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_NEXT))
        }

        fun previous(context: Context) {
            Log.d(TAG, "command previous")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_PREVIOUS))
        }

        fun setSleepTimer(context: Context, minutes: Int) {
            Log.d(TAG, "command setSleepTimer: minutes=$minutes")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_SET_SLEEP_TIMER).putExtra(EXTRA_SLEEP_MINUTES, minutes))
        }

        fun cancelSleepTimer(context: Context) {
            Log.d(TAG, "command cancelSleepTimer")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_CANCEL_SLEEP_TIMER))
        }

        fun resetTransfer(context: Context) {
            Log.d(TAG, "command resetTransfer")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_RESET_TRANSFER))
        }

        fun toggleRecording(context: Context) {
            Log.d(TAG, "command toggleRecording")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_TOGGLE_RECORDING))
        }

        fun setVolume(context: Context, volume: Float) {
            val safeVolume = volume.coerceIn(0f, 1f)
            Log.d(TAG, "command setVolume: volume=$safeVolume")
            context.startService(
                Intent(context, RadioPlaybackService::class.java)
                    .setAction(ACTION_SET_VOLUME)
                    .putExtra(EXTRA_VOLUME, safeVolume),
            )
        }

        fun setEqualizerEnabled(context: Context, enabled: Boolean) {
            Log.d(TAG, "command setEqualizerEnabled: enabled=$enabled")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_SET_EQUALIZER_ENABLED).putExtra(EXTRA_ENABLED, enabled))
        }

        fun setEqualizerPreset(context: Context, preset: String) {
            Log.d(TAG, "command setEqualizerPreset: preset=$preset")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_SET_EQUALIZER_PRESET).putExtra(EXTRA_PRESET, preset))
        }

        fun setEqualizerBand(context: Context, index: Int, level: Int) {
            Log.d(TAG, "command setEqualizerBand: index=$index, level=$level")
            context.startService(
                Intent(context, RadioPlaybackService::class.java)
                    .setAction(ACTION_SET_EQUALIZER_BAND)
                    .putExtra(EXTRA_BAND_INDEX, index)
                    .putExtra(EXTRA_BAND_LEVEL, level),
            )
        }

        fun setColorofonEnabled(context: Context, enabled: Boolean) {
            Log.d(TAG, "command setColorofonEnabled: enabled=$enabled")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_SET_COLOROFON_ENABLED).putExtra(EXTRA_ENABLED, enabled))
        }

        fun setColorofonIntensity(context: Context, intensity: Int) {
            Log.d(TAG, "command setColorofonIntensity: intensity=$intensity")
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(ACTION_SET_COLOROFON_INTENSITY).putExtra(EXTRA_INTENSITY, intensity))
        }

        fun setColorofonBand(context: Context, band: String, level: Int) {
            Log.d(TAG, "command setColorofonBand: band=$band, level=$level")
            context.startService(
                Intent(context, RadioPlaybackService::class.java)
                    .setAction(ACTION_SET_COLOROFON_BAND)
                    .putExtra(EXTRA_BAND, band)
                    .putExtra(EXTRA_BAND_LEVEL, level),
            )
        }

    }
}

private fun Context.startPlaybackService(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

private fun String.toSafeFileName(): String =
    lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9ąćęłńóśźż]+"), "-")
        .trim('-')
        .ifBlank { "radio" }

private fun String.toRecordingFileNamePart(): String =
    trim()
        .lowercase(Locale.getDefault())
        .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "nieznany" }

private fun String.parseArtistTitle(): Pair<String, String?> {
    val normalized = trim()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
    val separators = listOf(" - ", " – ", " — ", " | ", " / ")
    for (separator in separators) {
        val parts = normalized.split(separator, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return parts[0] to parts[1]
        }
    }
    return "nieznany wykonawca" to normalized.takeIf { it.isNotBlank() }
}
