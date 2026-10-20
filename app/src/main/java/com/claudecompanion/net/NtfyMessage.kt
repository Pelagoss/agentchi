package com.claudecompanion.net

import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.EventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Message tel que renvoyé par l'API JSON de ntfy (https://docs.ntfy.sh/subscribe/api/). */
@Serializable
data class NtfyMessage(
    val id: String,
    val time: Long,
    val event: String,
    val topic: String = "",
    val title: String? = null,
    val message: String? = null,
    val tags: List<String>? = null,
    val priority: Int? = null,
)

object EventParser {
    private const val TYPE_PREFIX = "cc-"
    private const val SESSION_PREFIX = "ccs-"

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Parse une ligne du flux JSON ntfy ; null si invalide ou si ce n'est pas un message. */
    fun parseLine(line: String): NtfyMessage? {
        if (line.isBlank()) return null
        val msg = runCatching { json.decodeFromString<NtfyMessage>(line) }.getOrNull() ?: return null
        return msg.takeIf { it.event == "message" }
    }

    fun toEvent(msg: NtfyMessage): ClaudeEvent {
        val tags = msg.tags.orEmpty()
        val typeKey = tags.firstOrNull { it.startsWith(TYPE_PREFIX) && !it.startsWith(SESSION_PREFIX) }
            ?.removePrefix(TYPE_PREFIX)
        val session = tags.firstOrNull { it.startsWith(SESSION_PREFIX) }?.removePrefix(SESSION_PREFIX)
        val type = EventType.fromKey(typeKey)
        return ClaudeEvent(
            id = msg.id,
            type = type,
            message = msg.message?.takeIf { it.isNotBlank() && it != "triggered" } ?: type.label,
            project = msg.title?.takeIf { it.isNotBlank() },
            session = session?.takeIf { it.isNotBlank() },
            time = msg.time,
        )
    }
}
