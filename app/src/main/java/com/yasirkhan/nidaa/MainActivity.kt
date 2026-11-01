package com.yasirkhan.nidaa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yasirkhan.nidaa.data.PrayerApi
import com.yasirkhan.nidaa.data.PrayerTimes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF073F35)
private val Green = Color(0xFF087A5B)
private val Mint = Color(0xFFE3F1E9)
private val Canvas = Color(0xFFF6F7F2)
private val Ink = Color(0xFF1C302A)
private val Muted = Color(0xFF718078)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NidaaApp() }
    }
}

@Composable
private fun NidaaApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("nidaa", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Home") }
    var prayer by remember { mutableStateOf<PrayerTimes?>(null) }
    var locationLabel by remember { mutableStateOf("Location not detected") }
    var status by remember { mutableStateOf("Allow location access to calculate today's prayer times.") }
    var loading by remember { mutableStateOf(false) }
    var method by remember { mutableIntStateOf(prefs.getInt("method", 1)) }
    var school by remember { mutableIntStateOf(prefs.getInt("school", 1)) }
    var jamaat by remember { mutableStateOf(prefs.getString("jamaat", "05:30\n13:30\n17:00\n18:30\n20:30") ?: "") }
    var reminders by remember { mutableStateOf(prefs.getString("reminders", "") ?: "") }
    var tasks by remember { mutableStateOf(prefs.getString("tasks", "") ?: "") }
    var notes by remember { mutableStateOf(prefs.getString("notes", "") ?: "") }

    fun getLocationAndFetch() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        loading = false
                        status = "Couldn't get a location fix. Turn on GPS and try again."
                    } else {
                        locationLabel = "Location detected · %.4f, %.4f".format(Locale.US, loc.latitude, loc.longitude)
                        scope.launch {
                            loading = true
                            status = "Getting today's prayer timetable…"
                            try {
                                prayer = PrayerApi.fetch(loc.latitude, loc.longitude, method, school)
                                status = "Prayer times updated · ${prayer?.timezone ?: "local time"}"
                            } catch (e: Exception) {
                                status = "Location found, but prayer times could not load. Check internet."
                            } finally { loading = false }
                        }
                    }
                }
                .addOnFailureListener {
                    loading = false
                    status = "Location failed: ${it.localizedMessage ?: "check location settings"}"
                }
        } catch (e: SecurityException) {
            loading = false
            status = "Location permission is missing. Please allow location access."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            getLocationAndFetch()
        } else {
            status = "Location permission denied. You can still use manual Jamaat settings."
        }
    }

    fun refresh() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            loading = true
            getLocationAndFetch()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Color.White, secondary = Forest,
            background = Canvas, surface = Color.White, onBackground = Ink,
            onSurface = Ink, surfaceVariant = Mint
        ),
        typography = Typography()
    ) {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    listOf(
                        Triple("Home", Icons.Default.Home, "Home"),
                        Triple("Salah", Icons.Default.AccessTime, "Salah"),
                        Triple("Reminders", Icons.Default.Notifications, "Reminders"),
                        Triple("Planner", Icons.Default.CalendarMonth, "Planner"),
                        Triple("More", Icons.Default.Tune, "More")
                    ).forEach { item ->
                        NavigationBarItem(
                            selected = tab == item.first,
                            onClick = { tab = item.first },
                            icon = { Icon(item.second, contentDescription = item.third) },
                            label = { Text(item.third) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, indicatorColor = Mint, selectedTextColor = Green)
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                "Home" -> HomeScreen(Modifier.padding(padding), prayer, status, locationLabel, loading, ::refresh, reminders, tasks)
                "Salah" -> SalahScreen(Modifier.padding(padding), prayer, status, loading, ::refresh, jamaat) {
                    jamaat = it; prefs.edit().putString("jamaat", it).apply()
                }
                "Reminders" -> RemindersScreen()
                "Planner" -> PlannerScreen()
                else -> SettingsScreen(Modifier.padding(padding), method, school, { method = it; prefs.edit().putInt("method", it).apply(); refresh() },
                    { school = it; prefs.edit().putInt("school", it).apply(); refresh() }, notes) {
                    notes = it; prefs.edit().putString("notes", it).apply()
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Forest).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 24.dp)) {
        Text("N I D A A", color = Color(0xFFB9DDCE), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFFD7E8DF), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, prayer: PrayerTimes?, status: String, location: String, loading: Boolean,
    refresh: () -> Unit, reminders: String, tasks: String
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader("Assalamu Alaikum", SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())) }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Forest)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Color(0xFFB9DDCE))
                            Spacer(Modifier.width(6.dp))
                            Text(location, color = Color(0xFFB9DDCE), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("YOUR DAILY SALAH", color = Color(0xFFB9DDCE), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Make space for what matters.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(14.dp))
                        Text(status, color = Color(0xFFD7E8DF), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = refresh, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9DDCE), contentColor = Forest)) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.MyLocation, null)
                            Spacer(Modifier.width(8.dp)); Text(if (loading) "Updating…" else "Detect location & refresh")
                        }
                    }
                }
                Text("Prayer schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (prayer == null) EmptyCard("Prayer times will appear here after location is detected.")
                else PrayerList(prayer, null)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallStat("Reminders", reminders.lines().count { it.isNotBlank() }.toString(), Icons.Default.Notifications, Modifier.weight(1f))
                    SmallStat("Tasks", tasks.lines().count { it.isNotBlank() }.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SalahScreen(modifier: Modifier, prayer: PrayerTimes?, status: String, loading: Boolean, refresh: () -> Unit, jamaat: String, onJamaat: (String) -> Unit) {
    val iqamah = jamaat.lines()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Salah manager", "Calculated Adhan times and your mosque's Iqamah") }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today's timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = refresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh", tint = Green) }
                }
                if (prayer == null) EmptyCard("Prayer times are not loaded yet. Check location permission and internet, then tap refresh.")
                else PrayerList(prayer, iqamah)
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mosque Jamaat / Iqamah", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Enter your mosque's announced Jamaat times. These are not supplied by the prayer-time API.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = jamaat, onValueChange = onJamaat, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Fajr, Dhuhr, Asr, Maghrib, Isha") }, supportingText = { Text("One time per line, in that order (HH:mm)") })
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerList(prayer: PrayerTimes, iqamah: List<String>?) {
    val rows = listOf("Fajr" to prayer.fajr, "Sunrise" to prayer.sunrise, "Dhuhr" to prayer.dhuhr, "Asr" to prayer.asr, "Maghrib" to prayer.maghrib, "Isha" to prayer.isha)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFEAF0EB))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (row.first == "Sunrise") Icons.Default.WbSunny else Icons.Default.Mosque, null, tint = Green, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.first, fontWeight = FontWeight.SemiBold)
                        Text(if (row.first == "Sunrise") "Sunrise" else "Adhan / prayer start", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(row.second, color = Forest, fontWeight = FontWeight.Bold)
                        if (row.first != "Sunrise") Text("Jamaat: ${iqamah?.getOrNull(index)?.ifBlank { "—" } ?: "Set below"}", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallStat(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Green)
            Spacer(Modifier.width(8.dp))
            Column { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(title, color = Muted, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(text, Modifier.padding(18.dp), color = Muted)
    }
}

@Composable
private fun EditorScreen(modifier: Modifier, title: String, subtitle: String, value: String, onChange: (String) -> Unit) {
    Column(modifier.fillMaxSize()) {
        PageHeader(title, subtitle)
        OutlinedTextField(value, onChange, Modifier.fillMaxSize().padding(16.dp), placeholder = { Text("Write here…") }, shape = RoundedCornerShape(18.dp))
        Text("Saved on this device", Modifier.padding(start = 18.dp, bottom = 12.dp), color = Green, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, method: Int, school: Int, setMethod: (Int) -> Unit, setSchool: (Int) -> Unit, notes: String, setNotes: (String) -> Unit) {
    Column(modifier.fillMaxSize()) {
        PageHeader("Settings & journal", "Make Nidaa work the way you pray and plan")
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Calculation method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    listOf(1 to "Karachi", 3 to "Muslim World League", 2 to "ISNA", 4 to "Umm Al-Qura").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = method == option.first, onClick = { setMethod(option.first) }); Text(option.second) }
                    }
                }
            }
            Text("Asr calculation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = school == 0, onClick = { setSchool(0) }); Text("Shafi / Standard") }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = school == 1, onClick = { setSchool(1) }); Text("Hanafi") }
                }
            }
            Text("Personal journal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(notes, setNotes, Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Write a reflection…") }, shape = RoundedCornerShape(18.dp))
        }
    }
}
