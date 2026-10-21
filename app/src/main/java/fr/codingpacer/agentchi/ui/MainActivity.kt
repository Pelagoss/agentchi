package fr.codingpacer.agentchi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import fr.codingpacer.agentchi.CompanionApp
import fr.codingpacer.agentchi.ui.achievements.AchievementsScreen
import fr.codingpacer.agentchi.ui.ask.AskActivity
import fr.codingpacer.agentchi.ui.sessions.SessionsScreen
import fr.codingpacer.agentchi.ui.history.HistoryScreen
import fr.codingpacer.agentchi.ui.pairing.PairingScreen
import fr.codingpacer.agentchi.ui.pet.PetScreen
import fr.codingpacer.agentchi.ui.settings.SettingsRoute
import fr.codingpacer.agentchi.ui.settings.settingsPages
import fr.codingpacer.agentchi.ui.theme.CompanionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CompanionViewModel by viewModels()

    /** Écran à ouvrir, demandé par l'intent (voir [EXTRA_ROUTE]). */
    private val route = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        openPendingAsk(intent)
        route.value = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            CompanionTheme {
                val navController = rememberSwipeDismissableNavController()
                // Depuis une notification (résumé groupé, succès) : ouvre l'écran demandé.
                val requested by route
                LaunchedEffect(requested) {
                    requested?.let {
                        navController.navigate(it) { launchSingleTop = true }
                        route.value = null
                    }
                }
                SwipeDismissableNavHost(navController = navController, startDestination = "pet") {
                    composable("pet") {
                        PetScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate(SettingsRoute.MENU) },
                            onOpenHistory = { navController.navigate(ROUTE_HISTORY) },
                            onOpenSessions = { navController.navigate(ROUTE_SESSIONS) },
                            onOpenAchievements = { navController.navigate(ROUTE_ACHIEVEMENTS) },
                        )
                    }
                    settingsPages(viewModel, navigate = { navController.navigate(it) }, onOpenPairing = { navController.navigate("pairing") })
                    composable(ROUTE_HISTORY) { HistoryScreen(viewModel) }
                    composable(ROUTE_SESSIONS) { SessionsScreen(viewModel) }
                    composable(ROUTE_ACHIEVEMENTS) { AchievementsScreen(viewModel) }
                    composable("pairing") { PairingScreen(viewModel) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPendingAsk(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }

    /** Depuis la tuile ou la complication : ouvre directement la question en attente, s'il y en a une. */
    private fun openPendingAsk(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ASK, false) != true) return
        intent.removeExtra(EXTRA_OPEN_ASK)
        lifecycleScope.launch {
            (application as CompanionApp).responder.pending()?.let { startActivity(AskActivity.intent(this@MainActivity, it)) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPushToken()
    }

    companion object {
        const val EXTRA_OPEN_ASK = "open_ask"

        /** Écran à ouvrir : [ROUTE_HISTORY], [ROUTE_SESSIONS] ou [ROUTE_ACHIEVEMENTS]. */
        const val EXTRA_ROUTE = "route"
        const val ROUTE_HISTORY = "history"
        const val ROUTE_SESSIONS = "sessions"
        const val ROUTE_ACHIEVEMENTS = "achievements"
    }
}
