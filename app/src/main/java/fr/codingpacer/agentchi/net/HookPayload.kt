package fr.codingpacer.agentchi.net

import fr.codingpacer.agentchi.data.Agent
import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.EventType
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
    /** Question à laquelle la montre peut répondre. */
    val q: Ask? = null,
    /** Type « ask_end » : question qui n'attend plus de réponse. */
    val e: String? = null,
    /** Agent : absent pour Claude Code, « codex » pour Codex. */
    val ag: String? = null,
)

object EventParser {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Événement contenu dans un push FCM du serveur (champs `id`, `time`, `payload`).
     * Retourne null si le message est incomplet ou illisible.
     */
    fun fromPush(data: Map<String, String>): AgentEvent? {
        val id = data["id"]?.takeIf { it.isNotBlank() } ?: return null
        val time = data["time"]?.toLongOrNull() ?: return null
        return toEvent(id, time, data["payload"].orEmpty())
    }

    /** Identifiant de la question retirée par un push « ask_end », null pour un autre push. */
    fun askEnd(data: Map<String, String>): String? {
        val payload = runCatching { json.decodeFromString<HookPayload>(data["payload"].orEmpty()) }.getOrNull()
        return payload?.takeIf { it.t == ASK_END }?.e?.takeIf { it.isNotBlank() }
    }

    private const val ASK_END = "ask_end"

    fun toEvent(id: String, time: Long, payloadJson: String): AgentEvent? {
        val payload = runCatching { json.decodeFromString<HookPayload>(payloadJson) }.getOrNull() ?: return null
        val type = EventType.fromKey(payload.t)
        return AgentEvent(
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
            ask = payload.q?.takeIf { it.id.isNotBlank() },
            agent = Agent.fromKey(payload.ag),
        )
    }
}
