package com.claudecompanion.data

import kotlinx.serialization.Serializable

/**
 * Types d'événements envoyés par les hooks Claude Code.
 * La clé correspond au champ `t` produit par le serveur (server/hook.go).
 */
enum class EventType(
    val key: String,
    val label: String,
    val emoji: String,
    val urgent: Boolean,
    val defaultEnabled: Boolean,
) {
    PERMISSION("permission", "Demande d'autorisation", "🔐", urgent = true, defaultEnabled = true),
    INPUT_NEEDED("input", "Claude attend ta réponse", "💬", urgent = true, defaultEnabled = true),
    TASK_DONE("done", "Tâche terminée", "✅", urgent = false, defaultEnabled = true),
    SUBAGENT_DONE("subagent", "Sous-agent terminé", "🤖", urgent = false, defaultEnabled = false),
    SESSION_START("session_start", "Session démarrée", "🚀", urgent = false, defaultEnabled = false),
    SESSION_END("session_end", "Session terminée", "🏁", urgent = false, defaultEnabled = true),
    COMPACT("compact", "Compaction du contexte", "🗜️", urgent = false, defaultEnabled = false),
    ERROR("error", "Erreur", "⚠️", urgent = true, defaultEnabled = true),
    OTHER("other", "Autre notification", "✳️", urgent = false, defaultEnabled = true);

    companion object {
        fun fromKey(key: String?): EventType = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

@Serializable
data class ClaudeEvent(
    val id: String,
    val type: EventType,
    val message: String,
    val project: String? = null,
    val session: String? = null,
    /** Nom de l'ordinateur ou de l'environnement cloud (code d'appairage), ou compte. */
    val account: String? = null,
    /** Session cloud (claude.ai/code) plutôt que locale. */
    val cloud: Boolean = false,
    /** Lien de la session cloud, ouvrable sur le téléphone. */
    val url: String? = null,
    /** Notification de l'appli Claude relayée par le téléphone. */
    val fromPhone: Boolean = false,
    /** Clé de la notification d'origine sur le téléphone, pour l'y ouvrir. */
    val phoneKey: String? = null,
    /** Horodatage Unix en secondes. */
    val time: Long,
) {
    val title: String
        get() = if (fromPhone && type == EventType.OTHER) "💬 ${project ?: "Claude"}" else "${type.emoji} ${type.label}"

    /** Peut-on ouvrir l'événement sur le téléphone ? */
    val openableOnPhone: Boolean get() = url != null || phoneKey != null

    /** « ☁️ projet · compte » : d'où vient l'événement. */
    val origin: String
        get() = (if (fromPhone) "📱 " else if (cloud) "☁️ " else "") +
            listOfNotNull(project, account?.substringBefore('@')).joinToString(" · ").ifEmpty { if (cloud) "Cloud" else "Claude" }
}
