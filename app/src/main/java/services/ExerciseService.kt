package services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.HealthServices
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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
        // START_STICKY sorgt dafür, dass der Service neu startet, falls er abstürzt
        return START_STICKY
    }

    // --- Platzhalter für Ihre Logik ---

    fun startMonitoring() {
        lifecycleScope.launch {
            // Hier kommt später der Code für exerciseClient.startExerciseAsync() hin
            // Sobald das Training startet, müssen wir den Service "promoten":
            startForeground(1, createNotification())
        }
    }

    fun stopMonitoring() {
        lifecycleScope.launch {
            // exerciseClient.endExerciseAsync()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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
