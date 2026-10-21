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
        val bitmap = bitmap(look, face)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    /**
     * La mascotte centrée sur une image carrée, pour la complication du cadran. Grandes cases ([cell] pixels) :
     * le cadran l'affiche en grand, et un pixel-art agrandi par le système serait flou.
     */
    fun square(look: MascotSprite.Look, face: MascotSprite.Face, cell: Int = 24): Bitmap {
        val side = MascotSprite.WIDTH * cell
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), look, face, cell, dy = (side - MascotSprite.HEIGHT * cell) / 2f)
        return bitmap
    }

    fun bitmap(look: MascotSprite.Look, face: MascotSprite.Face): Bitmap =
        Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), look, face, CELL, 0f) }

    private fun draw(canvas: Canvas, look: MascotSprite.Look, face: MascotSprite.Face, cell: Int, dy: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        MascotSprite.pixels(look, face).forEach { p ->
            paint.color = p.argb
            val left = p.x * cell.toFloat()
            val top = dy + (p.y + p.dy) * cell
            if (p.round) {
                canvas.drawCircle(left + cell * 0.5f, top + p.h * cell / 2, cell * 0.46f, paint)
            } else {
                canvas.drawRect(left, top, left + cell, top + p.h * cell, paint)
            }
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
