package fr.codingpacer.agentchi.widget

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import fr.codingpacer.agentchi.CompanionApp
import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.pet.PetState
import fr.codingpacer.agentchi.text.Markdown
import fr.codingpacer.agentchi.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/** Tuile : la mascotte et son humeur, ou la question de l'agent qui attend une réponse. */
class CompanionTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val app = application as CompanionApp
            val now = System.currentTimeMillis()
            val pet = PetLogic.tick(app.store.pet.first(), now)
            val pending = app.responder.pending()
            val face = faceOf(pet, now, pending != null)
            val look = app.store.settings.first().mascot
            TileBuilders.Tile.Builder()
                // Une version par apparence et expression : la tuile redemande l'image quand elle change.
                .setResourcesVersion(MascotBitmap.version(look, face))
                // Les jauges baissent avec le temps.
                .setFreshnessIntervalMillis(15 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(requestParams.deviceConfiguration, pet, now, pending, face)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val (look, face) = MascotBitmap.parse(requestParams.version)
            ResourceBuilders.Resources.Builder()
                .setVersion(requestParams.version)
                .addIdToImageMapping(
                    MASCOT,
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(MascotBitmap.png(look, face))
                                .setWidthPx(MascotBitmap.WIDTH_PX)
                                .setHeightPx(MascotBitmap.HEIGHT_PX)
                                // Données PNG, décodées par la tuile.
                                .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                                .build(),
                        )
                        .build(),
                )
                .build()
        }

    /** Expression de la mascotte sur la tuile : affolée quand un agent attend une réponse. */
    private fun faceOf(pet: PetState, now: Long, pending: Boolean): MascotSprite.Face =
        if (pending) MascotSprite.Face.WIDE else MascotSprite.face(PetLogic.mood(pet), PetLogic.currentReaction(pet, now))

    private fun layout(
        device: DeviceParametersBuilders.DeviceParameters,
        pet: PetState,
        now: Long,
        pending: AgentEvent?,
        face: MascotSprite.Face,
    ): LayoutElementBuilders.LayoutElement {
        val mood = PetLogic.mood(pet)
        val reaction = PetLogic.currentReaction(pet, now)
        val bubble = if (pending != null) "❗" else MascotSprite.bubble(mood, reaction)
        val title = if (pending != null) "${pending.type.emoji} ${pending.agent.label} attend" else "${pet.name} · niv. ${PetLogic.level(pet.xp)}"
        val body = pending?.message?.let { Markdown.plain(it) } ?: listOfNotNull(bubble, PetLogic.moodLine(pet, reaction)).joinToString(" ")
        val footer = pending?.origin ?: "🍪 ${pet.satiety.toInt()}  ⚡ ${pet.energy.toInt()}  💖 ${pet.happiness.toInt()}"
        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(text(title, Typography.TYPOGRAPHY_CAPTION1, ORANGE, maxLines = 1))
            .setContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(MASCOT)
                            .setWidth(DimensionBuilders.dp(MascotBitmap.WIDTH_DP))
                            .setHeight(DimensionBuilders.dp(MascotBitmap.HEIGHT_DP))
                            .build(),
                    )
                    .addContent(text(body, Typography.TYPOGRAPHY_CAPTION1, WHITE, maxLines = 2))
                    .build(),
            )
            .setSecondaryLabelTextContent(text(footer, Typography.TYPOGRAPHY_CAPTION2, GREY, maxLines = 1))
            .setPrimaryChipContent(
                CompactChip.Builder(this, if (pending != null) "Répondre" else "Ouvrir", openApp(pending != null), device).build(),
            )
            .build()
    }

    private fun text(value: String, typography: Int, color: Int, maxLines: Int) =
        Text.Builder(this, value)
            .setTypography(typography)
            .setColor(ColorBuilders.argb(color))
            .setMaxLines(maxLines)
            .build()

    /** Ouvre l'appli, ou directement l'écran de réponse s'il y a une question en attente. */
    private fun openApp(ask: Boolean) = ModifiersBuilders.Clickable.Builder()
        .setId(if (ask) "ask" else "open")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .addKeyToExtraMapping(MainActivity.EXTRA_OPEN_ASK, ActionBuilders.AndroidBooleanExtra.Builder().setValue(ask).build())
                        .build(),
                )
                .build(),
        )
        .build()

    companion object {
        private const val MASCOT = "mascot"
        private const val ORANGE = 0xFFD97757.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GREY = 0xFFB0AEA5.toInt()
    }
}
