package fr.codingpacer.agentchi.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import fr.codingpacer.agentchi.data.AgentEvent

/**
 * Ouvre le lien d'une session cloud (claude.ai/code) dans le navigateur du téléphone appairé.
 * Passe par Wear OS lui-même : aucune appli compagnon n'est nécessaire sur le téléphone.
 */
object PhoneLink {
    fun open(context: Context, event: AgentEvent) {
        event.url?.let { open(context, it) }
    }

    fun open(context: Context, url: String) {
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
        intent.getStringExtra(EXTRA_URL)?.let { PhoneLink.open(context, it) }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, event: AgentEvent, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, OpenOnPhoneReceiver::class.java)
                .putExtra(EXTRA_URL, event.url)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
