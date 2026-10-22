package fr.codingpacer.agentchi.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import fr.codingpacer.agentchi.CompanionApp
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.pet.PetState
import fr.codingpacer.agentchi.ui.MainActivity
import kotlinx.coroutines.flow.first

/**
 * Complication : forme de la mascotte sur le cadran, 🔔 quand un agent attend une réponse.
 * En image (PHOTO_IMAGE, SMALL_IMAGE), la mascotte elle-même : c'est elle qu'affiche le cadran Agentchi.
 */
class PetComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val app = application as CompanionApp
        val now = System.currentTimeMillis()
        val pet = PetLogic.tick(app.store.pet.first(), now)
        val pending = app.responder.pending() != null
        // Affolée quand un agent attend une réponse, comme sur la tuile.
        val face = if (pending) MascotSprite.Face.WIDE else MascotSprite.face(PetLogic.mood(pet), PetLogic.currentReaction(pet, now))
        return data(request.complicationType, pet, pending, app.store.settings.first().mascot, face)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, PetState(), pending = false, MascotSprite.Look(), MascotSprite.Face.OPEN)

    private fun data(
        type: ComplicationType,
        pet: PetState,
        pending: Boolean,
        look: MascotSprite.Look,
        face: MascotSprite.Face,
    ): ComplicationData? {
        val emoji = if (pending) "🔔" else PetLogic.emoji(PetLogic.mood(pet))
        val value = PetLogic.wellbeing(pet)
        val description = plain(if (pending) "Un agent attend ta réponse" else "${pet.name} : en forme à $value %")
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
            ComplicationType.PHOTO_IMAGE -> PhotoImageComplicationData.Builder(mascot(look, face), description)
                .setTapAction(tap)
                .build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(mascot(look, face), SmallImageType.PHOTO).build(),
                description,
            )
                .setTapAction(tap)
                .build()
            else -> null
        }
    }

    private fun mascot(look: MascotSprite.Look, face: MascotSprite.Face): Icon = Icon.createWithBitmap(MascotBitmap.square(look, face))

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
