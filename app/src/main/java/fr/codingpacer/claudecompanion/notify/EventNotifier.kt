package fr.codingpacer.claudecompanion.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.codingpacer.claudecompanion.R
import fr.codingpacer.claudecompanion.data.AlertStyle
import fr.codingpacer.claudecompanion.data.Ask
import fr.codingpacer.claudecompanion.data.AskAnswer
import fr.codingpacer.claudecompanion.data.ClaudeEvent
import fr.codingpacer.claudecompanion.data.CompanionSettings
import fr.codingpacer.claudecompanion.data.VibrationPattern
import fr.codingpacer.claudecompanion.ui.MainActivity
import fr.codingpacer.claudecompanion.ui.alert.AlertActivity
import fr.codingpacer.claudecompanion.ui.ask.AskActivity

/**
 * Affiche les notifications selon le style choisi par l'utilisateur.
 * Les canaux n'ont pas de vibration propre : elle est jouée manuellement
 * pour pouvoir changer de motif sans recréer les canaux.
 */
class EventNotifier(private val context: Context) {

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        fun channel(id: String, name: String, importance: Int, withSound: Boolean) =
            NotificationChannel(id, name, importance).apply {
                enableVibration(false)
                setSound(if (withSound) sound else null, if (withSound) audio else null)
            }
        nm.createNotificationChannels(
            listOf(
                channel(CH_URGENT, "Claude a besoin de toi", NotificationManager.IMPORTANCE_HIGH, false),
                channel(CH_URGENT_SOUND, "Claude a besoin de toi (son)", NotificationManager.IMPORTANCE_HIGH, true),
                channel(CH_INFO, "Activité des sessions", NotificationManager.IMPORTANCE_DEFAULT, false),
                channel(CH_INFO_SOUND, "Activité des sessions (son)", NotificationManager.IMPORTANCE_DEFAULT, true),
                channel(CH_SILENT, "Notifications discrètes", NotificationManager.IMPORTANCE_LOW, false),
            ),
        )
        // Ancien mode instantané (connexion permanente), remplacé par le push FCM.
        nm.deleteNotificationChannel(OLD_CH_SERVICE)
    }

    fun notify(event: ClaudeEvent, settings: CompanionSettings, muted: Boolean = false) {
        if (!canPost()) return
        val style = if (muted) AlertStyle.SILENT else settings.styleFor(event.type)
        val channel = when {
            style == AlertStyle.SILENT -> CH_SILENT
            event.type.urgent -> if (settings.sound) CH_URGENT_SOUND else CH_URGENT
            else -> if (settings.sound) CH_INFO_SOUND else CH_INFO
        }
        val ask = event.ask?.takeUnless { it.isExpired() }
        val text = when {
            !settings.showPreview -> event.project ?: "Session Claude"
            ask?.kind == Ask.PERMISSION && ask.detail.isNotBlank() -> "${event.message} : ${ask.detail}"
            ask?.kind == Ask.REPLY && ask.detail.isNotBlank() -> ask.detail
            else -> event.message
        }
        val notificationId = event.notificationId

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_claude)
            .setColor(ContextCompat.getColor(context, R.color.claude_orange))
            .setContentTitle(event.title)
            .setContentText(text)
            .setSubText(event.origin)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(event.time * 1000)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(if (event.type.urgent) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .setPriority(
                when {
                    style == AlertStyle.SILENT -> NotificationCompat.PRIORITY_LOW
                    event.type.urgent -> NotificationCompat.PRIORITY_HIGH
                    else -> NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setContentIntent(if (ask != null) AskActivity.pendingIntent(context, event) else openAppIntent())
            .setGroup(GROUP)

        if (ask != null) addAnswerActions(builder, ask, settings.quickReplies)

        if (event.openableOnPhone) {
            builder.addAction(0, "📱 Ouvrir sur le téléphone", OpenOnPhoneReceiver.pendingIntent(context, event, notificationId))
        }

        if (style == AlertStyle.FULL_SCREEN) {
            val fullScreen = if (ask != null) AskActivity.pendingIntent(context, event) else AlertActivity.pendingIntent(context, event, notificationId)
            builder.setFullScreenIntent(fullScreen, true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())

        if (style != AlertStyle.SILENT) vibrate(settings.vibrationFor(event.type))
    }

    /**
     * Réponses possibles directement depuis la notification. Le reste (« toujours autoriser »,
     * choix multiples, refus avec un message) se fait dans [AskActivity], en touchant la notification.
     */
    private fun addAnswerActions(builder: NotificationCompat.Builder, ask: Ask, quickReplies: List<String>) {
        builder.setAutoCancel(false)
            .setOnlyAlertOnce(true)
            // Balayée : l'ordinateur pose la question sans attendre la fin du délai.
            .setDeleteIntent(AnswerReceiver.pendingIntent(context, ask, 0, AskAnswer.pass))
        val remaining = ask.expires * 1000 - System.currentTimeMillis()
        if (ask.expires > 0 && remaining > 0) builder.setTimeoutAfter(remaining)

        fun add(index: Int, title: String, answer: AskAnswer) =
            builder.addAction(AnswerReceiver.action(context, ask, index, title, answer))
        when (ask.kind) {
            Ask.PERMISSION -> {
                add(1, "✅ Autoriser", AskAnswer(AskAnswer.ALLOW))
                add(2, "❌ Refuser", AskAnswer(AskAnswer.DENY))
            }
            Ask.PLAN -> {
                add(1, "✅ Approuver le plan", AskAnswer(AskAnswer.ALLOW))
                add(2, "❌ Refuser", AskAnswer(AskAnswer.DENY))
            }
            Ask.QUESTION -> {
                val single = ask.questions.singleOrNull()?.takeIf { !it.multi && it.options.size in 1..4 }
                single?.options?.forEachIndexed { i, option ->
                    add(1 + i, option.label, AskAnswer(AskAnswer.ALLOW, answers = mapOf(single.question to option.label)))
                }
            }
            Ask.REPLY -> {
                builder.addAction(AnswerReceiver.replyAction(context, ask, 1, quickReplies))
                add(2, "🏁 Terminer", AskAnswer.pass)
            }
        }
    }

    fun vibrate(pattern: VibrationPattern) {
        val timings = pattern.timings ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        val effect = VibrationEffect.createWaveform(timings, -1)
        // Usage « notification » obligatoire : sans lui, Android ignore les vibrations
        // d'une appli en arrière-plan, ce qui est le cas à la réception d'un push FCM.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
    }

    fun cancel(notificationId: Int) = NotificationManagerCompat.from(context).cancel(notificationId)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CH_URGENT = "claude_urgent"
        const val CH_URGENT_SOUND = "claude_urgent_sound"
        const val CH_INFO = "claude_info"
        const val CH_INFO_SOUND = "claude_info_sound"
        const val CH_SILENT = "claude_silent"
        private const val OLD_CH_SERVICE = "claude_service"
        private const val GROUP = "claude_events"
    }
}
