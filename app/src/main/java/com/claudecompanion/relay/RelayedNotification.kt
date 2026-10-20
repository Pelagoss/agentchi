package com.claudecompanion.relay

import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.EventType
import kotlinx.serialization.Serializable

/**
 * Notification de l'appli Claude relayée par le téléphone.
 * Même format que `com.claudecompanion.phone.RelayedNotification`.
 */
@Serializable
data class RelayedNotification(
    val key: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val conversation: String? = null,
    val channel: String? = null,
    val time: Long,
)

object RelayPaths {
    const val NOTIFICATION = "/claude/notification"
    const val OPEN = "/claude/open"
}

/** Transforme une notification de l'appli Claude en événement (logique pure, testée en JVM). */
object PhoneEventMapper {
    private val permission = Regex("permission|autoris|approv|allow|accès|access")
    private val input = Regex("waiting|needs? your|need(s)? input|input needed|question|attend|ta réponse|your response|respond")
    private val error = Regex("error|failed|failure|erreur|échec|echec")
    private val done = Regex("complete|finished|\\bdone\\b|terminé|termine|fini|ready|prêt|pret")

    fun classify(title: String?, text: String?): EventType {
        val s = "${title.orEmpty()} ${text.orEmpty()}".lowercase()
        return when {
            permission.containsMatchIn(s) -> EventType.PERMISSION
            input.containsMatchIn(s) -> EventType.INPUT_NEEDED
            error.containsMatchIn(s) -> EventType.ERROR
            done.containsMatchIn(s) -> EventType.TASK_DONE
            else -> EventType.OTHER
        }
    }

    fun toEvent(n: RelayedNotification): ClaudeEvent {
        val title = n.conversation?.takeIf { it.isNotBlank() } ?: n.title?.takeIf { it.isNotBlank() }
        return ClaudeEvent(
            id = "phone-${n.key.hashCode()}-${n.time}",
            type = classify(n.title, n.text),
            message = n.text?.takeIf { it.isNotBlank() } ?: n.title.orEmpty(),
            project = title,
            time = n.time / 1000,
            fromPhone = true,
            phoneKey = n.key,
        )
    }
}
