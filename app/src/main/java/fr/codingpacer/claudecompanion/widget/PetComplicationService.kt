package fr.codingpacer.claudecompanion.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import fr.codingpacer.claudecompanion.CompanionApp
import fr.codingpacer.claudecompanion.pet.PetLogic
import fr.codingpacer.claudecompanion.pet.PetState
import fr.codingpacer.claudecompanion.ui.MainActivity
import kotlinx.coroutines.flow.first

/** Complication : forme de la mascotte sur le cadran, 🔔 quand Claude attend une réponse. */
class PetComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val app = application as CompanionApp
        val pet = PetLogic.tick(app.store.pet.first(), System.currentTimeMillis())
        return data(request.complicationType, pet, pending = app.responder.pending() != null)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, PetState(), pending = false)

    private fun data(type: ComplicationType, pet: PetState, pending: Boolean): ComplicationData? {
        val emoji = if (pending) "🔔" else PetLogic.emoji(PetLogic.mood(pet))
        val value = PetLogic.wellbeing(pet)
        val description = plain(if (pending) "Claude attend ta réponse" else "${pet.name} : en forme à $value %")
        val tap = openApp(this)
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(value.toFloat(), 0f, 100f, description)
                .setText(plain(emoji))
                .setTapAction(tap)
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(if (pending) emoji else "$emoji $value"), description)
                .setTitle(plain(pet.name.take(7)))
                .setTapAction(tap)
                .build()
            else -> null
        }
    }

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()

    companion object {
        /** Ouvre l'appli, ou l'écran de réponse s'il y a une question en attente. */
        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_ASK, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val REQUEST_OPEN = 4_201
    }
}
