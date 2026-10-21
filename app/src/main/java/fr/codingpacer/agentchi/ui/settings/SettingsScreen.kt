package fr.codingpacer.agentchi.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
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
import androidx.wear.compose.navigation.composable
import fr.codingpacer.agentchi.BuildConfig
import fr.codingpacer.agentchi.data.Agent
import fr.codingpacer.agentchi.data.CompanionSettings
import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.data.next
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.ui.pet.Mascot
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.common.rememberTextInput

/** Pages des paramètres : un menu de catégories, une page par catégorie. */
object SettingsRoute {
    const val MENU = "settings"
    const val SOURCES = "settings/sources"
    const val ANSWERS = "settings/answers"
    const val QUICK_REPLIES = "settings/answers/quick"
    const val NOTIFICATIONS = "settings/notifications"
    const val EVENTS = "settings/notifications/events"
    const val STYLES = "settings/notifications/styles"
    const val SERVER = "settings/server"
    const val MASCOT = "settings/mascot"
    const val TESTS = "settings/tests"
    const val ABOUT = "settings/about"
}

/** Déclare les pages des paramètres. [onOpenPairing] : écran « Ordinateurs et cloud ». */
fun NavGraphBuilder.settingsPages(viewModel: CompanionViewModel, navigate: (String) -> Unit, onOpenPairing: () -> Unit) {
    composable(SettingsRoute.MENU) { SettingsMenu(viewModel, navigate) }
    composable(SettingsRoute.SOURCES) { SourcesPage(viewModel) }
    composable(SettingsRoute.ANSWERS) { AnswersPage(viewModel, navigate) }
    composable(SettingsRoute.QUICK_REPLIES) { QuickRepliesPage(viewModel) }
    composable(SettingsRoute.NOTIFICATIONS) { NotificationsPage(viewModel, navigate) }
    composable(SettingsRoute.EVENTS) { EventsPage(viewModel) }
    composable(SettingsRoute.STYLES) { StylesPage(viewModel) }
    composable(SettingsRoute.SERVER) { ServerPage(viewModel, onOpenPairing) }
    composable(SettingsRoute.MASCOT) { MascotPage(viewModel) }
    composable(SettingsRoute.TESTS) { TestsPage(viewModel) }
    composable(SettingsRoute.ABOUT) { AboutPage() }
}

@Composable
private fun SettingsMenu(viewModel: CompanionViewModel, navigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val (pet, _) = viewModel.pet.collectAsStateWithLifecycle().value
    Page("Paramètres") {
        item {
            Category("📥", "Sources", (Agent.entries.filter { it !in settings.mutedAgents }.map { it.label } +
                listOfNotNull("Ordinateurs".takeIf { settings.localSessions }, "Cloud".takeIf { settings.cloudSessions }))
                .joinToString(" · ").ifEmpty { "Aucune source" }) { navigate(SettingsRoute.SOURCES) }
        }
        item {
            Category("⌚", "Réponses", if (settings.remoteAnswers) "Activées · ${settings.answerDelay.label}" else "Désactivées") {
                navigate(SettingsRoute.ANSWERS)
            }
        }
        item {
            Category("🔔", "Notifications", if (settings.quietHoursEnabled) "Calme ${settings.quietStart}h → ${settings.quietEnd}h" else "${settings.enabledEvents.size} événements") {
                navigate(SettingsRoute.NOTIFICATIONS)
            }
        }
        item { Category("🔗", "Serveur", if (settings.isConnected) "Montre connectée" else "Montre non connectée") { navigate(SettingsRoute.SERVER) } }
        item { Category("🦀", "Mascotte", "${pet.name} · ${settings.mascot.species.label} · ${settings.mascot.palette.label}") { navigate(SettingsRoute.MASCOT) } }
        item { Category("🧪", "Tests", "Simuler une notification") { navigate(SettingsRoute.TESTS) } }
        item { Category("ℹ️", "À propos", "Version ${BuildConfig.VERSION_NAME}") { navigate(SettingsRoute.ABOUT) } }
    }
}

// --- Sources ---

@Composable
private fun SourcesPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accounts by viewModel.knownAccounts.collectAsStateWithLifecycle()
    val update = viewModel::updateSettings
    Page("Sources") {
        Agent.entries.forEach { agent ->
            item {
                Toggle(if (agent == Agent.CLAUDE) "✳️ ${agent.product}" else "⬡ ${agent.product}", null, agent !in settings.mutedAgents) { on ->
                    update { it.copy(mutedAgents = if (on) it.mutedAgents - agent else it.mutedAgents + agent) }
                }
            }
        }
        section("Où")
        item {
            Toggle("💻 Ordinateurs", "Claude Code, Codex", settings.localSessions) { on -> update { it.copy(localSessions = on) } }
        }
        item {
            Toggle("☁️ Sessions cloud", "claude.ai/code", settings.cloudSessions) { on -> update { it.copy(cloudSessions = on) } }
        }
        if (accounts.isNotEmpty()) section("Ordinateurs et environnements")
        accounts.sorted().forEach { account ->
            item {
                Toggle("👤 $account", null, account !in settings.mutedAccounts) { on -> viewModel.setAccountEnabled(account, on) }
            }
        }
    }
}

// --- Réponses ---

@Composable
private fun AnswersPage(viewModel: CompanionViewModel, navigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toast = rememberToast()
    Page("Réponses") {
        item {
            Toggle("⌚ Répondre depuis la montre", "Autorisations, questions, messages", settings.remoteAnswers) { on ->
                viewModel.setRemoteAnswers(on, onError = toast)
            }
        }
        item { Setting("⏱ Attendre ma réponse", settings.answerDelay.label) { viewModel.nextAnswerDelay(onError = toast) } }
        item {
            Note(
                if (settings.remoteAnswers) {
                    "L'agent attend ta réponse ${settings.answerDelay.label}, puis demande sur l'ordinateur. Balaie la notification pour répondre tout de suite sur l'ordinateur."
                } else {
                    "À activer quand tu t'éloignes : les questions de Claude Code et de Codex arrivent ici avec leurs réponses."
                },
            )
        }
        item { Category("💬", "Réponses rapides", settings.quickReplies.joinToString(" · ").ifEmpty { "Aucune" }) { navigate(SettingsRoute.QUICK_REPLIES) } }
    }
}

@Composable
private fun QuickRepliesPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Réponse dépliée (Modifier / Supprimer), et celle en cours de saisie (hors de la liste : nouvelle).
    var selected by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableIntStateOf(0) }
    val edit = rememberTextInput("Réponse rapide") { text ->
        viewModel.setQuickReply(editing, text)
        selected = null
    }
    Page("Réponses rapides") {
        settings.quickReplies.forEachIndexed { index, reply ->
            item {
                Setting("💬 $reply", if (selected == index) "Toucher pour fermer" else "Toucher pour modifier") {
                    selected = if (selected == index) null else index
                }
            }
            if (selected == index) {
                item {
                    Setting("✏️ Modifier", reply) {
                        editing = index
                        edit()
                    }
                }
                item {
                    Setting("🗑️ Supprimer", reply) {
                        viewModel.removeQuickReply(index)
                        selected = null
                    }
                }
            }
        }
        if (settings.quickReplies.size < CompanionSettings.MAX_QUICK_REPLIES) {
            item {
                Setting("➕ Ajouter", "Nouvelle réponse rapide") {
                    editing = settings.quickReplies.size
                    edit()
                }
            }
        }
        if (settings.quickReplies != CompanionSettings.DEFAULT_QUICK_REPLIES) {
            item { Setting("↩️ Rétablir", "Réponses par défaut", onClick = viewModel::resetQuickReplies) }
        }
    }
}

// --- Notifications ---

@Composable
private fun NotificationsPage(viewModel: CompanionViewModel, navigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val update = viewModel::updateSettings
    Page("Notifications") {
        item { Category("📋", "Me notifier quand…", "${settings.enabledEvents.size} sur ${EventType.entries.size}") { navigate(SettingsRoute.EVENTS) } }
        item {
            Category("🎨", "Styles des alertes", "Urgentes : ${settings.urgentStyle.label} · Autres : ${settings.infoStyle.label}") {
                navigate(SettingsRoute.STYLES)
            }
        }
        item { Toggle("🔊 Son", null, settings.sound) { on -> update { it.copy(sound = on) } } }
        item {
            Toggle("👁️ Aperçu du message", "Masque le texte si désactivé", settings.showPreview) { on -> update { it.copy(showPreview = on) } }
        }

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
                Toggle("Laisser passer les urgences", null, settings.quietAllowsUrgent) { on -> update { it.copy(quietAllowsUrgent = on) } }
            }
        }
    }
}

@Composable
private fun EventsPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Page("Me notifier quand…") {
        EventType.entries.forEach { type ->
            item {
                Toggle("${type.emoji} ${type.label}", if (type.urgent) "Urgent" else null, type in settings.enabledEvents) { on ->
                    viewModel.updateSettings { s -> s.copy(enabledEvents = if (on) s.enabledEvents + type else s.enabledEvents - type) }
                }
            }
        }
    }
}

@Composable
private fun StylesPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val update = viewModel::updateSettings
    Page("Styles des alertes") {
        section("Urgentes")
        item { Setting("Affichage", settings.urgentStyle.label) { update { it.copy(urgentStyle = it.urgentStyle.next()) } } }
        item {
            Setting("Vibration", settings.urgentVibration.label) {
                update { it.copy(urgentVibration = it.urgentVibration.next()) }
                viewModel.previewVibration(settings.urgentVibration.next())
            }
        }
        section("Autres")
        item { Setting("Affichage", settings.infoStyle.label) { update { it.copy(infoStyle = it.infoStyle.next()) } } }
        item {
            Setting("Vibration", settings.infoVibration.label) {
                update { it.copy(infoVibration = it.infoVibration.next()) }
                viewModel.previewVibration(settings.infoVibration.next())
            }
        }
    }
}

// --- Serveur, mascotte, tests, à propos ---

@Composable
private fun ServerPage(viewModel: CompanionViewModel, onOpenPairing: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toast = rememberToast()
    val editServer = rememberTextInput("Adresse du serveur Agentchi") { url ->
        if (url.startsWith("https://") || url.startsWith("http://")) viewModel.setServer(url)
        else toast("L'adresse doit commencer par https://")
    }
    Page("Serveur") {
        item {
            Category("🔗", "Ordinateurs et cloud", if (settings.isConnected) "Codes d'appairage" else "Montre non connectée", onClick = onOpenPairing)
        }
        item { Setting("🌐 Adresse", settings.serverUrl.ifBlank { "Non configurée" }, onClick = editServer) }
    }
}

@Composable
private fun MascotPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val (pet, now) = viewModel.pet.collectAsStateWithLifecycle().value
    val look = settings.mascot
    val level = PetLogic.level(pet.xp)
    val toast = rememberToast()
    val editName = rememberTextInput("Nom de la mascotte") { name -> viewModel.rename(name) }
    Page("Mascotte") {
        item {
            Mascot(
                mood = PetLogic.mood(pet),
                reaction = PetLogic.currentReaction(pet, now),
                look = look,
                modifier = Modifier.size(width = 96.dp, height = 80.dp),
                onTap = viewModel::caress,
            )
        }
        item { Setting("✏️ Nom", pet.name, onClick = editName) }
        item {
            Setting("🧬 Espèce", look.species.label) {
                val species = look.species.next()
                // Nouvelle espèce : ses couleurs par défaut.
                viewModel.setLook(look.copy(species = species, palette = species.defaultPalette))
            }
        }
        item { Setting("🎨 Couleurs", look.palette.label) { viewModel.setLook(look.copy(palette = look.palette.next())) } }
        item {
            val locked = MascotSprite.Accessory.entries.count { it.level > level }
            Setting("🎩 Accessoire", look.accessory.label + if (locked > 0) " · $locked à débloquer" else "") {
                val unlocked = MascotSprite.Accessory.entries.filter { it.level <= level }
                val next = unlocked[(unlocked.indexOf(look.accessory) + 1) % unlocked.size]
                viewModel.setLook(look.copy(accessory = next))
            }
        }
        MascotSprite.Accessory.entries.firstOrNull { it.level > level }?.let { next ->
            item { Note("🔒 ${next.label} au niveau ${next.level} : accomplis des tâches avec ton agent pour monter de niveau.") }
        }
        item { Setting("🖼️ Icônes d'actions", settings.iconSet.label) { viewModel.updateSettings { it.copy(iconSet = it.iconSet.next()) } } }
        item {
            Setting("📱 Icône de l'appli", settings.appIcon.label) {
                viewModel.setAppIcon(settings.appIcon.next())
                toast("L'icône change dans le lanceur d'ici quelques secondes")
            }
        }
    }
}

@Composable
private fun TestsPage(viewModel: CompanionViewModel) {
    Page("Tests") {
        item { Setting("🔐 Alerte urgente", "Simuler une demande") { viewModel.sendTest(EventType.PERMISSION) } }
        item { Setting("✅ Tâche terminée", "Simuler une fin de tâche") { viewModel.sendTest(EventType.TASK_DONE) } }
    }
}

@Composable
private fun AboutPage() {
    Page("À propos") {
        item { Setting("⌚ Agentchi", "Version ${BuildConfig.VERSION_NAME}") {} }
        item {
            Note("Appli non officielle, sans lien avec Anthropic ni OpenAI.\ngithub.com/Pelagoss/claude-companion")
        }
    }
}

// --- Composants communs ---

@Composable
private fun Page(title: String, content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(title) } }
            content()
        }
    }
}

@Composable
private fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } }
}

private fun ScalingLazyListScope.section(title: String) = item {
    ListHeader { Text(title, color = MaterialTheme.colors.primary) }
}

/** Entrée vers une page ou une sous-page. */
@Composable
private fun Category(icon: String, label: String, summary: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Text(icon) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
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
