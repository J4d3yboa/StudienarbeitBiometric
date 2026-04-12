package com.example.studienarbeitbiometric.presentation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.pow

data class VitalData(
    val heartRate: Int = 0,
    val hrvRmssd: Double = 0.0,
    val realMovement: Int = 0
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val EMERGENCY_COOLDOWN_MS = 10000L
        private const val HRV_LOOKBACK_HOURS = 24L
        private const val CALIBRATION_PHASE_COUNT = 10
        private const val SLIDING_WINDOW_SIZE = 15
        private const val CRITICAL_STATE_THRESHOLD = 12

        private val EMERGENCY_HR_LOWER_RANGE = 1..40
        private const val EMERGENCY_HR_UPPER_LIMIT = 140

        private const val HRV_STRESS_THRESHOLD = 20.0
        private const val MOVEMENT_AGITATION = 100
        private const val MOVEMENT_MENTAL_LOAD = 20
        private const val MOVEMENT_SLEEP = 15
        private const val MOVEMENT_WAKE_CORRECTION_LOWER = 15
        private const val MOVEMENT_WAKE_CORRECTION_UPPER = 45
    }

    private val healthClient = HealthServices.getClient(application)
    private val exerciseClient = healthClient.exerciseClient
    private val repository = HealthServicesRepository(exerciseClient, viewModelScope)

    private val realHeartRateFlow: Flow<Int> = repository.exerciseUpdateFlow
        .mapNotNull { update ->
            val dataPoints = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            if (dataPoints.isNotEmpty()) dataPoints.last().value.toInt() else null
        }

    private val realMovementFlow: Flow<Int> = repository.exerciseUpdateFlow
        .mapNotNull { update ->
            val dataPoints = update.latestMetrics.getData(DataType.STEPS_PER_MINUTE)
            if (dataPoints.isNotEmpty()) dataPoints.last().value.toInt() else null
        }

    private val realHrvState = MutableStateFlow(0.0)
    val realHrvFlow: StateFlow<Double> = realHrvState

    private val _emergencyEventFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val emergencyEventFlow: SharedFlow<Unit> = _emergencyEventFlow.asSharedFlow()

    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            val status = HealthConnectClient.getSdkStatus(application, "com.google.android.apps.healthdata")
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(application)
            } else null
        } catch (e: Exception) {
            Log.w("SensorViewModel", "Health Connect Initialisierung fehlgeschlagen (Wear OS): ${e.message}")
            null
        }
    }

    private var baselineHeartRate: Int? = null
    private val initialHeartRates = mutableListOf<Int>()
    private var baselineVariance: Double = 0.0
    private val recentHeartRates = mutableListOf<Int>()

    private var activeCollectionJob: Job? = null
    private var lastEmergencyTime = 0L
    private var criticalStateCounter = 0

    val aggregatedStatus: StateFlow<statusWithHeart> = combine(
        realHeartRateFlow, realHrvFlow, realMovementFlow
    ) { hr, hrv, movement ->
        if (hr <= 0) return@combine statusWithHeart(ActivityStatus.SEHR_GUT, hr)
        calculateDriverFitnessStatus(hr, hrv, movement)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = statusWithHeart(ActivityStatus.SEHR_GUT, 0)
    )

    val vitalDataState: StateFlow<VitalData> = combine(
        realHeartRateFlow, realHrvFlow, realMovementFlow
    ) { hr, hrv, movement ->
        VitalData(hr, hrv, movement)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = VitalData(0, 0.0, 0)
    )

    fun startDataCollection() {
        activeCollectionJob?.cancel()
        criticalStateCounter = 0
        recentHeartRates.clear()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endTime = Instant.now()
                val startTime = endTime.minus(HRV_LOOKBACK_HOURS, ChronoUnit.HOURS)

                val response = healthConnectClient?.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateVariabilityRmssdRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )

                val latestRecord = response?.records?.maxByOrNull { it.time }
                realHrvState.value = latestRecord?.heartRateVariabilityMillis ?: 0.0

            } catch (e: Exception) {
                Log.e("SensorViewModel", "Fehler beim Abruf der HRV-Daten: ${e.message}")
                realHrvState.value = 0.0
            }
        }

        activeCollectionJob = viewModelScope.launch {
            aggregatedStatus.collect { state ->
                if (state.status == ActivityStatus.GARNICHT_GUT) {
                    criticalStateCounter++
                    if (criticalStateCounter >= CRITICAL_STATE_THRESHOLD) {
                        triggerEmergencyAlert()
                        criticalStateCounter = 0
                    }
                } else {
                    if (criticalStateCounter > 0) {
                        criticalStateCounter--
                    }
                }
            }
        }
    }

    fun stopDataCollection() {
        activeCollectionJob?.cancel()
        activeCollectionJob = null
        baselineHeartRate = null
        initialHeartRates.clear()
        recentHeartRates.clear()
        baselineVariance = 0.0
        criticalStateCounter = 0
    }

    private fun triggerEmergencyAlert() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmergencyTime < EMERGENCY_COOLDOWN_MS) return

        lastEmergencyTime = currentTime

        val context = getApplication<Application>()

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

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)

        _emergencyEventFlow.tryEmit(Unit)
        stopDataCollection()
    }

    private fun calculateDriverFitnessStatus(
        liveHr: Int,
        baselineHrv: Double,
        liveMovement: Int
    ): statusWithHeart {
        return if (liveHr in EMERGENCY_HR_LOWER_RANGE || liveHr > EMERGENCY_HR_UPPER_LIMIT) {
            calculateDriverFitnessStatusCore(liveHr, baselineHrv, liveMovement, extraRisk = 2)
        } else {
            calculateDriverFitnessStatusCore(liveHr, baselineHrv, liveMovement, extraRisk = 0)
        }
    }

    private fun calculateDriverFitnessStatusCore(
        liveHr: Int,
        baselineHrv: Double,
        liveMovement: Int,
        extraRisk: Int
    ): statusWithHeart {

        if (baselineHeartRate == null) {
            if (initialHeartRates.size < CALIBRATION_PHASE_COUNT) {
                if (liveHr > 0) initialHeartRates.add(liveHr)
                return statusWithHeart(ActivityStatus.SEHR_GUT, liveHr)
            } else {
                val mean = initialHeartRates.average()
                baselineHeartRate = mean.toInt()
                baselineVariance = initialHeartRates.map { (it - mean).pow(2) }.average()
            }
        }

        recentHeartRates.add(liveHr)
        if (recentHeartRates.size > SLIDING_WINDOW_SIZE) {
            recentHeartRates.removeAt(0)
        }
        val smoothedHr = recentHeartRates.average().toInt()

        val baseHr = baselineHeartRate ?: smoothedHr

        val deltaPercentage = if (baseHr > 0) ((smoothedHr - baseHr).toDouble() / baseHr) * 100.0 else 0.0

        val isChronicallyStressed = baselineHrv > 0.0 && baselineHrv < HRV_STRESS_THRESHOLD
        var riskScore = if (isChronicallyStressed) 2 else 1
        riskScore += extraRisk

        if (isChronicallyStressed) {
            when {
                deltaPercentage >= 35.0 -> riskScore += 3
                deltaPercentage in 20.0..34.9 -> riskScore += 2
                deltaPercentage in 10.0..19.9 -> riskScore += 1
                deltaPercentage <= -15.0 -> riskScore += 2
                deltaPercentage in -14.9..-10.0 -> riskScore += 1
            }
        } else {
            when {
                deltaPercentage >= 40.0 -> riskScore += 3
                deltaPercentage in 25.0..39.9 -> riskScore += 2
                deltaPercentage in 15.0..24.9 -> riskScore += 1
                deltaPercentage <= -15.0 -> riskScore += 2
                deltaPercentage in -14.9..-10.0 -> riskScore += 1
            }
        }

        if (riskScore >= 2) {
            if (deltaPercentage >= 20.0 && liveMovement > MOVEMENT_AGITATION) {
                riskScore -= 1
            } else if (deltaPercentage >= 20.0 && liveMovement < MOVEMENT_MENTAL_LOAD) {
                riskScore += 2
            } else if (deltaPercentage <= -10.0 && liveMovement < MOVEMENT_SLEEP) {
                riskScore += 2
            }
        }

        if (deltaPercentage in -9.9..-5.0 && liveMovement in MOVEMENT_WAKE_CORRECTION_LOWER..MOVEMENT_WAKE_CORRECTION_UPPER) {
            riskScore -= 1
        }
        if (deltaPercentage in 1.0..24.9 && liveMovement < MOVEMENT_SLEEP) {
            riskScore -= 1
        }

        val finalScore = riskScore.coerceIn(1, 5)
        return when (finalScore) {
            1 -> statusWithHeart(ActivityStatus.SEHR_GUT, liveHr)
            2 -> statusWithHeart(ActivityStatus.GUT, liveHr)
            3 -> statusWithHeart(ActivityStatus.OK, liveHr)
            4 -> statusWithHeart(ActivityStatus.NICHT_SO_GUT, liveHr)
            else -> statusWithHeart(ActivityStatus.GARNICHT_GUT, liveHr)
        }
    }
}