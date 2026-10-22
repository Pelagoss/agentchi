package fr.codingpacer.agentchi.data

import kotlinx.serialization.Serializable

/** Agent de code à l'origine d'un événement. La clé correspond au champ `ag` du serveur. */
enum class Agent(val key: String, val label: String, val product: String) {
    CLAUDE("claude", "Claude", "Claude Code"),
    CODEX("codex", "Codex", "Codex");

    companion object {
        fun fromKey(key: String?): Agent = entries.firstOrNull { it.key == key } ?: CLAUDE
    }
}

/**
 * Types d'événements envoyés par les hooks Claude Code et Codex.
 * La clé correspond au champ `t` produit par le serveur (server/hook.go).
 */
enum class EventType(
    val key: String,
    val label: String,
    val emoji: String,
    val urgent: Boolean,
    val defaultEnabled: Boolean,
    /** Jamais notifié ni gardé dans l'historique : sert seulement à l'écran « Sessions en cours ». */
    val silent: Boolean = false,
) {
    PERMISSION("permission", "Demande d'autorisation", "🔐", urgent = true, defaultEnabled = true),
    INPUT_NEEDED("input", "Réponse attendue", "💬", urgent = true, defaultEnabled = true),
    TASK_DONE("done", "Tâche terminée", "✅", urgent = false, defaultEnabled = true),
    SUBAGENT_DONE("subagent", "Sous-agent terminé", "🤖", urgent = false, defaultEnabled = false),
    SESSION_START("session_start", "Session démarrée", "🚀", urgent = false, defaultEnabled = false),
    SESSION_END("session_end", "Session terminée", "🏁", urgent = false, defaultEnabled = true),
    COMPACT("compact", "Compaction du contexte", "🗜️", urgent = false, defaultEnabled = false),
    ERROR("error", "Erreur", "⚠️", urgent = true, defaultEnabled = true),
    OTHER("other", "Autre notification", "✳️", urgent = false, defaultEnabled = true),
    PROMPT("prompt", "Message envoyé à l'agent", "⌨️", urgent = false, defaultEnabled = false, silent = true);

    companion object {
        fun fromKey(key: String?): EventType = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

@Serializable
data class AgentEvent(
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
    val agent: Agent = Agent.CLAUDE,
) {
    /** Question qui attend encore une réponse. */
    val isPendingAsk: Boolean get() = ask != null && answer == null && !ask.isExpired()

    /** Une question garde le même identifiant de notification, pour la retirer quand elle expire. */
    val notificationId: Int get() = (ask?.id ?: id).hashCode()

    val title: String
        get() = "${type.emoji} ${type.label}"

    /** Peut-on ouvrir l'événement sur le téléphone ? */
    val openableOnPhone: Boolean get() = url != null

    /** « ☁️ Claude · projet · compte » : d'où vient l'événement. */
    val origin: String
        get() = (if (cloud) "☁️ " else "") + listOfNotNull(agent.label, project, account?.substringBefore('@')).joinToString(" · ")
}
