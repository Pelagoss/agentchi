package com.claudecompanion

import android.app.Application
import com.claudecompanion.data.CompanionStore
import com.claudecompanion.net.NtfyClient
import com.claudecompanion.notify.EventNotifier
import com.claudecompanion.sync.EventProcessor
import com.claudecompanion.sync.PollWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CompanionApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())

    lateinit var store: CompanionStore
        private set
    lateinit var notifier: EventNotifier
        private set
    lateinit var processor: EventProcessor
        private set
    val ntfy by lazy { NtfyClient() }

    override fun onCreate() {
        super.onCreate()
        store = CompanionStore(this)
        notifier = EventNotifier(this).also { it.createChannels() }
        processor = EventProcessor(store, notifier)
        appScope.launch { store.ensureTopic() }
        PollWorker.schedule(this)
    }
}
