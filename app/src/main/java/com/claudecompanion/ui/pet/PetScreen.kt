package com.claudecompanion.ui.pet

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.claudecompanion.pet.Mood
import com.claudecompanion.pet.PetLogic
import com.claudecompanion.ui.CompanionViewModel
import com.claudecompanion.ui.theme.ClaudeOrange

@Composable
fun PetScreen(
    viewModel: CompanionViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val (pet, now) = viewModel.pet.collectAsStateWithLifecycle().value
    val history by viewModel.history.collectAsStateWithLifecycle()
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
                ClaudeMascot(
                    mood = mood,
                    reaction = reaction,
                    modifier = Modifier.size(width = 120.dp, height = 96.dp),
                    onTap = viewModel::caress,
                )
            }
            item {
                Text(
                    text = "${pet.name} · niv. ${PetLogic.level(pet.xp)}",
                    style = MaterialTheme.typography.title3,
                    color = ClaudeOrange,
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
                    StatRing("🍪", pet.satiety, Color(0xFFE8B04A))
                    StatRing("⚡", pet.energy, Color(0xFF7FB3FF))
                    StatRing("💖", pet.happiness, Color(0xFFE97CA0))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("🍪", "Nourrir", enabled = !pet.sleeping, onClick = viewModel::feed)
                    ActionButton("🎾", "Jouer", enabled = !pet.sleeping && pet.energy >= 10f, onClick = viewModel::play)
                    ActionButton(if (mood == Mood.SLEEPING) "☀️" else "😴", "Dormir", onClick = viewModel::toggleSleep)
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
                    icon = { Text("🔔") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onOpenSettings,
                    label = { Text("Paramètres") },
                    icon = { Text("⚙️") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatRing(emoji: String, value: Float, color: Color) {
    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = value / 100f,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = if (value < 25f) Color(0xFFFF6B5E) else color,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
            strokeWidth = 4.dp,
        )
        Text(emoji, fontSize = 14.sp)
    }
}

@Composable
private fun ActionButton(emoji: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
    ) {
        Text(emoji, fontSize = 18.sp)
    }
}
