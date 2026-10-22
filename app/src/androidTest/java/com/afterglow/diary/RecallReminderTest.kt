package com.afterglow.diary

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecallReminderTest {
    @Test fun reminderPostsOnlyWhenEnabledAndSettingsPersist() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val store = NotebookPreferencesStore(context)
        val previous = store.load()
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            val enabled = previous.copy(reminder = true, reminderHour = 9, reminderMinute = 17)
            store.save(enabled)
            assertEquals(enabled, NotebookPreferencesStore(context).load())
            RecallReminder.schedule(context, enabled)
            RecallReceiver().onReceive(context, Intent())
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (manager.activeNotifications.none { it.id == 41 } && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50)
            val posted = manager.activeNotifications.first { it.id == 41 }.notification
            assertEquals("A moment for your dreams", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
            assertNotNull(posted.contentIntent)
            manager.cancel(41)
            val cancelDeadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (manager.activeNotifications.any { it.id == 41 } && android.os.SystemClock.uptimeMillis() < cancelDeadline) android.os.SystemClock.sleep(50)
            store.save(enabled.copy(reminder = false))
            RecallReceiver().onReceive(context, Intent())
            assertFalse(manager.activeNotifications.any { it.id == 41 })
        } finally {
            manager.cancel(41)
            store.save(previous)
            RecallReminder.schedule(context, previous)
        }
    }
}
