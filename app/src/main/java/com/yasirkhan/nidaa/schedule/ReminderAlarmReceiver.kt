package com.yasirkhan.nidaa.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.ZoneId

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: "Nidaa reminder"
        val description = intent.getStringExtra("description") ?: "It's time."
        val days = intent.getIntArrayExtra("repeatDays")?.toSet() ?: emptySet()
        val hour = intent.getIntExtra("triggerHour", 9)
        val minute = intent.getIntExtra("triggerMinute", 0)
        val soundString = intent.getStringExtra("soundUri")
        val isFajr = intent.getBooleanExtra("isFajr", false) || title.contains("Fajr", ignoreCase = true)
        val channelId = "nidaa_schedule_" + (soundString ?: "default").hashCode().toUInt().toString(16) + (if (isFajr) "_fajr" else "")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Wake screen with a temporary WakeLock
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "nidaa:alarm_wakelock",
            )
            wakeLock.acquire(10_000)
        } catch (_: Exception) {}

        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, if (isFajr) "Nidaa Fajr Alarms" else "Nidaa Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = "Scheduled prayer alarms and reminder alerts"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                if (soundString != "silent") {
                    val sound = soundString?.let(Uri::parse) ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                } else setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        // Full-screen intent for AlarmActivity to wake and show screen
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtras(intent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, id, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            context.startActivity(alarmIntent)
        } catch (_: Exception) {}

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .build()
        manager.notify(id, notification)

        if (days.isNotEmpty()) {
            val today = LocalDate.now()
            val next = (1..7).asSequence().map { today.plusDays(it.toLong()) }
                .first { days.contains(it.dayOfWeek.value) }
                .atTime(hour, minute)
            val nextIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("title", title)
                putExtra("description", description)
                putExtra("repeatDays", days.toIntArray())
                putExtra("soundUri", soundString)
                putExtra("triggerHour", hour)
                putExtra("triggerMinute", minute)
                putExtra("isFajr", isFajr)
            }
            val pending = PendingIntent.getBroadcast(context, id, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }
}
