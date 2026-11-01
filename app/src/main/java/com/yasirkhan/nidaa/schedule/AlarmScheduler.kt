package com.yasirkhan.nidaa.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    fun schedule(context: Context, id: Int, title: String, description: String, triggerAt: Long, repeatDays: Set<Int>) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("description", description)
            putExtra("repeatDays", repeatDays.toIntArray())
        }
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    fun nextWeeklyTime(hour: Int, minute: Int, days: Set<Int>): Long {
        val now = LocalDateTime.now()
        val next = (0..7).map { now.toLocalDate().plusDays(it.toLong()) }
            .filter { days.contains(it.dayOfWeek.value) }
            .map { it.atTime(hour, minute) }
            .first { it.isAfter(now) }
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
