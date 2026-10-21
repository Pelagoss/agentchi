package fr.codingpacer.claudecompanion

import fr.codingpacer.claudecompanion.text.Markdown
import fr.codingpacer.claudecompanion.text.Markdown.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    private fun styled(md: String): List<Pair<Style, String>> {
        val r = Markdown.parse(md)
        return r.spans.map { it.style to r.text.substring(it.start, it.end) }
    }

    @Test
    fun `styles en ligne`() {
        val md = "J'ai **corrigé** le bug dans `auth.ts`, *ajouté* des tests et ~~rien~~ cassé."
        assertEquals("J'ai corrigé le bug dans auth.ts, ajouté des tests et rien cassé.", Markdown.plain(md))
        assertEquals(
            listOf(Style.BOLD to "corrigé", Style.CODE to "auth.ts", Style.ITALIC to "ajouté", Style.STRIKE to "rien"),
            styled(md),
        )
    }

    @Test
    fun `titres listes citations et liens`() {
        val md = "## Résumé\n\n- un\n  - deux\n- [x] fait\n- [ ] à faire\n\n> **Note**\n\nVoir [la doc](https://example.com)."
        assertEquals("Résumé\n\n• un\n  • deux\n☑ fait\n☐ à faire\n\n▎ Note\n\nVoir la doc.", Markdown.plain(md))
        assertEquals(listOf(Style.BOLD to "Résumé", Style.BOLD to "Note", Style.ITALIC to "Note"), styled(md))
    }

    @Test
    fun `blocs de code tableaux et separateurs`() {
        val md = "Avant\n```kotlin\nval x = 1\n```\n| A | B |\n|---|:-:|\n| 1 | 2 |\n\n---\nAprès"
        assertEquals("Avant\nval x = 1\nA · B\n1 · 2\n\n———\nAprès", Markdown.plain(md))
        assertEquals(listOf(Style.CODE to "val x = 1"), styled(md))
    }

    @Test
    fun `laisse les identifiants et les calculs tranquilles`() {
        assertEquals("snake_case_var 2*3*4 a_b", Markdown.plain("snake_case_var 2*3*4 a_b"))
    }

    @Test
    fun `message coupe au milieu du gras`() {
        assertEquals("Fini : le module", Markdown.plain("Fini : **le module"))
    }
}
