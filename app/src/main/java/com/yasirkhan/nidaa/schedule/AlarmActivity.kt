package com.yasirkhan.nidaa.schedule

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasirkhan.nidaa.data.SalahTracker
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )

        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: "Prayer Reminder"
        val description = intent.getStringExtra("description") ?: "Time for prayer."
        val soundUriStr = intent.getStringExtra("soundUri")
        val isFajr = intent.getBooleanExtra("isFajr", false) || title.contains("Fajr", ignoreCase = true)
        val prayerName = intent.getStringExtra("prayerName")
            ?: listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").firstOrNull { title.contains(it, ignoreCase = true) }
        val jamaatInfo = intent.getStringExtra("jamaatInfo")
        val soundMode = intent.getStringExtra("soundMode") ?: if (isFajr) "LOUDEST" else "NORMAL"
        val soundTone = intent.getStringExtra("soundTone") ?: "Adhan Makkah"

        startAlarmSoundAndVibration(soundUriStr, soundMode)

        setContent {
            AlarmScreen(
                title = title,
                description = description,
                soundMode = soundMode,
                soundTone = soundTone,
                prayerName = prayerName,
                jamaatInfo = jamaatInfo,
                onPrayed = {
                    prayerName?.let { name ->
                        SalahTracker.setPrayerStatus(this@AlarmActivity, name, prayed = true)
                    }
                    stopAndFinish()
                },
                onSnooze = {
                    snoozeAlarm(id, title, description, soundUriStr, isFajr, prayerName, jamaatInfo, soundMode, soundTone)
                    stopAndFinish()
                },
                onStop = {
                    stopAndFinish()
                }
            )
        }

        // Auto-stop after 2 minutes (120 seconds) if unaddressed
        countDownTimer = object : CountDownTimer(120_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                // Not stopped/prayed by user -> stays recorded as not prayed
                stopAndFinish()
            }
        }.start()
    }

    private fun startAlarmSoundAndVibration(soundUriStr: String?, soundMode: String) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        when (soundMode) {
            "LOUDEST" -> {
                try {
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                } catch (_: Exception) {}
            }
            "QUIET" -> {
                try {
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    val gentleVolume = (maxVolume * 0.3f).toInt().coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, gentleVolume, 0)
                } catch (_: Exception) {}
            }
        }

        val shouldPlaySound = soundMode !in listOf("VIBRATE", "OFF") && soundUriStr != "silent"

        if (shouldPlaySound) {
            try {
                val soundUri = soundUriStr?.let(Uri::parse)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmActivity, soundUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                try {
                    val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    mediaPlayer = MediaPlayer.create(this, fallbackUri)?.apply {
                        isLooping = true
                        start()
                    }
                } catch (_: Exception) {}
            }
        }

        // Vibration
        if (soundMode != "OFF") {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }

                val pattern = if (soundMode == "QUIET") {
                    longArrayOf(0, 400, 800, 400, 800)
                } else {
                    longArrayOf(0, 1000, 500, 1000, 500)
                }
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } catch (_: Exception) {}
        }
    }

    private fun snoozeAlarm(
        id: Int, title: String, description: String, soundUriStr: String?,
        isFajr: Boolean, prayerName: String?, jamaatInfo: String?,
        soundMode: String, soundTone: String
    ) {
        val snoozeMillis = System.currentTimeMillis() + (5 * 60 * 1000)
        AlarmScheduler.schedule(
            context = this,
            id = id + 10000,
            title = "$title (Snoozed)",
            description = description,
            triggerAt = snoozeMillis,
            repeatDays = emptySet(),
            soundUri = soundUriStr,
            isFajr = isFajr,
            prayerName = prayerName,
            jamaatInfo = jamaatInfo,
            soundMode = soundMode,
            soundTone = soundTone,
        )
    }

    private fun stopAndFinish() {
        try {
            countDownTimer?.cancel()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (_: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAndFinish()
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    description: String,
    soundMode: String,
    soundTone: String,
    prayerName: String?,
    jamaatInfo: String?,
    onPrayed: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    val currentTime = remember {
        LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }
    var remainingSeconds by remember { mutableIntStateOf(120) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(1.seconds)
            remainingSeconds--
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF04261E) // Deep Forest Green
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF087A5B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))
                Surface(
                    color = when (soundMode) {
                        "LOUDEST" -> Color(0xFFD4AF37)
                        "QUIET" -> Color(0xFF818CF8)
                        "VIBRATE" -> Color(0xFF6EE7B7)
                        else -> Color(0xFF10B981)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (soundMode) {
                            "LOUDEST" -> "🔊 LOUDEST (OVERRIDES SILENT) • $soundTone"
                            "QUIET" -> "🍃 GENTLE QUIET • $soundTone"
                            "VIBRATE" -> "📳 VIBRATE ONLY"
                            "OFF" -> "🔕 SILENT"
                            else -> "🔔 PHONE VOLUME • $soundTone"
                        },
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Message Card
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF07382D),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center
                    )

                    if (!jamaatInfo.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFFE8F7F0),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = jamaatInfo,
                                color = Color(0xFF047857),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Ringing for 2 min • Stops in ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (prayerName != null) {
                    Button(
                        onClick = onPrayed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("I Have Prayed ($prayerName)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Icon(Icons.Default.Snooze, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Snooze (5m)")
                    }

                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}
