/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.studienarbeitbiometric.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.studienarbeitbiometric.R
import com.example.studienarbeitbiometric.presentation.theme.StudienarbeitBiometricTheme
//hinzugefügt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.wear.compose.material.Icon

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

@Composable
fun WearApp(greetingName: String) {
    StudienarbeitBiometricTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {Navigation(navController)}
                composable("auto") {Auto(navController)}
            }
        }
    }
}

@Composable
fun MainMenu() {
    Text(text = "Meine Biometrie App")
}

@Composable
fun Auto(navController: NavController) {
    Text(text = "Ich bin ein Auto")
}

@Composable
fun Navigation(navController: NavController) {
    Column {
        Chip(
            onClick = { navController.navigate("auto") },
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
            onClick = { println("Sport") },
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
                Text("Auto")
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}