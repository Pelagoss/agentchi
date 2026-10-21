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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import fr.codingpacer.agentchi.data.IconSet
import fr.codingpacer.agentchi.pet.Mood
import fr.codingpacer.agentchi.pet.PixelIcon
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.theme.BrandOrange

@Composable
fun PetScreen(
    viewModel: CompanionViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val (pet, now) = viewModel.pet.collectAsStateWithLifecycle().value
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
                    onTap = viewModel::caress,
                )
            }
            item {
                Text(
                    text = "${pet.name} · niv. ${PetLogic.level(pet.xp)}",
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
                    ActionButton(PixelIcon.FEED, settings.iconSet, "Nourrir", enabled = !pet.sleeping, onClick = viewModel::feed)
                    ActionButton(PixelIcon.PLAY, settings.iconSet, "Jouer", enabled = !pet.sleeping && pet.energy >= 10f, onClick = viewModel::play)
                    ActionButton(
                        if (mood == Mood.SLEEPING) PixelIcon.WAKE else PixelIcon.SLEEP,
                        settings.iconSet,
                        if (mood == Mood.SLEEPING) "Réveiller" else "Dormir",
                        onClick = viewModel::toggleSleep,
                    )
                }
            }
            item {
                Text(
                    text = "✅ ${pet.tasksCompleted} tâches accomplies avec toi",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            item {
                Chip(
                    onClick = onOpenHistory,
                    label = { Text("Notifications") },
                    secondaryLabel = { Text(history.firstOrNull()?.title ?: "Aucune pour l'instant") },
                    icon = { ActionIcon(PixelIcon.NOTIFICATIONS, settings.iconSet) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onOpenSettings,
                    label = { Text("Paramètres") },
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
