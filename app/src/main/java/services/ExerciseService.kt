package services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseType
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.studienarbeitbiometric.presentation.MainActivity
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class ExerciseService : LifecycleService() {

    private val binder = LocalBinder()
    private lateinit var exerciseClient: ExerciseClient
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): ExerciseService = this@ExerciseService
    }

    override fun onCreate() {
        super.onCreate()
        val healthClient = HealthServices.getClient(this)
        exerciseClient = healthClient.exerciseClient
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            "ACTION_START" -> startMonitoring()
            "ACTION_STOP" -> stopMonitoring()
        }
        return START_STICKY
    }

    fun startMonitoring() {
        val hasHeartRatePerm = checkSelfPermission("android.permission.health.READ_HEART_RATE") == PackageManager.PERMISSION_GRANTED
        val hasBodySensorsPerm = checkSelfPermission(android.Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

        // Wiederherstellung der exakten Logik: Abbruch erfolgt nur, wenn BEIDE Berechtigungen fehlen
        if (!hasHeartRatePerm && !hasBodySensorsPerm) {
            Log.e("ExerciseService", "ABBRUCH: Keine Berechtigungen vorhanden!")
            stopSelf()
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StudienarbeitBiometric::FatigueWakeLock")
        wakeLock?.acquire()

        if (Build.VERSION.SDK_INT >= 34) {
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
                val config = ExerciseConfig.builder(ExerciseType.WALKING)
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_PER_MINUTE))
                    .setIsAutoPauseAndResumeEnabled(false)
                    .build()

                exerciseClient.startExerciseAsync(config).await()
            } catch (e: SecurityException) {
                Log.e("ExerciseService", "SecurityException trotz Check: ${e.message}")
                releaseWakeLock()
                stopSelf()
            } catch (e: Exception) {
                Log.e("ExerciseService", "Allgemeiner Fehler beim Starten!", e)
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    fun stopMonitoring() {
        lifecycleScope.launch {
            try {
                exerciseClient.endExerciseAsync().await()
            } catch (e: Exception) {
                Log.w("ExerciseService", "Fehler beim Beenden (Kein aktives Training): ${e.message}")
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "exercise_channel")
            .setContentTitle("Training aktiv")
            .setContentText("Überwache Vitalwerte...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        val ongoingActivityStatus = Status.Builder()
            .addTemplate("Überwachung aktiv")
            .build()

        val ongoingActivity = OngoingActivity.Builder(
            this,
            1,
            builder
        )
            .setStaticIcon(android.R.drawable.ic_dialog_info)
            .setTouchIntent(pendingIntent)
            .setStatus(ongoingActivityStatus)
            .build()

        ongoingActivity.apply(this)

        return builder.build()
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