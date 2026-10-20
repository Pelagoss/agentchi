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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.claudecompanion.ui.CompanionViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Affiche le code d'appairage (sujet + clé secrète) à copier dans
 * `hooks/install.sh` sur chaque ordinateur où tourne Claude Code.
 */
@Composable
fun PairingScreen(viewModel: CompanionViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val code = settings.pairingCode
    val matrix = remember(code) { if (settings.secret.isBlank()) null else qrMatrix(code) }
    val context = LocalContext.current
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Hook Claude Code") } }
            item {
                if (matrix != null) QrCode(matrix, Modifier.size(128.dp))
            }
            item {
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    "Facultatif, pour Claude Code dans un terminal.\nSur l'ordinateur : ./hooks/install.sh",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    "Ce code est secret : il permet de lire et d'envoyer tes notifications.",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Chip(
                    onClick = {
                        if (confirmReset) {
                            viewModel.regeneratePairing()
                            confirmReset = false
                            Toast.makeText(context, "Nouveau code : relance install.sh sur tes ordinateurs", Toast.LENGTH_LONG).show()
                        } else {
                            confirmReset = true
                        }
                    },
                    label = { Text(if (confirmReset) "Confirmer ?" else "Nouveau code") },
                    secondaryLabel = { Text("Révoque les ordinateurs appairés") },
                    colors = if (confirmReset) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

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
