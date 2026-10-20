package com.claudecompanion.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.claudecompanion.ui.CompanionViewModel

@Composable
fun HistoryScreen(viewModel: CompanionViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Notifications") } }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Rien pour le moment. Les événements de tes sessions Claude apparaîtront ici.",
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
            items(history, key = { it.id }) { event ->
                TitleCard(
                    onClick = {},
                    title = { Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    time = {
                        Text(
                            DateUtils.getRelativeTimeSpanString(event.time * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                .toString(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    event.project?.let { Text("📁 $it", style = MaterialTheme.typography.caption3) }
                    event.account?.let { Text("👤 $it", style = MaterialTheme.typography.caption3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(event.message, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (history.isNotEmpty()) {
                item {
                    Chip(
                        onClick = viewModel::clearHistory,
                        label = { Text("Tout effacer") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}
