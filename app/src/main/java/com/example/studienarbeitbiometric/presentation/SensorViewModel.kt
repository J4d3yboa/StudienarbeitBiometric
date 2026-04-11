package com.example.studienarbeitbiometric.presentation

import android.app.Application
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
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

data class VitalData(
    val heartRate: Int = 0,
    val hrvRmssd: Double = 0.0,
    val realMovement: Int = 0
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Zeitliche Konstanten
        private const val EMERGENCY_COOLDOWN_MS = 10000L
        private const val HRV_LOOKBACK_HOURS = 24L

        // Kalibrierung
        private const val CALIBRATION_PHASE_COUNT = 10

        // --- NEU: Konstante für die Karenzzeit ---
        private const val CRITICAL_STATE_THRESHOLD = 5

        // Algorithmus: Hardware / Physiologie-Toleranz
        private val EMERGENCY_HR_LOWER_RANGE = 1..40
        private const val EMERGENCY_HR_UPPER_LIMIT = 140

        // Algorithmus: HRV Schwellen
        private const val HRV_STRESS_THRESHOLD = 20.0

        // Algorithmus: Bewegungsschwellen
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

    private val _realHrvState = MutableStateFlow(0.0)
    val realHrvFlow: StateFlow<Double> = _realHrvState

    // Clean Architecture: ViewModel triggert nur ein Event, Activity führt UI/Vibration aus
    private val _emergencyEventFlow = MutableSharedFlow<Unit>()
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

    // Baseline‑Kalibrierung
    private var baselineHeartRate: Int? = null
    private val initialHeartRates = mutableListOf<Int>()

    // Notfall‑Steuerung
    private var activeCollectionJob: Job? = null
    private var lastEmergencyTime = 0L

    // --- NEU: Zähler für die Karenzzeit ---
    private var criticalStateCounter = 0

    val aggregatedStatus: StateFlow<statusWithHeart> = combine(
        realHeartRateFlow, realHrvFlow, realMovementFlow
    ) { hr, hrv, movement ->
        if (hr == 0) return@combine statusWithHeart(ActivityStatus.SEHR_GUT, hr)
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
        // Bestehenden Job sicher beenden, bevor ein neuer gestartet wird
        activeCollectionJob?.cancel()

        // Zähler beim Neustart zurücksetzen
        criticalStateCounter = 0

        // DB Abfragen zwingend auf Dispatchers.IO ausführen
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
                _realHrvState.value = latestRecord?.heartRateVariabilityMillis ?: 0.0

            } catch (e: Exception) {
                Log.e("SensorViewModel", "Fehler beim Abruf der HRV-Daten: ${e.message}")
                _realHrvState.value = 0.0
            }
        }

        activeCollectionJob = viewModelScope.launch {
            aggregatedStatus.collect { state ->
                // --- NEU: Implementierung der Karenzzeit ---
                if (state.status == ActivityStatus.GARNICHT_GUT) {
                    criticalStateCounter++
                    if (criticalStateCounter >= CRITICAL_STATE_THRESHOLD) {
                        triggerEmergencyAlert()
                        criticalStateCounter = 0 // Reset nach Auslösung
                    }
                } else {
                    // Zähler bei besserem Zustand schrittweise reduzieren
                    if (criticalStateCounter > 0) criticalStateCounter--
                }
            }
        }
    }

    fun stopDataCollection() {
        activeCollectionJob?.cancel()
        activeCollectionJob = null
        baselineHeartRate = null
        initialHeartRates.clear()
        criticalStateCounter = 0 // Zähler beim Stoppen zurücksetzen
    }

    private fun triggerEmergencyAlert() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmergencyTime < EMERGENCY_COOLDOWN_MS) return
        lastEmergencyTime = currentTime

        stopDataCollection()

        // Statt direkter Systemaufrufe wird ein Event emittiert.
        // Die Activity/das Fragment muss diesen Flow abonnieren und die UI-Action ausführen.
        viewModelScope.launch {
            _emergencyEventFlow.emit(Unit)
        }
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

        // Phase 1: BASELINE & INITIALRISIKO
        if (baselineHeartRate == null) {
            if (initialHeartRates.size < CALIBRATION_PHASE_COUNT) {
                if (liveHr > 0) initialHeartRates.add(liveHr)
                return statusWithHeart(ActivityStatus.SEHR_GUT, liveHr)
            } else {
                baselineHeartRate = initialHeartRates.average().toInt()
            }
        }

        val baseHr = baselineHeartRate ?: liveHr
        val deltaHR = liveHr - baseHr

        val isChronicallyStressed = baselineHrv > 0.0 && baselineHrv < HRV_STRESS_THRESHOLD
        var riskScore = if (isChronicallyStressed) 2 else 1

        riskScore += extraRisk

        // Phase 2: AKUTE PULS-ABWEICHUNG
        if (isChronicallyStressed) {
            when {
                deltaHR >= 35 -> riskScore += 3
                deltaHR in 20..34 -> riskScore += 2
                deltaHR in 10..19 -> riskScore += 1
                deltaHR <= -15 -> riskScore += 2
                deltaHR in -14..-10 -> riskScore += 1
            }
        } else {
            when {
                deltaHR >= 40 -> riskScore += 3
                deltaHR in 25..39 -> riskScore += 2
                deltaHR in 15..24 -> riskScore += 1
                deltaHR <= -15 -> riskScore += 2
                deltaHR in -14..-10 -> riskScore += 1
            }
        }

        // Phase 3: KINEMATISCHE VERHALTENSVALIDIERUNG
        if (riskScore >= 2) {
            if (deltaHR >= 20 && liveMovement > MOVEMENT_AGITATION) {
                riskScore += 1
            } else if (deltaHR >= 20 && liveMovement < MOVEMENT_MENTAL_LOAD) {
                riskScore += 2
            } else if (deltaHR <= -10 && liveMovement < MOVEMENT_SLEEP) {
                riskScore += 2
            }
        }

        // Phase 4: ZUSATZ-KORREKTUREN
        if (deltaHR in -9..-5 && liveMovement in MOVEMENT_WAKE_CORRECTION_LOWER..MOVEMENT_WAKE_CORRECTION_UPPER) {
            riskScore -= 1
        }

        if (deltaHR in 1..24 && liveMovement < MOVEMENT_SLEEP) {
            riskScore -= 1
        }

        // Phase 5: MAPPING
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
