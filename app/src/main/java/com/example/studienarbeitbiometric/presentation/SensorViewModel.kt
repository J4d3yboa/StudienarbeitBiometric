package com.example.studienarbeitbiometric.presentation
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Datenmodell für die rohen Sensordaten
data class VitalData(
    val heartRate: Int = 70,
    val stressLevel: Int = 10 // Skala 0-100
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Interne Flows für einzelne Sensordaten (Simuliert)
    private val _heartRate = MutableStateFlow(70)
    private val _stressLevel = MutableStateFlow(10)

    // 2. Kombinierter Flow: Berechnet den Score (1-5) immer wenn sich ein Wert ändert
    val aggregatedStatus: StateFlow<ActivityStatus> = combine(_heartRate, _stressLevel) { hr, stress ->
        calculateStatus(hr, stress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityStatus.SEHR_GUT
    )

    private var isMonitoring = false

    // Startet die Simulation von Sensordaten
    fun startSimulation() {
        if (isMonitoring) return
        isMonitoring = true
        viewModelScope.launch {
            while (isMonitoring) {
                // Simuliere schwankende Werte
                val newHr = (60..190).random()
                val newStress = (0..100).random()

                _heartRate.value = newHr
                _stressLevel.value = newStress

                delay(2000) // Alle 2 Sekunden neue Werte
            }
        }
    }

    fun stopSimulation() {
        isMonitoring = false
    }

    // 3. Die eigentliche Berechnungslogik (Gibt Status zurück)
    private fun calculateStatus(hr: Int, stress: Int): ActivityStatus {
        // Einfache Logik: Je höher Puls & Stress, desto schlechter der Status (1 bis 5)
        // Score Berechnung (Beispiel):
        // Puls > 170 -> +3 Punkte
        // Puls > 140 -> +2 Punkte
        // Stress > 80 -> +2 Punkte
        // Stress > 50 -> +1 Punkt

        var score = 1 // Basiswert (Sehr gut)

        if (hr > 170) score += 3
        else if (hr > 140) score += 2
        else if (hr > 110) score += 1

        if (stress > 80) score += 2
        else if (stress > 50) score += 1

        // Begrenzen auf 1-5
        val finalScore = score.coerceIn(1, 5)

        // Mapping von Zahl (1-5) auf Enum
        return when (finalScore) {
            1 -> ActivityStatus.SEHR_GUT
            2 -> ActivityStatus.GUT
            3 -> ActivityStatus.OK
            4 -> ActivityStatus.NICHT_SO_GUT
            else -> ActivityStatus.GARNICHT_GUT
        }
    }
}
