package com.yasirkhan.nidaa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yasirkhan.nidaa.data.PrayerApi
import com.yasirkhan.nidaa.data.PrayerTimes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF075A46)
private val Emerald = Color(0xFF0A8062)
private val Cream = Color(0xFFF5F4E9)
private val Ink = Color(0xFF18352D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NidaaApp() } }
}

@Composable
private fun NidaaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nidaa", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Home") }
    var times by remember { mutableStateOf<PrayerTimes?>(null) }
    var message by remember { mutableStateOf("Tap refresh to get prayer times for your location") }
    var busy by remember { mutableStateOf(false) }
    var method by remember { mutableIntStateOf(prefs.getInt("method", 1)) }
    var school by remember { mutableIntStateOf(prefs.getInt("school", 1)) }
    var jamaat by remember { mutableStateOf(prefs.getString("jamaat", "Fajr 05:30\nDhuhr 13:30\nAsr 17:00\nMaghrib 18:30\nIsha 20:30") ?: "") }
    var reminders by remember { mutableStateOf(prefs.getString("reminders", "") ?: "") }
    var tasks by remember { mutableStateOf(prefs.getString("tasks", "") ?: "") }
    var notes by remember { mutableStateOf(prefs.getString("notes", "") ?: "") }
    fun fetch(location: Location) {
        scope.launch {
            busy = true; message = "Fetching today's prayer times…"
            try { times = PrayerApi.fetch(location.latitude, location.longitude, method, school); message = "Updated · " + (times?.timezone ?: "local time") }
            catch (_: Exception) { message = "Unable to load prayer times. Check your internet connection and try again." }
            finally { busy = false }
        }
    }
    fun useLocation() {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            @Suppress("MissingPermission")
            manager.getCurrentLocation(provider, null, context.mainExecutor) { loc -> if (loc != null) fetch(loc) else message = "Location unavailable. Turn on location services." }
        } catch (_: Exception) { message = "Could not access location. Check device settings." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) useLocation() else message = "Location permission denied. You can enable it in Android settings."
    }
    fun refresh() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) useLocation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Emerald, secondary = Forest, background = Cream, surface = Color.White)) {
        Scaffold(containerColor = Cream, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Home", "Salah", "Reminders", "Planner", "More").forEach { item ->
                    val icon = when (item) { "Home" -> Icons.Default.Home; "Salah" -> Icons.Default.AccessTime; "Reminders" -> Icons.Default.Notifications; "Planner" -> Icons.Default.CalendarMonth; else -> Icons.Default.Settings }
                    NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(icon, contentDescription = item) }, label = { Text(item) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    "Home" -> HomeScreen(times, message, busy, ::refresh, reminders, tasks)
                    "Salah" -> SalahScreen(times, message, busy, ::refresh, jamaat) { jamaat = it; prefs.edit().putString("jamaat", it).apply() }
                    "Reminders" -> TextEditor("Reminders", "Add a reminder on each line", reminders) { reminders = it; prefs.edit().putString("reminders", it).apply() }
                    "Planner" -> TextEditor("Tasks & planner", "Add tasks, deadlines or events, one per line", tasks) { tasks = it; prefs.edit().putString("tasks", it).apply() }
                    else -> SettingsScreen(method, school, { method = it; prefs.edit().putInt("method", it).apply(); refresh() }, { school = it; prefs.edit().putInt("school", it).apply(); refresh() }, notes) { notes = it; prefs.edit().putString("notes", it).apply() }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Forest).padding(22.dp)) {
        Text("N I D A A", color = Color(0xFFBDE4D3), style = MaterialTheme.typography.labelLarge)
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFFD9EEE4), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HomeScreen(times: PrayerTimes?, message: String, busy: Boolean, refresh: () -> Unit, reminders: String, tasks: String) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { AppHeader("Assalamu Alaikum", SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())) }
        item {
            Column(Modifier.padding(16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("TODAY'S SALAH", color = Color(0xFFBDE4D3), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(12.dp))
                        if (times == null) Text("Your prayer timetable will appear here after location is detected.", color = Color.White)
                        else Text("Fajr  ${times.fajr}     Dhuhr  ${times.dhuhr}\nAsr  ${times.asr}       Maghrib  ${times.maghrib}\nIsha  ${times.isha}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp)); Text(message, color = Color(0xFFD9EEE4), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = refresh, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text(if (busy) "Loading…" else "Detect location & refresh") }
                    }
                }
                Spacer(Modifier.height(18.dp)); Text("Your day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                Text("${reminders.lines().count { it.isNotBlank() }} reminders · ${tasks.lines().count { it.isNotBlank() }} tasks", color = Ink)
                Spacer(Modifier.height(14.dp)); Text("Jamaat times can be entered in the Salah tab. They are mosque-specific and separate from calculated prayer times.", color = Ink)
            }
        }
    }
}

@Composable
private fun SalahScreen(times: PrayerTimes?, message: String, busy: Boolean, refresh: () -> Unit, jamaat: String, onJamaat: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("Salah manager", "Prayer start times and mosque Jamaat")
        LazyColumn(Modifier.weight(1f).padding(16.dp)) {
            item { Text(message, color = Ink); OutlinedButton(onClick = refresh, enabled = !busy) { Text("Refresh from location") }; Spacer(Modifier.height(10.dp)) }
            val rows = times?.let { listOf("Fajr" to it.fajr, "Sunrise" to it.sunrise, "Dhuhr" to it.dhuhr, "Asr" to it.asr, "Maghrib" to it.maghrib, "Isha" to it.isha) } ?: emptyList()
            items(rows) { row -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { ListItem(headlineContent = { Text(row.first) }, supportingContent = { Text("Calculated prayer start") }, trailingContent = { Text(row.second, color = Emerald, fontWeight = FontWeight.Bold) }) } }
            item { Spacer(Modifier.height(18.dp)); Text("Mosque Jamaat timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Enter the Iqamah times announced by your mosque.", color = Ink); OutlinedTextField(value = jamaat, onValueChange = onJamaat, modifier = Modifier.fillMaxWidth().height(190.dp), label = { Text("Prayer name and Jamaat time") }) }
        }
    }
}

@Composable
private fun TextEditor(title: String, hint: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) { AppHeader(title, hint); OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxSize().padding(16.dp), placeholder = { Text("Write here…") }); Text("Saved automatically on this device", color = Emerald, modifier = Modifier.padding(16.dp)) }
}

@Composable
private fun SettingsScreen(method: Int, school: Int, setMethod: (Int) -> Unit, setSchool: (Int) -> Unit, notes: String, setNotes: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("Settings & notes", "Configure prayer calculations and journal")
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Calculation method", fontWeight = FontWeight.Bold)
            listOf(1 to "Karachi", 3 to "Muslim World League", 2 to "ISNA", 4 to "Umm Al-Qura").forEach { option -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = method == option.first, onClick = { setMethod(option.first) }); Text(option.second) } }
            HorizontalDivider(); Text("Asr calculation", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = school == 0, onClick = { setSchool(0) }); Text("Shafi / Standard") }
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = school == 1, onClick = { setSchool(1) }); Text("Hanafi") }
            HorizontalDivider(); Text("Notes & journal", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = notes, onValueChange = setNotes, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Write a reflection or note…") })
        }
    }
}
