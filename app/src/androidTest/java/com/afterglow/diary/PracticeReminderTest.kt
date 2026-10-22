package com.afterglow.diary

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeReminderTest {
    @Test fun aPrivateReminderHasWorkingPauseAndStaleAlarmsDoNotRepost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val store = LucidPracticeStore(context)
        val backup = store.load()
        val manager = context.getSystemService(NotificationManager::class.java)
        val due = System.currentTimeMillis() - 1000
        try {
            store.update { LucidPracticeData(settings = LucidSettings(realityEnabled = true, startMinute = 0, endMinute = 1440), pendingRealityAt = due) }
            val alarm = Intent(context, PracticeReceiver::class.java).setAction(PracticeAlarms.REALITY).putExtra("due", due)
            PracticeReceiver().onReceive(context, alarm)
            val deadline = SystemClock.uptimeMillis() + 5000
            while (manager.activeNotifications.none { it.id == 3100 } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            val posted = manager.activeNotifications.first { it.id == 3100 }.notification
            assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
            assertEquals(listOf("Start check", "In 10 minutes", "Pause today"), posted.actions.map { it.title.toString() })
            posted.actions.last().actionIntent.send()
            val pauseDeadline = SystemClock.uptimeMillis() + 5000
            while (store.load().settings.pausedUntil <= System.currentTimeMillis() && SystemClock.uptimeMillis() < pauseDeadline) SystemClock.sleep(50)
            assertTrue(store.load().settings.pausedUntil > System.currentTimeMillis())
            PracticeReceiver().onReceive(context, alarm)
            val cancelDeadline = SystemClock.uptimeMillis() + 5000
            while (manager.activeNotifications.any { it.id == 3100 } && SystemClock.uptimeMillis() < cancelDeadline) SystemClock.sleep(50)
            assertFalse(manager.activeNotifications.any { it.id == 3100 })
        } finally {
            manager.cancel(3100)
            store.update { backup }
            PracticeAlarms.reschedule(context)
        }
    }
}
