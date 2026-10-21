package fr.codingpacer.agentchi.notify

import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.text.Markdown

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
            0 -> "${events.size} événements"
            1 -> "sur ${projects.single()}"
            else -> "sur ${projects.size} projets : ${projects.joinToString(", ")}"
        }
        val lines = events.take(MAX_LINES).map { e ->
            listOfNotNull(e.type.emoji, e.project, Markdown.plain(e.message).lineSequence().firstOrNull()).joinToString(" ")
        }
        return Summary(title, text, lines)
    }

    /** « 1 tâche terminée », « 3 tâches terminées ». */
    fun count(type: EventType, n: Int): String {
        val (one, many) = when (type) {
            EventType.TASK_DONE -> "tâche terminée" to "tâches terminées"
            EventType.SUBAGENT_DONE -> "sous-agent terminé" to "sous-agents terminés"
            EventType.SESSION_START -> "session démarrée" to "sessions démarrées"
            EventType.SESSION_END -> "session terminée" to "sessions terminées"
            EventType.COMPACT -> "compaction" to "compactions"
            EventType.PERMISSION -> "demande d'autorisation" to "demandes d'autorisation"
            EventType.INPUT_NEEDED -> "réponse attendue" to "réponses attendues"
            EventType.ERROR -> "erreur" to "erreurs"
            EventType.OTHER -> "notification" to "notifications"
            EventType.PROMPT -> "message" to "messages"
        }
        return "$n ${if (n > 1) many else one}"
    }
}
