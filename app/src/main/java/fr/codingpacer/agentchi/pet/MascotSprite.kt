package fr.codingpacer.agentchi.pet

/**
 * Dessin pixel-art de la mascotte, sans dépendance Android : partagé par l'appli (Compose)
 * et la tuile (bitmap). Grille 12 x 8 : corps, bras (lignes 3-4) et pattes (ligne 7).
 */
object MascotSprite {
    const val WIDTH = 12
    const val HEIGHT = 8

    private val BODY = listOf(
        "..########..",
        "..########..",
        "..########..",
        "############",
        "############",
        "..########..",
        "..########..",
    )
    private const val LEGS_A = "..#.#..#.#.."
    private const val LEGS_B = "...#.#..#.#."

    enum class Face { OPEN, CLOSED, HAPPY, SAD, WIDE }

    enum class Ink { BODY, ARM, EYE, TEAR }

    /** Une case de la grille. [h] et [dy] : hauteur et décalage vertical, en cases. */
    data class Pixel(val x: Int, val y: Int, val ink: Ink, val h: Float = 1f, val dy: Float = 0f)

    fun face(mood: Mood, reaction: Reaction, blinking: Boolean = false): Face = when {
        mood == Mood.SLEEPING -> Face.CLOSED
        reaction == Reaction.ALERT -> Face.WIDE
        blinking -> Face.CLOSED
        reaction in setOf(Reaction.CELEBRATE, Reaction.LOVE, Reaction.PLAY, Reaction.EAT, Reaction.WAVE) -> Face.HAPPY
        mood == Mood.HAPPY -> Face.HAPPY
        mood == Mood.SAD || mood == Mood.TIRED -> Face.SAD
        else -> Face.OPEN
    }

    /** Bulle au-dessus de la mascotte, ou null. */
    fun bubble(mood: Mood, reaction: Reaction): String? = when {
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

    /** Les cases à peindre. [stretch] : étirement du corps (respiration pendant le sommeil). */
    fun pixels(face: Face, altLegs: Boolean = false, stretch: Float = 1f): List<Pixel> {
        val out = mutableListOf<Pixel>()
        BODY.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '#') {
                    val isArm = (y == 3 || y == 4) && (x < 2 || x > 9)
                    out += Pixel(x, y, if (isArm) Ink.ARM else Ink.BODY, h = stretch)
                }
            }
        }
        (if (altLegs) LEGS_B else LEGS_A).forEachIndexed { x, c ->
            if (c == '#') out += Pixel(x, 7, Ink.ARM)
        }
        when (face) {
            Face.OPEN -> listOf(3, 8).forEach { x -> out += Pixel(x, 1, Ink.EYE); out += Pixel(x, 2, Ink.EYE) }
            Face.CLOSED -> listOf(3, 8).forEach { x -> out += Pixel(x, 2, Ink.EYE, h = 0.35f, dy = 0.4f) }
            Face.HAPPY -> // ^ ^
                listOf(2 to 2, 3 to 1, 4 to 2, 7 to 2, 8 to 1, 9 to 2).forEach { (x, y) -> out += Pixel(x, y, Ink.EYE, h = 0.5f, dy = 0.25f) }
            Face.SAD -> {
                out += Pixel(3, 2, Ink.EYE)
                out += Pixel(8, 2, Ink.EYE)
                out += Pixel(3, 3, Ink.TEAR, h = 0.6f) // larme
            }
            Face.WIDE -> {
                listOf(3, 4, 7, 8).forEach { x -> out += Pixel(x, 1, Ink.EYE); out += Pixel(x, 2, Ink.EYE) }
                out += Pixel(5, 4, Ink.EYE, h = 0.6f) // bouche « o »
                out += Pixel(6, 4, Ink.EYE, h = 0.6f)
            }
        }
        return out
    }

    /** Couleurs ARGB, communes à l'appli et à la tuile. */
    fun argb(ink: Ink): Int = when (ink) {
        Ink.BODY -> 0xFFD97757.toInt()
        Ink.ARM -> 0xFFB4593B.toInt()
        Ink.EYE -> 0xFF1F1E1D.toInt()
        Ink.TEAR -> 0xFF7FB3FF.toInt()
    }
}
