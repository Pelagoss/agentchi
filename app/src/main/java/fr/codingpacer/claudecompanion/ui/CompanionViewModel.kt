package fr.codingpacer.claudecompanion.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.codingpacer.claudecompanion.CompanionApp
import fr.codingpacer.claudecompanion.data.ClaudeEvent
import fr.codingpacer.claudecompanion.data.CompanionSettings
import fr.codingpacer.claudecompanion.data.EventType
import fr.codingpacer.claudecompanion.data.VibrationPattern
import fr.codingpacer.claudecompanion.net.PairingCode
import fr.codingpacer.claudecompanion.pet.PetLogic
import fr.codingpacer.claudecompanion.pet.PetState
import fr.codingpacer.claudecompanion.push.Push
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    val knownAccounts: StateFlow<Set<String>> =
        store.knownAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setAccountEnabled(account: String, enabled: Boolean) = updateSettings {
        it.copy(mutedAccounts = if (enabled) it.mutedAccounts - account else it.mutedAccounts + account)
    }

    // --- Serveur et ordinateurs appairés ---

    /** État de l'écran « Ordinateurs et cloud ». [newCode] n'est affiché qu'une fois, à sa création. */
    data class PairingUi(
        val codes: List<PairingCode> = emptyList(),
        val newCode: PairingCode? = null,
        val busy: Boolean = false,
        val error: String? = null,
    )

    private val _pairing = MutableStateFlow(PairingUi())
    val pairing: StateFlow<PairingUi> = _pairing.asStateFlow()

    /** Change de serveur : l'enregistrement sur l'ancien ne vaut plus. */
    fun setServer(url: String) = viewModelScope.launch {
        store.updateSettings { it.copy(serverUrl = url.trim().trimEnd('/'), deviceId = "", deviceToken = "") }
        _pairing.value = PairingUi()
    }

    /** Enregistre la montre sur le serveur configuré, avec son jeton FCM. */
    fun connect() = serverCall {
        val settings = store.settings.first()
        val pushToken = Push.token()
        val device = app.api.register(settings.serverUrl, Build.MODEL ?: "Montre", pushToken)
        store.saveDevice(settings.serverUrl, device.id, device.token)
        if (settings.remoteAnswers) runCatching { app.api.setAnswers(store.settings.first(), true) }
        val codes = app.api.codes(store.settings.first())
        _pairing.update { it.copy(codes = codes) }
    }

    fun refreshCodes() = serverCall(requireConnected = true) {
        val codes = app.api.codes(store.settings.first())
        _pairing.update { it.copy(codes = codes) }
    }

    fun createCode(label: String) = serverCall(requireConnected = true) {
        val settings = store.settings.first()
        val code = app.api.createCode(settings, label)
        val codes = app.api.codes(settings)
        _pairing.update { it.copy(newCode = code, codes = codes) }
    }

    fun dismissNewCode() = _pairing.update { it.copy(newCode = null) }

    fun revokeCode(id: String) = serverCall(requireConnected = true) {
        val settings = store.settings.first()
        app.api.deleteCode(settings, id)
        val codes = app.api.codes(settings)
        _pairing.update { it.copy(codes = codes) }
    }

    /** Le serveur envoie un push de test : s'il arrive, toute la chaîne fonctionne. */
    fun testServer() = serverCall(requireConnected = true) {
        val settings = store.settings.first()
        app.api.setPushToken(settings, Push.token())
        app.api.test(settings)
    }

    /**
     * Retransmet le jeton FCM au serveur à chaque ouverture de l'appli, au cas où un
     * renouvellement (onNewToken) n'aurait pas pu être transmis. Silencieux en cas d'échec.
     */
    fun refreshPushToken() = viewModelScope.launch {
        val settings = store.settings.first()
        if (settings.isConnected && Push.isConfigured) runCatching { app.api.setPushToken(settings, Push.token()) }
        if (settings.isConnected) runCatching { app.api.setAnswers(settings, settings.remoteAnswers) }
    }

    /** Active les réponses depuis la montre, sur la montre et sur le serveur. [onError] : message à afficher. */
    fun setRemoteAnswers(enabled: Boolean, onError: (String) -> Unit) = viewModelScope.launch {
        store.updateSettings { it.copy(remoteAnswers = enabled) }
        val settings = store.settings.first()
        if (!settings.isConnected) {
            if (enabled) onError("Connecte d'abord la montre au serveur")
            store.updateSettings { it.copy(remoteAnswers = false) }
            return@launch
        }
        runCatching { app.api.setAnswers(settings, enabled) }.onFailure {
            store.updateSettings { s -> s.copy(remoteAnswers = !enabled) }
            onError(describe(it))
        }
    }

    /** Supprime la montre du serveur : tous ses codes sont révoqués. */
    fun disconnect() = serverCall(requireConnected = true) {
        runCatching { app.api.deleteDevice(store.settings.first()) }
        store.clearDevice()
        _pairing.value = PairingUi()
    }

    private fun serverCall(requireConnected: Boolean = false, block: suspend () -> Unit) = viewModelScope.launch {
        if (requireConnected && !store.settings.first().isConnected) return@launch
        _pairing.update { it.copy(busy = true, error = null) }
        val error = runCatching { block() }.exceptionOrNull()
        _pairing.update { it.copy(busy = false, error = error?.let { describe(it) }) }
    }

    private fun describe(error: Throwable): String = when (error) {
        is fr.codingpacer.claudecompanion.net.ServerException ->
            if (error.status == 401) "La montre n'est plus reconnue par le serveur : reconnecte-la." else error.message.orEmpty()
        is java.net.UnknownHostException -> "Serveur introuvable : vérifie l'adresse."
        is java.io.IOException -> "Serveur injoignable."
        is IllegalStateException -> error.message ?: "Push FCM indisponible"
        else -> error.message ?: "Erreur inattendue"
    }

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
