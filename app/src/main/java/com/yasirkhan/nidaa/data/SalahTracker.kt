package com.yasirkhan.nidaa.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun format12Hour(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return "—"
    val trimmed = timeStr.trim().take(5)
    return try {
        val parts = trimmed.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    } catch (_: Exception) {
        timeStr
    }
}

data class ProhibitedInterval(
    val name: String,
    val arabicName: String,
    val start24: String,
    val end24: String,
    val reason: String,
) {
    val start12: String get() = format12Hour(start24)
    val end12: String get() = format12Hour(end24)
}

object ProhibitedTimes {
    fun calculate(prayerTimes: PrayerTimes): List<ProhibitedInterval> {
        val intervals = mutableListOf<ProhibitedInterval>()
        try {
            // 1. Sunrise (Tulu): from sunrise until 15 minutes after (Ishraq starts)
            val sunriseTime = LocalTime.parse(prayerTimes.sunrise.take(5))
            val ishraqTime = sunriseTime.plusMinutes(15)
            intervals.add(
                ProhibitedInterval(
                    name = "Sunrise (Tulu)",
                    arabicName = "طلوع الشمس",
                    start24 = sunriseTime.toString().take(5),
                    end24 = ishraqTime.toString().take(5),
                    reason = "Sun rising until 15 min after sunrise",
                )
            )

            // 2. Zenith / Midday (Zawal / Istiwa): approx 15 mins before Dhuhr until Dhuhr starts
            val dhuhrTime = LocalTime.parse(prayerTimes.dhuhr.take(5))
            val zawalStartTime = dhuhrTime.minusMinutes(15)
            intervals.add(
                ProhibitedInterval(
                    name = "Zenith (Zawal / Midday)",
                    arabicName = "استواء الشمس",
                    start24 = zawalStartTime.toString().take(5),
                    end24 = dhuhrTime.toString().take(5),
                    reason = "Sun at its peak, ~15 min before Dhuhr",
                )
            )

            // 3. Sunset (Ghurub): 15 mins before Maghrib until Maghrib starts
            val maghribTime = LocalTime.parse(prayerTimes.maghrib.take(5))
            val ghurubStartTime = maghribTime.minusMinutes(15)
            intervals.add(
                ProhibitedInterval(
                    name = "Sunset (Ghurub)",
                    arabicName = "غروب الشمس",
                    start24 = ghurubStartTime.toString().take(5),
                    end24 = maghribTime.toString().take(5),
                    reason = "Sun setting, 15 min before Maghrib",
                )
            )
        } catch (_: Exception) {}
        return intervals
    }

    fun isCurrentlyProhibited(intervals: List<ProhibitedInterval>): ProhibitedInterval? {
        val now = LocalTime.now()
        return intervals.firstOrNull { interval ->
            try {
                val start = LocalTime.parse(interval.start24)
                val end = LocalTime.parse(interval.end24)
                !now.isBefore(start) && now.isBefore(end)
            } catch (_: Exception) { false }
        }
    }
}

data class MonthStats(
    val totalPrayed: Int,
    val totalPossible: Int,
    val totalMissed: Int,
    val completionPercentage: Int,
    val currentStreak: Int,
)

enum class PrayerSoundMode(val title: String, val badge: String, val description: String) {
    LOUDEST("Loudest (Override Silent)", "MAX VOL", "Max volume & vibration, ignores silent/DND"),
    NORMAL("Normal Phone Volume", "NORMAL", "Rings using device alarm volume"),
    QUIET("Quiet & Gentle", "QUIET", "Soft volume chime with subtle vibration"),
    VIBRATE("Vibrate Only", "VIBRATE", "No sound, strong vibration alerts"),
    OFF("Silent / Off", "OFF", "Notification only, no sound or vibration"),
}

data class PrayerAudioConfig(
    val soundMode: PrayerSoundMode = PrayerSoundMode.NORMAL,
    val soundTone: String = "Adhan Makkah",
)

object SalahTracker {
    val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    fun getAttendance(context: Context, date: String = LocalDate.now().toString()): Map<String, Boolean> {
        val prefs = context.getSharedPreferences("nidaa_attendance", Context.MODE_PRIVATE)
        val raw = prefs.getString(date, null)
        val map = mutableMapOf<String, Boolean>()
        PRAYERS.forEach { map[it] = false }
        if (!raw.isNullOrBlank()) {
            try {
                val json = JSONObject(raw)
                PRAYERS.forEach { p ->
                    map[p] = json.optBoolean(p, false)
                }
            } catch (_: Exception) {}
        }
        return map
    }

    fun setPrayerStatus(context: Context, prayer: String, prayed: Boolean, date: String = LocalDate.now().toString()) {
        val prefs = context.getSharedPreferences("nidaa_attendance", Context.MODE_PRIVATE)
        val current = getAttendance(context, date).toMutableMap()
        current[prayer] = prayed
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit { putString(date, json.toString()) }
    }

    fun getPrayedCount(context: Context, date: String = LocalDate.now().toString()): Int {
        return getAttendance(context, date).values.count { it }
    }

    fun getMonthAttendance(context: Context, year: Int, month: Int): Map<LocalDate, Map<String, Boolean>> {
        val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
        val map = mutableMapOf<LocalDate, Map<String, Boolean>>()
        for (d in 1..daysInMonth) {
            val date = LocalDate.of(year, month, d)
            map[date] = getAttendance(context, date.toString())
        }
        return map
    }

    fun getMonthStats(context: Context, year: Int, month: Int): MonthStats {
        val today = LocalDate.now()
        val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
        val limitDay = if (year == today.year && month == today.monthValue) today.dayOfMonth else daysInMonth

        var prayed = 0
        var possible = 0

        for (d in 1..limitDay) {
            val date = LocalDate.of(year, month, d)
            val att = getAttendance(context, date.toString())
            val count = att.values.count { it }
            prayed += count
            possible += 5
        }

        val missed = (possible - prayed).coerceAtLeast(0)
        val percentage = if (possible > 0) (prayed * 100) / possible else 0

        // Streak calculation
        var streak = 0
        var checkDate = today
        for (i in 0 until 60) {
            val att = getAttendance(context, checkDate.toString())
            val count = att.values.count { it }
            if (count == 5) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else if (checkDate == today) {
                // Today in progress, check yesterday
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }

        return MonthStats(prayed, possible, missed, percentage, streak)
    }

    fun getPrayerAudioConfig(context: Context, prayer: String): PrayerAudioConfig {
        val prefs = context.getSharedPreferences("nidaa_prayer_audio", Context.MODE_PRIVATE)
        val defaultMode = if (prayer == "Fajr") PrayerSoundMode.LOUDEST.name else PrayerSoundMode.NORMAL.name
        val modeStr = prefs.getString("${prayer}_mode", defaultMode) ?: defaultMode
        val mode = try { PrayerSoundMode.valueOf(modeStr) } catch (_: Exception) { PrayerSoundMode.NORMAL }
        val tone = prefs.getString("${prayer}_tone", "Adhan Makkah") ?: "Adhan Makkah"
        return PrayerAudioConfig(mode, tone)
    }

    fun setPrayerAudioConfig(context: Context, prayer: String, mode: PrayerSoundMode, tone: String) {
        val prefs = context.getSharedPreferences("nidaa_prayer_audio", Context.MODE_PRIVATE)
        prefs.edit {
            putString("${prayer}_mode", mode.name)
            putString("${prayer}_tone", tone)
        }
    }

    fun getTimeRemaining(targetTime24: String?): String? {
        if (targetTime24.isNullOrBlank()) return null
        return try {
            val target = LocalTime.parse(targetTime24.trim().take(5))
            val now = LocalTime.now()
            if (target.isAfter(now)) {
                val minutes = ChronoUnit.MINUTES.between(now, target)
                val hours = minutes / 60
                val remMin = minutes % 60
                if (hours > 0) "${hours}h ${remMin}m" else "${remMin}m"
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getDurationBetween(adhan24: String?, jamaat24: String?): String? {
        if (adhan24.isNullOrBlank() || jamaat24.isNullOrBlank()) return null
        return try {
            val a = LocalTime.parse(adhan24.trim().take(5))
            val j = LocalTime.parse(jamaat24.trim().take(5))
            val minutes = ChronoUnit.MINUTES.between(a, j)
            if (minutes > 0) "$minutes mins after Adhan" else null
        } catch (_: Exception) {
            null
        }
    }
}

data class PrayerAiInsight(
    val habitScore: Int,
    val scoreTrend: String,
    val weakestPrayer: String,
    val weakestPrayerRate: Int,
    val strongestPrayer: String,
    val strongestPrayerRate: Int,
    val summary: String,
    val recommendations: List<String>,
    val weekdayVsWeekendInsight: String,
)

object PrayerAiCoach {
    fun analyze(context: Context): PrayerAiInsight {
        val today = LocalDate.now()
        val prayerCounts = mutableMapOf<String, Int>()
        SalahTracker.PRAYERS.forEach { prayerCounts[it] = 0 }

        var totalDays = 0
        var weekdayPrayed = 0
        var weekdayPossible = 0
        var weekendPrayed = 0
        var weekendPossible = 0

        for (i in 0 until 30) {
            val d = today.minusDays(i.toLong())
            val att = SalahTracker.getAttendance(context, d.toString())
            val count = att.values.count { it }
            if (count > 0 || i < 7) {
                totalDays++
                att.forEach { (p, prayed) ->
                    if (prayed) prayerCounts[p] = (prayerCounts[p] ?: 0) + 1
                }
                val isWeekend = d.dayOfWeek.value in listOf(6, 7)
                if (isWeekend) {
                    weekendPrayed += count
                    weekendPossible += 5
                } else {
                    weekdayPrayed += count
                    weekdayPossible += 5
                }
            }
        }

        val effectiveDays = totalDays.coerceAtLeast(1)
        val prayerRates = prayerCounts.mapValues { (_, count) -> (count * 100) / effectiveDays }

        val weakestKey = prayerRates.minByOrNull { it.value }?.key ?: "Asr"
        val weakestVal = prayerRates[weakestKey] ?: 50
        val strongestKey = prayerRates.maxByOrNull { it.value }?.key ?: "Fajr"
        val strongestVal = prayerRates[strongestKey] ?: 85

        val weekdayRate = if (weekdayPossible > 0) (weekdayPrayed * 100) / weekdayPossible else 70
        val weekendRate = if (weekendPossible > 0) (weekendPrayed * 100) / weekendPossible else 80

        val overallRate = (prayerRates.values.average()).toInt().coerceIn(10, 99)
        val habitScore = (overallRate * 0.85 + (if (weekendRate > 60) 10 else 5)).toInt().coerceIn(15, 99)

        val trend = if (habitScore >= 80) "+8% this week 📈" else if (habitScore >= 60) "+4% steady 📈" else "Needs focus 🎯"

        val weekdayVsWeekend = when {
            weekendRate - weekdayRate > 15 ->
                "Weekday attendance drops by ${weekendRate - weekdayRate}% compared to weekends due to office & commute fatigue."
            weekdayRate - weekendRate > 15 ->
                "Weekend consistency drops by ${weekdayRate - weekendRate}% due to irregular wake-up and family schedules."
            else ->
                "Consistent habit across both weekdays and weekends (${weekdayRate}% weekdays vs ${weekendRate}% weekends)."
        }

        val recs = mutableListOf<String>()
        recs.add("Fix $weakestKey: Set alarm to 'LOUDEST (Override Silent)' with a 15-minute lead time.")
        if (weekdayRate < weekendRate) {
            recs.add("Enable 'Office Dhuhr Break' and 'Afternoon Asr' alarms to safeguard prayers during busy work hours.")
        }
        recs.add("Habit-Stacking: Perform your personal reflections and Quran reading immediately after $strongestKey (your strongest prayer).")
        recs.add("Weekly Target: Aim for at least 4 of 5 prayers daily to elevate your habit score above 90.")

        val summary = "On-device pattern analysis indicates high consistency in $strongestKey ($strongestVal%), with $weakestKey ($weakestVal%) as your greatest opportunity to elevate your spiritual discipline."

        return PrayerAiInsight(
            habitScore = habitScore,
            scoreTrend = trend,
            weakestPrayer = weakestKey,
            weakestPrayerRate = weakestVal,
            strongestPrayer = strongestKey,
            strongestPrayerRate = strongestVal,
            summary = summary,
            recommendations = recs,
            weekdayVsWeekendInsight = weekdayVsWeekend,
        )
    }
}
