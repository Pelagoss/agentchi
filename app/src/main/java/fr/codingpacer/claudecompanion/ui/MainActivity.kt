package fr.codingpacer.claudecompanion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import fr.codingpacer.claudecompanion.ui.history.HistoryScreen
import fr.codingpacer.claudecompanion.ui.pairing.PairingScreen
import fr.codingpacer.claudecompanion.ui.pet.PetScreen
import fr.codingpacer.claudecompanion.ui.settings.SettingsScreen
import fr.codingpacer.claudecompanion.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CompanionViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CompanionTheme {
                val navController = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(navController = navController, startDestination = "pet") {
                    composable("pet") {
                        PetScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenHistory = { navController.navigate("history") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(viewModel, onOpenPairing = { navController.navigate("pairing") })
                    }
                    composable("history") { HistoryScreen(viewModel) }
                    composable("pairing") { PairingScreen(viewModel) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPushToken()
    }
}
