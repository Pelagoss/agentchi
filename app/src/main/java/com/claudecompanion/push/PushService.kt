package com.claudecompanion.push

import com.claudecompanion.CompanionApp
import com.claudecompanion.net.EventParser
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Reçoit les événements poussés par le serveur Claude Companion via FCM. */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val event = EventParser.fromPush(message.data) ?: return
        // Appelé sur un thread d'arrière-plan : on peut attendre la fin du traitement.
        runBlocking { (application as CompanionApp).processor.process(event) }
    }

    /** Firebase renouvelle le jeton de temps en temps : on prévient le serveur. */
    override fun onNewToken(token: String) {
        val app = application as CompanionApp
        app.appScope.launch {
            val settings = app.store.settings.first()
            if (settings.isConnected) runCatching { app.api.setPushToken(settings, token) }
        }
    }
}
