package fr.codingpacer.agentchi.ui.pet

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
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.pet.Mood
import fr.codingpacer.agentchi.pet.PixelIcon
import fr.codingpacer.agentchi.pet.Reaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Mascotte pixel-art animée. Respire, cligne des yeux, saute quand on la touche
 * et change d'expression selon son humeur ou les événements des agents.
 */
@Composable
fun Mascot(
    mood: Mood,
    reaction: Reaction,
    look: MascotSprite.Look,
    modifier: Modifier = Modifier,
    /** Cacas posés à côté de la mascotte. */
    poops: Int = 0,
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

    val face = MascotSprite.face(mood, reaction, blinking)
    val altLegs = excited && bounce > 0.5f
    val bubble = MascotSprite.bubble(mood, reaction)

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
            val px = minOf(size.width / (MascotSprite.WIDTH + 2), size.height / (MascotSprite.HEIGHT + 3))
            val amplitude = if (excited) px * 1.2f else px * 0.35f
            val origin = Offset(
                (size.width - MascotSprite.WIDTH * px) / 2,
                (size.height - MascotSprite.HEIGHT * px) / 2 - bounce * amplitude - jump.value * px * 2.5f,
            )
            // Ombre au sol
            val shadowW = MascotSprite.WIDTH * px * (0.75f - bounce * 0.1f)
            drawOval(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset((size.width - shadowW) / 2, (size.height + MascotSprite.HEIGHT * px) / 2 + px * 0.2f),
                size = Size(shadowW, px * 0.8f),
            )
            drawMascot(origin, px, look, face, altLegs, sleeping = mood == Mood.SLEEPING, breath = bounce)
            drawPoops(poops, px, groundY = (size.height + MascotSprite.HEIGHT * px) / 2)
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

/** Les cacas, posés au sol de part et d'autre de la mascotte (le troisième devant, à gauche). */
private fun DrawScope.drawPoops(count: Int, px: Float, groundY: Float) {
    if (count <= 0) return
    val cell = px * 0.26f
    val side = PixelIcon.SIZE * cell
    val spots = listOf(
        Offset(0f, groundY - side),
        Offset(size.width - side, groundY - side),
        Offset(side * 0.8f, groundY - side * 0.6f),
    )
    spots.take(count).forEach { at ->
        PixelIcon.POOP.pixels().forEach { (x, y, argb) ->
            drawRect(Color(argb), topLeft = Offset(at.x + x * cell, at.y + y * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

private fun DrawScope.drawMascot(
    origin: Offset,
    px: Float,
    look: MascotSprite.Look,
    face: MascotSprite.Face,
    altLegs: Boolean,
    sleeping: Boolean,
    breath: Float,
) {
    // Respiration : le corps s'étire très légèrement pendant le sommeil
    val stretch = if (sleeping) 1f + breath * 0.04f else 1f
    MascotSprite.pixels(look, face, altLegs, stretch).forEach { p ->
        val top = origin.y + (p.y + p.dy) * px
        if (p.round) {
            drawCircle(Color(p.argb), radius = px * 0.46f, center = Offset(origin.x + (p.x + 0.5f) * px, top + px * p.h / 2))
        } else {
            drawRect(
                color = Color(p.argb),
                topLeft = Offset(origin.x + p.x * px, top),
                // léger chevauchement pour éviter les lignes d'anticrénelage entre pixels
                size = Size(px + 0.5f, px * p.h + 0.5f),
            )
        }
    }
}
