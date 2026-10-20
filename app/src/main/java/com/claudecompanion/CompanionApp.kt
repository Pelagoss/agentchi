package com.claudecompanion

import android.app.Application
import com.claudecompanion.data.CompanionStore
import com.claudecompanion.net.CompanionApi
import com.claudecompanion.notify.EventNotifier
import com.claudecompanion.push.Push
import com.claudecompanion.sync.EventProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class CompanionApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())

    lateinit var store: CompanionStore
        private set
    lateinit var notifier: EventNotifier
        private set
    lateinit var processor: EventProcessor
        private set
    val api by lazy { CompanionApi() }

    override fun onCreate() {
        super.onCreate()
        store = CompanionStore(this)
        notifier = EventNotifier(this).also { it.createChannels() }
        processor = EventProcessor(store, notifier)
        Push.init(this)
    }
}
