package com.afterglow.diary

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.time.LocalDate

/** Bundled audio is played only for a preview or a requested, bounded night cue. */
internal class PracticeCuePlayer(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var generation = 0

    fun play(cue: LucidCue, volume: Float, alarm: Boolean = false, loop: Boolean = false, onEnd: () -> Unit = {}): Boolean {
        close()
        val token = generation
        val attributes = AudioAttributes.Builder().setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (token == generation && (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) { close(); onEnd() }
            }.build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return try {
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(attributes)
            media.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            val descriptor = context.resources.openRawResourceFd(if (cue == LucidCue.Bell) R.raw.lucid_bell else R.raw.lucid_taps)
                ?: error("The cue recording is unavailable")
            descriptor.use {
                media.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            media.isLooping = loop
            media.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            media.setOnCompletionListener { if (token == generation) { close(); onEnd() } }
            media.setOnErrorListener { _, _, _ -> if (token == generation) { close(); onEnd() }; true }
            media.prepare()
            media.start()
            true
        } catch (_: Exception) { close(); false }
    }

    fun close() {
        generation++
        player?.let { media ->
            media.setOnCompletionListener(null); media.setOnErrorListener(null)
            runCatching { media.stop() }; media.release()
        }
        player = null
        focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
    }
}

/** Exact alarms start this service for seconds of playback, never for hours of waiting. */
class LucidNightService : Service() {
    companion object {
        @Volatile private var playingSession: String? = null
        fun isPlaying(id: String) = playingSession == id
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: PracticeCuePlayer
    private var sessionId: String? = null
    private var night: String? = null
    private var wake = false
    private val finish = Runnable { finishPlayback() }
    override fun onCreate() { super.onCreate(); player = PracticeCuePlayer(this) }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val store = LucidPracticeStore(this)
        val data = runCatching { store.load() }.getOrNull()
        val session = data?.session
        if (data == null || session == null || session.id != intent.getStringExtra("session") ||
            intent.action !in setOf(PracticeAlarms.WAKE, PracticeAlarms.CUE)) { stopSelf(); return START_NOT_STICKY }
        handler.removeCallbacks(finish)
        player.close()
        sessionId = session.id
        playingSession = session.id
        val playbackNight = intent.getStringExtra(PRACTICE_NIGHT) ?: session.night.toString()
        night = playbackNight
        wake = intent.action == PracticeAlarms.WAKE
        PracticeAlarms.ensureChannels(this, data.settings)
        val notification = PracticeAlarms.playbackNotification(this, wake, playbackNight)
        if (Build.VERSION.SDK_INT >= 29) startForeground(PracticeAlarms.PLAYING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(PracticeAlarms.PLAYING_ID, notification)
        val played = player.play(session.cue, if (wake) .8f else session.volume, alarm = wake, loop = wake) { finishPlayback() }
        if (played) {
            runCatching { store.update { d -> if (d.session?.id != session.id) d else d.record(PracticeEvent(
                id = "${session.id}:${intent.action}:${intent.getLongExtra("due", 0)}", night = LocalDate.parse(playbackNight),
                method = if (wake) PracticeMethod.WBTB else PracticeMethod.SoundCue,
                detail = if (wake) "Wake-up reminder sounded; waking is not confirmed." else "Scheduled trained cue played; sleep stage is unknown.")) } }
            handler.postDelayed(finish, if (wake) 60_000 else 5_000)
        } else {
            runCatching { store.update { it.copy(notice = "Audio couldn't play. Check your device's sound settings before starting another night.") } }
            finishPlayback()
        }
        return START_NOT_STICKY
    }

    private fun finishPlayback() {
        handler.removeCallbacks(finish)
        player.close()
        if (wake) night?.let { PracticeAlarms.wakeFinished(this, it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.close()
        if (playingSession == sessionId) playingSession = null
        runCatching {
            LucidPracticeStore(this).update { d ->
                val n = d.session
                if (n != null && n.id == sessionId && n.wakeAt == 0L && n.cueAt == 0L) d.copy(session = null) else d
            }
            PracticeAlarms.showNightStatus(this)
        }
        super.onDestroy()
    }
}
