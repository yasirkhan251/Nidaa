package com.yasirkhan.nidaa.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrayerTimes(val fajr:String,val sunrise:String,val dhuhr:String,val asr:String,val maghrib:String,val isha:String,val timezone:String)
object PrayerApi {
 suspend fun fetch(lat:Double,lon:Double,method:Int,school:Int):PrayerTimes = withContext(Dispatchers.IO) {
  val date=SimpleDateFormat("dd-MM-yyyy",Locale.US).format(Date())
  val url="https://api.aladhan.com/v1/timings/$date?latitude=$lat&longitude=$lon&method=$method&school=$school"
  val c=URL(url).openConnection() as HttpURLConnection
  c.connectTimeout=15000; c.readTimeout=15000
  try {
   val json=JSONObject(c.inputStream.bufferedReader().use{it.readText()})
   val data=json.getJSONObject("data"); val t=data.getJSONObject("timings")
   fun time(k:String)=t.getString(k).substringBefore(" ").take(5)
   PrayerTimes(time("Fajr"),time("Sunrise"),time("Dhuhr"),time("Asr"),time("Maghrib"),time("Isha"),data.getJSONObject("meta").optString("timezone","Local"))
  } finally { c.disconnect() }
 }
}
