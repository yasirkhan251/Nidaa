package com.yasirkhan.nidaa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yasirkhan.nidaa.data.PrayerApi
import com.yasirkhan.nidaa.data.PrayerTimes
import com.yasirkhan.nidaa.data.ProhibitedTimes
import com.yasirkhan.nidaa.data.SalahTracker
import com.yasirkhan.nidaa.data.format12Hour
import com.yasirkhan.nidaa.schedule.AlarmScheduler
import com.yasirkhan.nidaa.schedule.PlannerScreen
import com.yasirkhan.nidaa.schedule.RemindersScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
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
    val context = LocalContext.current
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
    var selectedAttendanceDate by remember { mutableStateOf(LocalDate.now().toString()) }

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
                                val fetched = PrayerApi.fetch(loc.latitude, loc.longitude, method, school)
                                prayer = fetched
                                status = "Prayer times updated · ${fetched.timezone}"
                                // Automatically schedule alarms for Adhan and Jamaat
                                AlarmScheduler.scheduleAllPrayers(context, fetched, jamaat.lines())
                            } catch (_: Exception) {
                                status = "Location found, but prayer times could not load. Check internet."
                            } finally { loading = false }
                        }
                    }
                }
                .addOnFailureListener {
                    loading = false
                    status = "Location failed: ${it.localizedMessage ?: "check location settings"}"
                }
        } catch (_: SecurityException) {
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
                "Home" -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    prayer = prayer,
                    status = status,
                    location = locationLabel,
                    loading = loading,
                    refresh = ::refresh,
                    reminders = reminders,
                    tasks = tasks,
                    selectedDate = selectedAttendanceDate,
                    onDateSelect = { selectedAttendanceDate = it }
                )
                "Salah" -> SalahScreen(
                    modifier = Modifier.padding(padding),
                    prayer = prayer,
                    status = status,
                    loading = loading,
                    refresh = ::refresh,
                    jamaat = jamaat,
                    selectedDate = selectedAttendanceDate,
                    onDateSelect = { selectedAttendanceDate = it },
                    onJamaat = {
                        jamaat = it
                        prefs.edit { putString("jamaat", it) }
                        prayer?.let { p -> AlarmScheduler.scheduleAllPrayers(context, p, it.lines()) }
                    }
                )
                "Reminders" -> RemindersScreen()
                "Planner" -> PlannerScreen()
                else -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    method = method,
                    school = school,
                    setMethod = {
                        method = it
                        prefs.edit { putInt("method", it) }
                        refresh()
                    },
                    setSchool = {
                        school = it
                        prefs.edit { putInt("school", it) }
                        refresh()
                    },
                    notes = notes
                ) {
                    notes = it
                    prefs.edit { putString("notes", it) }
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
    modifier: Modifier,
    prayer: PrayerTimes?,
    status: String,
    location: String,
    loading: Boolean,
    refresh: () -> Unit,
    reminders: String,
    tasks: String,
    selectedDate: String,
    onDateSelect: (String) -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader("Assalamu Alaikum", SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())) }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Location & Quick Action Card
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

                // Daily Prayer Attendance Tracker
                AttendanceTrackerCard(selectedDate, onDateSelect)

                // Prohibited Prayer Times (Makruh)
                if (prayer != null) {
                    ProhibitedTimesCard(prayer)
                }

                // Today's Prayer Schedule in 12-hour format
                Text("Today's Timetable (12-hr)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
private fun SalahScreen(
    modifier: Modifier,
    prayer: PrayerTimes?,
    status: String,
    loading: Boolean,
    refresh: () -> Unit,
    jamaat: String,
    selectedDate: String,
    onDateSelect: (String) -> Unit,
    onJamaat: (String) -> Unit
) {
    val iqamah = jamaat.lines()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Salah manager", "Calculated Adhan times, Mosque Iqamah & Attendance") }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today's timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = refresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh", tint = Green) }
                }

                if (prayer == null) {
                    EmptyCard("Prayer times are not loaded yet. Check location permission and internet, then tap refresh.")
                } else {
                    PrayerList(prayer, iqamah)
                    ProhibitedTimesCard(prayer)
                }

                // Attendance Tracker with Date Selector
                AttendanceTrackerCard(selectedDate, onDateSelect)

                // Mosque Iqamah Editor
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mosque Jamaat / Iqamah Times", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Enter your local mosque's announced Jamaat times (Fajr, Dhuhr, Asr, Maghrib, Isha). Nidaa will calculate countdowns and alarms automatically.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = jamaat,
                            onValueChange = onJamaat,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            label = { Text("Fajr, Dhuhr, Asr, Maghrib, Isha (HH:mm)") },
                            supportingText = { Text("One time per line in 24-hr format (e.g. 05:30, 13:30, 17:00, 18:30, 20:30)") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceTrackerCard(
    date: String,
    onDateSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var attendance by remember(date) { mutableStateOf(SalahTracker.getAttendance(context, date)) }
    val prayedCount = attendance.values.count { it }
    val today = LocalDate.now()
    val isToday = date == today.toString()
    val parsedDate = try { LocalDate.parse(date) } catch (_: Exception) { today }
    val formattedDateTitle = if (isToday) "Today" else parsedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Daily Salah Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$formattedDateTitle · $prayedCount of 5 Prayed", color = if (prayedCount == 5) Green else Forest, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    color = if (prayedCount == 5) Color(0xFFE8F5E9) else Mint,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "$prayedCount/5",
                        color = if (prayedCount == 5) Color(0xFF2E7D32) else Green,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { prayedCount / 5f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Green,
                trackColor = Color(0xFFEAF0EB)
            )

            // Date switcher (Today, Yesterday, 2 days ago) to allow editing past emergency prayers
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0L to "Today", 1L to "Yesterday", 2L to "2 days ago").forEach { (offset, label) ->
                    val dStr = today.minusDays(offset).toString()
                    val selected = dStr == date
                    Surface(
                        shape = CircleShape,
                        color = if (selected) Forest else Color(0xFFF2F4EE),
                        modifier = Modifier.clickable { onDateSelect(dStr) }
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else Muted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tap any prayer to toggle (Prayed / Missed / Prayed Later):",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))

            // Prayer toggles
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SalahTracker.PRAYERS.forEach { p ->
                    val isPrayed = attendance[p] == true
                    FilterChip(
                        selected = isPrayed,
                        onClick = {
                            val newStatus = !isPrayed
                            SalahTracker.setPrayerStatus(context, p, newStatus, date)
                            attendance = SalahTracker.getAttendance(context, date)
                        },
                        label = { Text(p, fontSize = 11.sp, fontWeight = if (isPrayed) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                if (isPrayed) Icons.Default.CheckCircle else Icons.Default.Check,
                                null,
                                tint = if (isPrayed) Green else Muted,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mint,
                            selectedLabelColor = Green
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ProhibitedTimesCard(prayer: PrayerTimes) {
    val intervals = remember(prayer) { ProhibitedTimes.calculate(prayer) }
    val currentProhibited = remember(intervals) { ProhibitedTimes.isCurrentlyProhibited(intervals) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentProhibited != null) Color(0xFFFFF5F5) else Color.White
        ),
        border = if (currentProhibited != null) BorderStroke(1.dp, Color(0xFFEF9A9A)) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (currentProhibited != null) Color(0xFFD32F2F) else Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Prohibited Prayer Times (Makruh)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (currentProhibited != null) Color(0xFFD32F2F) else Forest
                )
            }

            if (currentProhibited != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "⚠️ PRAYER PROHIBITED RIGHT NOW (${currentProhibited.name})",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Voluntary (Nafl) prayers are prohibited during 3 periods: Sunrise, Midday Zenith (Zawal), and Sunset.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))

            intervals.forEachIndexed { i, interval ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFF2F4F2))
                val isThisActive = currentProhibited == interval
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            interval.name,
                            fontWeight = if (isThisActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isThisActive) Color(0xFFD32F2F) else Forest,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(interval.reason, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(
                        color = if (isThisActive) Color(0xFFFFCDD2) else Mint,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${interval.start12} – ${interval.end12}",
                            color = if (isThisActive) Color(0xFFB71C1C) else Green,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerList(prayer: PrayerTimes, iqamah: List<String>?) {
    val rows = listOf(
        "Fajr" to prayer.fajr,
        "Sunrise" to prayer.sunrise,
        "Dhuhr" to prayer.dhuhr,
        "Asr" to prayer.asr,
        "Maghrib" to prayer.maghrib,
        "Isha" to prayer.isha
    )

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFEAF0EB))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(38.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(
                            if (row.first == "Sunrise") Icons.Default.WbSunny else Icons.Default.Mosque,
                            null,
                            tint = Green,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.first, fontWeight = FontWeight.SemiBold)
                            if (row.first == "Fajr") {
                                Spacer(Modifier.width(6.dp))
                                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                                    Text("LOUDER ALARM", color = Color(0xFFE65100), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(if (row.first == "Sunrise") "Sunrise (Prohibited ends)" else "Adhan / Start", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        // 12-Hour formatted Adhan time
                        Text(format12Hour(row.second), color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)

                        if (row.first != "Sunrise") {
                            val jIndex = if (index >= 1) index - 1 else index
                            val jVal = iqamah?.getOrNull(jIndex)?.takeIf { it.isNotBlank() }
                            val jTime12 = format12Hour(jVal)
                            val countdown = SalahTracker.getTimeRemaining(jVal)
                            val duration = SalahTracker.getDurationBetween(row.second, jVal)

                            if (countdown != null) {
                                Text("Jamaat in $countdown ($jTime12)", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            } else if (jVal != null) {
                                Text("Jamaat: $jTime12" + (if (duration != null) " ($duration)" else ""), color = Muted, style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("Jamaat: Set below", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
private fun SettingsScreen(
    modifier: Modifier,
    method: Int,
    school: Int,
    setMethod: (Int) -> Unit,
    setSchool: (Int) -> Unit,
    notes: String,
    setNotes: (String) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        PageHeader("Settings & journal", "Make Nidaa work the way you pray and plan")
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Calculation method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    listOf(1 to "Karachi", 3 to "Muslim World League", 2 to "ISNA", 4 to "Umm Al-Qura").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = method == option.first, onClick = { setMethod(option.first) })
                            Text(option.second)
                        }
                    }
                }
            }
            Text("Asr calculation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = school == 0, onClick = { setSchool(0) })
                        Text("Shafi / Standard")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = school == 1, onClick = { setSchool(1) })
                        Text("Hanafi")
                    }
                }
            }
            Text("Personal journal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = notes,
                onValueChange = setNotes,
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("Write a reflection…") },
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}
