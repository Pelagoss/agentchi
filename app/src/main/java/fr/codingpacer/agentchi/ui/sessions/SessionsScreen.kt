package fr.codingpacer.agentchi.ui.sessions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TitleCard
import fr.codingpacer.agentchi.data.SessionState
import fr.codingpacer.agentchi.data.Sessions
import fr.codingpacer.agentchi.notify.PhoneLink
import fr.codingpacer.agentchi.text.markdownAnnotated
import fr.codingpacer.agentchi.text.tr
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.ask.AskActivity

/** Les sessions des agents et leur état : au travail, en attente de réponse, terminées. */
@Composable
fun SessionsScreen(viewModel: CompanionViewModel) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val (_, nowMs) = viewModel.pet.collectAsStateWithLifecycle().value
    val now = nowMs / 1000
    val visible = Sessions.visible(sessions, now)
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(tr("Sessions en cours", "Active sessions")) } }
            if (visible.isEmpty()) {
                item {
                    Text(
                        tr("Aucune session active. Elles apparaissent ici dès qu'un agent démarre ou te répond.", "No active session. They show up here as soon as an agent starts or replies to you."),
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
            items(visible, key = { it.key }) { session ->
                // Question à laquelle répondre depuis la montre, si elle attend encore.
                val pending = session.askId?.let { id -> history.firstOrNull { it.ask?.id == id && it.isPendingAsk } }
                TitleCard(
                    onClick = {
                        when {
                            pending != null -> context.startActivity(AskActivity.intent(context, pending))
                            session.url != null -> PhoneLink.open(context, session.url)
                        }
                    },
                    title = { Text("${session.state.emoji} ${session.state.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    time = { Text(ago(now - session.since)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(session.origin, style = MaterialTheme.typography.caption3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        pending != null -> Text(tr("⌚ Toucher pour répondre", "⌚ Tap to reply"), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.primary)
                        session.url != null -> Text(tr("📱 Toucher pour ouvrir sur le téléphone", "📱 Tap to open on phone"), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.primary)
                    }
                    session.message?.let { Text(markdownAnnotated(it), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    if (session.state == SessionState.WORKING && now - session.lastEvent > 30 * 60) {
                        Text(tr("Sans nouvelles depuis ${ago(now - session.lastEvent)}", "No news for ${ago(now - session.lastEvent)}"), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** « 3 min », « 2 h », « à l'instant ». */
private fun ago(seconds: Long): String = when {
    seconds < 60 -> tr("à l'instant", "now")
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}
