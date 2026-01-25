/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.studienarbeitbiometric.presentation

import android.R.attr.type
import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.studienarbeitbiometric.R
import com.example.studienarbeitbiometric.presentation.theme.StudienarbeitBiometricTheme
//hinzugefügt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.compose.material.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.composables.ProgressIndicatorSegment
import com.google.android.horologist.composables.SegmentedProgressIndicator


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp("Android")
        }
    }
}

enum class ActivityStatus(val label: String, val color: Color, val progress: Float) {
    SEHR_GUT("Sehr gut", Color.Green, 0.2f),
    GUT("Gut", Color(0xFF8BC34A), 0.4f), // Helles Grün
    OK("Ok", Color.Yellow,0.6f),
    NICHT_SO_GUT("Nicht so gut", Color(0xFFFF9800),0.8f), // Orange
    GARNICHT_GUT("Garnicht gut", Color.Red,1.0f); // Kritischer Status
}

@Composable
fun WearApp(greetingName: String) {
    StudienarbeitBiometricTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.onBackground),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {Navigation(navController)}
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
fun MainMenu() {
    Text(text = "Meine Biometrie App")
}

@Composable
fun Activity(navController: NavController, activityId: String?,  viewModel: SensorViewModel = viewModel() ) {
    // 1. Zustands-Variablen
    val status by viewModel.aggregatedStatus.collectAsStateWithLifecycle()
    var isStarted by remember { mutableStateOf(false) } // NEU: Initial nicht gestartet
    var isPaused by remember { mutableStateOf(false) }
    var isCritical by remember { mutableStateOf(false) }

    LaunchedEffect(isStarted) {
        if (isStarted) {
            viewModel.startSimulation()
        } else {
            viewModel.stopSimulation()
        }
    }

    // Logik-Überwachung (bleibt gleich)
    LaunchedEffect(status) {
        if (status == ActivityStatus.GARNICHT_GUT) {
            isCritical = true
            isPaused = true
        }
    }

    // 2. Das UI-Gerüst mit erweiterter Weiche
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (!isStarted) {
            // --- 1. START SCREEN (Wird als erstes angezeigt) ---
            StartScreen(
                activityId = activityId,
                onStart = { isStarted = true }, // Wechselt in den Modus "Laufend"

            )
        }
        else if (isCritical) {
            // --- 2. WARN SCREEN (Hat Vorrang vor Pause) ---
            CriticalWarningScreen(
                onResume = {
                    isCritical = false
                    isPaused = false
                },
                onStop = { navController.popBackStack() }
            )
        }
        else if (isPaused) {
            // --- 3. PAUSE SCREEN ---
            PauseScreen(
                onResume = { isPaused = false },
                onStop = { navController.popBackStack() }
            )
        }
        else {
            // --- 4. RUNNING SCREEN (Normalbetrieb) ---
            RunningScreen(
                activityId = activityId,
                currentStatus = status,
                onPause = { isPaused = true },
                onSimulateBadStatus = {  }
            )
        }
    }
}

@Composable
fun StartScreen(
    activityId: String?,
    onStart: () -> Unit
    // onBack habe ich aus den Parametern entfernt, da der Button weg ist
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Nur der Text mit der ID
        Text(
            text = activityId ?: "Aktivität",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Nur der Start-Button
        Button(
            onClick = onStart
        ) {
            Text("Start")
        }
    }
}


@OptIn(ExperimentalHorologistApi::class)
@Composable
fun RunningScreen(
    activityId: String?,
    currentStatus: ActivityStatus,
    onPause: () -> Unit,
    onSimulateBadStatus: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Etwas Abstand zum Rand
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp) // Gleichmäßige, kleinere Abstände
        ) {
            // 1. Überschrift etwas kleiner


            // 2. Karte passt sich dem Inhalt an (keine feste Höhe mehr)

                    Text(
                        text = currentStatus.label,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color= currentStatus.color
                    )



            // 3. Buttons nebeneinander (Row) statt untereinander
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPause) {
                    Text("Pause")
                }

                // Debug Button (etwas kleinerer Text vielleicht, falls es eng wird)
                Button(
                    onClick = onSimulateBadStatus,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Test: Schlecht")
                }
            }
        }

        SegmentedProgressIndicator(
            progress = currentStatus.progress,
            modifier = Modifier.fillMaxSize(),
            trackSegments = listOf(
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.SEHR_GUT.color, trackColor = Color.DarkGray),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.GUT.color, trackColor = Color.DarkGray),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.OK.color, trackColor = Color.DarkGray),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.NICHT_SO_GUT.color, trackColor = Color.DarkGray),
                ProgressIndicatorSegment(weight = 1f, indicatorColor = ActivityStatus.GARNICHT_GUT.color, trackColor = Color.DarkGray))
        )
    }
}


@Composable
fun PauseScreen(onResume: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF9C4)), // Helles Gelb
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PAUSE", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("Tief durchatmen...", modifier = Modifier.padding(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onResume) { Text("Weiter") }
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Beenden") }
        }
    }
}

@Composable
fun CriticalWarningScreen(onResume: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Red),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WARNUNG!", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(
            "Werte sind kritisch. Sofort anhalten!",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red)
        ) {
            Text("Mir geht es wieder gut")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            Text("Not-Stopp")
        }
    }
}
@Composable
fun Navigation(navController: NavController) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Navigationsmenü")
        Spacer(modifier = Modifier.height(8.dp))
        Chip(
            onClick = { navController.navigate("activity/auto") },
            colors = ChipDefaults.chipColors(),
            border = ChipDefaults.chipBorder(),
        )
        {
            Row {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar, // "DirectionsCar" ist das Standard Auto Icon
                    contentDescription = "Auto Icon",
                    // Optional: Farbe anpassen
                    // tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp)) // Etwas Abstand
                Text("Auto")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Chip(
            onClick = { navController.navigate("activity/sport") },
            colors = ChipDefaults.chipColors(),
            border = ChipDefaults.chipBorder(),
        )
        {
            Row {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter, // "DirectionsCar" ist das Standard Auto Icon
                    contentDescription = "Hantel Icon",
                    // Optional: Farbe anpassen
                    // tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp)) // Etwas Abstand
                Text("Sport")
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}