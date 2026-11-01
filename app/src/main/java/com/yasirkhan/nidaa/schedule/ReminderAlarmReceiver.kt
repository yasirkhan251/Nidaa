package com.yasirkhan.nidaa.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yasirkhan.nidaa.MainActivity
import com.yasirkhan.nidaa.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: "Nidaa reminder"
        val description = intent.getStringExtra("description") ?: "It's time."
        val days = intent.getIntArrayExtra("repeatDays")?.toSet() ?: emptySet()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("nidaa_schedule", "Reminders and planner", NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = "Scheduled reminders and planner alerts"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
        }
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "nidaa_schedule")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(id, notification)

        if (days.isNotEmpty()) {
            val next = (1..7).map { LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).plusDays(it.toLong()) }
                .first { days.contains(it.dayOfWeek.value) }
            val original = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault())
            val scheduled = next.withHour(original.hour).withMinute(original.minute).withSecond(0).withNano(0)
            val nextIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra("id", id); putExtra("title", title); putExtra("description", description); putExtra("repeatDays", days.toIntArray())
            }
            val pending = PendingIntent.getBroadcast(context, id, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, scheduled.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
        }
    }
}
