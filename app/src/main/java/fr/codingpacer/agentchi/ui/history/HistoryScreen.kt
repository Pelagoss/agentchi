package fr.codingpacer.agentchi.ui.history

import android.text.format.DateUtils
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TitleCard
import fr.codingpacer.agentchi.notify.PhoneLink
import fr.codingpacer.agentchi.text.markdownAnnotated
import fr.codingpacer.agentchi.text.tr
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.ask.AskActivity

@Composable
fun HistoryScreen(viewModel: CompanionViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Notifications") } }
            if (history.isEmpty()) {
                item {
                    Text(
                        tr("Rien pour le moment. Les événements de Claude Code et de Codex apparaîtront ici.", "Nothing yet. Claude Code and Codex events will show up here."),
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
            items(history, key = { it.id }) { event ->
                val pending = event.isPendingAsk
                TitleCard(
                    onClick = {
                        when {
                            pending -> context.startActivity(AskActivity.intent(context, event))
                            event.openableOnPhone -> PhoneLink.open(context, event)
                        }
                    },
                    title = { Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    time = {
                        Text(
                            DateUtils.getRelativeTimeSpanString(event.time * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                .toString(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(event.origin, style = MaterialTheme.typography.caption3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        pending -> Text(tr("⌚ Toucher pour répondre", "⌚ Tap to reply"), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.primary)
                        event.openableOnPhone -> Text(tr("📱 Toucher pour ouvrir sur le téléphone", "📱 Tap to open on phone"), style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.primary)
                    }
                    Text(markdownAnnotated(event.message), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    event.answer?.let {
                        Text("↩️ $it", style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (history.isNotEmpty()) {
                item {
                    Chip(
                        onClick = viewModel::clearHistory,
                        label = { Text(tr("Tout effacer", "Clear all")) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}
