package com.claudecompanion.phone

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Notification de l'appli Claude transmise à la montre.
 * Même format que `com.claudecompanion.relay.RelayedNotification` côté montre.
 */
@Serializable
data class RelayedNotification(
    /** Clé Android de la notification, pour l'ouvrir depuis la montre. */
    val key: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val conversation: String? = null,
    val channel: String? = null,
    /** Horodatage en millisecondes. */
    val time: Long,
)

object Paths {
    const val NOTIFICATION = "/claude/notification"
    const val OPEN = "/claude/open"
}

val relayJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Règles de relais (sans dépendance Android, testées en JVM). */
object RelayPolicy {
    const val CLAUDE_PACKAGE = "com.anthropic.claude"

    fun shouldRelay(packageName: String, ongoing: Boolean, groupSummary: Boolean, hasContent: Boolean): Boolean =
        packageName == CLAUDE_PACKAGE && !ongoing && !groupSummary && hasContent

    /** Empreinte pour ne pas renvoyer une notification simplement mise à jour à l'identique. */
    fun fingerprint(n: RelayedNotification): String = listOf(n.key, n.title, n.text, n.conversation).joinToString("\u0000")
}
