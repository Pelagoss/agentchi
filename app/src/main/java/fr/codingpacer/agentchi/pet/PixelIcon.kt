package fr.codingpacer.agentchi.pet

/**
 * Icônes d'actions en pixel-art 8 x 8, assorties à la mascotte, avec leur emoji équivalent.
 * Dans les grilles, `a` et `b` sont les deux couleurs de l'icône, `.` est transparent.
 */
enum class PixelIcon(val emoji: String, val a: Int, val b: Int, val grid: List<String>) {
    FEED(
        "🍪", 0xFFE8B04A.toInt(), 0xFF8B5A2B.toInt(),
        listOf(
            "..aaaa..",
            ".aaaaba.",
            "aabaaaaa",
            "aaaaaaba",
            "abaaaaaa",
            "aaaabaaa",
            ".aaaaaa.",
            "..aaaa..",
        ),
    ),
    PLAY(
        "🎾", 0xFFC8E64C.toInt(), 0xFFF0EEE6.toInt(),
        listOf(
            "..aaaa..",
            ".baaaab.",
            "aabaabaa",
            "aaabbaaa",
            "aaabbaaa",
            "aabaabaa",
            ".baaaab.",
            "..aaaa..",
        ),
    ),
    SLEEP(
        "😴", 0xFFF2C14E.toInt(), 0xFFB9B5AA.toInt(),
        listOf(
            "...aaa..",
            "..aa...b",
            ".aa...b.",
            ".aa..bbb",
            ".aa.....",
            ".aaa....",
            "..aaaaa.",
            "...aaa..",
        ),
    ),
    WAKE(
        "☀️", 0xFFF2C14E.toInt(), 0xFFE8B04A.toInt(),
        listOf(
            "...a....",
            ".a.a.a..",
            "..bbb...",
            "aabbbaa.",
            "..bbb...",
            ".a.a.a..",
            "...a....",
            "........",
        ),
    ),
    SATIETY(
        "🍪", 0xFFE8B04A.toInt(), 0xFF8B5A2B.toInt(), FEED.grid,
    ),
    ENERGY(
        "⚡", 0xFF7FB3FF.toInt(), 0xFF7FB3FF.toInt(),
        listOf(
            "....aa..",
            "...aa...",
            "..aa....",
            ".aaaaaa.",
            "....aa..",
            "...aa...",
            "..aa....",
            ".a......",
        ),
    ),
    HAPPINESS(
        "💖", 0xFFE97CA0.toInt(), 0xFFF0EEE6.toInt(),
        listOf(
            ".aa..aa.",
            "abaaaaaa",
            "aaaaaaaa",
            "aaaaaaaa",
            ".aaaaaa.",
            "..aaaa..",
            "...aa...",
            "........",
        ),
    ),
    CLEAN(
        "🧽", 0xFFF2C14E.toInt(), 0xFF5DBB7A.toInt(),
        listOf(
            "......b.",
            ".....b.b",
            "......b.",
            "aaaaaa..",
            "abaaba..",
            "aaaaaa..",
            "aabaaa..",
            "aaaaaa..",
        ),
    ),
    /** Caca posé à côté de la mascotte (dessiné dans la scène, pas comme bouton). */
    POOP(
        "💩", 0xFF8B5A2B.toInt(), 0xFFB07A45.toInt(),
        listOf(
            "........",
            "....a...",
            "...aa...",
            "..abaa..",
            "..aaaa..",
            ".abaaba.",
            "aaaaaaaa",
            ".aaaaaa.",
        ),
    ),
    NOTIFICATIONS(
        "🔔", 0xFFF2C14E.toInt(), 0xFFB4593B.toInt(),
        listOf(
            "...aa...",
            "..aaaa..",
            ".aaaaaa.",
            ".aaaaaa.",
            ".aaaaaa.",
            "aaaaaaaa",
            "........",
            "...bb...",
        ),
    ),
    SETTINGS(
        "⚙️", 0xFFB0AEA5.toInt(), 0xFFB0AEA5.toInt(),
        listOf(
            "..a..a..",
            ".aaaaaa.",
            "aaa..aaa",
            ".a....a.",
            ".a....a.",
            "aaa..aaa",
            ".aaaaaa.",
            "..a..a..",
        ),
    ),
    ;

    /** Cases colorées : (x, y, couleur ARGB). */
    fun pixels(): List<Triple<Int, Int, Int>> = grid.flatMapIndexed { y, row ->
        row.mapIndexedNotNull { x, c ->
            when (c) {
                'a' -> Triple(x, y, a)
                'b' -> Triple(x, y, b)
                else -> null
            }
        }
    }

    companion object {
        const val SIZE = 8
    }
}
