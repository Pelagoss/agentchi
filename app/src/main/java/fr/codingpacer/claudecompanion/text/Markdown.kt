package fr.codingpacer.claudecompanion.text

/**
 * Markdown des messages de Claude, converti en texte enrichi que la montre sait afficher
 * (notifications, écrans) : gras, italique, code, barré. Titres en gras, listes à puces,
 * blocs de code en chasse fixe, liens réduits à leur texte. Sans dépendance Android.
 */
object Markdown {
    enum class Style { BOLD, ITALIC, CODE, STRIKE }

    data class Span(val start: Int, val end: Int, val style: Style)

    data class Rich(val text: String, val spans: List<Span>)

    private val FENCE = Regex("""^\s*(```|~~~)""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val TASK = Regex("""^\[( |x|X)]\s+(.*)$""")
    private val QUOTE = Regex("""^\s*>\s?(.*)$""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

    // Dans l'ordre de priorité : code, lien, gras, barré, italique.
    private val INLINE = Regex(
        """`([^`\n]+)`""" +
            """|\[([^\]\n]+)]\(([^)\s]+)\)""" +
            """|\*\*(.+?)\*\*|__(.+?)__""" +
            """|~~(.+?)~~""" +
            """|(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])""" +
            """|(?<![\w_])_(?![\s_])(.+?)(?<![\s_])_(?![\w_])""",
    )

    fun parse(markdown: String): Rich {
        val out = Builder()
        var inCode = false
        var blank = false
        markdown.replace("\r\n", "\n").lines().forEach { raw ->
            if (FENCE.containsMatchIn(raw)) {
                inCode = !inCode
                return@forEach
            }
            if (inCode) {
                out.line { out.styled(raw.trimEnd(), Style.CODE) }
                blank = false
                return@forEach
            }
            val line = raw.trimEnd()
            if (line.isBlank()) {
                // Une seule ligne vide entre deux paragraphes.
                if (!blank && out.isNotEmpty()) out.plain("\n\n")
                blank = true
                return@forEach
            }
            blank = false
            when {
                RULE.matches(line) -> out.line { out.plain("———") }
                TABLE_SEPARATOR.matches(line) && line.contains('-') && line.contains('|') -> Unit
                HEADING.matches(line) -> out.line {
                    val start = out.length
                    inline(HEADING.find(line)!!.groupValues[1], out)
                    out.span(start, Style.BOLD)
                }
                BULLET.matches(line) -> out.line {
                    val (indent, content) = BULLET.find(line)!!.destructured
                    out.plain("  ".repeat(indent.replace("\t", "  ").length / 2))
                    val task = TASK.find(content)
                    if (task != null) {
                        out.plain(if (task.groupValues[1].isBlank()) "☐ " else "☑ ")
                        inline(task.groupValues[2], out)
                    } else {
                        out.plain("• ")
                        inline(content, out)
                    }
                }
                QUOTE.matches(line) -> out.line {
                    out.plain("▎ ")
                    val start = out.length
                    inline(QUOTE.find(line)!!.groupValues[1], out)
                    out.span(start, Style.ITALIC)
                }
                line.trimStart().startsWith("|") -> out.line {
                    val cells = line.trim().trim('|').split('|').map { it.trim() }
                    cells.forEachIndexed { i, cell ->
                        if (i > 0) out.plain(" · ")
                        inline(cell, out)
                    }
                }
                else -> out.line { inline(line.trim(), out) }
            }
        }
        return out.build()
    }

    /** Texte seul, sans style : tuile, complication, titres. */
    fun plain(markdown: String): String = parse(markdown).text

    private fun inline(text: String, out: Builder) {
        var last = 0
        // Marqueur resté seul (message coupé au milieu d'un **gras**) : on l'enlève.
        fun rest(s: String) = out.plain(s.replace("**", ""))
        for (m in INLINE.findAll(text)) {
            rest(text.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> out.styled(g[1], Style.CODE)
                g[2].isNotEmpty() -> inline(g[2], out)
                g[4].isNotEmpty() || g[5].isNotEmpty() -> nested(g[4].ifEmpty { g[5] }, Style.BOLD, out)
                g[6].isNotEmpty() -> nested(g[6], Style.STRIKE, out)
                else -> nested(g[7].ifEmpty { g[8] }, Style.ITALIC, out)
            }
            last = m.range.last + 1
        }
        rest(text.substring(last))
    }

    private fun nested(text: String, style: Style, out: Builder) {
        val start = out.length
        inline(text, out)
        out.span(start, style)
    }

    private class Builder {
        private val text = StringBuilder()
        private val spans = mutableListOf<Span>()
        val length get() = text.length

        fun isNotEmpty() = text.isNotEmpty()
        fun plain(s: String) { text.append(s) }

        fun styled(s: String, style: Style) {
            val start = text.length
            text.append(s)
            span(start, style)
        }

        fun span(start: Int, style: Style) {
            if (text.length > start) spans += Span(start, text.length, style)
        }

        /** Une ligne de texte, séparée de la précédente. */
        fun line(content: () -> Unit) {
            if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
            content()
        }

        fun build(): Rich {
            val end = text.trimEnd().length
            return Rich(text.substring(0, end), spans.mapNotNull { s -> s.copy(end = minOf(s.end, end)).takeIf { it.start < it.end } })
        }
    }
}
