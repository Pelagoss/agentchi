package fr.codingpacer.claudecompanion.data

import fr.codingpacer.claudecompanion.BuildConfig

enum class AlertStyle(val label: String) {
    FULL_SCREEN("Plein écran"),
    HEADS_UP("Notification"),
    SILENT("Discrète"),
}

/** Attente maximale d'une réponse de la montre, avant que l'ordinateur pose la question. */
enum class AnswerDelay(val seconds: Int, val label: String) {
    ONE_MINUTE(60, "1 min"),
    TWO_MINUTES(120, "2 min"),
    FIVE_MINUTES(300, "5 min"),
}

enum class VibrationPattern(val label: String, val timings: LongArray?) {
    NONE("Aucune", null),
    SHORT("Courte", longArrayOf(0, 120)),
    DOUBLE("Double", longArrayOf(0, 100, 120, 100)),
    LONG("Longue", longArrayOf(0, 500)),
    HEARTBEAT("Battement", longArrayOf(0, 80, 100, 80, 400, 80, 100, 80)),
}

data class CompanionSettings(
    /** Serveur Claude Companion (dossier server/ du dépôt), qui reçoit les hooks. */
    val serverUrl: String = DEFAULT_SERVER,
    /** Identifiant et jeton de la montre sur ce serveur, obtenus à la connexion. */
    val deviceId: String = "",
    val deviceToken: String = "",
    /** Ordinateurs (ou comptes) dont on ignore les événements. */
    val mutedAccounts: Set<String> = emptySet(),
    /** Événements du hook Claude Code sur un ordinateur (plugin). */
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
    /** Répondre depuis la montre aux autorisations, questions et messages de Claude. */
    val remoteAnswers: Boolean = false,
    val answerDelay: AnswerDelay = AnswerDelay.TWO_MINUTES,
    /** Réponses proposées d'un toucher quand Claude attend un message. */
    val quickReplies: List<String> = DEFAULT_QUICK_REPLIES,
) {
    /** Faut-il ignorer complètement cet événement (compte ou origine filtrés) ? */
    fun isFiltered(event: ClaudeEvent): Boolean =
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

    /** Indique si un événement doit être muet à l'heure donnée (0-23). */
    fun isMuted(type: EventType, hour: Int): Boolean {
        if (!quietHoursEnabled) return false
        if (type.urgent && quietAllowsUrgent) return false
        return isInQuietHours(hour, quietStart, quietEnd)
    }

    companion object {
        /** Défini à la compilation : -PcompanionServer=https://… ou variable COMPANION_SERVER. */
        val DEFAULT_SERVER: String = BuildConfig.COMPANION_SERVER
        val DEVICE_ID_REGEX = Regex("^[-_A-Za-z0-9]{1,64}$")
        val DEFAULT_QUICK_REPLIES = listOf("Oui", "Non", "Continue", "Fais au mieux")
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
