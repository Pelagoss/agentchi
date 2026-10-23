package fr.codingpacer.agentchi.data

import fr.codingpacer.agentchi.text.tr
import kotlinx.serialization.Serializable

/** Où en est une session, d'après le dernier événement reçu. */
enum class SessionState(val emoji: String, private val fr: String, private val en: String) {
    WORKING("⚙️", "Au travail", "Working"),
    WAITING("🔔", "Attend ta réponse", "Waiting for you"),
    IDLE("✅", "À toi de jouer", "Your turn"),
    ERROR("⚠️", "Erreur", "Error");

    val label: String get() = tr(fr, en)
}

/** Une session d'agent en cours (écran « Sessions en cours »). */
@Serializable
data class AgentSession(
    val key: String,
    val agent: Agent = Agent.CLAUDE,
    val project: String? = null,
    val account: String? = null,
    val cloud: Boolean = false,
    val url: String? = null,
    val state: SessionState,
    /** Début de l'état actuel (Unix, secondes). */
    val since: Long,
    /** Dernier événement reçu (Unix, secondes). */
    val lastEvent: Long,
    /** Dernier message de l'agent. */
    val message: String? = null,
    /** Question en attente d'une réponse de la montre. */
    val askId: String? = null,
) {
    /** « ☁️ Claude · projet · compte » : comme [AgentEvent.origin]. */
    val origin: String
        get() = (if (cloud) "☁️ " else "") + listOfNotNull(agent.label, project, account?.substringBefore('@')).joinToString(" · ")
}

/**
 * Suit l'état des sessions à partir des événements des hooks, sans rien demander au serveur.
 * Logique pure, testable en JVM.
 */
object Sessions {
    /** Une session muette depuis aussi longtemps est oubliée (fermée sans SessionEnd, ordinateur éteint…). */
    const val STALE_SECONDS = 12 * 3600L
    private const val MAX_SESSIONS = 20

    /** Clé d'une session : l'id court envoyé par le serveur, sinon son origine. */
    fun keyOf(event: AgentEvent): String =
        listOf(event.agent.key, event.account.orEmpty(), event.session ?: "projet:" + event.project.orEmpty()).joinToString("|")

    fun onEvent(sessions: List<AgentSession>, event: AgentEvent): List<AgentSession> {
        val key = keyOf(event)
        val current = sessions.firstOrNull { it.key == key }
        if (event.type == EventType.SESSION_END) return sessions.filter { it.key != key }
        val state = when (event.type) {
            EventType.PERMISSION, EventType.INPUT_NEEDED -> SessionState.WAITING
            EventType.TASK_DONE, EventType.SESSION_START -> SessionState.IDLE
            EventType.SUBAGENT_DONE, EventType.COMPACT, EventType.PROMPT -> SessionState.WORKING
            EventType.ERROR -> SessionState.ERROR
            EventType.OTHER, EventType.SESSION_END -> current?.state ?: SessionState.WORKING
        }
        val session = AgentSession(
            key = key,
            agent = event.agent,
            project = event.project ?: current?.project,
            account = event.account ?: current?.account,
            cloud = event.cloud,
            url = event.url ?: current?.url,
            state = state,
            since = if (current?.state == state) current.since else event.time,
            lastEvent = event.time,
            // Un message tapé sur l'ordinateur n'est pas transmis : on garde celui de l'agent.
            message = if (event.type == EventType.PROMPT) current?.message else event.message,
            askId = event.ask?.id,
        )
        return (listOf(session) + sessions.filter { it.key != key }).take(MAX_SESSIONS)
    }

    /** Réponse envoyée depuis la montre : la session reprend le travail (sauf si l'ordinateur prend le relais). */
    fun onAnswer(sessions: List<AgentSession>, askId: String, answer: AskAnswer, nowSeconds: Long): List<AgentSession> =
        sessions.map { s ->
            when {
                s.askId != askId -> s
                answer.decision == AskAnswer.PASS -> s.copy(askId = null)
                // « Terminer » sans message : la session reste en attente de l'utilisateur.
                answer.decision != AskAnswer.DENY || !answer.message.isNullOrBlank() ->
                    s.copy(state = SessionState.WORKING, since = nowSeconds, askId = null)
                else -> s.copy(askId = null)
            }
        }

    /** La question n'attend plus de réponse de la montre (expirée, retirée par le serveur). */
    fun onAskEnded(sessions: List<AgentSession>, askId: String): List<AgentSession> =
        sessions.map { if (it.askId == askId) it.copy(askId = null) else it }

    /** Sessions à afficher : les plus urgentes d'abord, puis les plus récentes. */
    fun visible(sessions: List<AgentSession>, nowSeconds: Long): List<AgentSession> =
        sessions.filter { nowSeconds - it.lastEvent < STALE_SECONDS }
            .sortedWith(compareBy<AgentSession> { it.state != SessionState.WAITING }.thenByDescending { it.lastEvent })
}
