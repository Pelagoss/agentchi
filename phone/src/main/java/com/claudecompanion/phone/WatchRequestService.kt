package com.claudecompanion.phone

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Reçoit les demandes de la montre : « ouvrir cette notification sur le téléphone ». */
class WatchRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == Paths.OPEN) Relay.open(this, String(event.data))
    }
}
