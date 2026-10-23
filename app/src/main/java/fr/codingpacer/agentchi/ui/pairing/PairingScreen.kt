package fr.codingpacer.agentchi.ui.pairing

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
import fr.codingpacer.agentchi.net.PairingCode
import fr.codingpacer.agentchi.push.Push
import fr.codingpacer.agentchi.text.tr
import fr.codingpacer.agentchi.ui.CompanionViewModel
import fr.codingpacer.agentchi.ui.common.rememberTextInput
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Duration
import java.time.Instant

/**
 * Ordinateurs et environnements cloud appairés avec la montre via le serveur Agentchi.
 * Chaque code d'appairage se saisit dans le plugin Claude Code, les hooks Codex ou un environnement cloud
 * et se révoque ici.
 */
@Composable
fun PairingScreen(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.pairing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var confirm by remember { mutableStateOf<String?>(null) }

    val editServer = rememberTextInput(tr("Adresse du serveur Agentchi", "Agentchi server address")) { url ->
        if (url.startsWith("https://") || url.startsWith("http://")) viewModel.setServer(url)
        else Toast.makeText(context, tr("L'adresse doit commencer par https://", "The address must start with https://"), Toast.LENGTH_SHORT).show()
    }
    val newCode = rememberTextInput(tr("Nom de l'ordinateur ou de l'environnement", "Computer or environment name")) { label -> viewModel.createCode(label) }

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
            item { ListHeader { Text(tr("Serveur", "Server")) } }

            val created = ui.newCode
            val createdCode = created?.code
            when {
                settings.serverUrl.isBlank() -> {
                    hint(tr("Indique l'adresse de ton serveur Agentchi (dossier server/ du dépôt).", "Enter the address of your Agentchi server (the repo's server/ folder)."))
                    item { Action(tr("🌐 Adresse du serveur", "🌐 Server address"), tr("Non configurée", "Not set"), onClick = editServer) }
                }

                !Push.isConfigured -> {
                    hint(
                        tr(
                            "Cette version de l'appli a été compilée sans projet Firebase : la montre ne peut pas recevoir de push. Voir docs/self-hosted, étape « Firebase ».",
                            "This build of the app has no Firebase project: the watch can't receive pushes. See docs/self-hosted, \"Firebase\" step.",
                        ),
                    )
                }

                !settings.isConnected -> {
                    hint(tr("Connecte la montre à ${host(settings.serverUrl)} pour appairer tes ordinateurs.", "Connect the watch to ${host(settings.serverUrl)} to pair your computers."))
                    item {
                        Action(if (ui.busy) tr("Connexion…", "Connecting…") else tr("🔗 Connecter la montre", "🔗 Connect the watch"), null, primary = true) {
                            if (!ui.busy) viewModel.connect()
                        }
                    }
                    item { Action(tr("🌐 Adresse", "🌐 Address"), settings.serverUrl, onClick = editServer) }
                }

                created != null && createdCode != null -> {
                    item { Text(tr("Code pour « ${created.label} »", "Code for \"${created.label}\""), style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
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
                    hint(
                        tr(
                            "Claude Code : plugin agentchi. Codex : variable AGENTCHI_CODE. Serveur ${host(settings.serverUrl)}, commandes sur sa page.",
                            "Claude Code: agentchi plugin. Codex: AGENTCHI_CODE variable. Server ${host(settings.serverUrl)}, commands on its page.",
                        ),
                    )
                    hint(tr("Ce code n'est affiché qu'une fois.", "This code is only shown once."))
                    item { Action(tr("✅ C'est noté", "✅ Got it"), null, primary = true, onClick = viewModel::dismissNewCode) }
                }

                else -> {
                    item {
                        Action(
                            if (ui.busy) "…" else tr("➕ Nouveau code", "➕ New code"),
                            tr("Un par ordinateur ou environnement cloud", "One per computer or cloud environment"),
                            primary = true,
                            onClick = newCode,
                        )
                    }
                    if (ui.codes.isNotEmpty()) item { ListHeader { Text(tr("Ordinateurs et cloud", "Computers and cloud"), color = MaterialTheme.colors.primary) } }
                    if (ui.codes.isEmpty() && !ui.busy) hint(tr("Aucun ordinateur appairé pour l'instant.", "No computer paired yet."))
                    ui.codes.sortedByDescending { it.lastUsed ?: it.created }.forEach { code ->
                        item {
                            val confirming = confirm == code.id
                            Action(
                                label = if (confirming) tr("Révoquer « ${code.label} » ?", "Revoke \"${code.label}\"?") else "💻 ${code.label}",
                                secondary = if (confirming) tr("Toucher pour confirmer", "Tap to confirm") else usage(code),
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
                        Action(tr("📡 Tester le push", "📡 Test push"), tr("Le serveur envoie une notification", "The server sends a notification")) {
                            viewModel.testServer()
                            Toast.makeText(context, tr("Test envoyé…", "Test sent…"), Toast.LENGTH_SHORT).show()
                        }
                    }
                    item { Action(tr("🌐 Adresse", "🌐 Address"), settings.serverUrl, onClick = editServer) }
                    item {
                        val confirming = confirm == DISCONNECT
                        Action(
                            if (confirming) tr("Confirmer ?", "Confirm?") else tr("Déconnecter la montre", "Disconnect the watch"),
                            tr("Révoque tous les codes", "Revokes all codes"),
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
    val used = code.lastUsed?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return tr("Jamais utilisé", "Never used")
    val minutes = Duration.between(used, Instant.now()).toMinutes()
    val ago = when {
        minutes < 1 -> tr("à l'instant", "just now")
        minutes < 60 -> tr("il y a $minutes min", "$minutes min ago")
        minutes < 48 * 60 -> tr("il y a ${minutes / 60} h", "${minutes / 60} h ago")
        else -> tr("il y a ${minutes / (24 * 60)} j", "${minutes / (24 * 60)} d ago")
    }
    return listOfNotNull(tr("Utilisé $ago", "Used $ago"), code.lastProject).joinToString(" · ")
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
