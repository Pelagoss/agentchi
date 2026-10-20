package com.claudecompanion.sync

import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.CompanionStore
import com.claudecompanion.net.EventParser
import com.claudecompanion.net.NtfyMessage
import com.claudecompanion.notify.EventNotifier
import com.claudecompanion.pet.PetLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** Point d'entrée unique des messages reçus (sondage ou flux instantané). */
class EventProcessor(
    private val store: CompanionStore,
    private val notifier: EventNotifier,
) {
    private val mutex = Mutex()

    suspend fun process(messages: List<NtfyMessage>) = mutex.withLock {
        val settings = store.settings.first()
        messages.sortedBy { it.time }.forEach { msg ->
            // Message illisible (version du serveur plus récente…) : ignoré.
            val event = EventParser.toEvent(msg)
            event?.account?.let { store.addKnownAccount(it) }
            if (event == null || settings.isFiltered(event)) {
                store.setSince(msg.id)
            } else {
                handle(event)
            }
        }
    }

    suspend fun process(message: NtfyMessage) = process(listOf(message))

    /** Notification de l'appli Claude relayée par le téléphone. */
    suspend fun processRelayed(event: ClaudeEvent) = mutex.withLock {
        if (!store.settings.first().isFiltered(event)) handle(event)
    }

    /** Simule un événement (bouton « tester » des paramètres). */
    suspend fun test(event: ClaudeEvent) = mutex.withLock { handle(event, record = false) }

    private suspend fun handle(event: ClaudeEvent, record: Boolean = true) {
        if (record && !store.addEvent(event)) return // déjà traité
        val now = System.currentTimeMillis()
        store.updatePet { PetLogic.onClaudeEvent(it, event.type, now) }

        val settings = store.settings.first()
        if (event.type !in settings.enabledEvents) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        notifier.notify(event, settings, muted = settings.isMuted(event.type, hour))
    }
}
