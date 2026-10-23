package fr.codingpacer.agentchi.pet

/**
 * Dessin pixel-art de la mascotte, sans dépendance Android : partagé par l'appli (Compose),
 * la tuile (bitmap) et les icônes d'appli (vecteurs générés). Grille de [WIDTH] x [HEIGHT] :
 * [TOP] rangées pour les accessoires, puis 7 rangées de corps et une de pattes.
 */
object MascotSprite {
    const val WIDTH = 12
    const val HEIGHT = 10
    const val TOP = 2

    enum class Face { OPEN, CLOSED, HAPPY, SAD, WIDE }

    /**
     * Base de la mascotte : corps (`#` : couleur principale, `+` : couleur secondaire), deux poses
     * de pattes, et colonnes des yeux ([leftEye], [rightEye]) sur la rangée [eyeRow] du corps.
     * [round] : pixels dessinés en points ronds.
     */
    enum class Species(
        val label: String,
        val body: List<String>,
        val legsA: String,
        val legsB: String,
        val leftEye: Int,
        val rightEye: Int,
        val eyeRow: Int,
        val round: Boolean,
        val defaultPalette: Palette,
    ) {
        CRAB(
            "Crabe",
            listOf(
                "..########..",
                "..########..",
                "..########..",
                "++########++",
                "++########++",
                "..########..",
                "..########..",
            ),
            "..+.+..+.+..",
            "...+.+..+.+.",
            leftEye = 3, rightEye = 8, eyeRow = 1, round = false, defaultPalette = Palette.CORAL,
        ),
        DOTS(
            "Points",
            listOf(
                "...######...",
                "..########..",
                ".##########.",
                ".##########.",
                ".##########.",
                "..########..",
                "...######...",
            ),
            "....+..+....",
            "...+....+...",
            leftEye = 3, rightEye = 8, eyeRow = 2, round = true, defaultPalette = Palette.INK,
        ),
        GHOST(
            "Fantôme",
            listOf(
                "...######...",
                "..########..",
                ".##########.",
                ".##########.",
                ".##########.",
                ".##########.",
                ".##########.",
            ),
            ".+.++..++.+.",
            ".++.++.++.+.",
            leftEye = 3, rightEye = 8, eyeRow = 2, round = false, defaultPalette = Palette.LILAC,
        ),
    }

    /** Couleurs ARGB : corps, secondaire (bras, pattes), yeux. */
    enum class Palette(val label: String, val body: Int, val accent: Int, val eye: Int) {
        CORAL("Corail", 0xFFD97757.toInt(), 0xFFB4593B.toInt(), INK_DARK),
        INK("Encre", 0xFFF0EEE6.toInt(), 0xFFB9B5AA.toInt(), INK_DARK),
        OCEAN("Océan", 0xFF5B8DEF.toInt(), 0xFF3D6AC4.toInt(), INK_DARK),
        MINT("Menthe", 0xFF5DBB7A.toInt(), 0xFF3E8E58.toInt(), INK_DARK),
        LILAC("Lilas", 0xFFA77BE0.toInt(), 0xFF7E55B8.toInt(), INK_DARK),
        ROSE("Rose", 0xFFE97CA0.toInt(), 0xFFC25A7E.toInt(), INK_DARK),
    }

    /** Accessoire porté au-dessus du corps (ou sur les yeux), débloqué au niveau [level]. */
    enum class Accessory(val label: String, val level: Int) {
        NONE("Aucun", 1),
        PARTY_HAT("Chapeau de fête", 1),
        BOW("Nœud", 1),
        SUNGLASSES("Lunettes de soleil", 2),
        TOP_HAT("Haut-de-forme", 3),
        CROWN("Couronne", 5),
    }

    data class Look(
        val species: Species = Species.CRAB,
        val palette: Palette = species.defaultPalette,
        val accessory: Accessory = Accessory.NONE,
    )

    /** Une case peinte. [h] et [dy] : hauteur et décalage vertical, en cases ; [round] : point rond. */
    data class Pixel(val x: Int, val y: Int, val argb: Int, val h: Float = 1f, val dy: Float = 0f, val round: Boolean = false)

    fun face(mood: Mood, reaction: Reaction, blinking: Boolean = false): Face = when {
        mood == Mood.SLEEPING -> Face.CLOSED
        reaction == Reaction.ALERT -> Face.WIDE
        blinking -> Face.CLOSED
        reaction in setOf(Reaction.CELEBRATE, Reaction.LOVE, Reaction.PLAY, Reaction.EAT, Reaction.WAVE, Reaction.CLEAN) -> Face.HAPPY
        mood == Mood.HAPPY -> Face.HAPPY
        mood == Mood.SAD || mood == Mood.TIRED || mood == Mood.DIRTY -> Face.SAD
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
        reaction == Reaction.CLEAN -> "✨"
        mood == Mood.HUNGRY -> "🍽️"
        mood == Mood.DIRTY -> "🤢"
        else -> null
    }

    /** Les cases à peindre, dans l'ordre. [stretch] : étirement du corps (respiration pendant le sommeil). */
    fun pixels(look: Look, face: Face, altLegs: Boolean = false, stretch: Float = 1f): List<Pixel> {
        val s = look.species
        val p = look.palette
        val out = mutableListOf<Pixel>()
        s.body.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                when (c) {
                    '#' -> out += Pixel(x, TOP + y, p.body, h = stretch, round = s.round)
                    '+' -> out += Pixel(x, TOP + y, p.accent, h = stretch, round = s.round)
                }
            }
        }
        (if (altLegs) s.legsB else s.legsA).forEachIndexed { x, c ->
            if (c != '.') out += Pixel(x, TOP + 7, p.accent, round = s.round)
        }
        out += eyes(s, p, face)
        out += accessory(s, look.accessory)
        return out
    }

    private fun eyes(s: Species, p: Palette, face: Face): List<Pixel> {
        val l = s.leftEye
        val r = s.rightEye
        val y = TOP + s.eyeRow
        val eye = p.eye
        return when (face) {
            Face.OPEN -> listOf(l, r).flatMap { x -> listOf(Pixel(x, y, eye), Pixel(x, y + 1, eye)) }
            Face.CLOSED -> listOf(l, r).map { x -> Pixel(x, y + 1, eye, h = 0.35f, dy = 0.4f) }
            Face.HAPPY -> // ^ ^
                listOf(l, r).flatMap { x ->
                    listOf(Pixel(x - 1, y + 1, eye, 0.5f, 0.25f), Pixel(x, y, eye, 0.5f, 0.25f), Pixel(x + 1, y + 1, eye, 0.5f, 0.25f))
                }
            Face.SAD -> listOf(Pixel(l, y + 1, eye), Pixel(r, y + 1, eye), Pixel(l, y + 2, TEAR, h = 0.6f))
            Face.WIDE -> listOf(l, l + 1, r - 1, r).flatMap { x -> listOf(Pixel(x, y, eye), Pixel(x, y + 1, eye)) } +
                // bouche « o »
                listOf(Pixel(5, y + 3, eye, h = 0.6f), Pixel(6, y + 3, eye, h = 0.6f))
        }
    }

    private fun accessory(s: Species, a: Accessory): List<Pixel> {
        // Haut de la tête : première rangée du corps.
        val head = s.body.first()
        val left = head.indexOfFirst { it != '.' }
        val right = head.indexOfLast { it != '.' }
        val mid = (left + right) / 2 // 5 pour une tête de 12 cases centrée
        fun row(y: Int, from: Int, to: Int, argb: Int) = (from..to).map { Pixel(it, y, argb) }
        return when (a) {
            Accessory.NONE -> emptyList()
            Accessory.PARTY_HAT -> listOf(Pixel(mid, 0, GOLD), Pixel(mid + 1, 0, GOLD)) +
                listOf(Pixel(mid - 1, 1, PINK), Pixel(mid, 1, CYAN), Pixel(mid + 1, 1, PINK), Pixel(mid + 2, 1, CYAN))
            Accessory.BOW -> listOf(Pixel(right - 2, 1, PINK), Pixel(right - 1, 1, PINK_DARK), Pixel(right, 1, PINK), Pixel(right - 2, 0, PINK), Pixel(right, 0, PINK))
            Accessory.TOP_HAT -> row(0, mid - 1, mid + 2, HAT) + row(1, mid - 2, mid + 3, HAT) + listOf(Pixel(mid - 1, 1, RED, h = 0.4f, dy = 0.3f))
            Accessory.CROWN -> listOf(Pixel(mid - 2, 0, GOLD), Pixel(mid, 0, GOLD), Pixel(mid + 1, 0, GOLD), Pixel(mid + 3, 0, GOLD)) +
                row(1, mid - 2, mid + 3, GOLD) + listOf(Pixel(mid, 1, RED, h = 0.5f, dy = 0.25f))
            Accessory.SUNGLASSES -> {
                val y = TOP + s.eyeRow
                (listOf(s.leftEye - 1, s.leftEye, s.leftEye + 1, s.rightEye - 1, s.rightEye, s.rightEye + 1).flatMap { x ->
                    listOf(Pixel(x, y, SHADES), Pixel(x, y + 1, SHADES, h = 0.7f))
                } + row(y, s.leftEye + 2, s.rightEye - 2, SHADES).map { it.copy(h = 0.4f) } +
                    listOf(Pixel(s.leftEye - 1, y, WHITE, h = 0.35f), Pixel(s.rightEye - 1, y, WHITE, h = 0.35f)))
            }
        }
    }

    private const val INK_DARK = 0xFF1F1E1D.toInt()
    private const val TEAR = 0xFF7FB3FF.toInt()
    private const val GOLD = 0xFFF2C14E.toInt()
    private const val PINK = 0xFFF28AB2.toInt()
    private const val PINK_DARK = 0xFFC25A7E.toInt()
    private const val CYAN = 0xFF6FD3E0.toInt()
    private const val HAT = 0xFF5A524C.toInt()
    private const val RED = 0xFFD9534F.toInt()
    private const val SHADES = 0xFF111111.toInt()
    private const val WHITE = 0xFFF0EEE6.toInt()
}
