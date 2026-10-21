package fr.codingpacer.agentchi.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import fr.codingpacer.agentchi.pet.MascotSprite
import java.io.ByteArrayOutputStream

/** La mascotte en PNG, pour la tuile (qui n'affiche pas de Compose). */
object MascotBitmap {
    /** Côté d'une case de la grille, en pixels. */
    private const val CELL = 8
    const val WIDTH_PX = MascotSprite.WIDTH * CELL
    const val HEIGHT_PX = MascotSprite.HEIGHT * CELL
    const val WIDTH_DP = 48f
    const val HEIGHT_DP = 40f

    fun png(look: MascotSprite.Look, face: MascotSprite.Face): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        MascotSprite.pixels(look, face).forEach { p ->
            paint.color = p.argb
            val top = (p.y + p.dy) * CELL
            if (p.round) {
                canvas.drawCircle((p.x + 0.5f) * CELL, top + p.h * CELL / 2, CELL * 0.46f, paint)
            } else {
                canvas.drawRect(p.x * CELL.toFloat(), top, (p.x + 1f) * CELL, top + p.h * CELL, paint)
            }
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    /** Version des ressources de la tuile : une par apparence et expression. */
    fun version(look: MascotSprite.Look, face: MascotSprite.Face): String =
        listOf(look.species.name, look.palette.name, look.accessory.name, face.name).joinToString(".")

    fun parse(version: String): Pair<MascotSprite.Look, MascotSprite.Face> {
        val parts = version.split('.')
        fun <T : Enum<T>> pick(values: Array<T>, i: Int, default: T) = values.firstOrNull { it.name == parts.getOrNull(i) } ?: default
        val species = pick(MascotSprite.Species.entries.toTypedArray(), 0, MascotSprite.Species.CRAB)
        val look = MascotSprite.Look(
            species = species,
            palette = pick(MascotSprite.Palette.entries.toTypedArray(), 1, species.defaultPalette),
            accessory = pick(MascotSprite.Accessory.entries.toTypedArray(), 2, MascotSprite.Accessory.NONE),
        )
        return look to pick(MascotSprite.Face.entries.toTypedArray(), 3, MascotSprite.Face.OPEN)
    }
}
