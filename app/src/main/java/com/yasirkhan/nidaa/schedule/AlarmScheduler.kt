package com.yasirkhan.nidaa.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yasirkhan.nidaa.data.PrayerTimes
import com.yasirkhan.nidaa.data.SalahTracker
import com.yasirkhan.nidaa.data.format12Hour
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {
    fun schedule(
        context: Context,
        id: Int,
        title: String,
        description: String,
        triggerAt: Long,
        repeatDays: Set<Int>,
        soundUri: String?,
        isFajr: Boolean = false,
        prayerName: String? = null,
        jamaatInfo: String? = null
    ) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val localTime = java.time.Instant.ofEpochMilli(triggerAt).atZone(ZoneId.systemDefault())
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("description", description)
            putExtra("repeatDays", repeatDays.toIntArray())
            putExtra("soundUri", soundUri)
            putExtra("triggerHour", localTime.hour)
            putExtra("triggerMinute", localTime.minute)
            putExtra("isFajr", isFajr)
            putExtra("prayerName", prayerName)
            putExtra("jamaatInfo", jamaatInfo)
        }
        val pending = PendingIntent.getBroadcast(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    /**
     * Schedules alarms for the 5 daily prayers and their mosque Jamaat times.
     */
    fun scheduleAllPrayers(context: Context, prayer: PrayerTimes, jamaatLines: List<String>) {
        val now = LocalDateTime.now()
        val today = LocalDate.now()

        val prayersList = listOf(
            Triple("Fajr", prayer.fajr, true),
            Triple("Dhuhr", prayer.dhuhr, false),
            Triple("Asr", prayer.asr, false),
            Triple("Maghrib", prayer.maghrib, false),
            Triple("Isha", prayer.isha, false)
        )

        prayersList.forEachIndexed { index, (name, time24, isFajr) ->
            try {
                val adhanLt = LocalTime.parse(time24.take(5))
                val adhanDt = today.atTime(adhanLt)
                val jTime24 = jamaatLines.getOrNull(if (index >= 1) index + 1 else index)?.takeIf { it.isNotBlank() }

                val durationText = SalahTracker.getDurationBetween(time24, jTime24)
                val jamaatNotice = if (jTime24 != null) {
                    "Jamaat: ${format12Hour(jTime24)}" + (if (durationText != null) " ($durationText)" else "")
                } else null

                // Schedule Adhan alarm (ID: 9000 + index)
                if (adhanDt.isAfter(now)) {
                    val millis = adhanDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    schedule(
                        context = context,
                        id = 9000 + index,
                        title = "Time for $name Adhan",
                        description = "Adhan is at ${format12Hour(time24)}. " + (jamaatNotice ?: "Prepare for prayer."),
                        triggerAt = millis,
                        repeatDays = emptySet(),
                        soundUri = null,
                        isFajr = isFajr,
                        prayerName = name,
                        jamaatInfo = jamaatNotice
                    )
                }

                // Schedule Jamaat alarm (ID: 9100 + index)
                if (jTime24 != null) {
                    val jLt = LocalTime.parse(jTime24.take(5))
                    val jDt = today.atTime(jLt)
                    if (jDt.isAfter(now)) {
                        val millis = jDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        schedule(
                            context = context,
                            id = 9100 + index,
                            title = "$name Jamaat Started",
                            description = "Congregational prayer for $name is beginning now (${format12Hour(jTime24)}).",
                            triggerAt = millis,
                            repeatDays = emptySet(),
                            soundUri = null,
                            isFajr = isFajr,
                            prayerName = name,
                            jamaatInfo = "Jamaat in progress"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
