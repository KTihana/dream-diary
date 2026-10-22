package com.afterglow.diary

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import java.time.ZonedDateTime

object RecallReminder {
    const val CHANNEL = "morning-recall"
    fun schedule(context: Context, settings: NotebookPreferences) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 41, Intent(context, RecallReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        if (!settings.reminder) return
        val now = ZonedDateTime.now()
        var next = now.withHour(settings.reminderHour.coerceIn(0, 23)).withMinute(settings.reminderMinute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }
}
class RecallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = NotebookPreferencesStore(context).load()
        if (!prefs.reminder) return
        RecallReminder.schedule(context, prefs)
        if (intent.action != null) return // Boot/time changes only reschedule.
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RecallReminder.CHANNEL, "Morning dream recall", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 42, practiceIntent(context, "morning"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val write = PendingIntent.getActivity(context, 44, Intent(context, MainActivity::class.java).putExtra("capture", "write"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(41, Notification.Builder(context, RecallReminder.CHANNEL).setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("A moment for your dreams")
            .setContentText(RecallPrompts[java.time.LocalDate.now().dayOfYear % RecallPrompts.size])
            .setContentIntent(open).addAction(Notification.Action.Builder(0, "Write a dream", write).build())
            .setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
    }
}
class CaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.capture_widget)
            listOf(R.id.widget_write to "write", R.id.widget_voice to "voice").forEach { (view, action) ->
                views.setOnClickPendingIntent(view, PendingIntent.getActivity(context, id * 10 + view,
                    Intent(context, MainActivity::class.java).putExtra("capture", action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
class DreamCaptureTile : TileService() {
    // API 26–33 require the Intent overload; Android 14+ always uses PendingIntent below.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).putExtra("capture", "voice").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 43, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
