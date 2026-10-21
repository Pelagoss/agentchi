package fr.codingpacer.claudecompanion.sync

import fr.codingpacer.claudecompanion.data.ClaudeEvent
import fr.codingpacer.claudecompanion.data.CompanionStore
import fr.codingpacer.claudecompanion.notify.EventNotifier
import fr.codingpacer.claudecompanion.pet.PetLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** Point d'entrée unique des événements reçus. */
class EventProcessor(
    private val store: CompanionStore,
    private val notifier: EventNotifier,
    private val responder: AskResponder,
) {
    private val mutex = Mutex()

    /** Événement reçu par push FCM. */
    suspend fun process(event: ClaudeEvent) = mutex.withLock {
        event.account?.let { store.addKnownAccount(it) }
        if (!store.settings.first().isFiltered(event)) {
            handle(event)
        } else {
            // Question qui ne sera pas affichée : l'ordinateur la pose sans attendre.
            event.ask?.let { responder.pass(it) }
        }
    }

    /** Le serveur retire une question restée sans réponse. */
    fun askEnded(askId: String) = responder.end(askId)

    /** Simule un événement (bouton « tester » des paramètres). */
    suspend fun test(event: ClaudeEvent) = mutex.withLock { handle(event, record = false) }

    private suspend fun handle(event: ClaudeEvent, record: Boolean = true) {
        if (record && !store.addEvent(event)) return // déjà traité
        val now = System.currentTimeMillis()
        store.updatePet { PetLogic.onClaudeEvent(it, event.type, now) }

        val settings = store.settings.first()
        if (event.type !in settings.enabledEvents) {
            event.ask?.let { responder.pass(it) }
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        notifier.notify(event, settings, muted = settings.isMuted(event.type, hour))
    }
}
