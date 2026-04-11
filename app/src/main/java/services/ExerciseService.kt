package services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseType
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import android.content.pm.PackageManager

class ExerciseService : LifecycleService() {

    // Verbindungskomponenten
    private val binder = LocalBinder()

    // Health Services Komponenten
    private lateinit var exerciseClient: ExerciseClient

    inner class LocalBinder : Binder() {
        // Dies gibt der Activity Zugriff auf den laufenden Service
        fun getService(): ExerciseService = this@ExerciseService
    }

    override fun onCreate() {
        super.onCreate()
        // Initialisierung der Health Services
        val healthClient = HealthServices.getClient(this)
        exerciseClient = healthClient.exerciseClient

        // Kanal für die Benachrichtigung erstellen (Pflicht für Foreground Services)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent) // Wichtig für LifecycleService!
        return binder
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "ACTION_START" -> {
                startMonitoring() // Ihre Funktion, die exerciseClient.startExerciseAsync aufruft
            }
            "ACTION_STOP" -> {
                stopMonitoring() // Ihre Funktion, die stoppt und stopSelf() ruft
            }
        }

        return START_STICKY
    }


    // --- Platzhalter für Ihre Logik ---

    fun startMonitoring() {
        Log.d("ExerciseService", "Versuche Training zu starten...")

        // 1. SICHERHEITS-CHECK VOR DEM START
        val hasHeartRatePerm = checkSelfPermission("android.permission.health.READ_HEART_RATE") == PackageManager.PERMISSION_GRANTED
        val hasBodySensorsPerm = checkSelfPermission(android.Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

        if (!hasHeartRatePerm && !hasBodySensorsPerm) {
            Log.e("ExerciseService", "ABBRUCH: Keine Berechtigungen vorhanden!")
            stopSelf() // Service sofort beenden
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(1, createNotification())
        }


        lifecycleScope.launch {
            try {
                // Konfiguration um den Datentyp für RMSSD (Herzratenvariabilität) erweitert
                // FIX: WALKING erlaubt STEPS_PER_MINUTE und ist empfindlich genug,
                // um die Vibrationen am Autolenkrad zu registrieren.
                val config = ExerciseConfig.builder(ExerciseType.WALKING)
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_PER_MINUTE))
                    .setIsAutoPauseAndResumeEnabled(false)
                    .build()




                Log.d("ExerciseService", "Rufe startExerciseAsync auf...")

                exerciseClient.startExerciseAsync(config).await()

                Log.d("ExerciseService", "Training ERFOLGREICH gestartet!")

            } catch (e: SecurityException) {
                // Spezifischer Catch für Permissions
                Log.e("ExerciseService", "SecurityException trotz Check: ${e.message}")
                stopSelf()
            } catch (e: Exception) {
                Log.e("ExerciseService", "Allgemeiner Fehler beim Starten!", e)
                stopSelf()
            }
        }
    }



    fun stopMonitoring() {
        lifecycleScope.launch {
            try {
                // Versuche das Training offiziell zu beenden
                exerciseClient.endExerciseAsync().await()
                Log.d("ExerciseService", "Training erfolgreich beendet.")
            } catch (e: Exception) {
                // Fange die HealthServicesException ab, falls gar kein Training aktiv war
                Log.w("ExerciseService", "Fehler beim Beenden (Kein aktives Training): ${e.message}")
            } finally {
                // Service in jedem Fall zuverlässig stoppen
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }



    // --- Hilfsfunktionen für Benachrichtigungen ---

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "exercise_channel")
            .setContentTitle("Training aktiv")
            .setContentText("Überwache Vitalwerte...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Ersetzen Sie dies durch Ihr App-Icon
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "exercise_channel",
            "Training Überwachung",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
