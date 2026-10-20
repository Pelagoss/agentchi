package com.claudecompanion.net

import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.EventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ligne du flux du serveur Claude Companion. Le format est celui de l'API d'abonnement
 * de ntfy (https://docs.ntfy.sh/subscribe/api/).
 */
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

    /** Parse une ligne du flux JSON ; null si invalide ou si ce n'est pas un message. */
    fun parseLine(line: String): NtfyMessage? {
        if (line.isBlank()) return null
        val msg = runCatching { json.decodeFromString<NtfyMessage>(line) }.getOrNull() ?: return null
        return msg.takeIf { it.event == "message" }
    }

    /**
     * Événement contenu dans un message du serveur. Le flux n'est lisible qu'avec le jeton
     * de la montre : seuls les ordinateurs appairés avec elle y publient.
     */
    fun toEvent(msg: NtfyMessage): ClaudeEvent? {
        val payload = runCatching { json.decodeFromString<HookPayload>(msg.message.orEmpty()) }.getOrNull() ?: return null
        val type = EventType.fromKey(payload.t)
        return ClaudeEvent(
            id = msg.id,
            type = type,
            message = payload.m.ifBlank { type.label },
            project = payload.p?.takeIf { it.isNotBlank() },
            session = payload.s?.takeIf { it.isNotBlank() },
            account = payload.a?.takeIf { it.isNotBlank() },
            cloud = payload.c,
            // Seuls les liens claude.ai sont ouverts sur le téléphone
            url = payload.u?.takeIf { it.startsWith("https://claude.ai/") },
            time = msg.time,
        )
    }
}
