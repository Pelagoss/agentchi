package fr.codingpacer.agentchi.notify

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
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.codingpacer.agentchi.R
import fr.codingpacer.agentchi.data.AlertStyle
import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AskAnswer
import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.CompanionSettings
import fr.codingpacer.agentchi.data.VibrationPattern
import fr.codingpacer.agentchi.net.EventParser
import fr.codingpacer.agentchi.pet.Achievement
import fr.codingpacer.agentchi.text.tr
import fr.codingpacer.agentchi.ui.MainActivity
import fr.codingpacer.agentchi.text.markdownSpanned
import fr.codingpacer.agentchi.ui.alert.AlertActivity
import fr.codingpacer.agentchi.ui.ask.AskActivity
import kotlinx.serialization.builtins.ListSerializer

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
                channel(CH_URGENT, tr("Un agent a besoin de toi", "An agent needs you"), NotificationManager.IMPORTANCE_HIGH, false),
                channel(CH_URGENT_SOUND, tr("Un agent a besoin de toi (son)", "An agent needs you (sound)"), NotificationManager.IMPORTANCE_HIGH, true),
                channel(CH_INFO, tr("Activité des sessions", "Session activity"), NotificationManager.IMPORTANCE_DEFAULT, false),
                channel(CH_INFO_SOUND, tr("Activité des sessions (son)", "Session activity (sound)"), NotificationManager.IMPORTANCE_DEFAULT, true),
                channel(CH_SILENT, tr("Notifications discrètes", "Silent notifications"), NotificationManager.IMPORTANCE_LOW, false),
            ),
        )
    }

    fun notify(event: AgentEvent, settings: CompanionSettings, muted: Boolean = false) {
        if (!canPost()) return
        // Résumé groupé : un événement non urgent rejoint ceux encore affichés, sans vibrer de nouveau.
        val digestible = settings.groupInfo && Digest.accepts(event)
        if (digestible) {
            val earlier = takeDigestEvents().filter { it.id != event.id }
            if (earlier.isNotEmpty()) {
                notifyDigest((listOf(event) + earlier).take(Digest.MAX_EVENTS))
                return
            }
        }
        val style = if (muted) AlertStyle.SILENT else settings.styleFor(event.type)
        val channel = when {
            style == AlertStyle.SILENT -> CH_SILENT
            event.type.urgent -> if (settings.sound) CH_URGENT_SOUND else CH_URGENT
            else -> if (settings.sound) CH_INFO_SOUND else CH_INFO
        }
        val ask = event.ask?.takeUnless { it.isExpired() }
        // Les agents écrivent en Markdown : converti en gras, italique, code… que la notification sait afficher.
        val text: CharSequence = when {
            !settings.showPreview -> event.project ?: tr("Session ${event.agent.label}", "${event.agent.label} session")
            // Une commande n'est pas du Markdown : `rm *.log *.tmp` doit rester tel quel.
            ask?.kind == Ask.PERMISSION && ask.detail.isNotBlank() -> SpannableStringBuilder(event.message + tr(" : ", ": ")).apply {
                val start = length
                append(ask.detail)
                setSpan(TypefaceSpan("monospace"), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            ask?.kind == Ask.REPLY && ask.detail.isNotBlank() -> markdownSpanned(ask.detail)
            else -> markdownSpanned(event.message)
        }
        val notificationId = event.notificationId

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_agentchi)
            .setColor(ContextCompat.getColor(context, R.color.brand_orange))
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
            .setContentIntent(if (ask != null) AskActivity.pendingIntent(context, event) else openAppIntent(null))
            .setGroup(GROUP)

        if (digestible) builder.addExtras(digestExtras(listOf(event)))
        if (ask != null) addAnswerActions(builder, ask, settings.quickReplies)

        if (event.openableOnPhone) {
            builder.addAction(0, tr("📱 Ouvrir sur le téléphone", "📱 Open on phone"), OpenOnPhoneReceiver.pendingIntent(context, event, notificationId))
        }

        if (style == AlertStyle.FULL_SCREEN) {
            val fullScreen = if (ask != null) AskActivity.pendingIntent(context, event) else AlertActivity.pendingIntent(context, event, notificationId)
            builder.setFullScreenIntent(fullScreen, true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())

        if (style != AlertStyle.SILENT) vibrate(settings.vibrationFor(event.type))
    }

    /**
     * Une notification pour tous les événements non urgents pas encore lus : « ✅ 3 tâches terminées
     * sur 2 projets ». Elle remplace les notifications individuelles et ouvre l'historique.
     */
    private fun notifyDigest(events: List<AgentEvent>) {
        val summary = Digest.summary(events)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(summary.title).setSummaryText(summary.text)
        summary.lines.forEach { style.addLine(it) }
        val builder = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_agentchi)
            .setColor(ContextCompat.getColor(context, R.color.brand_orange))
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setStyle(style)
            .setNumber(events.size)
            .setWhen(events.first().time * 1000)
            .setShowWhen(true)
            .setAutoCancel(true)
            // Déjà signalé par la première notification : le résumé se met à jour en silence.
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(MainActivity.ROUTE_HISTORY))
            .setGroup(GROUP)
            .addExtras(digestExtras(events))
        NotificationManagerCompat.from(context).notify(DIGEST_ID, builder.build())
    }

    /** Succès débloqués : une notification chacun, qui ouvre la liste des succès. */
    fun notifyAchievements(achievements: List<Achievement>) {
        if (!canPost()) return
        achievements.forEach { a ->
            val builder = NotificationCompat.Builder(context, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat_agentchi)
                .setColor(ContextCompat.getColor(context, R.color.brand_orange))
                .setContentTitle(tr("🏆 Succès débloqué", "🏆 Achievement unlocked"))
                .setContentText("${a.emoji} ${a.title}${tr(" : ", ": ")}${a.description}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${a.emoji} ${a.title}\n${a.description}"))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openAppIntent(MainActivity.ROUTE_ACHIEVEMENTS))
            NotificationManagerCompat.from(context).notify(ACHIEVEMENT_ID + a.ordinal, builder.build())
        }
    }

    /** Événements des notifications regroupables encore affichées ; les individuelles sont retirées. */
    private fun takeDigestEvents(): List<AgentEvent> {
        val nm = context.getSystemService(NotificationManager::class.java)
        val active = runCatching { nm.activeNotifications.toList() }.getOrDefault(emptyList())
        return active.flatMap { sbn ->
            val raw = sbn.notification.extras.getString(EXTRA_DIGEST) ?: return@flatMap emptyList()
            if (sbn.id != DIGEST_ID) nm.cancel(sbn.id)
            runCatching { EventParser.json.decodeFromString(DIGEST_SERIALIZER, raw) }.getOrDefault(emptyList())
        }.distinctBy { it.id }.sortedByDescending { it.time }
    }

    private fun digestExtras(events: List<AgentEvent>) =
        Bundle().apply { putString(EXTRA_DIGEST, EventParser.json.encodeToString(DIGEST_SERIALIZER, events)) }

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
                add(1, tr("✅ Autoriser", "✅ Allow"), AskAnswer(AskAnswer.ALLOW))
                add(2, tr("❌ Refuser", "❌ Deny"), AskAnswer(AskAnswer.DENY))
            }
            Ask.PLAN -> {
                add(1, tr("✅ Approuver le plan", "✅ Approve the plan"), AskAnswer(AskAnswer.ALLOW))
                add(2, tr("❌ Refuser", "❌ Deny"), AskAnswer(AskAnswer.DENY))
            }
            Ask.QUESTION -> {
                val single = ask.questions.singleOrNull()?.takeIf { !it.multi && it.options.size in 1..4 }
                single?.options?.forEachIndexed { i, option ->
                    add(1 + i, option.label, AskAnswer(AskAnswer.ALLOW, answers = mapOf(single.question to option.label)))
                }
            }
            Ask.REPLY -> {
                builder.addAction(AnswerReceiver.replyAction(context, ask, 1, quickReplies))
                add(2, tr("🏁 Terminer", "🏁 Finish"), AskAnswer.pass)
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

    /** Ouvre l'appli, sur l'écran [route] s'il est donné (voir [MainActivity.EXTRA_ROUTE]). */
    private fun openAppIntent(route: String?): PendingIntent = PendingIntent.getActivity(
        context,
        route?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CH_URGENT = "agent_urgent"
        const val CH_URGENT_SOUND = "agent_urgent_sound"
        const val CH_INFO = "agent_info"
        const val CH_INFO_SOUND = "agent_info_sound"
        const val CH_SILENT = "agent_silent"
        private const val GROUP = "agent_events"
        private const val DIGEST_ID = 4_202
        private const val ACHIEVEMENT_ID = 4_300
        private const val EXTRA_DIGEST = "fr.codingpacer.agentchi.digest"
        private val DIGEST_SERIALIZER = ListSerializer(AgentEvent.serializer())
    }
}
