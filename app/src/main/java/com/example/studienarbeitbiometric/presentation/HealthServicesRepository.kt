package com.example.studienarbeitbiometric.presentation

import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

class HealthServicesRepository(
    private val exerciseClient: ExerciseClient,
    private val externalScope: CoroutineScope
) {

    private val rawExerciseFlow: Flow<ExerciseUpdate> = callbackFlow {
        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                trySendBlocking(update)
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}

            override fun onRegistered() {}

            override fun onRegistrationFailed(throwable: Throwable) {
                close(throwable)
            }

            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
        }

        exerciseClient.setUpdateCallback(callback)

        awaitClose {
            exerciseClient.clearUpdateCallbackAsync(callback)
        }
    }

    val exerciseUpdateFlow: Flow<ExerciseUpdate> = rawExerciseFlow.shareIn(
        scope = externalScope,
        started = SharingStarted.WhileSubscribed(5000),
        replay = 1
    )
}