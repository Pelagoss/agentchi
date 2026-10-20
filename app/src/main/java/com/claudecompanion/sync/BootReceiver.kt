package com.claudecompanion.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.claudecompanion.CompanionApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Relance la connexion instantanée après un redémarrage de la montre. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CompanionApp
        PollWorker.schedule(context)
        val instant = runBlocking { app.store.settings.first().instantMode }
        if (instant) InstantSyncService.start(context)
    }
}
