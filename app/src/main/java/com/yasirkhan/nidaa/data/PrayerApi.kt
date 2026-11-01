package com.yasirkhan.nidaa.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val timezone: String
)

object PrayerApi {
    suspend fun fetch(latitude: Double, longitude: Double, method: Int, school: Int): PrayerTimes =
        withContext(Dispatchers.IO) {
            val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
            val address = "https://api.aladhan.com/v1/timings/$date?latitude=$latitude&longitude=$longitude&method=$method&school=$school"
            val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) error("Prayer API returned HTTP $code")
                val root = JSONObject(body)
                if (root.optInt("code", 0) != 200) error(root.optString("status", "Prayer API error"))
                val data = root.getJSONObject("data")
                val timings = data.getJSONObject("timings")
                fun time(key: String) = timings.getString(key).substringBefore(" ").take(5)
                PrayerTimes(
                    fajr = time("Fajr"),
                    sunrise = time("Sunrise"),
                    dhuhr = time("Dhuhr"),
                    asr = time("Asr"),
                    maghrib = time("Maghrib"),
                    isha = time("Isha"),
                    timezone = data.getJSONObject("meta").optString("timezone", "Local time")
                )
            } finally {
                connection.disconnect()
            }
        }
}
