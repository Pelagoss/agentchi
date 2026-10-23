package fr.codingpacer.agentchi.ui.achievements

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import fr.codingpacer.agentchi.pet.Achievement
import fr.codingpacer.agentchi.pet.Achievements
import fr.codingpacer.agentchi.text.tr
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.theme.BrandOrange
import java.text.DateFormat
import java.util.Date

/** Séries de jours avec des tâches terminées, et succès débloqués ou à débloquer. */
@Composable
fun AchievementsScreen(viewModel: CompanionViewModel) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val (pet, now) = viewModel.pet.collectAsStateWithLifecycle().value
    val listState = rememberScalingLazyListState()
    val streak = Achievements.currentStreak(progress, now)
    // Débloqués d'abord, du plus récent au plus ancien ; puis les autres, du plus avancé au moins avancé.
    val sorted = Achievement.entries.sortedWith(
        compareByDescending<Achievement> { progress.unlocked[it.name] ?: -1L }
            .thenByDescending { Achievements.progressOf(it, progress, pet).toFloat() / it.goal },
    )

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { ListHeader { Text(tr("Succès", "Achievements")) } }
            item {
                Text(
                    when {
                        streak <= 0 -> tr("🔥 Pas de série en cours", "🔥 No streak yet")
                        else -> tr("🔥 $streak jour${if (streak > 1) "s" else ""} d'affilée", "🔥 $streak-day streak")
                    },
                    style = MaterialTheme.typography.title3,
                    color = BrandOrange,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    tr(
                        "Record : ${progress.bestStreak} j · ${progress.unlocked.size} sur ${Achievement.entries.size} succès",
                        "Best: ${progress.bestStreak} d · ${progress.unlocked.size} of ${Achievement.entries.size} unlocked",
                    ),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            items(sorted, key = { it.name }) { a ->
                val unlockedAt = progress.unlocked[a.name]
                val secondary = when {
                    unlockedAt != null -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(unlockedAt)) + " · " + a.description
                    a.goal > 1 -> "${Achievements.progressOf(a, progress, pet)} / ${a.goal} · ${a.description}"
                    else -> a.description
                }
                Chip(
                    onClick = {},
                    label = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    icon = { Text(if (unlockedAt != null) a.emoji else "🔒") },
                    colors = if (unlockedAt != null) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
