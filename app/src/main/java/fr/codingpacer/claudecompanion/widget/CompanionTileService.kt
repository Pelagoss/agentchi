package fr.codingpacer.claudecompanion.widget

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
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
import fr.codingpacer.claudecompanion.CompanionApp
import fr.codingpacer.claudecompanion.data.ClaudeEvent
import fr.codingpacer.claudecompanion.pet.PetLogic
import fr.codingpacer.claudecompanion.pet.PetState
import fr.codingpacer.claudecompanion.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/** Tuile : humeur de la mascotte, ou la question de Claude qui attend une réponse. */
class CompanionTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val app = application as CompanionApp
            val now = System.currentTimeMillis()
            val pet = PetLogic.tick(app.store.pet.first(), now)
            val pending = app.responder.pending()
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                // Les jauges baissent avec le temps.
                .setFreshnessIntervalMillis(15 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(requestParams.deviceConfiguration, pet, now, pending)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private fun layout(
        device: DeviceParametersBuilders.DeviceParameters,
        pet: PetState,
        now: Long,
        pending: ClaudeEvent?,
    ): LayoutElementBuilders.LayoutElement {
        val mood = "${PetLogic.emoji(PetLogic.mood(pet))} ${PetLogic.moodLine(pet, PetLogic.currentReaction(pet, now))}"
        val title = if (pending != null) "${pending.type.emoji} Claude attend" else "${pet.name} · niv. ${PetLogic.level(pet.xp)}"
        val body = pending?.message ?: mood
        val footer = pending?.origin ?: "🍪 ${pet.satiety.toInt()}  ⚡ ${pet.energy.toInt()}  💖 ${pet.happiness.toInt()}"
        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(text(title, Typography.TYPOGRAPHY_CAPTION1, ORANGE, maxLines = 1))
            .setContent(text(body, Typography.TYPOGRAPHY_BODY1, WHITE, maxLines = 3))
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
        private const val RESOURCES_VERSION = "1"
        private const val ORANGE = 0xFFD97757.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GREY = 0xFFB0AEA5.toInt()
    }
}
