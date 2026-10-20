package com.claudecompanion.data

import kotlinx.serialization.Serializable

/**
 * Types d'événements envoyés par le hook Claude Code.
 * La clé correspond au tag `cc-<clé>` publié par `hooks/claude-companion-notify.sh`.
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
    /** Compte Claude (e-mail) ou libellé choisi sur l'ordinateur. */
    val account: String? = null,
    /** Horodatage Unix en secondes. */
    val time: Long,
) {
    val title: String get() = "${type.emoji} ${type.label}"
}
