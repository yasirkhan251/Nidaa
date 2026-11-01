package com.yasirkhan.nidaa.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    fun schedule(context: Context, id: Int, title: String, description: String, triggerAt: Long, repeatDays: Set<Int>, soundUri: String?) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val localTime = java.time.Instant.ofEpochMilli(triggerAt).atZone(java.time.ZoneId.systemDefault())
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("description", description)
            putExtra("repeatDays", repeatDays.toIntArray())
            putExtra("soundUri", soundUri)
            putExtra("triggerHour", localTime.hour)
            putExtra("triggerMinute", localTime.minute)
        }
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }
}
