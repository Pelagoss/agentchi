package fr.codingpacer.claudecompanion.net

import fr.codingpacer.claudecompanion.data.AskAnswer
import fr.codingpacer.claudecompanion.data.CompanionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class DeviceRegistration(val id: String, val token: String)

/** Code d'appairage d'un ordinateur ou d'un environnement cloud. [code] n'est connu qu'à la création. */
@Serializable
data class PairingCode(
    val id: String,
    val label: String,
    val created: String,
    @SerialName("last_used") val lastUsed: String? = null,
    @SerialName("last_project") val lastProject: String? = null,
    val code: String? = null,
)

/** Erreur renvoyée par le serveur, avec son message lisible. */
class ServerException(val status: Int, message: String) : IOException(message)

/** API du serveur Claude Companion (dossier server/ du dépôt). */
class CompanionApi(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun register(serverUrl: String, name: String, pushToken: String): DeviceRegistration =
        call(serverUrl, null, "POST", "/v1/devices", jsonBody { put("name", name); put("push_token", pushToken) })
            .let { EventParser.json.decodeFromString(DeviceRegistration.serializer(), it) }

    /** Transmet le jeton FCM de la montre (Firebase le renouvelle de temps en temps). */
    suspend fun setPushToken(settings: CompanionSettings, token: String) {
        call(settings, "PUT", "/v1/device/push", jsonBody { put("token", token) })
    }

    /** Active ou non les réponses depuis la montre : les hooks attendent alors sa réponse. */
    suspend fun setAnswers(settings: CompanionSettings, enabled: Boolean) {
        call(settings, "PUT", "/v1/device/answers", jsonBody { put("enabled", enabled) })
    }

    /** Répond à une question de Claude. HTTP 410 : Claude ne l'attend plus. */
    suspend fun answer(settings: CompanionSettings, askId: String, answer: AskAnswer) {
        val body = EventParser.json.encodeToString(AskAnswer.serializer(), answer).toRequestBody("application/json".toMediaType())
        call(settings, "POST", "/v1/asks/$askId", body)
    }

    suspend fun createCode(settings: CompanionSettings, label: String): PairingCode =
        call(settings, "POST", "/v1/codes", jsonBody { put("label", label) })
            .let { EventParser.json.decodeFromString(PairingCode.serializer(), it) }

    suspend fun codes(settings: CompanionSettings): List<PairingCode> =
        call(settings, "GET", "/v1/codes")
            .let { EventParser.json.decodeFromString(ListSerializer(PairingCode.serializer()), it) }

    suspend fun deleteCode(settings: CompanionSettings, id: String) {
        call(settings, "DELETE", "/v1/codes/$id")
    }

    /** Demande au serveur d'envoyer un push de test à la montre. */
    suspend fun test(settings: CompanionSettings) {
        call(settings, "POST", "/v1/test", jsonBody { })
    }

    /** Supprime la montre et tous ses codes du serveur. */
    suspend fun deleteDevice(settings: CompanionSettings) {
        call(settings, "DELETE", "/v1/device")
    }

    private suspend fun call(settings: CompanionSettings, method: String, path: String, body: RequestBody? = null) =
        call(settings.serverUrl, settings.deviceToken, method, path, body)

    private suspend fun call(serverUrl: String, token: String?, method: String, path: String, body: RequestBody? = null): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(serverUrl.trim().trimEnd('/') + path)
                .method(method, body)
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        EventParser.json.decodeFromString(ErrorBody.serializer(), text).error
                    }.getOrNull()
                    throw ServerException(response.code, message ?: "Erreur du serveur (HTTP ${response.code})")
                }
                text
            }
        }

    @Serializable
    private data class ErrorBody(val error: String)

    private fun jsonBody(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): RequestBody =
        buildJsonObject(build).toString().toRequestBody("application/json".toMediaType())
}
