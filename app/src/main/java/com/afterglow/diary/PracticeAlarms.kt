package com.afterglow.diary

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal const val PRACTICE_ROUTE = "practice_route"
internal const val PRACTICE_NIGHT = "practice_night"

internal fun practiceIntent(context: Context, route: String, night: String? = null): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(PRACTICE_ROUTE, route).putExtra(PRACTICE_NIGHT, night)

object PracticeAlarms {
    const val REALITY = "com.afterglow.diary.REALITY"
    const val BEDTIME = "com.afterglow.diary.BEDTIME"
    const val WAKE = "com.afterglow.diary.WAKE"
    const val CUE = "com.afterglow.diary.CUE"
    const val SNOOZE = "com.afterglow.diary.SNOOZE_CHECK"
    const val PAUSE = "com.afterglow.diary.PAUSE_CHECKS"
    const val STOP = "com.afterglow.diary.STOP_NIGHT"
    const val NIGHT_CHANNEL = "lucid-night-tools"
    private const val BED_CHANNEL = "lucid-bedtime"
    private const val REALITY_ID = 3100
    private const val BED_ID = 3101
    const val SESSION_ID = 3102
    const val PLAYING_ID = 3103
    private const val WAKE_RESULT_ID = 3104
    private val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun canNotify(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun canUseExactAlarms(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun checkChannel(s: LucidSettings) = "lucid-check-${if (s.notificationSound) 1 else 0}-${if (s.vibration) 1 else 0}"
    fun ensureChannels(context: Context, s: LucidSettings) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(NotificationChannelGroup("lucid-practice", "Lucid practice"))
        val check = NotificationChannel(checkChannel(s), "Reality checks · ${if (s.notificationSound) "sound" else "quiet"}${if (s.vibration) " & vibration" else ""}", NotificationManager.IMPORTANCE_DEFAULT)
        check.group = "lucid-practice"
        check.description = "Daytime awareness prompts during your chosen hours."
        check.enableVibration(s.vibration)
        if (s.vibration) check.vibrationPattern = longArrayOf(0, 120)
        check.setSound(if (s.notificationSound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        manager.createNotificationChannel(check)
        manager.createNotificationChannel(NotificationChannel(BED_CHANNEL, "Evening rehearsal", NotificationManager.IMPORTANCE_DEFAULT).apply {
            group = "lucid-practice"; setSound(null, null); enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(NIGHT_CHANNEL, "Optional nighttime tools", NotificationManager.IMPORTANCE_HIGH).apply {
            group = "lucid-practice"; description = "Status, wake reminders and stop controls for a night you explicitly start."
            setSound(null, null); enableVibration(false)
        })
    }

    private fun broadcast(context: Context, code: Int, action: String, due: Long = 0, session: String? = null) =
        PendingIntent.getBroadcast(context, code, Intent(context, PracticeReceiver::class.java).setAction(action)
            .putExtra("due", due).putExtra("session", session), FLAGS)
    private fun open(context: Context, route: String, night: String? = null, stopAudio: Boolean = false) =
        PendingIntent.getActivity(context, "$route|$night|$stopAudio".hashCode(), practiceIntent(context, route, night).putExtra("stop_lucid_audio", stopAudio), FLAGS)
    private fun builder(context: Context, channel: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_moon).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true)

    @Synchronized fun reschedule(context: Context) {
        rescheduleDay(context)
        val store = LucidPracticeStore(context)
        val session = store.load().session
        if (session != null) {
            if (session.wakeAt == 0L && session.cueAt == 0L && !LucidNightService.isPlaying(session.id) &&
                System.currentTimeMillis() - session.playbackRequestedAt > 10_000) {
                stopNight(context)
            } else if (!canUseExactAlarms(context) || System.currentTimeMillis() - session.startedAt > 14 * 60 * 60 * 1000L) {
                stopNight(context, "The previous night session ended. Start a new one when you're ready for sleep.")
            } else {
                // Never replay an expired alarm when the app is reopened.
                if ((session.wakeAt > 0 && session.wakeAt < System.currentTimeMillis() - 10 * 60_000) ||
                    (session.cueAt > 0 && session.cueAt < System.currentTimeMillis() - 10 * 60_000)) {
                    stopNight(context, "A delayed night reminder was skipped to protect your sleep.")
                } else { scheduleNight(context); showNightStatus(context) }
            }
        }
    }

    @Synchronized fun rescheduleDay(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(broadcast(context, 3001, REALITY)); alarm.cancel(broadcast(context, 3002, BEDTIME))
        val now = ZonedDateTime.now()
        val data = LucidPracticeStore(context).update { d ->
            val snooze = d.snoozeUntil.takeIf { it > now.toInstant().toEpochMilli() &&
                insideRealityWindow(Instant.ofEpochMilli(it).atZone(now.zone), d.settings) }
            d.copy(pendingRealityAt = snooze ?: (nextRealityCheck(now, d.settings)?.toInstant()?.toEpochMilli() ?: 0),
                pendingBedtimeAt = nextBedtimePractice(now, d.settings)?.toInstant()?.toEpochMilli() ?: 0,
                snoozeUntil = snooze ?: 0)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!data.settings.realityEnabled || data.settings.pausedUntil > System.currentTimeMillis()) manager.cancel(REALITY_ID)
        if (!data.settings.bedtimeEnabled) manager.cancel(BED_ID)
        if (data.pendingRealityAt > 0) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, data.pendingRealityAt, broadcast(context, 3001, REALITY, data.pendingRealityAt))
        if (data.pendingBedtimeAt > 0) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, data.pendingBedtimeAt, broadcast(context, 3002, BEDTIME, data.pendingBedtimeAt))
    }

    @Synchronized fun scheduleNight(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(broadcast(context, 3003, WAKE)); alarm.cancel(broadcast(context, 3004, CUE))
        val session = LucidPracticeStore(context).load().session ?: return
        check(canUseExactAlarms(context)) { "Allow Alarms & reminders before starting a night session." }
        if (session.wakeAt > 0) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.wakeAt, broadcast(context, 3003, WAKE, session.wakeAt, session.id))
        if (session.cueAt > 0 && session.remainingCues > 0) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.cueAt, broadcast(context, 3004, CUE, session.cueAt, session.id))
    }

    fun showNightStatus(context: Context) {
        val data = LucidPracticeStore(context).load()
        val manager = context.getSystemService(NotificationManager::class.java)
        val session = data.session
        if (session == null || (session.wakeAt == 0L && session.cueAt == 0L)) { manager.cancel(SESSION_ID); return }
        if (!canNotify(context)) return
        ensureChannels(context, data.settings)
        val text = listOfNotNull(if (session.wakeAt > 0) "Wake reminder ${time(session.wakeAt)}" else null,
            if (session.cueAt > 0) "${session.remainingCues} cue${if (session.remainingCues == 1) "" else "s"} from ${time(session.cueAt)}" else null).joinToString(" · ")
        manager.notify(SESSION_ID, builder(context, NIGHT_CHANNEL).setContentTitle("Tonight in Dream Diary").setContentText(text)
            .setContentIntent(open(context, "night", session.night.toString())).setOngoing(true).setAutoCancel(false)
            .addAction(Notification.Action.Builder(0, "Stop tonight", broadcast(context, 3005, STOP)).build()).build())
    }
    fun time(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    @Synchronized fun stopNight(context: Context, notice: String = "") {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(broadcast(context, 3003, WAKE)); alarm.cancel(broadcast(context, 3004, CUE))
        LucidPracticeStore(context).update { it.copy(session = null, notice = notice) }
        context.stopService(Intent(context, LucidNightService::class.java))
        context.getSystemService(NotificationManager::class.java).apply { cancel(SESSION_ID); cancel(PLAYING_ID); cancel(WAKE_RESULT_ID) }
    }

    fun receive(context: Context, intent: Intent) {
        val store = LucidPracticeStore(context)
        val now = System.currentTimeMillis()
        when (intent.action) {
            REALITY, BEDTIME -> {
                var deliver = false
                val data = store.update { d ->
                    val due = intent.getLongExtra("due", 0)
                    val expected = if (intent.action == REALITY) d.pendingRealityAt else d.pendingBedtimeAt
                    if (due == 0L || due != expected || now < due - 2000) d else {
                        deliver = now - due < 30 * 60_000 && if (intent.action == REALITY) insideRealityWindow(ZonedDateTime.now(), d.settings)
                            else d.settings.bedtimeEnabled && ZonedDateTime.now().dayOfWeek.value in d.settings.days
                        if (intent.action == REALITY) d.copy(pendingRealityAt = 0, snoozeUntil = 0) else d.copy(pendingBedtimeAt = 0)
                    }
                }
                rescheduleDay(context)
                if (!deliver || !canNotify(context)) return
                ensureChannels(context, data.settings)
                val manager = context.getSystemService(NotificationManager::class.java)
                if (intent.action == REALITY) {
                    val signs = data.settings.signs
                    val sign = signs.getOrNull((now / 60_000 % signs.size.coerceAtLeast(1)).toInt())
                    val text = if (sign == null) "Pause. How did you get here? Notice your surroundings and read a short line twice."
                        else "Your dream sign: $sign. Pause, notice your surroundings, and recall how you got here."
                    manager.notify(REALITY_ID, builder(context, checkChannel(data.settings)).setContentTitle("A moment of awareness")
                        .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open(context, "check"))
                        .addAction(Notification.Action.Builder(0, "Start check", open(context, "check")).build())
                        .addAction(Notification.Action.Builder(0, "In 10 minutes", broadcast(context, 3006, SNOOZE)).build())
                        .addAction(Notification.Action.Builder(0, "Pause today", broadcast(context, 3007, PAUSE)).build()).build())
                } else manager.notify(BED_ID, builder(context, BED_CHANNEL).setContentTitle("A little rehearsal for tonight")
                    .setContentText("Revisit a dream and practise recognizing that you're dreaming.")
                    .setContentIntent(open(context, "mild", upcomingPracticeNight().toString())).build())
            }
            SNOOZE -> {
                val later = ZonedDateTime.now().plusMinutes(10)
                store.update { it.copy(snoozeUntil = if (insideRealityWindow(later, it.settings)) later.toInstant().toEpochMilli() else 0) }
                context.getSystemService(NotificationManager::class.java).cancel(REALITY_ID)
                rescheduleDay(context)
            }
            PAUSE -> {
                val until = java.time.LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                store.update { it.copy(settings = it.settings.copy(pausedUntil = until), snoozeUntil = 0) }
                context.getSystemService(NotificationManager::class.java).cancel(REALITY_ID)
                rescheduleDay(context)
            }
            STOP -> stopNight(context)
            WAKE, CUE -> {
                var play: LucidNightSession? = null
                val due = intent.getLongExtra("due", 0)
                store.update { d ->
                    val n = d.session
                    val expected = if (intent.action == WAKE) n?.wakeAt else n?.cueAt
                    when {
                        n == null || n.id != intent.getStringExtra("session") || due == 0L || due != expected || now < due - 2000 -> d
                        now - due > 10 * 60_000 -> d.copy(session = null, notice = "A delayed night reminder was skipped to protect your sleep.")
                        else -> {
                            play = n
                            d.copy(session = if (intent.action == WAKE) n.copy(wakeAt = 0, playbackRequestedAt = now) else n.copy(
                                remainingCues = (n.remainingCues - 1).coerceAtLeast(0),
                                cueAt = if (n.remainingCues > 1) now + n.cueGapMinutes * 60_000L else 0, playbackRequestedAt = now))
                        }
                    }
                }
                scheduleNight(context); showNightStatus(context)
                play?.let { n ->
                    try {
                        context.startForegroundService(Intent(context, LucidNightService::class.java).setAction(intent.action)
                            .putExtra("session", n.id).putExtra("due", due).putExtra(PRACTICE_NIGHT, n.night.toString()))
                    } catch (_: Exception) { stopNight(context, "Android couldn't play the night reminder. Start a new session from the app.") }
                }
            }
            else -> {
                // Reboots/clock changes never restart nighttime audio.
                if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
                    if (store.load().session != null) stopNight(context, "Night tools stopped after a restart or clock change. Start tonight again when you're ready.")
                }
                rescheduleDay(context)
            }
        }
    }

    fun playbackNotification(context: Context, wake: Boolean, night: String): Notification {
        val notification = builder(context, NIGHT_CHANNEL).setContentTitle(if (wake) "Wake gently, then return to sleep" else "Your trained sound cue")
            .setContentText(if (wake) "Open your MILD guide, or stop tonight's reminders." else "A scheduled cue, without REM detection.")
            .setContentIntent(open(context, if (wake) "mild" else "night", night, stopAudio = true))
            .setCategory(if (wake) Notification.CATEGORY_ALARM else Notification.CATEGORY_SERVICE).setOngoing(true).setAutoCancel(false)
            .addAction(Notification.Action.Builder(0, if (wake) "Open MILD" else "Open practice", open(context, if (wake) "mild" else "night", night, true)).build())
            .addAction(Notification.Action.Builder(0, "Stop tonight", broadcast(context, 3005, STOP)).build())
        if (Build.VERSION.SDK_INT >= 31) notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return notification.build()
    }

    fun wakeFinished(context: Context, night: String) {
        if (canNotify(context)) context.getSystemService(NotificationManager::class.java).notify(WAKE_RESULT_ID,
            builder(context, NIGHT_CHANNEL).setContentTitle("Your MILD guide is here")
                .setContentText("If you're awake, rehearse recognizing a dream, then settle back to sleep.")
                .setContentIntent(open(context, "mild", night, true)).build())
    }

    fun opened(context: Context, route: String, stopAudio: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (route == "check") manager.cancel(REALITY_ID)
        if (route == "mild") manager.cancel(BED_ID)
        if (route == "morning") manager.cancel(41)
        if (stopAudio) {
            manager.cancel(WAKE_RESULT_ID)
            context.stopService(Intent(context, LucidNightService::class.java))
        }
    }
}

class PracticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A damaged or newer practice file must never be overwritten by a receiver.
        runCatching { PracticeAlarms.receive(context, intent) }
    }
}
