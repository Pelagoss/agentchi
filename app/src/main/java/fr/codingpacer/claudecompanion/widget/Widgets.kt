package fr.codingpacer.claudecompanion.widget

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/** Redemande l'affichage de la tuile et de la complication (mascotte, question en attente). */
object Widgets {
    fun refresh(context: Context) {
        val app = context.applicationContext
        runCatching { TileService.getUpdater(app).requestUpdate(CompanionTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, PetComplicationService::class.java)).requestUpdateAll()
        }
    }
}
