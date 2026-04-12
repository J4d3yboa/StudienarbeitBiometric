package com.example.studienarbeitbiometric.presentation

import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.studienarbeitbiometric.presentation.theme.StudienarbeitBiometricTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.composables.ProgressIndicatorSegment
import com.google.android.horologist.composables.SegmentedProgressIndicator
import services.ExerciseService

data class statusWithHeart(
    val status: ActivityStatus,
    val heart: Int
)

enum class ActivityStatus(val label: String, val color: Color, val progress: Float) {
    SEHR_GUT("Sehr gut", Color(0xFF4CAF50), 0.2f),
    GUT("Gut", Color(0xFF8BC34A), 0.4f),
    OK("Ok", Color(0xFFFFEB3B), 0.6f),
    NICHT_SO_GUT("Riskant", Color(0xFFFF9800), 0.8f),
    GARNICHT_GUT("Kritisch", Color(0xFFE53935), 1.0f)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setTurnScreenOn(true)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WearApp() {
    val context = LocalContext.current

    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.BODY_SENSORS,
            android.Manifest.permission.ACTIVITY_RECOGNITION,
            android.Manifest.permission.VIBRATE,
            "android.permission.health.READ_HEART_RATE",
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    )

    val healthConnectClient = remember {
        try {
            if (HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata") == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else null
        } catch (e: Exception) { null }
    }

    val hrvPermission = setOf(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { }

    LaunchedEffect(Unit) {
        permissionState.launchMultiplePermissionRequest()
        healthConnectClient?.let { client ->
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(hrvPermission)) {
                healthConnectPermissionLauncher.launch(hrvPermission)
            }
        }
    }

    StudienarbeitBiometricTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TimeText()
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "home") {
                composable("home") { Navigation(navController) }
                composable(
                    route = "activity/{activityId}",
                    arguments = listOf(navArgument("activityId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val activityId = backStackEntry.arguments?.getString("activityId")
                    Activity(navController, activityId)
                }
            }
        }
    }
}

@Composable
fun Activity(navController: NavController, activityId: String?, viewModel: SensorViewModel = viewModel()) {
    val status by viewModel.aggregatedStatus.collectAsStateWithLifecycle()
    var isStarted by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isCritical by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(isStarted) {
        if (isStarted) {
            viewModel.startDataCollection()
            val intent = Intent(context, ExerciseService::class.java).apply { action = "ACTION_START" }
            context.startForegroundService(intent)
        } else {
            viewModel.stopDataCollection()
            val intent = Intent(context, ExerciseService::class.java).apply { action = "ACTION_STOP" }
            context.startService(intent)
        }
    }

    LaunchedEffect(status.status) {
        if (status.status == ActivityStatus.GARNICHT_GUT && !isPaused) {
            isCritical = true
        }
    }

    LaunchedEffect(isCritical) {
        val activityContext = context as? ComponentActivity

        if (isCritical) {
            activityContext?.setTurnScreenOn(true)
            activityContext?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1500)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }

            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
                    audioManager.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                }

                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                ringtone.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            activityContext?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    when {
        !isStarted -> StartScreen(activityId = activityId, onStart = { isStarted = true })
        isCritical -> CriticalWarningScreen(
            onPause = { isCritical = false; isPaused = true },
            onStop = { isStarted = false; isCritical = false; navController.popBackStack() }
        )
        isPaused -> PauseScreen(
            currentStatus = status.status,
            onResume = { isPaused = false },
            onStop = { isStarted = false; isPaused = false; navController.popBackStack() }
        )
        else -> RunningScreen(
            activityId = activityId,
            currentStatus = status.status,
            heart = status.heart,
            onPause = { isPaused = true }
        )
    }
}

@Composable
fun StartScreen(activityId: String?, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = activityId?.uppercase() ?: "AKTIVITÄT",
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start", modifier = Modifier.size(32.dp))
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun RunningScreen(
    activityId: String?,
    currentStatus: ActivityStatus,
    heart: Int,
    onPause: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentStatus.label.uppercase(),
                style = MaterialTheme.typography.title3,
                color = currentStatus.color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = heart.toString(),
                    style = MaterialTheme.typography.display1,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
                Text(
                    text = " bpm",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPause,
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
            }
        }

        SegmentedProgressIndicator(
            progress = currentStatus.progress,
            modifier = Modifier.fillMaxSize(),
            trackSegments = listOf(
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.SEHR_GUT.color, trackColor = Color(0xFF222222)),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.GUT.color, trackColor = Color(0xFF222222)),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.OK.color, trackColor = Color(0xFF222222)),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.NICHT_SO_GUT.color, trackColor = Color(0xFF222222)),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.GARNICHT_GUT.color, trackColor = Color(0xFF222222))
            )
        )
    }
}

@Composable
fun CriticalWarningScreen(onPause: () -> Unit, onStop: () -> Unit) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Warnung",
                tint = ActivityStatus.GARNICHT_GUT.color,
                modifier = Modifier.size(48.dp)
            )
        }
        item {
            Text(
                "GEFAHR!",
                color = ActivityStatus.GARNICHT_GUT.color,
                style = MaterialTheme.typography.title2,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                "Sofort anhalten.",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            Chip(
                onClick = onPause,
                label = { Text("Pause einleiten") },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            Chip(
                onClick = onStop,
                label = { Text("Beenden") },
                colors = ChipDefaults.primaryChipColors(backgroundColor = ActivityStatus.GARNICHT_GUT.color),
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

@Composable
fun PauseScreen(currentStatus: ActivityStatus, onResume: () -> Unit, onStop: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val canResume = currentStatus.ordinal <= ActivityStatus.OK.ordinal

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text("PAUSE", style = MaterialTheme.typography.title1, color = Color(0xFFBB86FC))
        }
        item {
            Text(
                text = if (canResume) "Puls normalisiert" else "Puls zu hoch",
                color = if (canResume) Color.Gray else ActivityStatus.GARNICHT_GUT.color,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        if (canResume) {
            item {
                Chip(
                    onClick = onResume,
                    label = { Text("Weiter") },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Play") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = ActivityStatus.SEHR_GUT.color),
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            Chip(
                onClick = onStop,
                label = { Text("Beenden") },
                icon = { Icon(Icons.Filled.Stop, contentDescription = "Stop") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

@Composable
fun Navigation(navController: NavController) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Modus wählen",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            Chip(
                onClick = { navController.navigate("activity/auto") },
                label = { Text("Auto") },
                icon = { Icon(Icons.Filled.DirectionsCar, "Auto") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}