package com.claudecompanion.relay

import com.claudecompanion.CompanionApp
import com.claudecompanion.net.EventParser
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** Reçoit les notifications de l'appli Claude relayées par le téléphone. */
class PhoneRelayService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != RelayPaths.NOTIFICATION) return
        val n = runCatching {
            EventParser.json.decodeFromString(RelayedNotification.serializer(), String(event.data))
        }.getOrNull() ?: return
        // Appelé sur un thread d'arrière-plan : on peut attendre la fin du traitement.
        runBlocking { (application as CompanionApp).processor.processRelayed(PhoneEventMapper.toEvent(n)) }
    }
}
