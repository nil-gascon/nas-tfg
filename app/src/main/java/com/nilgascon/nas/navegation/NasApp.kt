package com.nilgascon.nas.navegation
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nilgascon.nas.ui.screens.*
import com.nilgascon.nas.viewmodel.NasViewModel
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Drive : Screen("drive")
    object SystemDetails : Screen("system_details")
    object Backup : Screen("backup")
    object Logs : Screen("logs")
    object Setup : Screen("setup")
    object UserManagement : Screen("user_management")
}
@Composable
fun NasApp() {
    val context = LocalContext.current
    val viewModel: NasViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NasViewModel(context.applicationContext as Application) as T
            }
        }
    )
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isSetupNeeded by viewModel.isSetupNeeded.collectAsState()
    LaunchedEffect(isLoggedIn, isSetupNeeded) {
        if (isLoggedIn) {
            if (isSetupNeeded) {
                navController.navigate(Screen.Setup.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                    popUpTo(Screen.Setup.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(viewModel = viewModel)
        }
        composable(Screen.Setup.route) {
            SetupScreen(viewModel = viewModel, onSetupComplete = {
                viewModel.verificarSetupStatus() 
            })
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onOpenDrive = { navController.navigate(Screen.Drive.route) },
                onOpenSystemDetails = { navController.navigate(Screen.SystemDetails.route) },
                onOpenBackup = { navController.navigate(Screen.Backup.route) },
                onOpenLogs = { navController.navigate(Screen.Logs.route) },
                onOpenUserManagement = { navController.navigate(Screen.UserManagement.route) }
            )
        }
        composable(Screen.UserManagement.route) {
            UserManagementScreen(viewModel = viewModel, onGoBack = { navController.popBackStack() })
        }
        composable(Screen.Drive.route) {
            GestorFitxersScreen(
                viewModel = viewModel,
                onGoBack = { navController.popBackStack() }
            )
        }
        composable(Screen.SystemDetails.route) {
            SystemDetailsScreen(
                viewModel = viewModel,
                onGoBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Backup.route) {
            BackupScreen(
                viewModel = viewModel,
                onGoBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Logs.route) {
            LogScreen(viewModel = viewModel, onGoBack = { navController.popBackStack() })
        }
    }
}
