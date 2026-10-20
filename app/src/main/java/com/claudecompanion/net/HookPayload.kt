package com.claudecompanion.net

import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.EventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Événement de hook mis en forme par le serveur (voir server/hook.go, Payload). */
@Serializable
data class HookPayload(
    /** Type d'événement (clé de [EventType]). */
    val t: String,
    val m: String = "",
    /** Projet (nom du dossier). */
    val p: String? = null,
    /** Session (8 premiers caractères de l'id). */
    val s: String? = null,
    /** Nom donné au code d'appairage (ordinateur, environnement cloud). */
    val a: String? = null,
    /** Session cloud. */
    val c: Boolean = false,
    /** Lien de la session cloud. */
    val u: String? = null,
)

object EventParser {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Événement contenu dans un push FCM du serveur (champs `id`, `time`, `payload`).
     * Retourne null si le message est incomplet ou illisible.
     */
    fun fromPush(data: Map<String, String>): ClaudeEvent? {
        val id = data["id"]?.takeIf { it.isNotBlank() } ?: return null
        val time = data["time"]?.toLongOrNull() ?: return null
        return toEvent(id, time, data["payload"].orEmpty())
    }

    fun toEvent(id: String, time: Long, payloadJson: String): ClaudeEvent? {
        val payload = runCatching { json.decodeFromString<HookPayload>(payloadJson) }.getOrNull() ?: return null
        val type = EventType.fromKey(payload.t)
        return ClaudeEvent(
            id = id,
            type = type,
            message = payload.m.ifBlank { type.label },
            project = payload.p?.takeIf { it.isNotBlank() },
            session = payload.s?.takeIf { it.isNotBlank() },
            account = payload.a?.takeIf { it.isNotBlank() },
            cloud = payload.c,
            // Seuls les liens claude.ai sont ouverts sur le téléphone
            url = payload.u?.takeIf { it.startsWith("https://claude.ai/") },
            time = time,
        )
    }
}
