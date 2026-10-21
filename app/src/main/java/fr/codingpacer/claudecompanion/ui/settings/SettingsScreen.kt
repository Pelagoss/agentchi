package fr.codingpacer.claudecompanion.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import fr.codingpacer.claudecompanion.BuildConfig
import fr.codingpacer.claudecompanion.data.CompanionSettings
import fr.codingpacer.claudecompanion.data.EventType
import fr.codingpacer.claudecompanion.data.next
import fr.codingpacer.claudecompanion.ui.CompanionViewModel
import fr.codingpacer.claudecompanion.ui.common.rememberTextInput

@Composable
fun SettingsScreen(viewModel: CompanionViewModel, onOpenPairing: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val (pet, _) = viewModel.pet.collectAsStateWithLifecycle().value
    val accounts by viewModel.knownAccounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val update = viewModel::updateSettings

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val editServer = rememberTextInput("Adresse du serveur Claude Companion") { url ->
        if (url.startsWith("https://") || url.startsWith("http://")) viewModel.setServer(url)
        else toast("L'adresse doit commencer par https://")
    }
    val editName = rememberTextInput("Nom de la mascotte") { name -> viewModel.rename(name) }
    // Réponse rapide en cours de modification (index hors de la liste : nouvelle réponse).
    var editingReply by remember { mutableIntStateOf(0) }
    val editReply = rememberTextInput("Réponse rapide (vide pour l'effacer)", allowEmpty = true) { text ->
        viewModel.setQuickReply(editingReply, text)
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Paramètres") } }
            // --- Sources ---
            section("Sources")
            item {
                Toggle("💻 Ordinateurs", "Plugin Claude Code", settings.localSessions) { on ->
                    update { it.copy(localSessions = on) }
                }
            }
            item {
                Toggle("☁️ Sessions cloud", "claude.ai/code", settings.cloudSessions) { on ->
                    update { it.copy(cloudSessions = on) }
                }
            }
            accounts.sorted().forEach { account ->
                item {
                    Toggle("👤 $account", null, account !in settings.mutedAccounts) { on ->
                        viewModel.setAccountEnabled(account, on)
                    }
                }
            }

            // --- Réponses ---
            section("Répondre depuis la montre")
            item {
                Toggle("⌚ Répondre à Claude", "Autorisations, questions, messages", settings.remoteAnswers) { on ->
                    viewModel.setRemoteAnswers(on, onError = ::toast)
                }
            }
            item {
                Setting("⏱ Attendre ma réponse", settings.answerDelay.label) { viewModel.nextAnswerDelay(onError = ::toast) }
            }
            item {
                Text(
                    if (settings.remoteAnswers) {
                        "Claude attend ta réponse ${settings.answerDelay.label}, puis demande sur l'ordinateur. Balaie la notification pour répondre tout de suite sur l'ordinateur."
                    } else {
                        "À activer quand tu t'éloignes : les questions de Claude arrivent ici avec leurs réponses."
                    },
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            section("Réponses rapides")
            settings.quickReplies.forEachIndexed { index, reply ->
                item {
                    Setting("💬 $reply", "Toucher pour modifier ou effacer") {
                        editingReply = index
                        editReply()
                    }
                }
            }
            if (settings.quickReplies.size < CompanionSettings.MAX_QUICK_REPLIES) {
                item {
                    Setting("➕ Ajouter", "Nouvelle réponse rapide") {
                        editingReply = settings.quickReplies.size
                        editReply()
                    }
                }
            }
            if (settings.quickReplies != CompanionSettings.DEFAULT_QUICK_REPLIES) {
                item { Setting("↩️ Rétablir", "Réponses par défaut", onClick = viewModel::resetQuickReplies) }
            }

            // --- Événements ---
            section("Me notifier quand…")
            EventType.entries.forEach { type ->
                item {
                    Toggle(
                        label = "${type.emoji} ${type.label}",
                        secondary = if (type.urgent) "Urgent" else null,
                        checked = type in settings.enabledEvents,
                    ) { on ->
                        update { s -> s.copy(enabledEvents = if (on) s.enabledEvents + type else s.enabledEvents - type) }
                    }
                }
            }

            // --- Style ---
            section("Style des alertes urgentes")
            item {
                Setting("Affichage", settings.urgentStyle.label) { update { it.copy(urgentStyle = it.urgentStyle.next()) } }
            }
            item {
                Setting("Vibration", settings.urgentVibration.label) {
                    update { it.copy(urgentVibration = it.urgentVibration.next()) }
                    viewModel.previewVibration(settings.urgentVibration.next())
                }
            }
            section("Style des autres notifications")
            item {
                Setting("Affichage", settings.infoStyle.label) { update { it.copy(infoStyle = it.infoStyle.next()) } }
            }
            item {
                Setting("Vibration", settings.infoVibration.label) {
                    update { it.copy(infoVibration = it.infoVibration.next()) }
                    viewModel.previewVibration(settings.infoVibration.next())
                }
            }
            item { Toggle("🔊 Son", null, settings.sound) { on -> update { it.copy(sound = on) } } }
            item {
                Toggle("👁️ Aperçu du message", "Masque le texte si désactivé", settings.showPreview) { on ->
                    update { it.copy(showPreview = on) }
                }
            }

            // --- Ne pas déranger ---
            section("Ne pas déranger")
            item {
                Toggle("🌙 Heures calmes", "${settings.quietStart}h → ${settings.quietEnd}h", settings.quietHoursEnabled) { on ->
                    update { it.copy(quietHoursEnabled = on) }
                }
            }
            if (settings.quietHoursEnabled) {
                item { Setting("Début", "${settings.quietStart}h") { update { it.copy(quietStart = (it.quietStart + 1) % 24) } } }
                item { Setting("Fin", "${settings.quietEnd}h") { update { it.copy(quietEnd = (it.quietEnd + 1) % 24) } } }
                item {
                    Toggle("Laisser passer les urgences", null, settings.quietAllowsUrgent) { on ->
                        update { it.copy(quietAllowsUrgent = on) }
                    }
                }
            }

            // --- Connexion ---
            section("Serveur Claude Companion")
            item {
                Chip(
                    onClick = onOpenPairing,
                    label = { Text("Ordinateurs et cloud") },
                    secondaryLabel = { Text(if (settings.isConnected) "Codes d'appairage" else "Montre non connectée") },
                    icon = { Text("🔗") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Setting("Serveur", settings.serverUrl.ifBlank { "Non configuré" }, onClick = editServer) }

            // --- Mascotte & tests ---
            section("Mascotte")
            item { Setting("Nom", pet.name, onClick = editName) }

            section("Tester")
            item { Setting("🔐 Alerte urgente", "Simuler une demande") { viewModel.sendTest(EventType.PERMISSION) } }
            item { Setting("✅ Tâche terminée", "Simuler une fin de tâche") { viewModel.sendTest(EventType.TASK_DONE) } }

            item {
                Text(
                    "Claude Companion ${BuildConfig.VERSION_NAME}\nAppli non officielle",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun ScalingLazyListScope.section(title: String) = item {
    ListHeader { Text(title, color = MaterialTheme.colors.primary) }
}

@Composable
private fun Setting(label: String, value: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(label: String, secondary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onChange,
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        toggleControl = { Switch(checked = checked) },
        modifier = Modifier.fillMaxWidth(),
    )
}
