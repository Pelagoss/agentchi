package com.claudecompanion.sync

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.claudecompanion.CompanionApp
import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.notify.EventNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Mode instantané : garde une connexion ouverte avec ntfy pour recevoir
 * les événements en quelques secondes. Consomme plus de batterie.
 */
class InstantSyncService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val app = application as CompanionApp
        ServiceCompat.startForeground(
            this,
            EventNotifier.SERVICE_NOTIFICATION_ID,
            app.notifier.serviceNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            },
        )

        lifecycleScope.launch {
            app.store.settings
                .map { Triple(it.instantMode, it, it.serverUrl + it.topic + it.token) }
                .distinctUntilChanged { a, b -> a.first == b.first && a.third == b.third }
                .collectLatest { (instant, settings, _) ->
                    if (!instant || !CompanionSettings.TOPIC_REGEX.matches(settings.topic)) {
                        stopSelf()
                        return@collectLatest
                    }
                    var backoff = 2_000L
                    while (true) {
                        runCatching {
                            app.ntfy.stream(settings, app.store.since()) { message ->
                                backoff = 2_000L
                                app.processor.process(message)
                            }
                        }
                        delay(backoff)
                        backoff = (backoff * 2).coerceAtMost(60_000L)
                    }
                }
        }
    }

    companion object {
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, InstantSyncService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, InstantSyncService::class.java))
    }
}
