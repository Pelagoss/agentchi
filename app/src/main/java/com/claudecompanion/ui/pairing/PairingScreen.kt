package com.claudecompanion.ui.pairing

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.claudecompanion.net.PairingCode
import com.claudecompanion.ui.CompanionViewModel
import com.claudecompanion.ui.common.rememberTextInput
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Duration
import java.time.Instant

/**
 * Ordinateurs et environnements cloud appairés avec la montre via le serveur Claude Companion.
 * Chaque code d'appairage se saisit dans le plugin Claude Code (ou un environnement cloud)
 * et se révoque ici.
 */
@Composable
fun PairingScreen(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.pairing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var confirm by remember { mutableStateOf<String?>(null) }

    val editServer = rememberTextInput("Adresse du serveur Claude Companion") { url ->
        if (url.startsWith("https://") || url.startsWith("http://")) viewModel.setServer(url)
        else Toast.makeText(context, "L'adresse doit commencer par https://", Toast.LENGTH_SHORT).show()
    }
    val newCode = rememberTextInput("Nom de l'ordinateur ou de l'environnement") { label -> viewModel.createCode(label) }

    LaunchedEffect(settings.isConnected) { if (settings.isConnected) viewModel.refreshCodes() }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Ordinateurs et cloud") } }

            val created = ui.newCode
            val createdCode = created?.code
            when {
                settings.serverUrl.isBlank() -> {
                    hint("Indique l'adresse de ton serveur Claude Companion (dossier server/ du dépôt).")
                    item { Action("🌐 Adresse du serveur", "Non configurée", onClick = editServer) }
                }

                !settings.isConnected -> {
                    hint("Connecte la montre à ${host(settings.serverUrl)} pour appairer tes ordinateurs.")
                    item { Action(if (ui.busy) "Connexion…" else "🔗 Connecter la montre", null, primary = true) { if (!ui.busy) viewModel.connect() } }
                    item { Action("🌐 Serveur", settings.serverUrl, onClick = editServer) }
                }

                created != null && createdCode != null -> {
                    item { Text("Code pour « ${created.label} »", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
                    item {
                        Text(
                            createdCode,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.title3,
                            color = MaterialTheme.colors.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    item { QrCode(remember(createdCode) { qrMatrix(createdCode) }, Modifier.size(96.dp)) }
                    hint("Dans Claude Code : plugin watch-companion, serveur ${host(settings.serverUrl)}. Commande complète sur la page du serveur.")
                    hint("Ce code n'est affiché qu'une fois.")
                    item { Action("✅ C'est noté", null, primary = true, onClick = viewModel::dismissNewCode) }
                }

                else -> {
                    item { Action(if (ui.busy) "…" else "➕ Nouveau code", "Un par ordinateur ou environnement cloud", primary = true, onClick = newCode) }
                    if (ui.codes.isEmpty() && !ui.busy) hint("Aucun ordinateur appairé pour l'instant.")
                    ui.codes.sortedByDescending { it.lastUsed ?: it.created }.forEach { code ->
                        item {
                            val confirming = confirm == code.id
                            Action(
                                label = if (confirming) "Révoquer « ${code.label} » ?" else "💻 ${code.label}",
                                secondary = if (confirming) "Toucher pour confirmer" else usage(code),
                                primary = confirming,
                            ) {
                                if (confirming) {
                                    viewModel.revokeCode(code.id)
                                    confirm = null
                                } else {
                                    confirm = code.id
                                }
                            }
                        }
                    }
                    item {
                        Action("📡 Tester le serveur", "Envoie une notification à la montre") {
                            viewModel.testServer()
                            Toast.makeText(context, "Test envoyé…", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item { Action("🌐 Serveur", settings.serverUrl, onClick = editServer) }
                    item {
                        val confirming = confirm == DISCONNECT
                        Action(
                            if (confirming) "Confirmer ?" else "Déconnecter la montre",
                            "Révoque tous les codes",
                            primary = confirming,
                        ) {
                            if (confirming) {
                                viewModel.disconnect()
                                confirm = null
                            } else {
                                confirm = DISCONNECT
                            }
                        }
                    }
                }
            }

            ui.error?.let { error ->
                item {
                    Text(
                        "⚠️ $error",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val DISCONNECT = "__disconnect__"

private fun ScalingLazyListScope.hint(text: String) = item {
    Text(
        text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Action(label: String, secondary: String?, primary: Boolean = false, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
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

/** « Utilisé il y a 5 min · mon-projet », ou « Jamais utilisé ». */
private fun usage(code: PairingCode): String {
    val used = code.lastUsed?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "Jamais utilisé"
    val minutes = Duration.between(used, Instant.now()).toMinutes()
    val ago = when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 48 * 60 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (24 * 60)} j"
    }
    return listOfNotNull("Utilisé $ago", code.lastProject).joinToString(" · ")
}

private fun host(url: String) = url.substringAfter("://").substringBefore('/')

@Composable
private fun QrCode(matrix: BitMatrix, modifier: Modifier) {
    Canvas(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(6.dp),
    ) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

private fun qrMatrix(text: String): BitMatrix =
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
