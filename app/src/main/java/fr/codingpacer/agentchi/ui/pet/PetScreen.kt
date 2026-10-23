package fr.codingpacer.agentchi.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import fr.codingpacer.agentchi.data.IconSet
import fr.codingpacer.agentchi.data.SessionState
import fr.codingpacer.agentchi.data.Sessions
import fr.codingpacer.agentchi.pet.Achievement
import fr.codingpacer.agentchi.pet.Achievements
import fr.codingpacer.agentchi.pet.Mood
import fr.codingpacer.agentchi.pet.PixelIcon
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.text.tr
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.theme.BrandOrange

@Composable
fun PetScreen(
    viewModel: CompanionViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenAchievements: () -> Unit,
) {
    val (pet, now) = viewModel.pet.collectAsStateWithLifecycle().value
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val visible = Sessions.visible(sessions, now / 1000)
    val streak = Achievements.currentStreak(progress, now)
    val reaction = PetLogic.currentReaction(pet, now)
    val mood = PetLogic.mood(pet)
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Mascot(
                    mood = mood,
                    reaction = reaction,
                    look = settings.mascot,
                    modifier = Modifier.size(width = 120.dp, height = 96.dp),
                    poops = pet.poops,
                    onTap = viewModel::caress,
                )
            }
            item {
                Text(
                    text = "${pet.name} · ${tr("niv.", "Lv.")} ${PetLogic.level(pet.xp)}",
                    style = MaterialTheme.typography.title3,
                    color = BrandOrange,
                )
            }
            item {
                Text(
                    text = PetLogic.moodLine(pet, reaction),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRing(PixelIcon.SATIETY, settings.iconSet, pet.satiety, Color(0xFFE8B04A))
                    StatRing(PixelIcon.ENERGY, settings.iconSet, pet.energy, Color(0xFF7FB3FF))
                    StatRing(PixelIcon.HAPPINESS, settings.iconSet, pet.happiness, Color(0xFFE97CA0))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton(PixelIcon.FEED, settings.iconSet, tr("Nourrir", "Feed"), enabled = !pet.sleeping, onClick = viewModel::feed)
                    ActionButton(PixelIcon.PLAY, settings.iconSet, tr("Jouer", "Play"), enabled = !pet.sleeping && pet.energy >= 10f, onClick = viewModel::play)
                    ActionButton(
                        if (mood == Mood.SLEEPING) PixelIcon.WAKE else PixelIcon.SLEEP,
                        settings.iconSet,
                        if (mood == Mood.SLEEPING) tr("Réveiller", "Wake up") else tr("Dormir", "Sleep"),
                        onClick = viewModel::toggleSleep,
                    )
                }
            }
            if (pet.poops > 0) {
                item {
                    CompactChip(
                        onClick = viewModel::clean,
                        label = { Text(if (pet.poops > 1) tr("Nettoyer", "Clean") + " (${pet.poops})" else tr("Nettoyer", "Clean")) },
                        icon = { ActionIcon(PixelIcon.CLEAN, settings.iconSet, size = 16.dp) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
            item {
                Text(
                    text = tr("✅ ${pet.tasksCompleted} tâches accomplies avec toi", "✅ ${pet.tasksCompleted} tasks done together"),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            item {
                val waiting = visible.count { it.state == SessionState.WAITING }
                val working = visible.count { it.state == SessionState.WORKING }
                Chip(
                    onClick = onOpenSessions,
                    label = { Text(tr("Sessions en cours", "Active sessions")) },
                    secondaryLabel = {
                        Text(
                            when {
                                visible.isEmpty() -> tr("Aucune", "None")
                                else -> listOfNotNull(
                                    tr("${visible.size} active${if (visible.size > 1) "s" else ""}", "${visible.size} active"),
                                    tr("$working au travail", "$working working").takeIf { working > 0 },
                                    tr("$waiting t'attend${if (waiting > 1) "ent" else ""}", "$waiting waiting").takeIf { waiting > 0 },
                                ).joinToString(" · ")
                            },
                        )
                    },
                    icon = { Text(if (waiting > 0) "🔔" else "⚙️") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onOpenAchievements,
                    label = { Text(tr("Succès", "Achievements")) },
                    secondaryLabel = {
                        Text(
                            listOfNotNull(
                                tr("🔥 $streak j", "🔥 $streak d").takeIf { streak > 0 },
                                "${progress.unlocked.size} / ${Achievement.entries.size}",
                            ).joinToString(" · "),
                        )
                    },
                    icon = { Text("🏆") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onOpenHistory,
                    label = { Text("Notifications") },
                    secondaryLabel = { Text(history.firstOrNull()?.title ?: tr("Aucune pour l'instant", "None yet")) },
                    icon = { ActionIcon(PixelIcon.NOTIFICATIONS, settings.iconSet) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onOpenSettings,
                    label = { Text(tr("Paramètres", "Settings")) },
                    icon = { ActionIcon(PixelIcon.SETTINGS, settings.iconSet) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatRing(icon: PixelIcon, set: IconSet, value: Float, color: Color) {
    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = value / 100f,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = if (value < 25f) Color(0xFFFF6B5E) else color,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
            strokeWidth = 4.dp,
        )
        ActionIcon(icon, set, size = 14.dp)
    }
}

@Composable
private fun ActionButton(icon: PixelIcon, set: IconSet, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(ButtonDefaults.SmallButtonSize).semantics { contentDescription = description },
    ) {
        ActionIcon(icon, set, size = 20.dp)
    }
}
