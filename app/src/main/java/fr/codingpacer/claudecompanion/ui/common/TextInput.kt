package fr.codingpacer.claudecompanion.ui.common

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper

private const val KEY = "text"

/**
 * Ouvre la saisie texte de Wear OS (clavier, dictée…) et renvoie le texte saisi.
 * [allowEmpty] : un texte vide est aussi renvoyé (pour effacer), pas seulement une annulation.
 */
@Composable
fun rememberTextInput(label: String, allowEmpty: Boolean = false, onResult: (String) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data: Intent = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(KEY)?.toString()?.trim() ?: return@rememberLauncherForActivityResult
        if (text.isNotEmpty() || allowEmpty) callback(text)
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(KEY).setLabel(label).build()))
        launcher.launch(intent)
    }
}
