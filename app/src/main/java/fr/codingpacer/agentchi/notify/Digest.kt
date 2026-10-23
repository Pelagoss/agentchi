package fr.codingpacer.agentchi.notify

import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.text.Markdown
import fr.codingpacer.agentchi.text.tr

/**
 * Résumé des événements non urgents (« ✅ 3 tâches terminées sur 2 projets ») : une seule
 * notification au lieu d'une par tâche. Logique pure, testable en JVM.
 */
object Digest {
    /** Au-delà, les plus anciens sortent du résumé. */
    const val MAX_EVENTS = 50

    /** Lignes détaillées sous le résumé (limite des notifications InboxStyle). */
    private const val MAX_LINES = 5

    /** L'événement peut-il rejoindre le résumé ? Pas les urgences, ni les questions qui attendent une réponse. */
    fun accepts(event: AgentEvent): Boolean = !event.type.urgent && event.ask == null

    data class Summary(val title: String, val text: String, val lines: List<String>)

    /** [events] du plus récent au plus ancien. */
    fun summary(events: List<AgentEvent>): Summary {
        val counts = events.groupingBy { it.type }.eachCount()
        // Le type le plus fréquent donne l'emoji du titre.
        val main = counts.maxByOrNull { it.value }?.key ?: EventType.OTHER
        val title = main.emoji + " " + EventType.entries
            .filter { it in counts }
            .sortedByDescending { counts.getValue(it) }
            .joinToString(" · ") { count(it, counts.getValue(it)) }
        val projects = events.mapNotNull { it.project }.distinct()
        val text = when (projects.size) {
            0 -> tr("${events.size} événements", "${events.size} events")
            1 -> tr("sur ${projects.single()}", "on ${projects.single()}")
            else -> tr("sur ${projects.size} projets : ", "across ${projects.size} projects: ") + projects.joinToString(", ")
        }
        val lines = events.take(MAX_LINES).map { e ->
            listOfNotNull(e.type.emoji, e.project, Markdown.plain(e.message).lineSequence().firstOrNull()).joinToString(" ")
        }
        return Summary(title, text, lines)
    }

    /** « 1 tâche terminée », « 3 tâches terminées » (« 1 task done », « 3 tasks done »). */
    fun count(type: EventType, n: Int): String {
        val (one, many) = when (type) {
            EventType.TASK_DONE -> tr("tâche terminée", "task done") to tr("tâches terminées", "tasks done")
            EventType.SUBAGENT_DONE -> tr("sous-agent terminé", "subagent finished") to tr("sous-agents terminés", "subagents finished")
            EventType.SESSION_START -> tr("session démarrée", "session started") to tr("sessions démarrées", "sessions started")
            EventType.SESSION_END -> tr("session terminée", "session ended") to tr("sessions terminées", "sessions ended")
            EventType.COMPACT -> "compaction" to "compactions"
            EventType.PERMISSION -> tr("demande d'autorisation", "permission request") to tr("demandes d'autorisation", "permission requests")
            EventType.INPUT_NEEDED -> tr("réponse attendue", "reply expected") to tr("réponses attendues", "replies expected")
            EventType.ERROR -> tr("erreur", "error") to tr("erreurs", "errors")
            EventType.OTHER -> "notification" to "notifications"
            EventType.PROMPT -> "message" to "messages"
        }
        return "$n ${if (n > 1) many else one}"
    }
}
