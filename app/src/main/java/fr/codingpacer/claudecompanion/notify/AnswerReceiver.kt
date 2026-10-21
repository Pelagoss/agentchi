package fr.codingpacer.claudecompanion.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import fr.codingpacer.claudecompanion.CompanionApp
import fr.codingpacer.claudecompanion.data.Ask
import fr.codingpacer.claudecompanion.data.AskAnswer
import fr.codingpacer.claudecompanion.net.EventParser
import kotlinx.coroutines.launch

/**
 * Actions de réponse des notifications : autoriser, refuser, choisir une option, répondre à Claude.
 * Balayer la notification laisse l'ordinateur poser la question.
 */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ask = intent.getStringExtra(EXTRA_ASK)
            ?.let { runCatching { EventParser.json.decodeFromString(Ask.serializer(), it) }.getOrNull() } ?: return
        val answer = intent.getStringExtra(EXTRA_ANSWER)
            ?.let { runCatching { EventParser.json.decodeFromString(AskAnswer.serializer(), it) }.getOrNull() }
            ?: replyFrom(intent)
            ?: return
        val app = context.applicationContext as CompanionApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val result = app.responder.answer(ask, answer)
                // Notification balayée : pas de message, l'ordinateur prend le relais.
                if (answer.decision != AskAnswer.PASS) app.responder.toast(result)
            } finally {
                pending.finish()
            }
        }
    }

    /** Texte saisi ou dicté dans la notification (réponse à Claude). */
    private fun replyFrom(intent: Intent): AskAnswer? {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        return text?.takeIf { it.isNotEmpty() }?.let { AskAnswer(AskAnswer.ALLOW, message = it) }
    }

    companion object {
        private const val EXTRA_ASK = "ask"
        private const val EXTRA_ANSWER = "answer"
        private const val KEY_REPLY = "reply"

        private fun intent(context: Context, ask: Ask) = Intent(context, AnswerReceiver::class.java)
            .putExtra(EXTRA_ASK, EventParser.json.encodeToString(Ask.serializer(), ask))

        /** Action qui envoie [answer] sans ouvrir l'appli. [index] distingue les actions d'une même notification. */
        fun action(context: Context, ask: Ask, index: Int, title: String, answer: AskAnswer): NotificationCompat.Action =
            NotificationCompat.Action.Builder(0, title, pendingIntent(context, ask, index, answer)).build()

        fun pendingIntent(context: Context, ask: Ask, index: Int, answer: AskAnswer): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(ask, index),
            intent(context, ask).putExtra(EXTRA_ANSWER, EventParser.json.encodeToString(AskAnswer.serializer(), answer)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Action « Répondre » : clavier, dictée ou réponse rapide ([choices]), directement depuis la notification. */
        fun replyAction(context: Context, ask: Ask, index: Int, choices: List<String>): NotificationCompat.Action {
            val input = RemoteInput.Builder(KEY_REPLY)
                .setLabel("Ta réponse à Claude")
                .apply { if (choices.isNotEmpty()) setChoices(choices.toTypedArray()) }
                .build()
            // La saisie est ajoutée à l'intent par le système : il doit rester modifiable.
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(ask, index),
                intent(context, ask),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            return NotificationCompat.Action.Builder(0, "💬 Répondre", pending)
                .addRemoteInput(input)
                .setAllowGeneratedReplies(false)
                .build()
        }

        private fun requestCode(ask: Ask, index: Int) = ask.id.hashCode() * 31 + index
    }
}
