package fr.codingpacer.agentchi.push

import android.content.Context
import fr.codingpacer.agentchi.BuildConfig
import fr.codingpacer.agentchi.text.tr
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Firebase Cloud Messaging : seul canal par lequel la montre reçoit les événements.
 * Le projet Firebase est fourni à la compilation (BuildConfig.FIREBASE_*), sans google-services.json.
 */
object Push {
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    /** Initialise Firebase au démarrage de l'appli. Sans configuration, le push est désactivé. */
    fun init(context: Context) {
        if (!isConfigured || FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }

    /** Jeton FCM de la montre, à transmettre au serveur. */
    suspend fun token(): String {
        check(isConfigured) { tr("Push FCM non configuré dans cette version de l'appli (projet Firebase manquant)", "FCM push isn't set up in this build of the app (no Firebase project)") }
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException(tr("Jeton FCM indisponible (services Google Play ?) : ", "FCM token unavailable (Google Play services?): ") + e.message, e)
        }
    }
}
