package fr.codingpacer.claudecompanion

import android.app.Application
import fr.codingpacer.claudecompanion.data.CompanionStore
import fr.codingpacer.claudecompanion.net.CompanionApi
import fr.codingpacer.claudecompanion.notify.EventNotifier
import fr.codingpacer.claudecompanion.push.Push
import fr.codingpacer.claudecompanion.sync.AskResponder
import fr.codingpacer.claudecompanion.sync.EventProcessor
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
    lateinit var responder: AskResponder
        private set
    val api by lazy { CompanionApi() }

    override fun onCreate() {
        super.onCreate()
        store = CompanionStore(this)
        notifier = EventNotifier(this).also { it.createChannels() }
        responder = AskResponder(this, store, { api }, notifier)
        processor = EventProcessor(store, notifier, responder)
        Push.init(this)
    }
}
