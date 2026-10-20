package com.claudecompanion.phone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val ClaudeOrange = Color(0xFFD97757)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ClaudeOrange, background = Color(0xFF1F1E1D))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RelayScreen()
                }
            }
        }
    }
}

@Composable
private fun RelayScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var accessGranted by remember { mutableStateOf(false) }
    var watches by remember { mutableStateOf<List<String>?>(null) }
    var enabled by remember { mutableStateOf(Relay.isEnabled(context)) }
    var hideOnPhone by remember { mutableStateOf(Relay.hideOnPhone(context)) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // Rafraîchit l'état à chaque retour sur l'écran (par ex. après les réglages Android).
    LaunchedEffect(refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessGranted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            watches = runCatching { Wearable.getNodeClient(context).connectedNodes.await().map { it.displayName } }
                .getOrDefault(emptyList())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Claude Companion", style = MaterialTheme.typography.headlineMedium, color = ClaudeOrange)
            Text(
                "Relaie les notifications de l'appli Claude (sessions cloud, conversations, Remote Control) vers ta montre.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Step(
                done = accessGranted,
                title = "1. Accès aux notifications",
                detail = if (accessGranted) {
                    "Autorisé"
                } else {
                    "Claude Companion lit uniquement les notifications de l'appli Claude, pour les afficher sur ta montre. " +
                        "Elles ne sont envoyées nulle part ailleurs et rien n'est collecté."
                },
            ) {
                if (!accessGranted) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("Autoriser")
                    }
                }
            }
        }
        item {
            val list = watches
            Step(
                done = !list.isNullOrEmpty(),
                title = "2. Montre connectée",
                detail = when {
                    list == null -> "Recherche…"
                    list.isEmpty() -> "Aucune montre connectée. Installe Claude Companion sur la montre."
                    else -> list.joinToString()
                },
            ) {
                OutlinedButton(onClick = { refresh++ }) { Text("Actualiser") }
            }
        }
        item {
            Step(done = enabled, title = "3. Relais actif", detail = "Couper sans retirer l'accès aux notifications.") {
                Switch(checked = enabled, onCheckedChange = { enabled = it; Relay.setEnabled(context, it) })
            }
        }
        item {
            Step(
                done = hideOnPhone,
                title = "4. Seulement sur la montre",
                detail = "Retire la notification Claude du téléphone dès que la montre l'a reçue. " +
                    "Si la montre est injoignable, elle reste sur le téléphone.\n" +
                    "Pour que le téléphone ne sonne pas non plus : Paramètres Android › Notifications › Claude › Silencieux (sans désactiver).",
            ) {
                Switch(checked = hideOnPhone, onCheckedChange = { hideOnPhone = it; Relay.setHideOnPhone(context, it) })
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val n = RelayedNotification(
                                key = "test",
                                title = "Test du relais",
                                text = "Claude a terminé : la notification arrive bien sur ta montre ✅",
                                time = System.currentTimeMillis(),
                            )
                            val count = Relay.send(context, n)
                            testResult = if (count > 0) "Envoyé à $count montre(s)." else "Aucune montre atteinte."
                        }
                    }) { Text("Envoyer un test à la montre") }
                    testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Text("Derniers relais", style = MaterialTheme.typography.titleMedium) }
        if (Relay.log.isEmpty()) {
            item { Text("Aucune notification relayée pour l'instant.", style = MaterialTheme.typography.bodySmall) }
        }
        items(Relay.log.toList()) { entry ->
            Column {
                Text(entry.title.ifBlank { "Claude" }, style = MaterialTheme.typography.bodyMedium)
                Text(entry.text, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.time).toString() +
                        if (entry.delivered > 0) " · ✓ montre" else " · ✗ montre non jointe",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun Step(done: Boolean, title: String, detail: String, action: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (done) "✅ " else "⬜ ") + title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.padding(4.dp))
            action()
        }
    }
}
