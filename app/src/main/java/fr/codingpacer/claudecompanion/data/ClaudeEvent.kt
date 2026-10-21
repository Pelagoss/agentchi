package fr.codingpacer.claudecompanion.data

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
    /** Horodatage Unix en secondes. */
    val time: Long,
    /** Question à laquelle répondre depuis la montre. */
    val ask: Ask? = null,
    /** Réponse donnée depuis la montre (voir [AskAnswer.summary]), ou [AskAnswer.UNANSWERED]. */
    val answer: String? = null,
) {
    /** Question qui attend encore une réponse. */
    val isPendingAsk: Boolean get() = ask != null && answer == null && !ask.isExpired()

    /** Une question garde le même identifiant de notification, pour la retirer quand elle expire. */
    val notificationId: Int get() = (ask?.id ?: id).hashCode()

    val title: String
        get() = "${type.emoji} ${type.label}"

    /** Peut-on ouvrir l'événement sur le téléphone ? */
    val openableOnPhone: Boolean get() = url != null

    /** « ☁️ projet · compte » : d'où vient l'événement. */
    val origin: String
        get() = (if (cloud) "☁️ " else "") +
            listOfNotNull(project, account?.substringBefore('@')).joinToString(" · ").ifEmpty { if (cloud) "Cloud" else "Claude" }
}
