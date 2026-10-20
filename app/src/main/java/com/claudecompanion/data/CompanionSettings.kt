package com.claudecompanion.data

import com.claudecompanion.net.PayloadCrypto

enum class AlertStyle(val label: String) {
    FULL_SCREEN("Plein écran"),
    HEADS_UP("Notification"),
    SILENT("Discrète"),
}

enum class VibrationPattern(val label: String, val timings: LongArray?) {
    NONE("Aucune", null),
    SHORT("Courte", longArrayOf(0, 120)),
    DOUBLE("Double", longArrayOf(0, 100, 120, 100)),
    LONG("Longue", longArrayOf(0, 500)),
    HEARTBEAT("Battement", longArrayOf(0, 80, 100, 80, 400, 80, 100, 80)),
}

data class CompanionSettings(
    val serverUrl: String = DEFAULT_SERVER,
    val topic: String = "",
    val token: String = "",
    /** Clé secrète partagée avec les ordinateurs appairés (chiffrement + signature). */
    val secret: String = "",
    /** Comptes Claude dont on ignore les événements. */
    val mutedAccounts: Set<String> = emptySet(),
    /** Notifications de l'appli Claude relayées par le téléphone. */
    val phoneNotifications: Boolean = true,
    /** Événements du hook Claude Code (terminal). */
    val localSessions: Boolean = true,
    val cloudSessions: Boolean = true,
    val instantMode: Boolean = false,
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
) {
    /** Faut-il ignorer complètement cet événement (compte ou origine filtrés) ? */
    fun isFiltered(event: ClaudeEvent): Boolean =
        (event.account != null && event.account in mutedAccounts) ||
            when {
                event.fromPhone -> !phoneNotifications
                event.cloud -> !cloudSessions
                else -> !localSessions
            }

    /**
     * Code à saisir dans `hooks/install.sh` : la clé, par groupes de 5 (« k7m2x-q9pza-… »).
     * Le sujet ntfy en est déduit. Un ancien appairage (sujet aléatoire) garde le format « sujet.clé ».
     */
    val pairingCode: String
        get() = if (topic == PayloadCrypto.topicFor(secret)) secret.chunked(5).joinToString("-") else "$topic.$secret"

    fun styleFor(type: EventType) = if (type.urgent) urgentStyle else infoStyle
    fun vibrationFor(type: EventType) = if (type.urgent) urgentVibration else infoVibration

    /** Indique si un événement doit être muet à l'heure donnée (0-23). */
    fun isMuted(type: EventType, hour: Int): Boolean {
        if (!quietHoursEnabled) return false
        if (type.urgent && quietAllowsUrgent) return false
        return isInQuietHours(hour, quietStart, quietEnd)
    }

    companion object {
        const val DEFAULT_SERVER = "https://ntfy.sh"
        val TOPIC_REGEX = Regex("^[-_A-Za-z0-9]{1,64}$")

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
