package com.claudecompanion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.claudecompanion.net.EventParser
import com.claudecompanion.pet.PetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "companion")

/** Toutes les données persistées : paramètres, mascotte, historique, état de synchro. */
class CompanionStore(context: Context) {
    private val store = context.dataStore

    private object Keys {
        val server = stringPreferencesKey("server")
        val topic = stringPreferencesKey("topic")
        val token = stringPreferencesKey("token")
        val instant = booleanPreferencesKey("instant")
        val events = stringSetPreferencesKey("events")
        val urgentStyle = stringPreferencesKey("urgent_style")
        val urgentVibration = stringPreferencesKey("urgent_vibration")
        val infoStyle = stringPreferencesKey("info_style")
        val infoVibration = stringPreferencesKey("info_vibration")
        val sound = booleanPreferencesKey("sound")
        val preview = booleanPreferencesKey("preview")
        val quiet = booleanPreferencesKey("quiet")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val quietUrgent = booleanPreferencesKey("quiet_urgent")

        val pet = stringPreferencesKey("pet")
        val history = stringPreferencesKey("history")
        val since = stringPreferencesKey("since")
    }

    val settings: Flow<CompanionSettings> = store.data.map { p ->
        val d = CompanionSettings()
        CompanionSettings(
            serverUrl = p[Keys.server] ?: d.serverUrl,
            topic = p[Keys.topic] ?: d.topic,
            token = p[Keys.token] ?: d.token,
            instantMode = p[Keys.instant] ?: d.instantMode,
            enabledEvents = p[Keys.events]?.map { EventType.fromKey(it) }?.toSet() ?: d.enabledEvents,
            urgentStyle = enumOr(p[Keys.urgentStyle], d.urgentStyle),
            urgentVibration = enumOr(p[Keys.urgentVibration], d.urgentVibration),
            infoStyle = enumOr(p[Keys.infoStyle], d.infoStyle),
            infoVibration = enumOr(p[Keys.infoVibration], d.infoVibration),
            sound = p[Keys.sound] ?: d.sound,
            showPreview = p[Keys.preview] ?: d.showPreview,
            quietHoursEnabled = p[Keys.quiet] ?: d.quietHoursEnabled,
            quietStart = p[Keys.quietStart] ?: d.quietStart,
            quietEnd = p[Keys.quietEnd] ?: d.quietEnd,
            quietAllowsUrgent = p[Keys.quietUrgent] ?: d.quietAllowsUrgent,
        )
    }.distinctUntilChanged()

    suspend fun updateSettings(transform: (CompanionSettings) -> CompanionSettings) {
        store.edit { p ->
            val s = transform(settings.first())
            p[Keys.server] = s.serverUrl.trim().trimEnd('/')
            p[Keys.topic] = s.topic
            p[Keys.token] = s.token
            p[Keys.instant] = s.instantMode
            p[Keys.events] = s.enabledEvents.map { it.key }.toSet()
            p[Keys.urgentStyle] = s.urgentStyle.name
            p[Keys.urgentVibration] = s.urgentVibration.name
            p[Keys.infoStyle] = s.infoStyle.name
            p[Keys.infoVibration] = s.infoVibration.name
            p[Keys.sound] = s.sound
            p[Keys.preview] = s.showPreview
            p[Keys.quiet] = s.quietHoursEnabled
            p[Keys.quietStart] = s.quietStart
            p[Keys.quietEnd] = s.quietEnd
            p[Keys.quietUrgent] = s.quietAllowsUrgent
        }
    }

    /** Génère un sujet ntfy aléatoire au premier lancement (difficile à deviner). */
    suspend fun ensureTopic() {
        store.edit { p ->
            if (p[Keys.topic].isNullOrBlank()) {
                p[Keys.topic] = randomTopic()
                p[Keys.since] = (System.currentTimeMillis() / 1000).toString()
            }
        }
    }

    // --- Mascotte ---

    val pet: Flow<PetState> = store.data.map { p ->
        p[Keys.pet]?.let { runCatching { EventParser.json.decodeFromString<PetState>(it) }.getOrNull() }
            ?: PetState()
    }.distinctUntilChanged()

    suspend fun updatePet(transform: (PetState) -> PetState) {
        store.edit { p ->
            val current = p[Keys.pet]?.let { runCatching { EventParser.json.decodeFromString<PetState>(it) }.getOrNull() }
                ?: PetState(lastUpdate = System.currentTimeMillis())
            p[Keys.pet] = EventParser.json.encodeToString(PetState.serializer(), transform(current))
        }
    }

    // --- Historique des événements ---

    val history: Flow<List<ClaudeEvent>> = store.data.map { p -> decodeHistory(p[Keys.history]) }
        .distinctUntilChanged()

    /** Ajoute l'événement à l'historique. Retourne false s'il était déjà connu. */
    suspend fun addEvent(event: ClaudeEvent): Boolean {
        var added = false
        store.edit { p ->
            val list = decodeHistory(p[Keys.history])
            if (list.none { it.id == event.id }) {
                added = true
                val next = (listOf(event) + list).take(MAX_HISTORY)
                p[Keys.history] = EventParser.json.encodeToString(HISTORY_SERIALIZER, next)
            }
            p[Keys.since] = event.id
        }
        return added
    }

    suspend fun clearHistory() {
        store.edit { it.remove(Keys.history) }
    }

    /** Valeur du paramètre `since` de ntfy : dernier id reçu, ou horodatage. */
    suspend fun since(): String =
        store.data.first()[Keys.since] ?: (System.currentTimeMillis() / 1000).toString()

    private fun decodeHistory(raw: String?): List<ClaudeEvent> =
        raw?.let { runCatching { EventParser.json.decodeFromString(HISTORY_SERIALIZER, it) }.getOrNull() }
            ?: emptyList()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    companion object {
        private const val MAX_HISTORY = 30
        private val HISTORY_SERIALIZER = kotlinx.serialization.builtins.ListSerializer(ClaudeEvent.serializer())

        fun randomTopic(): String {
            val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return "claude-companion-" + (1..10).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}
