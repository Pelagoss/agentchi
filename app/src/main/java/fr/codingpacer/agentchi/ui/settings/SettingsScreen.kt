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
import fr.codingpacer.agentchi.text.tr
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
    const val MASCOT = "settings/mascot"
    const val TESTS = "settings/tests"
    const val ABOUT = "settings/about"
}

/** Déclare les pages des paramètres. [onOpenPairing] : page « Serveur » (adresse, connexion, ordinateurs et cloud). */
fun NavGraphBuilder.settingsPages(viewModel: CompanionViewModel, navigate: (String) -> Unit, onOpenPairing: () -> Unit) {
    composable(SettingsRoute.MENU) { SettingsMenu(viewModel, navigate, onOpenPairing) }
    composable(SettingsRoute.SOURCES) { SourcesPage(viewModel) }
    composable(SettingsRoute.ANSWERS) { AnswersPage(viewModel, navigate) }
    composable(SettingsRoute.QUICK_REPLIES) { QuickRepliesPage(viewModel) }
    composable(SettingsRoute.NOTIFICATIONS) { NotificationsPage(viewModel, navigate) }
    composable(SettingsRoute.EVENTS) { EventsPage(viewModel) }
    composable(SettingsRoute.STYLES) { StylesPage(viewModel) }
    composable(SettingsRoute.MASCOT) { MascotPage(viewModel) }
    composable(SettingsRoute.TESTS) { TestsPage(viewModel) }
    composable(SettingsRoute.ABOUT) { AboutPage() }
}

@Composable
private fun SettingsMenu(viewModel: CompanionViewModel, navigate: (String) -> Unit, onOpenPairing: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val (pet, _) = viewModel.pet.collectAsStateWithLifecycle().value
    Page(tr("Paramètres", "Settings")) {
        item {
            Category("📥", "Sources", (Agent.entries.filter { it !in settings.mutedAgents }.map { it.label } +
                listOfNotNull(tr("Ordinateurs", "Computers").takeIf { settings.localSessions }, "Cloud".takeIf { settings.cloudSessions }))
                .joinToString(" · ").ifEmpty { tr("Aucune source", "No source") }) { navigate(SettingsRoute.SOURCES) }
        }
        item {
            Category("⌚", tr("Réponses", "Replies"), if (settings.remoteAnswers) tr("Activées", "On") + " · ${settings.answerDelay.label}" else tr("Désactivées", "Off")) {
                navigate(SettingsRoute.ANSWERS)
            }
        }
        item {
            val summary = listOfNotNull(
                tr("${settings.enabledEvents.size} événements", "${settings.enabledEvents.size} events"),
                tr("Résumé groupé", "Grouped summary").takeIf { settings.groupInfo },
                (tr("Calme", "Quiet") + " ${settings.quietStart}h → ${settings.quietEnd}h").takeIf { settings.quietHoursEnabled },
            ).joinToString(" · ")
            Category("🔔", "Notifications", summary) {
                navigate(SettingsRoute.NOTIFICATIONS)
            }
        }
        // Une seule page pour le serveur : adresse, connexion de la montre et codes des ordinateurs.
        item {
            Category("🔗", tr("Serveur", "Server"), if (settings.isConnected) tr("Ordinateurs et cloud", "Computers and cloud") else tr("Montre non connectée", "Watch not connected"), onClick = onOpenPairing)
        }
        item { Category("🦀", tr("Mascotte", "Mascot"), "${pet.name} · ${settings.mascot.species.label} · ${settings.mascot.palette.label}") { navigate(SettingsRoute.MASCOT) } }
        item { Category("🧪", "Tests", tr("Simuler une notification", "Simulate a notification")) { navigate(SettingsRoute.TESTS) } }
        item { Category("ℹ️", tr("À propos", "About"), "Version ${BuildConfig.VERSION_NAME}") { navigate(SettingsRoute.ABOUT) } }
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
        section(tr("Où", "Where"))
        item {
            Toggle(tr("💻 Ordinateurs", "💻 Computers"), "Claude Code, Codex", settings.localSessions) { on -> update { it.copy(localSessions = on) } }
        }
        item {
            Toggle(tr("☁️ Sessions cloud", "☁️ Cloud sessions"), "claude.ai/code", settings.cloudSessions) { on -> update { it.copy(cloudSessions = on) } }
        }
        if (accounts.isNotEmpty()) section(tr("Ordinateurs et environnements", "Computers and environments"))
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
    Page(tr("Réponses", "Replies")) {
        item {
            Toggle(tr("⌚ Répondre depuis la montre", "⌚ Reply from the watch"), tr("Autorisations, questions, messages", "Permissions, questions, messages"), settings.remoteAnswers) { on ->
                viewModel.setRemoteAnswers(on, onError = toast)
            }
        }
        item { Setting(tr("⏱ Attendre ma réponse", "⏱ Wait for my reply"), settings.answerDelay.label) { viewModel.nextAnswerDelay(onError = toast) } }
        item {
            Note(
                if (settings.remoteAnswers) {
                    tr(
                        "L'agent attend ta réponse ${settings.answerDelay.label}, puis demande sur l'ordinateur. Balaie la notification pour répondre tout de suite sur l'ordinateur.",
                        "The agent waits ${settings.answerDelay.label} for your reply, then asks on the computer. Swipe the notification away to answer on the computer right now.",
                    )
                } else {
                    tr(
                        "À activer quand tu t'éloignes : les questions de Claude Code et de Codex arrivent ici avec leurs réponses.",
                        "Turn it on when you step away: Claude Code and Codex questions arrive here with their answers.",
                    )
                },
            )
        }
        item { Category("💬", tr("Réponses rapides", "Quick replies"), settings.quickReplies.joinToString(" · ").ifEmpty { tr("Aucune", "None") }) { navigate(SettingsRoute.QUICK_REPLIES) } }
    }
}

@Composable
private fun QuickRepliesPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Réponse dépliée (Modifier / Supprimer), et celle en cours de saisie (hors de la liste : nouvelle).
    var selected by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableIntStateOf(0) }
    val edit = rememberTextInput(tr("Réponse rapide", "Quick reply")) { text ->
        viewModel.setQuickReply(editing, text)
        selected = null
    }
    Page(tr("Réponses rapides", "Quick replies")) {
        settings.quickReplies.forEachIndexed { index, reply ->
            item {
                Setting("💬 $reply", if (selected == index) tr("Toucher pour fermer", "Tap to close") else tr("Toucher pour modifier", "Tap to edit")) {
                    selected = if (selected == index) null else index
                }
            }
            if (selected == index) {
                item {
                    Setting(tr("✏️ Modifier", "✏️ Edit"), reply) {
                        editing = index
                        edit()
                    }
                }
                item {
                    Setting(tr("🗑️ Supprimer", "🗑️ Delete"), reply) {
                        viewModel.removeQuickReply(index)
                        selected = null
                    }
                }
            }
        }
        if (settings.quickReplies.size < CompanionSettings.MAX_QUICK_REPLIES) {
            item {
                Setting(tr("➕ Ajouter", "➕ Add"), tr("Nouvelle réponse rapide", "New quick reply")) {
                    editing = settings.quickReplies.size
                    edit()
                }
            }
        }
        if (settings.quickReplies != CompanionSettings.DEFAULT_QUICK_REPLIES) {
            item { Setting(tr("↩️ Rétablir", "↩️ Reset"), tr("Réponses par défaut", "Default replies"), onClick = viewModel::resetQuickReplies) }
        }
    }
}

// --- Notifications ---

@Composable
private fun NotificationsPage(viewModel: CompanionViewModel, navigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val update = viewModel::updateSettings
    Page("Notifications") {
        item {
            Category("📋", tr("Me notifier quand…", "Notify me when…"), "${settings.enabledEvents.size} ${tr("sur", "of")} ${EventType.entries.count { !it.silent }}") {
                navigate(SettingsRoute.EVENTS)
            }
        }
        item {
            Category("🎨", tr("Styles des alertes", "Alert styles"), tr("Urgentes : ", "Urgent: ") + settings.urgentStyle.label + tr(" · Autres : ", " · Others: ") + settings.infoStyle.label) {
                navigate(SettingsRoute.STYLES)
            }
        }
        item { Toggle(tr("🔊 Son", "🔊 Sound"), null, settings.sound) { on -> update { it.copy(sound = on) } } }
        item {
            Toggle(tr("👁️ Aperçu du message", "👁️ Message preview"), tr("Masque le texte si désactivé", "Hides the text when off"), settings.showPreview) { on -> update { it.copy(showPreview = on) } }
        }
        item {
            Toggle(tr("📚 Résumé groupé", "📚 Grouped summary"), tr("« 3 tâches terminées sur 2 projets »", "\"3 tasks done across 2 projects\""), settings.groupInfo) { on -> update { it.copy(groupInfo = on) } }
        }
        if (settings.groupInfo) {
            item {
                Note(
                    tr(
                        "Les événements non urgents s'ajoutent à une seule notification, qui ne vibre qu'une fois. Les urgences restent à part.",
                        "Non-urgent events stack into a single notification that only vibrates once. Urgent ones stay separate.",
                    ),
                )
            }
        }

        section(tr("Ne pas déranger", "Do not disturb"))
        item {
            Toggle(tr("⌚ Suivre la montre", "⌚ Follow the watch"), tr("Ne pas déranger, mode Coucher", "Do not disturb, Bedtime mode"), settings.followSystemQuiet) { on ->
                update { it.copy(followSystemQuiet = on) }
            }
        }
        item {
            Toggle(tr("🌙 Heures calmes", "🌙 Quiet hours"), "${settings.quietStart}h → ${settings.quietEnd}h", settings.quietHoursEnabled) { on ->
                update { it.copy(quietHoursEnabled = on) }
            }
        }
        if (settings.quietHoursEnabled) {
            item { Setting(tr("Début", "Start"), "${settings.quietStart}h") { update { it.copy(quietStart = (it.quietStart + 1) % 24) } } }
            item { Setting(tr("Fin", "End"), "${settings.quietEnd}h") { update { it.copy(quietEnd = (it.quietEnd + 1) % 24) } } }
        }
        if (settings.quietHoursEnabled || settings.followSystemQuiet) {
            item {
                Toggle(tr("Laisser passer les urgences", "Let urgent events through"), null, settings.quietAllowsUrgent) { on -> update { it.copy(quietAllowsUrgent = on) } }
            }
        }
    }
}

@Composable
private fun EventsPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Page(tr("Me notifier quand…", "Notify me when…")) {
        EventType.entries.filter { !it.silent }.forEach { type ->
            item {
                Toggle("${type.emoji} ${type.label}", if (type.urgent) tr("Urgent", "Urgent") else null, type in settings.enabledEvents) { on ->
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
    Page(tr("Styles des alertes", "Alert styles")) {
        section(tr("Urgentes", "Urgent"))
        item { Setting(tr("Affichage", "Display"), settings.urgentStyle.label) { update { it.copy(urgentStyle = it.urgentStyle.next()) } } }
        item {
            Setting("Vibration", settings.urgentVibration.label) {
                update { it.copy(urgentVibration = it.urgentVibration.next()) }
                viewModel.previewVibration(settings.urgentVibration.next())
            }
        }
        section(tr("Autres", "Others"))
        item { Setting(tr("Affichage", "Display"), settings.infoStyle.label) { update { it.copy(infoStyle = it.infoStyle.next()) } } }
        item {
            Setting("Vibration", settings.infoVibration.label) {
                update { it.copy(infoVibration = it.infoVibration.next()) }
                viewModel.previewVibration(settings.infoVibration.next())
            }
        }
    }
}

// --- Mascotte, tests, à propos ---

@Composable
private fun MascotPage(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val (pet, now) = viewModel.pet.collectAsStateWithLifecycle().value
    val look = settings.mascot
    val level = PetLogic.level(pet.xp)
    val toast = rememberToast()
    val editName = rememberTextInput(tr("Nom de la mascotte", "Mascot name")) { name -> viewModel.rename(name) }
    Page(tr("Mascotte", "Mascot")) {
        item {
            Mascot(
                mood = PetLogic.mood(pet),
                reaction = PetLogic.currentReaction(pet, now),
                look = look,
                modifier = Modifier.size(width = 96.dp, height = 80.dp),
                onTap = viewModel::caress,
            )
        }
        item { Setting(tr("✏️ Nom", "✏️ Name"), pet.name, onClick = editName) }
        item {
            Setting(tr("🧬 Espèce", "🧬 Species"), look.species.label) {
                val species = look.species.next()
                // Nouvelle espèce : ses couleurs par défaut.
                viewModel.setLook(look.copy(species = species, palette = species.defaultPalette))
            }
        }
        item { Setting(tr("🎨 Couleurs", "🎨 Colors"), look.palette.label) { viewModel.setLook(look.copy(palette = look.palette.next())) } }
        item {
            val locked = MascotSprite.Accessory.entries.count { it.level > level }
            Setting(tr("🎩 Accessoire", "🎩 Accessory"), look.accessory.label + if (locked > 0) " · $locked " + tr("à débloquer", "to unlock") else "") {
                val unlocked = MascotSprite.Accessory.entries.filter { it.level <= level }
                val next = unlocked[(unlocked.indexOf(look.accessory) + 1) % unlocked.size]
                viewModel.setLook(look.copy(accessory = next))
            }
        }
        MascotSprite.Accessory.entries.firstOrNull { it.level > level }?.let { next ->
            item {
                Note(
                    tr(
                        "🔒 ${next.label} au niveau ${next.level} : accomplis des tâches avec ton agent pour monter de niveau.",
                        "🔒 ${next.label} at level ${next.level}: get tasks done with your agent to level up.",
                    ),
                )
            }
        }
        item { Setting(tr("🖼️ Icônes d'actions", "🖼️ Action icons"), settings.iconSet.label) { viewModel.updateSettings { it.copy(iconSet = it.iconSet.next()) } } }
        item {
            Setting(tr("📱 Icône de l'appli", "📱 App icon"), settings.appIcon.label) {
                viewModel.setAppIcon(settings.appIcon.next())
                toast(tr("L'icône change dans le lanceur d'ici quelques secondes", "The launcher icon updates in a few seconds"))
            }
        }
    }
}

@Composable
private fun TestsPage(viewModel: CompanionViewModel) {
    Page("Tests") {
        item { Setting(tr("🔐 Alerte urgente", "🔐 Urgent alert"), tr("Simuler une demande", "Simulate a request")) { viewModel.sendTest(EventType.PERMISSION) } }
        item { Setting(tr("✅ Tâche terminée", "✅ Task done"), tr("Simuler une fin de tâche", "Simulate a finished task")) { viewModel.sendTest(EventType.TASK_DONE) } }
    }
}

@Composable
private fun AboutPage() {
    Page(tr("À propos", "About")) {
        item { Setting("⌚ Agentchi", "Version ${BuildConfig.VERSION_NAME}") {} }
        item {
            Note(tr("Appli non officielle, sans lien avec Anthropic ni OpenAI.", "Unofficial app, not affiliated with Anthropic or OpenAI.") + "\ngithub.com/Pelagoss/agentchi")
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
