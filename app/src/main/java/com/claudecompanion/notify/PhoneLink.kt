package com.claudecompanion.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.relay.RelayPaths
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Ouvre un événement sur le téléphone appairé. */
object PhoneLink {
    fun open(context: Context, event: ClaudeEvent) = open(context, event.url, event.phoneKey)

    fun open(context: Context, url: String?, phoneKey: String?) {
        when {
            phoneKey != null -> openNotification(context, phoneKey)
            url != null -> openUrl(context, url)
        }
    }

    /** Demande au téléphone d'ouvrir la notification d'origine de l'appli Claude. */
    private fun openNotification(context: Context, key: String) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching {
                val nodes = Wearable.getNodeClient(app).connectedNodes.await()
                nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, RelayPaths.OPEN, key.toByteArray()).await() }
                nodes.isNotEmpty()
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(app, if (ok) "📱 Ouvert sur le téléphone" else "Téléphone injoignable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Ouvre un lien (session claude.ai/code) dans le navigateur du téléphone. */
    private fun openUrl(context: Context, url: String) {
        val app = context.applicationContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val future = RemoteActivityHelper(app).startRemoteActivity(intent)
        future.addListener(
            {
                val ok = runCatching { future.get() }.isSuccess
                Toast.makeText(app, if (ok) "📱 Ouvert sur le téléphone" else "Téléphone injoignable", Toast.LENGTH_SHORT).show()
            },
            ContextCompat.getMainExecutor(app),
        )
    }
}

/** Action « Ouvrir sur le téléphone » des notifications. */
class OpenOnPhoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PhoneLink.open(context, intent.getStringExtra(EXTRA_URL), intent.getStringExtra(EXTRA_PHONE_KEY))
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_PHONE_KEY = "phone_key"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, event: ClaudeEvent, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, OpenOnPhoneReceiver::class.java)
                .putExtra(EXTRA_URL, event.url)
                .putExtra(EXTRA_PHONE_KEY, event.phoneKey)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
