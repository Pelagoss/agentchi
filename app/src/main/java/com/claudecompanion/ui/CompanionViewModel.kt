package com.claudecompanion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecompanion.CompanionApp
import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.EventType
import com.claudecompanion.data.VibrationPattern
import com.claudecompanion.pet.PetLogic
import com.claudecompanion.pet.PetState
import com.claudecompanion.sync.InstantSyncService
import com.claudecompanion.sync.PollWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CompanionApp
    private val store = app.store

    /** Horloge qui fait avancer les jauges pendant que l'appli est ouverte. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val pet: StateFlow<Pair<PetState, Long>> = combine(store.pet, clock) { pet, now -> PetLogic.tick(pet, now) to now }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetState() to System.currentTimeMillis())

    val settings: StateFlow<CompanionSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanionSettings())

    val history: StateFlow<List<ClaudeEvent>> =
        store.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun feed() = updatePet { s, now -> PetLogic.feed(s, now) }
    fun play() = updatePet { s, now -> PetLogic.play(s, now) }
    fun toggleSleep() = updatePet { s, now -> PetLogic.toggleSleep(s, now) }
    fun caress() = updatePet { s, now -> PetLogic.pet(s, now) }
    fun rename(name: String) = updatePet { s, _ -> s.copy(name = name.take(16)) }

    private fun updatePet(transform: (PetState, Long) -> PetState) {
        viewModelScope.launch { store.updatePet { transform(it, System.currentTimeMillis()) } }
    }

    fun updateSettings(transform: (CompanionSettings) -> CompanionSettings) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    fun setInstantMode(enabled: Boolean) {
        updateSettings { it.copy(instantMode = enabled) }
        if (enabled) InstantSyncService.start(app) else InstantSyncService.stop(app)
    }

    fun syncNow() = PollWorker.syncNow(app)

    fun previewVibration(pattern: VibrationPattern) = app.notifier.vibrate(pattern)

    fun clearHistory() {
        viewModelScope.launch { store.clearHistory() }
    }

    fun sendTest(type: EventType) {
        val event = ClaudeEvent(
            id = "test-${System.currentTimeMillis()}",
            type = type,
            message = when (type) {
                EventType.PERMISSION -> "Claude veut exécuter : npm test"
                EventType.INPUT_NEEDED -> "Claude attend ta réponse pour continuer."
                else -> "J'ai fini de refactorer le module d'authentification."
            },
            project = "demo",
            time = System.currentTimeMillis() / 1000,
        )
        viewModelScope.launch { app.processor.test(event) }
    }
}
