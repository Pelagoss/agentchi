package com.claudecompanion.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.claudecompanion.pet.Mood
import com.claudecompanion.pet.Reaction
import com.claudecompanion.ui.theme.ClaudeInk
import com.claudecompanion.ui.theme.ClaudeOrange
import com.claudecompanion.ui.theme.ClaudeOrangeDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Grille 12 x 8 : corps, bras (ligne 3-4) et pattes (ligne 7).
private val BODY = listOf(
    "..########..",
    "..########..",
    "..########..",
    "############",
    "############",
    "..########..",
    "..########..",
)
private val LEGS_A = "..#.#..#.#.."
private val LEGS_B = "...#.#..#.#."
private const val GRID_W = 12
private const val GRID_H = 8

private enum class Face { OPEN, CLOSED, HAPPY, SAD, WIDE }

/**
 * Mascotte pixel-art animée. Respire, cligne des yeux, saute quand on la touche
 * et change d'expression selon son humeur ou les événements Claude.
 */
@Composable
fun ClaudeMascot(
    mood: Mood,
    reaction: Reaction,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val excited = reaction in setOf(Reaction.CELEBRATE, Reaction.PLAY, Reaction.WAVE, Reaction.ALERT)
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = if (excited) 380 else if (mood == Mood.SLEEPING) 2400 else 1200, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "bounce",
    )

    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2_500, 5_000))
            blinking = true
            delay(140)
            blinking = false
        }
    }

    val jump = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val face = when {
        mood == Mood.SLEEPING -> Face.CLOSED
        reaction == Reaction.ALERT -> Face.WIDE
        blinking -> Face.CLOSED
        reaction in setOf(Reaction.CELEBRATE, Reaction.LOVE, Reaction.PLAY, Reaction.EAT, Reaction.WAVE) -> Face.HAPPY
        mood == Mood.HAPPY -> Face.HAPPY
        mood == Mood.SAD || mood == Mood.TIRED -> Face.SAD
        else -> Face.OPEN
    }
    val altLegs = excited && bounce > 0.5f
    val bubble = when {
        mood == Mood.SLEEPING -> "💤"
        reaction == Reaction.ALERT -> "❗"
        reaction == Reaction.CELEBRATE -> "🎉"
        reaction == Reaction.LOVE -> "💖"
        reaction == Reaction.EAT -> "🍪"
        reaction == Reaction.PLAY -> "🎾"
        reaction == Reaction.WAVE -> "👋"
        reaction == Reaction.THINK -> "💭"
        mood == Mood.HUNGRY -> "🍽️"
        else -> null
    }

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            onTap()
            scope.launch {
                jump.snapTo(0f)
                jump.animateTo(1f, tween(140))
                jump.animateTo(0f, spring(dampingRatio = 0.4f))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = minOf(size.width / (GRID_W + 2), size.height / (GRID_H + 4))
            val amplitude = if (excited) px * 1.2f else px * 0.35f
            val origin = Offset(
                (size.width - GRID_W * px) / 2,
                (size.height - GRID_H * px) / 2 + px - bounce * amplitude - jump.value * px * 2.5f,
            )
            // Ombre au sol
            val shadowW = GRID_W * px * (0.75f - bounce * 0.1f)
            drawOval(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset((size.width - shadowW) / 2, (size.height + GRID_H * px) / 2 + px * 1.2f),
                size = Size(shadowW, px * 0.8f),
            )
            drawMascot(origin, px, face, altLegs, sleeping = mood == Mood.SLEEPING, breath = bounce)
        }
        if (bubble != null) {
            Text(
                text = bubble,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = (4 - bounce * 4).dp),
            )
        }
    }
}

private fun DrawScope.drawMascot(origin: Offset, px: Float, face: Face, altLegs: Boolean, sleeping: Boolean, breath: Float) {
    fun cell(x: Int, y: Int, color: Color, h: Float = 1f, dy: Float = 0f) = drawRect(
        color = color,
        topLeft = Offset(origin.x + x * px, origin.y + (y + dy) * px),
        // léger chevauchement pour éviter les lignes d'anticrénelage entre pixels
        size = Size(px + 0.5f, px * h + 0.5f),
    )

    // Respiration : le corps s'étire très légèrement pendant le sommeil
    val stretch = if (sleeping) 1f + breath * 0.04f else 1f
    BODY.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            if (c == '#') {
                val armRow = y == 3 || y == 4
                val isArm = armRow && (x < 2 || x > 9)
                cell(x, y, if (isArm) ClaudeOrangeDark else ClaudeOrange, h = stretch)
            }
        }
    }
    (if (altLegs) LEGS_B else LEGS_A).forEachIndexed { x, c ->
        if (c == '#') cell(x, 7, ClaudeOrangeDark)
    }

    when (face) {
        Face.OPEN -> {
            cell(3, 1, ClaudeInk); cell(3, 2, ClaudeInk)
            cell(8, 1, ClaudeInk); cell(8, 2, ClaudeInk)
        }
        Face.CLOSED -> {
            cell(3, 2, ClaudeInk, h = 0.35f, dy = 0.4f)
            cell(8, 2, ClaudeInk, h = 0.35f, dy = 0.4f)
        }
        Face.HAPPY -> { // ^ ^
            listOf(2 to 2, 3 to 1, 4 to 2, 7 to 2, 8 to 1, 9 to 2).forEach { (x, y) -> cell(x, y, ClaudeInk, h = 0.5f, dy = 0.25f) }
        }
        Face.SAD -> {
            cell(3, 2, ClaudeInk); cell(8, 2, ClaudeInk)
            cell(3, 3, Color(0xFF7FB3FF), h = 0.6f) // larme
        }
        Face.WIDE -> {
            listOf(3, 4, 7, 8).forEach { x -> cell(x, 1, ClaudeInk); cell(x, 2, ClaudeInk) }
            cell(5, 4, ClaudeInk, h = 0.6f); cell(6, 4, ClaudeInk, h = 0.6f) // bouche « o »
        }
    }
}
