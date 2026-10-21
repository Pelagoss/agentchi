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
    const val HEIGHT_DP = 32f

    fun png(face: MascotSprite.Face): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        MascotSprite.pixels(face).forEach { p ->
            paint.color = MascotSprite.argb(p.ink)
            val top = (p.y + p.dy) * CELL
            canvas.drawRect(p.x * CELL.toFloat(), top, (p.x + 1f) * CELL, top + p.h * CELL, paint)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
