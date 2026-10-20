package com.claudecompanion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.claudecompanion.CompanionApp
import com.claudecompanion.ui.history.HistoryScreen
import com.claudecompanion.ui.pet.PetScreen
import com.claudecompanion.ui.settings.SettingsScreen
import com.claudecompanion.ui.theme.CompanionTheme
import com.claudecompanion.sync.InstantSyncService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                    composable("settings") { SettingsScreen(viewModel) }
                    composable("history") { HistoryScreen(viewModel) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncNow()
        lifecycleScope.launch {
            if ((application as CompanionApp).store.settings.first().instantMode) {
                InstantSyncService.start(this@MainActivity)
            }
        }
    }
}
