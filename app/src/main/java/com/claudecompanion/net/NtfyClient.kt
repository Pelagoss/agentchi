package com.claudecompanion.net

import com.claudecompanion.data.CompanionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Abonnement à un sujet ntfy (https://ntfy.sh ou serveur auto-hébergé). */
class NtfyClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // ntfy envoie un « keepalive » toutes les 45 s sur les connexions ouvertes
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    /** Récupère les messages publiés depuis [since] puis ferme la connexion. */
    suspend fun poll(settings: CompanionSettings, since: String): List<NtfyMessage> = withContext(Dispatchers.IO) {
        val request = request(settings, "poll=1&since=$since")
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ntfy HTTP ${response.code}")
            response.body?.string().orEmpty().lineSequence().mapNotNull(EventParser::parseLine).toList()
        }
    }

    /**
     * Garde une connexion ouverte et appelle [onMessage] pour chaque message reçu.
     * Retourne quand la connexion se ferme ; lève une exception en cas d'erreur.
     */
    suspend fun stream(
        settings: CompanionSettings,
        since: String,
        onMessage: suspend (NtfyMessage) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val call = http.newCall(request(settings, "since=$since"))
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("ntfy HTTP ${response.code}")
            val source = response.body?.source() ?: return@use
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                EventParser.parseLine(line)?.let { onMessage(it) }
            }
        }
    }

    private fun request(settings: CompanionSettings, query: String): Request {
        require(CompanionSettings.TOPIC_REGEX.matches(settings.topic)) { "Sujet ntfy invalide" }
        val url = "${settings.serverUrl.trimEnd('/')}/${settings.topic}/json?$query"
        return Request.Builder().url(url).apply {
            if (settings.token.isNotBlank()) header("Authorization", "Bearer ${settings.token}")
        }.build()
    }
}
