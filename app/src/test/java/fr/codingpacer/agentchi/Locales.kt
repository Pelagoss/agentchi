package fr.codingpacer.agentchi

import java.util.Locale

/** Exécute [block] avec la langue [locale] (textes de l'interface, voir text/Tr.kt), puis rétablit la précédente. */
fun <T> inLocale(locale: Locale, block: () -> T): T {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        Locale.setDefault(previous)
    }
}
