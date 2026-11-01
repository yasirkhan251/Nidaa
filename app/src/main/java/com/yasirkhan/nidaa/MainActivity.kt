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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yasirkhan.nidaa.data.PrayerAiCoach
import com.yasirkhan.nidaa.data.PrayerApi
import com.yasirkhan.nidaa.data.PrayerSoundMode
import com.yasirkhan.nidaa.data.PrayerTimes
import com.yasirkhan.nidaa.data.ProhibitedTimes
import com.yasirkhan.nidaa.data.SalahTracker
import com.yasirkhan.nidaa.data.format12Hour
import com.yasirkhan.nidaa.schedule.AlarmScheduler
import com.yasirkhan.nidaa.schedule.PlannerScreen
import com.yasirkhan.nidaa.schedule.RemindersScreen
import com.yasirkhan.nidaa.schedule.ScheduleItem
import com.yasirkhan.nidaa.schedule.loadScheduleItems
import com.yasirkhan.nidaa.schedule.saveScheduleItems
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

// Modern Youth & Gen-Z Aesthetic Color Palette
private val ForestDark = Color(0xFF03221B)
private val Forest = Color(0xFF07382D)
private val ForestMedium = Color(0xFF0B4E3F)
private val GreenEmerald = Color(0xFF059669)
private val MintSoft = Color(0xFFE8F7F0)
private val CanvasBg = Color(0xFFF7F8F5)
private val InkDark = Color(0xFF111827)
private val MutedText = Color(0xFF6B7280)
private val AmberWarm = Color(0xFFF59E0B)
private val AmberSoftBg = Color(0xFFFFFBEB)

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
    var showAlarmSettingsDialog by remember { mutableStateOf(false) }

    fun getLocationAndFetch() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        loading = false
                        status = "Couldn't get a location fix. Turn on GPS and try again."
                    } else {
                        locationLabel = "%.4f, %.4f".format(Locale.US, loc.latitude, loc.longitude)
                        scope.launch {
                            loading = true
                            status = "Getting today's prayer timetable…"
                            try {
                                val fetched = PrayerApi.fetch(loc.latitude, loc.longitude, method, school)
                                prayer = fetched
                                status = "Timetable synced · ${fetched.timezone}"
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
            primary = GreenEmerald, onPrimary = Color.White, secondary = Forest,
            background = CanvasBg, surface = Color.White, onBackground = InkDark,
            onSurface = InkDark, surfaceVariant = MintSoft
        ),
        typography = Typography()
    ) {
        Scaffold(
            containerColor = CanvasBg,
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 6.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
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
                            label = { Text(item.third, fontSize = 11.sp, fontWeight = if (tab == item.first) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenEmerald,
                                indicatorColor = MintSoft,
                                selectedTextColor = GreenEmerald,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText
                            )
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
                    onDateSelect = { selectedAttendanceDate = it },
                    onOpenAlarmSettings = { showAlarmSettingsDialog = true },
                    onNavigateToReminders = { tab = "Reminders" }
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
                    onOpenAlarmSettings = { showAlarmSettingsDialog = true },
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

            if (showAlarmSettingsDialog) {
                PrayerAlarmSettingsDialog(
                    prayerTimes = prayer,
                    jamaatLines = jamaat.lines(),
                    onDismiss = { showAlarmSettingsDialog = false }
                )
            }
        }
    }
}

fun getNextPrayerHighlight(prayer: PrayerTimes?): Pair<String, String>? {
    if (prayer == null) return null
    val now = LocalTime.now()
    val list = listOf(
        "Fajr" to prayer.fajr,
        "Sunrise" to prayer.sunrise,
        "Dhuhr" to prayer.dhuhr,
        "Asr" to prayer.asr,
        "Maghrib" to prayer.maghrib,
        "Isha" to prayer.isha
    )
    for ((name, time24) in list) {
        try {
            val lt = LocalTime.parse(time24.take(5))
            if (lt.isAfter(now)) {
                val remMin = ChronoUnit.MINUTES.between(now, lt)
                val h = remMin / 60
                val m = remMin % 60
                val inStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                return name to "in $inStr • ${format12Hour(time24)}"
            }
        } catch (_: Exception) {}
    }
    return "Fajr" to "Tomorrow • ${format12Hour(prayer.fajr)}"
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(ForestDark, Forest, ForestMedium)
                )
            )
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x22FFFFFF),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_nidaa_logo),
                            contentDescription = "Nidaa Logo",
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "N I D A A  •  نِـــدَاء",
                            color = Color(0xFFC7EBD9),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color(0xFFD3E7DC), style = MaterialTheme.typography.bodyMedium)
        }
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
    onDateSelect: (String) -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onNavigateToReminders: () -> Unit
) {
    val context = LocalContext.current
    val nextPrayer = remember(prayer) { getNextPrayerHighlight(prayer) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeader("Assalamu Alaikum ✨", SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()))
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Sleek Modern Hero Card
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF04261E), Color(0xFF0A4437)))
                            )
                            .padding(22.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0x24FFFFFF),
                                    shape = CircleShape
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.NearMe, null, tint = Color(0xFFB9DDCE), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(location, color = Color(0xFFC7EBD9), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                if (nextPrayer != null) {
                                    Surface(
                                        color = Color(0xFF10B981),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            "Next: ${nextPrayer.first}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("YOUR DAILY SALAH", color = Color(0xFF86EFAC), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (nextPrayer != null) "${nextPrayer.first} ${nextPrayer.second}" else "Make space for what matters.",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(status, color = Color(0xFFD1E8DC), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = refresh,
                                enabled = !loading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F7F0), contentColor = ForestDark),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ForestDark)
                                } else {
                                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (loading) "Updating timetable…" else "Sync location & times", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Gen-Z Daily Salah Tracker
                AttendanceTrackerCard(selectedDate, onDateSelect)

                // On-Device AI Habit Coach Card
                PrayerAiCoachCard(onAddOfficeAlarms = onNavigateToReminders)

                // Office & Daily Routine Quick Alarms
                OfficeAlarmsCard(onAlarmAdded = { title ->
                    Toast.makeText(context, "Added routine alarm: $title", Toast.LENGTH_SHORT).show()
                })

                // Sacred Rest Times (Makruh)
                if (prayer != null) {
                    ProhibitedTimesCard(prayer)
                }

                // Today's Timetable Section
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Today's Timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MintSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onOpenAlarmSettings() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = GreenEmerald, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Alarms", color = GreenEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(12.dp)) {
                            Text("12-hr", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                        }
                    }
                }

                if (prayer == null) EmptyCard("Prayer times will appear here after location is detected.")
                else PrayerList(prayer, null)

                // Monthly Calendar Attendance History
                MonthlyCalendarCard(selectedDate, onDateSelect)

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
    onOpenAlarmSettings: () -> Unit,
    onJamaat: (String) -> Unit
) {
    val iqamah = jamaat.lines()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Salah Manager", "Calculated Adhan times, Mosque Iqamah & Attendance") }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Quick Audio Settings Banner
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MintSoft,
                    border = BorderStroke(1.dp, Color(0xFFC7EBD9)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAlarmSettings() }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).background(GreenEmerald, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Custom Prayer Alarms & Adhan Settings", fontWeight = FontWeight.Bold, color = ForestDark, fontSize = 12.sp)
                            Text("Set Loudest (Override Silent), Quiet, Vibrate & Adhans", color = ForestMedium, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = ForestDark, modifier = Modifier.size(20.dp))
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today's Timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(status, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = refresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh", tint = GreenEmerald) }
                }

                if (prayer == null) {
                    EmptyCard("Prayer times are not loaded yet. Check location permission and internet, then tap refresh.")
                } else {
                    PrayerList(prayer, iqamah)
                    ProhibitedTimesCard(prayer)
                }

                // Attendance Tracker
                AttendanceTrackerCard(selectedDate, onDateSelect)

                // Mosque Iqamah Editor
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕌", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Mosque Jamaat / Iqamah Times", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Enter your local mosque's announced Jamaat times (Fajr, Dhuhr, Asr, Maghrib, Isha). Nidaa will calculate countdowns and alarms automatically.",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = jamaat,
                            onValueChange = onJamaat,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Fajr, Dhuhr, Asr, Maghrib, Isha") },
                            supportingText = { Text("One time per line (e.g. 05:30, 13:30, 17:00, 18:30, 20:30)") }
                        )
                    }
                }

                // Monthly Calendar Attendance History
                MonthlyCalendarCard(selectedDate, onDateSelect)
            }
        }
    }
}

/**
 * Youth-friendly, Gen-Z styled interactive Salah Attendance Tracker
 * Solves the cramped checkbox size issue by providing dedicated vertical capsules with emojis and touch feedback.
 */
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

    val prayerIcons = mapOf(
        "Fajr" to "🌅",
        "Dhuhr" to "☀️",
        "Asr" to "🌤️",
        "Maghrib" to "🌇",
        "Isha" to "🌙"
    )

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header with Streaks / Badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily Salah Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (prayedCount == 5) {
                            Spacer(Modifier.width(6.dp))
                            Text("🔥", fontSize = 14.sp)
                        }
                    }
                    Text(
                        if (prayedCount == 5) "All 5 prayers completed! MashaAllah"
                        else "$formattedDateTitle • ${5 - prayedCount} remaining for full attendance",
                        color = if (prayedCount == 5) GreenEmerald else MutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Surface(
                    color = if (prayedCount == 5) Color(0xFFD1FAE5) else MintSoft,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "$prayedCount / 5",
                        color = if (prayedCount == 5) Color(0xFF065F46) else GreenEmerald,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Smooth Rounded Gradient Progress Bar
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5E7EB))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (prayedCount / 5f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))
                            )
                        )
                )
            }

            // Segmented Date Switcher
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0L to "Today", 1L to "Yesterday", 2L to "2 days ago").forEach { (offset, label) ->
                    val dStr = today.minusDays(offset).toString()
                    val selected = dStr == date
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) ForestDark else Color(0xFFF3F4F6),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDateSelect(dStr) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else MutedText,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Tap to toggle (Prayed / Missed / Prayed Later):",
                color = MutedText,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))

            // Beautiful Prayer Capsules - No line wrapping, perfectly sized touch targets!
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SalahTracker.PRAYERS.forEach { p ->
                    val isPrayed = attendance[p] == true
                    val icon = prayerIcons[p] ?: "🕌"

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val newStatus = !isPrayed
                                SalahTracker.setPrayerStatus(context, p, newStatus, date)
                                attendance = SalahTracker.getAttendance(context, date)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPrayed) Color(0xFFE8F7F0) else Color(0xFFF9FAFB),
                        border = BorderStroke(
                            width = if (isPrayed) 1.5.dp else 1.dp,
                            color = if (isPrayed) Color(0xFF10B981) else Color(0xFFE5E7EB)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(icon, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = p,
                                fontSize = 11.sp,
                                fontWeight = if (isPrayed) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isPrayed) Color(0xFF047857) else Color(0xFF374151),
                                maxLines = 1
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        color = if (isPrayed) Color(0xFF10B981) else Color.White,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = if (isPrayed) Color(0xFF10B981) else Color(0xFFD1D5DB),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPrayed) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
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
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentProhibited != null) Color(0xFFFFF5F5) else AmberSoftBg
        ),
        border = BorderStroke(
            1.dp,
            if (currentProhibited != null) Color(0xFFFCA5A5) else Color(0xFFFDE68A)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (currentProhibited != null) "⚠️" else "⏳", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sacred Pause Times (Makruh)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentProhibited != null) Color(0xFFB91C1C) else Color(0xFF92400E)
                    )
                }

                Surface(
                    color = if (currentProhibited != null) Color(0xFFEF4444) else AmberWarm,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (currentProhibited != null) "PROHIBITED NOW" else "Makruh Tahrimi",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Voluntary (Nafl) prayers pause during these 3 solar transition windows:",
                color = if (currentProhibited != null) Color(0xFF991B1B) else Color(0xFF78350F),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            intervals.forEachIndexed { i, interval ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x22F59E0B))
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
                            color = if (isThisActive) Color(0xFFDC2626) else Color(0xFF451A03),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(interval.reason, color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(
                        color = if (isThisActive) Color(0xFFFEE2E2) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "${interval.start12} – ${interval.end12}",
                            color = if (isThisActive) Color(0xFFB91C1C) else Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
        Triple("Fajr", prayer.fajr, Color(0xFFEDE9FE) to Color(0xFF7C3AED)),      // Dawn violet
        Triple("Sunrise", prayer.sunrise, Color(0xFFFEF3C7) to Color(0xFFD97706)),  // Golden sun
        Triple("Dhuhr", prayer.dhuhr, Color(0xFFE0F2FE) to Color(0xFF0284C7)),      // Noon sky blue
        Triple("Asr", prayer.asr, Color(0xFFCCFBF1) to Color(0xFF0D9488)),          // Afternoon teal
        Triple("Maghrib", prayer.maghrib, Color(0xFFFFEDD5) to Color(0xFFEA580C)),  // Sunset coral
        Triple("Isha", prayer.isha, Color(0xFFE0E7FF) to Color(0xFF4338CA))         // Night indigo
    )

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            rows.forEachIndexed { index, (name, time24, colors) ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color(0xFFF3F4F6))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).background(colors.first, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (name == "Sunrise") Icons.Default.WbSunny else Icons.Default.Mosque,
                            null,
                            tint = colors.second,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            if (name == "Fajr") {
                                Spacer(Modifier.width(6.dp))
                                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                                    Text("LOUDER", color = Color(0xFFEA580C), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(if (name == "Sunrise") "Prohibited ends" else "Adhan time", color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(format12Hour(time24), color = InkDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                        if (name != "Sunrise") {
                            val jIndex = if (index >= 1) index - 1 else index
                            val jVal = iqamah?.getOrNull(jIndex)?.takeIf { it.isNotBlank() }
                            val jTime12 = format12Hour(jVal)
                            val countdown = SalahTracker.getTimeRemaining(jVal)
                            val duration = SalahTracker.getDurationBetween(time24, jVal)

                            if (countdown != null) {
                                Surface(color = MintSoft, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "Jamaat in $countdown",
                                        color = GreenEmerald,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (jVal != null) {
                                Text("Jamaat: $jTime12" + (if (duration != null) " ($duration)" else ""), color = MutedText, style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("Jamaat: Set below", color = MutedText, style = MaterialTheme.typography.labelSmall)
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
    Card(
        modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(MintSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = GreenEmerald, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(title, color = MutedText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Text(text, Modifier.padding(20.dp), color = MutedText, style = MaterialTheme.typography.bodyMedium)
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
        PageHeader("Settings & Journal ✍️", "Customize your calculation methods and personal reflections")
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Calculation Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFEFF2ED))
            ) {
                Column(Modifier.padding(12.dp)) {
                    listOf(1 to "Karachi (Univ. of Islamic Sciences)", 3 to "Muslim World League", 2 to "ISNA (North America)", 4 to "Umm Al-Qura (Makkah)").forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { setMethod(option.first) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = method == option.first,
                                onClick = { setMethod(option.first) },
                                colors = RadioButtonDefaults.colors(selectedColor = GreenEmerald)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(option.second, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text("Asr Juristic Calculation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFEFF2ED))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setSchool(0) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = school == 0,
                            onClick = { setSchool(0) },
                            colors = RadioButtonDefaults.colors(selectedColor = GreenEmerald)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Shafi / Hanbali / Maliki (Standard shadow)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setSchool(1) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = school == 1,
                            onClick = { setSchool(1) },
                            colors = RadioButtonDefaults.colors(selectedColor = GreenEmerald)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Hanafi (Double shadow)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text("Personal Reflection Journal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = notes,
                onValueChange = setNotes,
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("Write your thoughts, gratitude, or Quran reflections…") },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

/**
 * Monthly Prayer Attendance Calendar with selectable days and status history
 */
@Composable
private fun MonthlyCalendarCard(
    selectedDate: String,
    onDateSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    val stats = remember(currentYearMonth, selectedDate) {
        SalahTracker.getMonthStats(context, currentYearMonth.year, currentYearMonth.monthValue)
    }
    val attendanceMap = remember(currentYearMonth, selectedDate) {
        SalahTracker.getMonthAttendance(context, currentYearMonth.year, currentYearMonth.monthValue)
    }

    val today = LocalDate.now()
    val parsedSelected = try { LocalDate.parse(selectedDate) } catch (_: Exception) { today }

    val daysInMonth = currentYearMonth.lengthOfMonth()
    val firstDay = currentYearMonth.atDay(1)
    val firstDayOffset = firstDay.dayOfWeek.value - 1 // Mon=0, Sun=6

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Column(Modifier.padding(18.dp)) {
            // Month Header with Prev / Next Switcher
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Monthly Prayer Attendance Calendar",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Previous month", tint = InkDark)
                    }
                    IconButton(
                        onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, "Next month", tint = InkDark)
                    }
                }
            }

            // Month Stats Pills
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MintSoft,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.totalPrayed} / ${stats.totalPossible}", fontWeight = FontWeight.Bold, color = GreenEmerald, fontSize = 11.sp)
                        Text("Prayed", color = MutedText, fontSize = 9.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.totalMissed}", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), fontSize = 11.sp)
                        Text("Missed", color = MutedText, fontSize = 9.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.completionPercentage}%", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), fontSize = 11.sp)
                        Text("Rate", color = MutedText, fontSize = 9.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberSoftBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥 ${stats.currentStreak}d", fontWeight = FontWeight.Bold, color = AmberWarm, fontSize = 11.sp)
                        Text("Streak", color = MutedText, fontSize = 9.sp)
                    }
                }
            }

            // Weekday Labels
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dayLabel ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(dayLabel, fontSize = 10.sp, color = MutedText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Calendar Days Grid
            val totalCells = firstDayOffset + daysInMonth
            val weeks = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in 0 until weeks) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (d in 0 until 7) {
                            val cellIndex = w * 7 + d
                            val dayNum = cellIndex - firstDayOffset + 1
                            if (dayNum in 1..daysInMonth) {
                                val cellDate = currentYearMonth.atDay(dayNum)
                                val isSelected = cellDate == parsedSelected
                                val isToday = cellDate == today
                                val isFuture = cellDate.isAfter(today)
                                val prayersPrayed = attendanceMap[cellDate]?.values?.count { it } ?: 0

                                val dotColor = when {
                                    isFuture -> Color.Transparent
                                    prayersPrayed == 5 -> Color(0xFF059669)
                                    prayersPrayed in 3..4 -> Color(0xFFF59E0B)
                                    prayersPrayed in 1..2 -> Color(0xFFEA580C)
                                    else -> Color(0xFFD1D5DB)
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!isFuture) onDateSelect(cellDate.toString())
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isSelected -> ForestDark
                                        isToday -> MintSoft
                                        else -> Color(0xFFF9FAFB)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected || isToday) 1.5.dp else 0.5.dp,
                                        color = when {
                                            isSelected -> ForestDark
                                            isToday -> GreenEmerald
                                            else -> Color(0xFFE5E7EB)
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> ForestDark
                                                isFuture -> Color(0xFF9CA3AF)
                                                else -> InkDark
                                            }
                                        )
                                        if (!isFuture) {
                                            Spacer(Modifier.height(3.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(
                                                        if (isSelected && dotColor != Color(0xFF059669)) Color.White else dotColor,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Legend
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFF059669), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("5/5", fontSize = 10.sp, color = MutedText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFFF59E0B), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("3-4", fontSize = 10.sp, color = MutedText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFFEA580C), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("1-2", fontSize = 10.sp, color = MutedText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFFD1D5DB), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("Missed", fontSize = 10.sp, color = MutedText)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap any past day to review or update your prayer checklist (e.g. for Qada / emergencies).",
                color = MutedText,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * On-Device Smart AI / NPU Habit Coach Card
 */
@Composable
private fun PrayerAiCoachCard(
    onAddOfficeAlarms: () -> Unit
) {
    val context = LocalContext.current
    val insight = remember { PrayerAiCoach.analyze(context) }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header with AI Badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("On-Device AI Habit Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Poco F7 NPU Local Pattern Engine", color = GreenEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = MintSoft,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        insight.scoreTrend,
                        color = GreenEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Spiritual Discipline Score Banner
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF9FBFA),
                border = BorderStroke(1.dp, Color(0xFFE8F1EC))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(54.dp).background(
                            Brush.sweepGradient(listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${insight.habitScore}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("SCORE", color = Color(0xFFD1FAE5), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (insight.habitScore >= 80) "Disciplined & Steadfast ✨"
                            else if (insight.habitScore >= 60) "Building Momentum 🚀"
                            else "Ready for Renewal 🌱",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Strongest: ${insight.strongestPrayer} (${insight.strongestPrayerRate}%) • Weakest: ${insight.weakestPrayer} (${insight.weakestPrayerRate}%)",
                            color = MutedText,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Weekday vs Weekend Pattern
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("📊", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    insight.weekdayVsWeekendInsight,
                    color = InkDark,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Key AI Recommendations
            Spacer(Modifier.height(12.dp))
            Text("Smart Action Plan:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ForestDark)
            Spacer(Modifier.height(6.dp))
            insight.recommendations.forEach { rec ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("✦", color = GreenEmerald, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(rec, style = MaterialTheme.typography.labelSmall, color = Color(0xFF374151))
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAddOfficeAlarms,
                colors = ButtonDefaults.buttonColors(containerColor = ForestDark, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Alarm, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Reminders & Routine Alarms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 1-Tap Quick Office & Daily Routine Alarms
 */
@Composable
private fun OfficeAlarmsCard(
    onAlarmAdded: (String) -> Unit
) {
    val context = LocalContext.current
    val routines = listOf(
        Triple("Get Ready for Office", "07:30", "Morning preparation & breakfast"),
        Triple("Leave for Work", "08:15", "Commute & pack laptop/keys"),
        Triple("Office Dhuhr Break", "13:15", "Step away for Dhuhr & lunch"),
        Triple("Afternoon Asr Focus", "16:30", "Quick tea & Asr prayer"),
        Triple("Wrap Up & Maghrib", "18:00", "Review tasks & head home")
    )

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEFF2ED))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💼", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Office & Daily Routine Alarms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(color = Color(0xFFEFF6FF), shape = CircleShape) {
                    Text("Mon–Fri", color = Color(0xFF2563EB), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Quick 1-tap alarms for workdays so you never miss prayers or routines:",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                routines.forEach { (title, time24, detail) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier.clickable {
                            val existing = loadScheduleItems(context, "reminders")
                            val newId = (System.currentTimeMillis() % 100000).toInt()
                            val item = ScheduleItem(
                                id = newId,
                                title = title,
                                detail = detail,
                                date = LocalDate.now().toString(),
                                time = time24,
                                repeatDays = setOf(1, 2, 3, 4, 5),
                                leadMinutes = 0,
                                completed = false,
                                soundUri = null
                            )
                            saveScheduleItems(context, "reminders", existing + item)

                            val now = LocalDateTime.now()
                            val date = LocalDate.now()
                            val t = LocalTime.parse(time24)
                            val first = (0..7).map { date.plusDays(it.toLong()).atTime(t) }
                                .firstOrNull { it.isAfter(now) && item.repeatDays.contains(it.dayOfWeek.value) }
                            if (first != null) {
                                val millis = first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                AlarmScheduler.schedule(context, (item.id * 31) and 0x7fffffff, item.title, item.detail, millis, item.repeatDays, null)
                            }
                            onAlarmAdded(title)
                        }
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ForestDark)
                            Spacer(Modifier.height(4.dp))
                            Text(detail, fontSize = 10.sp, color = MutedText)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(format12Hour(time24), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GreenEmerald)
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MintSoft,
                                    shape = CircleShape
                                ) {
                                    Text("+ Add", color = GreenEmerald, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Per-Prayer Alarm & Adhan Tone Settings Dialog
 */
@Composable
private fun PrayerAlarmSettingsDialog(
    prayerTimes: PrayerTimes?,
    jamaatLines: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedPrayer by remember { mutableStateOf("Fajr") }

    val prayers = SalahTracker.PRAYERS
    var currentConfig by remember(selectedPrayer) {
        mutableStateOf(SalahTracker.getPrayerAudioConfig(context, selectedPrayer))
    }

    val tones = listOf("Adhan Makkah", "Adhan Madinah", "Soft Chime", "System Default Alarm")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Title
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🔊 Per-Prayer Alarm Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Customize volume override & Adhan per prayer", color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }

                // Prayer Tabs
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    prayers.forEach { p ->
                        val isSel = p == selectedPrayer
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) ForestDark else Color(0xFFF3F4F6),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPrayer = p }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    p,
                                    color = if (isSel) Color.White else InkDark,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF3F4F6))

                // Sound Mode Selection
                Text("Sound Mode for $selectedPrayer:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerSoundMode.entries.forEach { mode ->
                        val isModeSelected = currentConfig.soundMode == mode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isModeSelected) MintSoft else Color(0xFFF9FAFB),
                            border = BorderStroke(
                                1.dp,
                                if (isModeSelected) GreenEmerald else Color(0xFFE5E7EB)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentConfig = currentConfig.copy(soundMode = mode)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isModeSelected,
                                    onClick = { currentConfig = currentConfig.copy(soundMode = mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = GreenEmerald)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(mode.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (mode == PrayerSoundMode.LOUDEST) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(6.dp)) {
                                                Text("IGNORES SILENT", color = Color(0xFFDC2626), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Text(mode.description, color = MutedText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Adhan Tone Selection
                if (currentConfig.soundMode != PrayerSoundMode.OFF && currentConfig.soundMode != PrayerSoundMode.VIBRATE) {
                    Text("Adhan Tone / Sound:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tones.forEach { tone ->
                            val isToneSelected = currentConfig.soundTone == tone
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isToneSelected) ForestDark else Color(0xFFF3F4F6),
                                border = BorderStroke(1.dp, if (isToneSelected) ForestDark else Color(0xFFE5E7EB)),
                                modifier = Modifier.clickable {
                                    currentConfig = currentConfig.copy(soundTone = tone)
                                }
                            ) {
                                Text(
                                    tone,
                                    color = if (isToneSelected) Color.White else InkDark,
                                    fontWeight = if (isToneSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Actions: Save for Current Prayer or Apply to All
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            prayers.forEach { p ->
                                SalahTracker.setPrayerAudioConfig(context, p, currentConfig.soundMode, currentConfig.soundTone)
                            }
                            if (prayerTimes != null) {
                                AlarmScheduler.scheduleAllPrayers(context, prayerTimes, jamaatLines)
                            }
                            Toast.makeText(context, "Applied to all prayers", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Apply to All", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            SalahTracker.setPrayerAudioConfig(context, selectedPrayer, currentConfig.soundMode, currentConfig.soundTone)
                            if (prayerTimes != null) {
                                AlarmScheduler.scheduleAllPrayers(context, prayerTimes, jamaatLines)
                            }
                            Toast.makeText(context, "Saved for $selectedPrayer", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenEmerald)
                    ) {
                        Text("Save for $selectedPrayer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
