package com.afterglow.diary

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Exercises the actual alarm → private receiver → foreground playback → cleanup path. */
@RunWith(AndroidJUnit4::class)
class NightCueTest {
    @Test fun oneScheduledCuePlaysOnceAndReleasesItsSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = LucidPracticeStore(context)
        val backup = store.load()
        val manager = context.getSystemService(NotificationManager::class.java)
        fun setAlarmMode(mode: String) {
            val output = instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} SCHEDULE_EXACT_ALARM $mode")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            // Test-device setup, like the runtime permission above. Revoking this access
            // inside instrumentation would stop Android's app process and the test runner.
            setAlarmMode("allow")
        }
        try {
            await("Exact alarm permission") { PracticeAlarms.canUseExactAlarms(context) }
            val now = System.currentTimeMillis()
            // Accelerate a single fixture cue; the user-facing bedtime offsets stay at 5–8 hours.
            val session = LucidNightSession(startedAt = now, night = LocalDate.now(), cueAt = now + 2500,
                remainingCues = 1, cue = LucidCue.Bell)
            store.update { LucidPracticeData(session = session) }
            PracticeAlarms.ensureChannels(context, store.load().settings)
            PracticeAlarms.scheduleNight(context)
            await("Scheduled cue should reach foreground playback", 20_000) {
                store.load().events.any { it.id.startsWith(session.id) && it.method == PracticeMethod.SoundCue }
            }
            await("Completed cue should release its session and notification", 10_000) {
                store.load().session == null && !LucidNightService.isPlaying(session.id) &&
                    manager.activeNotifications.none { it.id == PracticeAlarms.PLAYING_ID }
            }
            assertEquals(1, store.load().events.count { it.id.startsWith(session.id) })
            PracticeAlarms.reschedule(context)
            assertNull(store.load().session)
            assertFalse(manager.activeNotifications.any { it.id == PracticeAlarms.SESSION_ID })
        } finally {
            PracticeAlarms.stopNight(context)
            store.update { backup }
            PracticeAlarms.reschedule(context)
        }
    }

    private fun await(message: String, timeout: Long = 5000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }
}
