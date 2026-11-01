package com.yasirkhan.nidaa.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val ScheduleGreen = Color(0xFF087A5B)
private val ScheduleForest = Color(0xFF073F35)
private val ScheduleMuted = Color(0xFF718078)
private val ScheduleDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val displayDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val displayTime = DateTimeFormatter.ofPattern("h:mm a")

data class ScheduleItem(
    val id: Int,
    val title: String,
    val detail: String,
    val date: String,
    val time: String,
    val repeatDays: Set<Int>,
    val leadMinutes: Int,
    val completed: Boolean = false
)

private fun loadItems(context: Context, key: String): List<ScheduleItem> {
    val raw = context.getSharedPreferences("nidaa_schedule", Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val days = o.optJSONArray("days") ?: JSONArray()
            ScheduleItem(o.getInt("id"), o.getString("title"), o.optString("detail"), o.optString("date"),
                o.getString("time"), (0 until days.length()).map { days.getInt(it) }.toSet(),
                o.optInt("lead", 0), o.optBoolean("completed", false))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveItems(context: Context, key: String, items: List<ScheduleItem>) {
    val array = JSONArray()
    items.forEach { item ->
        val days = JSONArray()
        item.repeatDays.sorted().forEach { day -> days.put(day) }
        array.put(JSONObject().put("id", item.id).put("title", item.title).put("detail", item.detail)
            .put("date", item.date).put("time", item.time).put("days", days)
            .put("lead", item.leadMinutes).put("completed", item.completed))
    }
    context.getSharedPreferences("nidaa_schedule", Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
}

private fun alarmId(item: ScheduleItem, suffix: Int = 0) = (item.id * 31 + suffix) and 0x7fffffff

private fun scheduleItem(context: Context, item: ScheduleItem, planner: Boolean) {
    val date = LocalDate.parse(item.date)
    val time = LocalTime.parse(item.time)
    val now = LocalDateTime.now()
    val first = if (item.repeatDays.isNotEmpty()) {
        (0..7).map { date.plusDays(it.toLong()).atTime(time) }
            .firstOrNull { it.isAfter(now) && item.repeatDays.contains(it.dayOfWeek.value) }
    } else {
        val base = date.atTime(time).minusMinutes(if (planner) item.leadMinutes.toLong() else 0L)
        base.takeIf { it.isAfter(now) }
    }
    if (first != null) {
        val millis = first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AlarmScheduler.schedule(context, alarmId(item, if (planner) 1 else 0), item.title,
            if (planner) "Upcoming task: ${item.detail.ifBlank { item.title }}" else item.detail.ifBlank { "Your reminder is due." },
            millis, item.repeatDays)
    }
}

@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(loadItems(context, "reminders")) }
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScheduleHeader("Reminders", "One-time alarms and weekly repeats")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${items.size} reminders", Modifier.weight(1f), color = ScheduleMuted)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New reminder") }
        }
        if (items.isEmpty()) ScheduleEmpty("No reminders yet. Create one and choose when it should ring.")
        else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                ScheduleCard(item, onDelete = {
                    AlarmScheduler.cancel(context, alarmId(item))
                    items = items.filterNot { it.id == item.id }; saveItems(context, "reminders", items)
                }, onToggle = { enabled ->
                    if (enabled) scheduleItem(context, item, false) else AlarmScheduler.cancel(context, alarmId(item))
                })
            }
        }
    }
    if (showAdd) ReminderDialog(onDismiss = { showAdd = false }, onSave = { item ->
        items = items + item; saveItems(context, "reminders", items); scheduleItem(context, item, false); showAdd = false
    })
}

@Composable
fun PlannerScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(loadItems(context, "planner")) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showAdd by remember { mutableStateOf(false) }
    val dayItems = items.filter { it.date == selectedDate.toString() }.sortedBy { it.time }
    Column(Modifier.fillMaxSize()) {
        ScheduleHeader("Planner", "Your calendar, tasks and deadlines")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selectedDate.format(displayDate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Select a date to view its schedule", color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                val c = Calendar.getInstance()
                c.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                DatePickerDialog(context, { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text("Calendar") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (-2..4).forEach { offset ->
                val date = LocalDate.now().plusDays(offset.toLong())
                val active = date == selectedDate
                Column(Modifier.weight(1f).background(if (active) ScheduleGreen else Color.White, RoundedCornerShape(12.dp))
                    .clickable { selectedDate = date }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.dayOfWeek.name.take(3), color = if (active) Color.White else ScheduleMuted, style = MaterialTheme.typography.labelSmall)
                    Text(date.dayOfMonth.toString(), color = if (active) Color.White else ScheduleForest, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Today's board", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text(" Add task") }
        }
        if (dayItems.isEmpty()) ScheduleEmpty("Nothing scheduled for this date. Add a task or event to your planner.")
        else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(dayItems, key = { it.id }) { item ->
                PlannerTaskCard(item, onComplete = {
                    items = items.map { if (it.id == item.id) it.copy(completed = !it.completed) else it }
                    saveItems(context, "planner", items)
                    if (item.completed.not()) AlarmScheduler.cancel(context, alarmId(item, 1))
                }, onDelete = {
                    AlarmScheduler.cancel(context, alarmId(item, 1))
                    items = items.filterNot { it.id == item.id }; saveItems(context, "planner", items)
                })
            }
        }
    }
    if (showAdd) PlannerDialog(initialDate = selectedDate, onDismiss = { showAdd = false }, onSave = { item ->
        items = items + item; saveItems(context, "planner", items); scheduleItem(context, item, true); showAdd = false
    })
}

@Composable
private fun ScheduleHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(ScheduleForest).padding(22.dp)) {
        Text("N I D A A", color = Color(0xFFB9DDCE), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFFD7E8DF), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScheduleEmpty(message: String) {
    Surface(Modifier.fillMaxWidth().padding(16.dp), color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Text(message, Modifier.padding(20.dp), color = ScheduleMuted)
    }
}

@Composable
private fun ScheduleCard(item: ScheduleItem, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    var enabled by remember(item.id) { mutableStateOf(true) }
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(LocalTime.parse(item.time).format(displayTime), color = ScheduleGreen, style = MaterialTheme.typography.titleLarge)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it; onToggle(it) })
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = ScheduleMuted) }
            }
            Text(if (item.repeatDays.isEmpty()) "One time · ${LocalDate.parse(item.date).format(displayDate)}" else "Repeats · " + ScheduleDays.filterIndexed { index, _ -> index + 1 in item.repeatDays }.joinToString(", "), color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
            if (item.detail.isNotBlank()) Text(item.detail, color = ScheduleMuted)
        }
    }
}

@Composable
private fun PlannerTaskCard(item: ScheduleItem, onComplete: () -> Unit, onDelete: () -> Unit) {
    val target = remember(item.id) { LocalDate.parse(item.date).atTime(LocalTime.parse(item.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val remaining = target - System.currentTimeMillis()
    val countdown = when {
        item.completed -> "Completed"
        remaining <= 0 -> "Due now / in progress"
        remaining < 3_600_000 -> "Starts in ${remaining / 60_000} min"
        else -> "Starts in ${remaining / 3_600_000} hr ${(remaining % 3_600_000) / 60_000} min"
    }
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onComplete) { Icon(Icons.Default.CheckCircle, "Complete", tint = if (item.completed) ScheduleGreen else ScheduleMuted) }
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text("${LocalTime.parse(item.time).format(displayTime)} · $countdown", color = ScheduleGreen, style = MaterialTheme.typography.bodySmall)
                if (item.detail.isNotBlank()) Text(item.detail, color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = ScheduleMuted) }
        }
    }
}

@Composable
private fun ReminderDialog(onDismiss: () -> Unit, onSave: (ScheduleItem) -> Unit) {
    ScheduleEditorDialog("New reminder", LocalDate.now(), false, onDismiss, onSave)
}

@Composable
private fun PlannerDialog(initialDate: LocalDate, onDismiss: () -> Unit, onSave: (ScheduleItem) -> Unit) {
    ScheduleEditorDialog("Add planner task", initialDate, true, onDismiss, onSave)
}

@Composable
private fun ScheduleEditorDialog(title: String, initialDate: LocalDate, planner: Boolean, onDismiss: () -> Unit, onSave: (ScheduleItem) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var weekly by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var lead by remember { mutableIntStateOf(15) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 680.dp).imePadding(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ScheduleForest)
                Text(if (planner) "Plan it. Set an alert. Get it done." else "Choose when Nidaa should remind you.", color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") }, singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    OutlinedTextField(
                        value = detail, onValueChange = { detail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (planner) "Task details (optional)" else "Description (optional)") },
                        minLines = 2, maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                    date.year, date.monthValue - 1, date.dayOfMonth).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text(date.format(DateTimeFormatter.ofPattern("d MMM"))) }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) },
                                    time.hour, time.minute, false).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) { Icon(Icons.Default.AccessTime, null); Spacer(Modifier.width(6.dp)); Text(time.format(displayTime)) }
                    }
                    if (planner) {
                        Text("Remind me", fontWeight = FontWeight.SemiBold, color = ScheduleForest)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 5, 15, 30, 60).forEach { value ->
                                FilterChip(
                                    selected = lead == value,
                                    onClick = { lead = value },
                                    label = { Text(if (value == 0) "At time" else "${value}m") },
                                    shape = CircleShape
                                )
                            }
                        }
                    } else {
                        Surface(color = Color(0xFFF1F6F2), shape = RoundedCornerShape(22.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Repeat every week", fontWeight = FontWeight.SemiBold, color = ScheduleForest)
                                    Text("Choose one or more days", color = ScheduleMuted, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(checked = weekly, onCheckedChange = { weekly = it; if (!it) days = emptySet() })
                            }
                        }
                        if (weekly) {
                            Text("Repeat on", fontWeight = FontWeight.SemiBold, color = ScheduleForest)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                ScheduleDays.take(4).forEachIndexed { index, day ->
                                    FilterChip(
                                        selected = index + 1 in days,
                                        onClick = { days = if (index + 1 in days) days - (index + 1) else days + (index + 1) },
                                        label = { Text(day) }, shape = CircleShape
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                ScheduleDays.drop(4).forEachIndexed { index, day ->
                                    val dayNumber = index + 5
                                    FilterChip(
                                        selected = dayNumber in days,
                                        onClick = { days = if (dayNumber in days) days - dayNumber else days + dayNumber },
                                        label = { Text(day) }, shape = CircleShape
                                    )
                                }
                            }
                            if (days.isNotEmpty()) Text(
                                "Repeats: " + ScheduleDays.filterIndexed { index, _ -> index + 1 in days }.joinToString(", "),
                                color = ScheduleGreen, style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = CircleShape) { Text("Cancel") }
                    Button(
                        onClick = {
                            val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                            onSave(ScheduleItem(id, name.trim(), detail.trim(), date.toString(), time.toString(),
                                if (weekly) days else emptySet(), if (planner) lead else 0))
                        },
                        enabled = name.isNotBlank() && (!weekly || days.isNotEmpty()),
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) { Text("Save") }
                }
            }
        }
    }
}
