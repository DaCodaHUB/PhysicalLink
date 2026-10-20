package com.dacodahub.physicallink.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dacodahub.physicallink.feature.dashboard.DashboardScreen
import com.dacodahub.physicallink.feature.devices.DevicesScreen

private object Destinations {
    const val Devices = "devices"
    const val Dashboard = "dashboard"
}

@Composable
fun PhysicalLinkNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destinations.Devices) {
        composable(Destinations.Devices) {
            DevicesScreen(onOpenDashboard = {
                navController.navigate(Destinations.Dashboard) { launchSingleTop = true }
            }, onTryDemo = {
                navController.navigate("${Destinations.Dashboard}?demo=true") { launchSingleTop = true }
            })
        }
        composable("${Destinations.Dashboard}?demo={demo}", arguments = listOf(navArgument("demo") {
            type = NavType.BoolType; defaultValue = false
        })) {
            DashboardScreen(onDevices = {
                navController.popBackStack(Destinations.Devices, inclusive = false)
            })
        }
    }
}
