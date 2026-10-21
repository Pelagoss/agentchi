package fr.codingpacer.agentchi.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import fr.codingpacer.agentchi.data.IconSet
import fr.codingpacer.agentchi.pet.PixelIcon

/** Icône d'action : pixel-art assorti à la mascotte, ou emoji, selon le réglage. */
@Composable
fun ActionIcon(icon: PixelIcon, set: IconSet, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    when (set) {
        IconSet.EMOJI -> Text(icon.emoji, fontSize = (size.value * 0.9f).sp, modifier = modifier)
        IconSet.PIXEL -> Canvas(modifier = modifier.size(size)) {
            val px = this.size.minDimension / PixelIcon.SIZE
            icon.pixels().forEach { (x, y, argb) ->
                // léger chevauchement pour éviter les lignes d'anticrénelage entre pixels
                drawRect(Color(argb), topLeft = Offset(x * px, y * px), size = Size(px + 0.5f, px + 0.5f))
            }
        }
    }
}
