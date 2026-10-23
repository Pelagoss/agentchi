package fr.codingpacer.agentchi.data

import fr.codingpacer.agentchi.BuildConfig
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.text.tr

enum class AlertStyle(private val fr: String, private val en: String) {
    FULL_SCREEN("Plein écran", "Full screen"),
    HEADS_UP("Notification", "Notification"),
    SILENT("Discrète", "Silent");

    val label: String get() = tr(fr, en)
}

/** Attente maximale d'une réponse de la montre, avant que l'ordinateur pose la question. */
enum class AnswerDelay(val seconds: Int, val label: String) {
    ONE_MINUTE(60, "1 min"),
    TWO_MINUTES(120, "2 min"),
    FIVE_MINUTES(300, "5 min"),
}

/** Icônes des actions de la mascotte (nourrir, jouer, dormir…). */
enum class IconSet(private val fr: String, private val en: String) {
    PIXEL("Pixel", "Pixel"),
    EMOJI("Emojis", "Emoji");

    val label: String get() = tr(fr, en)
}

/** Icône de l'appli dans le lanceur. */
enum class AppIcon(private val fr: String, private val en: String) {
    CLASSIC("Classique", "Classic"),
    MASCOT("Assortie à la mascotte", "Matches the mascot");

    val label: String get() = tr(fr, en)
}

enum class VibrationPattern(private val fr: String, private val en: String, val timings: LongArray?) {
    NONE("Aucune", "None", null),
    SHORT("Courte", "Short", longArrayOf(0, 120)),
    DOUBLE("Double", "Double", longArrayOf(0, 100, 120, 100)),
    LONG("Longue", "Long", longArrayOf(0, 500)),
    HEARTBEAT("Battement", "Heartbeat", longArrayOf(0, 80, 100, 80, 400, 80, 100, 80));

    val label: String get() = tr(fr, en)
}

data class CompanionSettings(
    /** Serveur Agentchi (dossier server/ du dépôt), qui reçoit les hooks. */
    val serverUrl: String = DEFAULT_SERVER,
    /** Identifiant et jeton de la montre sur ce serveur, obtenus à la connexion. */
    val deviceId: String = "",
    val deviceToken: String = "",
    /** Ordinateurs (ou comptes) dont on ignore les événements. */
    val mutedAccounts: Set<String> = emptySet(),
    /** Agents dont on ignore les événements. */
    val mutedAgents: Set<Agent> = emptySet(),
    /** Événements des hooks sur un ordinateur (plugin Claude Code, hooks Codex). */
    val localSessions: Boolean = true,
    val cloudSessions: Boolean = true,
    val enabledEvents: Set<EventType> = EventType.entries.filter { it.defaultEnabled }.toSet(),
    val urgentStyle: AlertStyle = AlertStyle.FULL_SCREEN,
    val urgentVibration: VibrationPattern = VibrationPattern.DOUBLE,
    val infoStyle: AlertStyle = AlertStyle.HEADS_UP,
    val infoVibration: VibrationPattern = VibrationPattern.SHORT,
    val sound: Boolean = false,
    val showPreview: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
    val quietAllowsUrgent: Boolean = true,
    /** Ne pas déranger ou mode Coucher de la montre : mêmes effets que les heures calmes. */
    val followSystemQuiet: Boolean = true,
    /** Un seul résumé pour les événements non urgents (« 3 tâches terminées sur 2 projets »). */
    val groupInfo: Boolean = true,
    /** Répondre depuis la montre aux autorisations, questions et messages des agents. */
    val remoteAnswers: Boolean = false,
    val answerDelay: AnswerDelay = AnswerDelay.TWO_MINUTES,
    /** Réponses proposées d'un toucher quand l'agent attend un message. */
    val quickReplies: List<String> = DEFAULT_QUICK_REPLIES,
    /** Apparence de la mascotte : espèce, couleurs, accessoire. */
    val mascot: MascotSprite.Look = MascotSprite.Look(),
    val iconSet: IconSet = IconSet.PIXEL,
    val appIcon: AppIcon = AppIcon.CLASSIC,
) {
    /** Faut-il ignorer complètement cet événement (compte ou origine filtrés) ? */
    fun isFiltered(event: AgentEvent): Boolean =
        event.agent in mutedAgents ||
            (event.account != null && event.account in mutedAccounts) ||
            when {
                event.cloud -> !cloudSessions
                else -> !localSessions
            }

    /** La montre est-elle enregistrée sur un serveur ? */
    val isConnected: Boolean
        get() = serverUrl.isNotBlank() && DEVICE_ID_REGEX.matches(deviceId) && deviceToken.isNotBlank()

    fun styleFor(type: EventType) = if (type.urgent) urgentStyle else infoStyle
    fun vibrationFor(type: EventType) = if (type.urgent) urgentVibration else infoVibration

    /**
     * Indique si un événement doit être muet à l'heure donnée (0-23).
     * [systemQuiet] : Ne pas déranger ou mode Coucher activé sur la montre.
     */
    fun isMuted(type: EventType, hour: Int, systemQuiet: Boolean = false): Boolean {
        val quiet = (quietHoursEnabled && isInQuietHours(hour, quietStart, quietEnd)) || (followSystemQuiet && systemQuiet)
        return quiet && !(type.urgent && quietAllowsUrgent)
    }

    companion object {
        /** Défini à la compilation : -PcompanionServer=https://… ou variable COMPANION_SERVER. */
        val DEFAULT_SERVER: String = BuildConfig.COMPANION_SERVER
        val DEVICE_ID_REGEX = Regex("^[-_A-Za-z0-9]{1,64}$")
        /** Dans la langue de la montre ; enregistrées seulement une fois modifiées (voir CompanionStore). */
        val DEFAULT_QUICK_REPLIES: List<String>
            get() = listOf(tr("Oui", "Yes"), tr("Non", "No"), tr("Continue", "Continue"), tr("Fais au mieux", "Do your best"))
        const val MAX_QUICK_REPLIES = 6

        fun isInQuietHours(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}

inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}
