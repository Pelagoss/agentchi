package fr.codingpacer.agentchi.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import fr.codingpacer.agentchi.data.AppIcon
import fr.codingpacer.agentchi.data.CompanionSettings

/** Active l'icône du lanceur choisie dans les réglages et désactive les autres. */
object AppIconSwitcher {
    fun apply(context: Context, settings: CompanionSettings) {
        val target = when (settings.appIcon) {
            AppIcon.CLASSIC -> AppIcons.CLASSIC
            AppIcon.MASCOT -> AppIcons.alias(settings.mascot.species, settings.mascot.palette)
        }
        val pm = context.packageManager
        fun set(alias: String, on: Boolean) {
            val component = ComponentName(context.packageName, context.packageName + alias)
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val current = pm.getComponentEnabledSetting(component)
            // L'état « par défaut » est celui du manifeste : seule l'icône classique y est active.
            val enabledNow = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == AppIcons.CLASSIC)
            if (enabledNow != on) runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
        }
        // D'abord la nouvelle, puis les autres : le lanceur garde toujours une entrée.
        set(target, true)
        AppIcons.aliases.filter { it != target }.forEach { set(it, false) }
    }
}
