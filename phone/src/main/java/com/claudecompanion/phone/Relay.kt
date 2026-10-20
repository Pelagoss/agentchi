package com.claudecompanion.phone

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** État partagé du relais : intentions d'ouverture et journal des derniers envois. */
object Relay {
    private const val PREFS = "relay"
    private const val ENABLED = "enabled"
    private const val HIDE_ON_PHONE = "hide_on_phone"
    private const val MAX = 50

    data class LogEntry(val time: Long, val title: String, val text: String, val delivered: Int)

    /** Derniers relais, affichés dans l'écran du téléphone. */
    val log = mutableStateListOf<LogEntry>()

    private val openIntents = object : LinkedHashMap<String, PendingIntent>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>) = size > MAX
    }
    private val sent = ArrayDeque<String>()

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()

    fun hideOnPhone(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HIDE_ON_PHONE, false)

    fun setHideOnPhone(context: Context, hide: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(HIDE_ON_PHONE, hide).apply()

    /** La dernière notification relayée a-t-elle atteint une montre ? */
    @Volatile
    var lastDeliveryOk = false
        private set

    @Synchronized
    fun remember(key: String, intent: PendingIntent?) {
        if (intent != null) openIntents[key] = intent
    }

    /** Retourne false si cette notification a déjà été envoyée telle quelle. */
    @Synchronized
    fun markSent(n: RelayedNotification): Boolean {
        val fp = RelayPolicy.fingerprint(n)
        if (fp in sent) return false
        sent.addLast(fp)
        if (sent.size > MAX) sent.removeFirst()
        return true
    }

    /** Envoie la notification à toutes les montres connectées ; retourne le nombre de montres atteintes. */
    suspend fun send(context: Context, n: RelayedNotification): Int {
        val bytes = relayJson.encodeToString(RelayedNotification.serializer(), n).toByteArray()
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }.getOrDefault(emptyList())
        val client = Wearable.getMessageClient(context)
        val delivered = nodes.count { node ->
            runCatching { client.sendMessage(node.id, Paths.NOTIFICATION, bytes).await() }.isSuccess
        }
        synchronized(log) {
            log.add(0, LogEntry(n.time, n.title.orEmpty(), n.text.orEmpty(), delivered))
            while (log.size > 20) log.removeAt(log.lastIndex)
        }
        lastDeliveryOk = delivered > 0
        return delivered
    }

    /** Ouvre la notification d'origine (demande venue de la montre), sinon l'appli Claude. */
    fun open(context: Context, key: String) {
        val intent = synchronized(this) { openIntents[key] }
        val opened = intent != null && runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                intent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
        }.isSuccess
        if (!opened) {
            context.packageManager.getLaunchIntentForPackage(RelayPolicy.CLAUDE_PACKAGE)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { context.startActivity(it) } }
        }
    }
}
