package com.claudecompanion.ui.alert

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.net.EventParser
import com.claudecompanion.notify.PhoneLink
import com.claudecompanion.pet.Mood
import com.claudecompanion.pet.Reaction
import com.claudecompanion.ui.pet.ClaudeMascot
import com.claudecompanion.ui.theme.ClaudeOrange
import com.claudecompanion.ui.theme.CompanionTheme
import androidx.core.app.NotificationManagerCompat

/** Alerte plein écran pour les événements urgents (autorisation, réponse attendue…). */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val event = intent.getStringExtra(EXTRA_EVENT)
            ?.let { runCatching { EventParser.json.decodeFromString(ClaudeEvent.serializer(), it) }.getOrNull() }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (event == null) {
            finish()
            return
        }
        setContent {
            CompanionTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    ClaudeMascot(mood = Mood.NEUTRAL, reaction = Reaction.ALERT, modifier = Modifier.size(80.dp, 60.dp))
                    Text(event.title, style = MaterialTheme.typography.title3, color = ClaudeOrange, textAlign = TextAlign.Center)
                    Text(event.origin, style = MaterialTheme.typography.caption3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        event.message,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        event.url?.let { url ->
                            Button(
                                onClick = {
                                    PhoneLink.open(this@AlertActivity, url)
                                    NotificationManagerCompat.from(this@AlertActivity).cancel(notificationId)
                                    finish()
                                },
                                colors = ButtonDefaults.secondaryButtonColors(),
                            ) { Text("📱") }
                        }
                        Button(onClick = {
                            NotificationManagerCompat.from(this@AlertActivity).cancel(notificationId)
                            finish()
                        }) { Text("OK") }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, event: ClaudeEvent, notificationId: Int): PendingIntent {
            val intent = Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_EVENT, EventParser.json.encodeToString(ClaudeEvent.serializer(), event))
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
