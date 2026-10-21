package fr.codingpacer.agentchi.ui.ask

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import fr.codingpacer.agentchi.CompanionApp
import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AskAnswer
import fr.codingpacer.agentchi.data.AskQuestion
import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.net.EventParser
import fr.codingpacer.agentchi.sync.AskResponder
import fr.codingpacer.agentchi.text.markdownAnnotated
import fr.codingpacer.agentchi.ui.common.rememberTextInput
import fr.codingpacer.agentchi.ui.theme.BrandOrange
import fr.codingpacer.agentchi.ui.theme.CompanionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Répondre à l'agent depuis la montre : autorisation, question à choix, plan ou message libre. */
class AskActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val event = intent.getStringExtra(EXTRA_EVENT)
            ?.let { runCatching { EventParser.json.decodeFromString(AgentEvent.serializer(), it) }.getOrNull() }
        val ask = event?.ask
        if (ask == null) {
            finish()
            return
        }
        val app = application as CompanionApp
        setContent {
            val settings by app.store.settings.collectAsStateWithLifecycle(initialValue = null)
            CompanionTheme {
                AskScreen(event, ask, app.responder, quickReplies = settings?.quickReplies.orEmpty(), onDone = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_EVENT = "event"

        fun intent(context: Context, event: AgentEvent): Intent = Intent(context, AskActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_EVENT, EventParser.json.encodeToString(AgentEvent.serializer(), event))

        fun pendingIntent(context: Context, event: AgentEvent): PendingIntent = PendingIntent.getActivity(
            context,
            event.notificationId,
            intent(context, event),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

@Composable
private fun AskScreen(event: AgentEvent, ask: Ask, responder: AskResponder, quickReplies: List<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val ended by responder.ended.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    // Réponse acceptée (ou expirée) : l'écran se ferme sans autre message.
    var answered by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    // Question traitée ailleurs ou expirée : plus rien à faire ici.
    val gone = ask.id in ended || ask.isExpired(now)
    LaunchedEffect(gone) {
        if (gone && !busy && !answered) {
            Toast.makeText(context, AskResponder.Result.EXPIRED.message, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    fun send(answer: AskAnswer) {
        if (busy) return
        busy = true
        answered = true
        scope.launch {
            val result = responder.answer(ask, answer)
            busy = false
            responder.toast(result)
            if (result != AskResponder.Result.FAILED) onDone() else answered = false
        }
    }

    // Saisie libre (clavier, dictée) : refus motivé, réponse à l'agent, autre réponse à une question.
    var otherFor by remember { mutableStateOf<AskQuestion?>(null) }
    val chosen = remember { mutableStateMapOf<String, Set<String>>() }
    val other = remember { mutableStateMapOf<String, String>() }
    val denyWithMessage = rememberTextInput(if (ask.kind == Ask.PLAN) "Ce qu'il faut changer" else "Pourquoi refuser ?") {
        send(AskAnswer(AskAnswer.DENY, message = it))
    }
    val reply = rememberTextInput("Ta réponse à ${event.agent.label}") { send(AskAnswer(AskAnswer.ALLOW, message = it)) }
    val otherAnswer = rememberTextInput("Ta réponse") { text ->
        otherFor?.let { q ->
            other[q.question] = text
            if (!q.multi) chosen[q.question] = emptySet()
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Text(event.title, style = MaterialTheme.typography.title3, color = BrandOrange, textAlign = TextAlign.Center)
            }
            item {
                val left = (ask.expires - now).coerceAtLeast(0)
                Text(
                    event.origin + if (ask.expires > 0) " · ⏱ ${left / 60}:${"%02d".format(left % 60)}" else "",
                    style = MaterialTheme.typography.caption3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (ask.kind) {
                Ask.PERMISSION -> {
                    item { Text(markdownAnnotated(event.message), style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
                    if (ask.detail.isNotBlank()) item { Detail(ask.detail, code = true) }
                    item { Action("✅ Autoriser", busy, primary = true) { send(AskAnswer(AskAnswer.ALLOW)) } }
                    if (ask.always) {
                        item { Action("♾️ Toujours autoriser", busy, secondary = "Ne plus demander pour ça") { send(AskAnswer(AskAnswer.ALWAYS)) } }
                    }
                    item { Action("❌ Refuser", busy) { send(AskAnswer(AskAnswer.DENY)) } }
                    item { Action("✍️ Refuser et expliquer", busy, onClick = denyWithMessage) }
                }
                Ask.PLAN -> {
                    if (ask.detail.isNotBlank()) item { Detail(ask.detail) }
                    item { Action("✅ Approuver le plan", busy, primary = true) { send(AskAnswer(AskAnswer.ALLOW)) } }
                    item { Action("✍️ Demander des changements", busy, onClick = denyWithMessage) }
                }
                Ask.QUESTION -> {
                    ask.questions.forEach { q ->
                        question(q, chosen, other, busy) {
                            otherFor = q
                            otherAnswer()
                        }
                    }
                    item {
                        val answers = ask.questions.associate { q ->
                            q.question to (q.options.map { it.label }.filter { it in chosen[q.question].orEmpty() } + listOfNotNull(other[q.question]))
                                .joinToString(", ")
                        }
                        val complete = answers.values.all { it.isNotBlank() }
                        Action("📨 Envoyer", busy || !complete, primary = true) { send(AskAnswer(AskAnswer.ALLOW, answers = answers)) }
                    }
                    item { Action("❌ Ne pas répondre", busy) { send(AskAnswer(AskAnswer.DENY)) } }
                }
                Ask.REPLY -> {
                    item { Detail(ask.detail.ifBlank { event.message }) }
                    item { Action("🎤 Répondre…", busy, primary = true, onClick = reply) }
                    quickReplies.forEach { text ->
                        item { Action(text, busy) { send(AskAnswer(AskAnswer.ALLOW, message = text)) } }
                    }
                    item { Action("🏁 Terminer", busy) { send(AskAnswer.pass) } }
                }
            }
            if (ask.kind != Ask.REPLY) {
                item {
                    Action("💻 Répondre sur l'ordinateur", busy, secondary = "${event.agent.product} pose la question") { send(AskAnswer.pass) }
                }
            }
        }
    }
}

/** Une question : options à cocher (une ou plusieurs) et réponse libre. */
private fun ScalingLazyListScope.question(
    q: AskQuestion,
    chosen: MutableMap<String, Set<String>>,
    other: MutableMap<String, String>,
    busy: Boolean,
    onOther: () -> Unit,
) {
    item { ListHeader { Text(q.header.ifBlank { "Question" }, color = MaterialTheme.colors.primary) } }
    item { Text(markdownAnnotated(q.question), style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
    q.options.forEach { option ->
        item {
            val selected = option.label in chosen[q.question].orEmpty()
            ToggleChip(
                checked = selected,
                enabled = !busy,
                onCheckedChange = { on ->
                    val current = chosen[q.question].orEmpty()
                    chosen[q.question] = when {
                        q.multi -> if (on) current + option.label else current - option.label
                        else -> setOf(option.label)
                    }
                    if (!q.multi) other.remove(q.question)
                },
                label = { Text(markdownAnnotated(option.label), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = if (option.description.isNotBlank()) {
                    { Text(markdownAnnotated(option.description), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                } else {
                    null
                },
                toggleControl = { if (q.multi) Checkbox(checked = selected) else RadioButton(selected = selected) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    item {
        Action("✍️ Autre réponse…", busy, secondary = other[q.question], onClick = onOther)
    }
}

/** Commande ([code], affichée telle quelle) ou texte Markdown de l'agent (plan, message). */
@Composable
private fun Detail(text: String, code: Boolean = false) {
    if (code) {
        Text(text, style = MaterialTheme.typography.caption2, fontFamily = FontFamily.Monospace, maxLines = 8, overflow = TextOverflow.Ellipsis)
    } else {
        Text(markdownAnnotated(text), style = MaterialTheme.typography.caption2, maxLines = 40, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Action(
    label: String,
    disabled: Boolean,
    primary: Boolean = false,
    secondary: String? = null,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        enabled = !disabled,
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
