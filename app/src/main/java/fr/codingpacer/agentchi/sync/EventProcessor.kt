package fr.codingpacer.agentchi.sync

import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.CompanionStore
import fr.codingpacer.agentchi.notify.EventNotifier
import fr.codingpacer.agentchi.pet.PetLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** Point d'entrée unique des événements reçus. */
class EventProcessor(
    private val store: CompanionStore,
    private val notifier: EventNotifier,
    private val responder: AskResponder,
    /** Mascotte ou questions changées : tuile et complication à redessiner. */
    private val onChange: () -> Unit = {},
) {
    private val mutex = Mutex()

    /** Événement reçu par push FCM. */
    suspend fun process(event: AgentEvent) = mutex.withLock {
        event.account?.let { store.addKnownAccount(it) }
        if (!store.settings.first().isFiltered(event)) {
            handle(event)
        } else {
            // Question qui ne sera pas affichée : l'ordinateur la pose sans attendre.
            event.ask?.let { responder.pass(it) }
        }
    }

    /** Le serveur retire une question restée sans réponse. */
    suspend fun askEnded(askId: String) = mutex.withLock { responder.expired(askId) }

    /** Simule un événement (bouton « tester » des paramètres). */
    suspend fun test(event: AgentEvent) = mutex.withLock { handle(event, record = false) }

    private suspend fun handle(event: AgentEvent, record: Boolean = true) {
        if (record && !store.addEvent(event)) return // déjà traité
        val now = System.currentTimeMillis()
        store.updatePet { PetLogic.onAgentEvent(it, event.type, now) }
        onChange()

        val settings = store.settings.first()
        if (event.type !in settings.enabledEvents) {
            event.ask?.let { responder.pass(it) }
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        notifier.notify(event, settings, muted = settings.isMuted(event.type, hour))
    }
}
