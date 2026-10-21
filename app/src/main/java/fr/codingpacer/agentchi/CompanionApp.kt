package fr.codingpacer.agentchi

import android.app.Application
import fr.codingpacer.agentchi.data.CompanionStore
import fr.codingpacer.agentchi.net.CompanionApi
import fr.codingpacer.agentchi.notify.EventNotifier
import fr.codingpacer.agentchi.notify.SystemQuiet
import fr.codingpacer.agentchi.push.Push
import fr.codingpacer.agentchi.sync.AskResponder
import fr.codingpacer.agentchi.sync.EventProcessor
import fr.codingpacer.agentchi.sync.Rewards
import fr.codingpacer.agentchi.widget.Widgets
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
    lateinit var rewards: Rewards
        private set
    val api by lazy { CompanionApi() }

    override fun onCreate() {
        super.onCreate()
        store = CompanionStore(this)
        notifier = EventNotifier(this).also { it.createChannels() }
        rewards = Rewards(store, notifier, onChange = { Widgets.refresh(this) })
        responder = AskResponder(this, store, { api }, notifier, rewards)
        processor = EventProcessor(
            store,
            notifier,
            responder,
            rewards,
            onChange = { Widgets.refresh(this) },
            systemQuiet = { SystemQuiet.isActive(this) },
        )
        Push.init(this)
    }
}
