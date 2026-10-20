package com.claudecompanion.phone

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Lit les notifications de l'appli Claude et les relaie à la montre. */
class ClaudeNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Relay.isEnabled(this)) return
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val lastMessage = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            ?.messages?.lastOrNull()?.text?.toString()
        val text = lastMessage
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val relay = RelayPolicy.shouldRelay(
            packageName = sbn.packageName,
            ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            hasContent = !title.isNullOrBlank() || !text.isNullOrBlank(),
        )
        if (!relay) return

        val relayed = RelayedNotification(
            key = sbn.key,
            title = title,
            text = text,
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            channel = n.channelId,
            time = sbn.postTime,
        )
        Relay.remember(sbn.key, n.contentIntent)
        if (!Relay.markSent(relayed)) return
        scope.launch { Relay.send(applicationContext, relayed) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
