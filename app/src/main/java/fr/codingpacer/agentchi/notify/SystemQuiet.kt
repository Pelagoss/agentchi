package fr.codingpacer.agentchi.notify

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings

/** Ne pas déranger ou mode Coucher activé sur la montre (réglage « Suivre la montre »). */
object SystemQuiet {
    /** Clé du mode Coucher de Wear OS (Settings.Global), absente sur les montres qui n'en ont pas. */
    private const val BEDTIME_MODE = "bedtime_mode"

    fun isActive(context: Context): Boolean = doNotDisturb(context) || bedtime(context)

    /** Tout filtre autre que « tout laisser passer » : priorités seules, alarmes seules, silence total. */
    private fun doNotDisturb(context: Context): Boolean {
        val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        return filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    // Le mode Coucher active d'ordinaire Ne pas déranger ; on le lit aussi au cas où l'utilisateur l'aurait séparé.
    private fun bedtime(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, BEDTIME_MODE, 0) == 1 }.getOrDefault(false)
}
