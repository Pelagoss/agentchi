package com.claudecompanion.notify

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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.claudecompanion.R
import com.claudecompanion.data.AlertStyle
import com.claudecompanion.data.ClaudeEvent
import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.VibrationPattern
import com.claudecompanion.ui.MainActivity
import com.claudecompanion.ui.alert.AlertActivity

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
                channel(CH_SERVICE, "Connexion instantanée", NotificationManager.IMPORTANCE_MIN, false),
            ),
        )
    }

    fun notify(event: ClaudeEvent, settings: CompanionSettings, muted: Boolean = false) {
        if (!canPost()) return
        val style = if (muted) AlertStyle.SILENT else settings.styleFor(event.type)
        val channel = when {
            style == AlertStyle.SILENT -> CH_SILENT
            event.type.urgent -> if (settings.sound) CH_URGENT_SOUND else CH_URGENT
            else -> if (settings.sound) CH_INFO_SOUND else CH_INFO
        }
        val text = if (settings.showPreview) event.message else event.project ?: "Session Claude"
        val notificationId = event.id.hashCode()

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_claude)
            .setColor(ContextCompat.getColor(context, R.color.claude_orange))
            .setContentTitle(event.title)
            .setContentText(text)
            .setSubText(event.project)
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
            .setContentIntent(openAppIntent())
            .setGroup(GROUP)

        if (style == AlertStyle.FULL_SCREEN) {
            builder.setFullScreenIntent(AlertActivity.pendingIntent(context, event, notificationId), true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())

        if (style != AlertStyle.SILENT) vibrate(settings.vibrationFor(event.type))
    }

    fun vibrate(pattern: VibrationPattern) {
        val timings = pattern.timings ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }

    fun cancel(notificationId: Int) = NotificationManagerCompat.from(context).cancel(notificationId)

    fun serviceNotification() = NotificationCompat.Builder(context, CH_SERVICE)
        .setSmallIcon(R.drawable.ic_stat_claude)
        .setContentTitle("Claude Companion")
        .setContentText("À l'écoute des sessions Claude")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(openAppIntent())
        .build()

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
        const val CH_SERVICE = "claude_service"
        const val SERVICE_NOTIFICATION_ID = 1
        private const val GROUP = "claude_events"
    }
}
