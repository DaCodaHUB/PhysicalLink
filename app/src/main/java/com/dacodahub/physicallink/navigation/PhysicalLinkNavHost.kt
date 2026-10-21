package com.dacodahub.physicallink.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
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
    PhysicalLinkNavContent(rememberNavController(),
        devices = { openDashboard, tryDemo -> DevicesScreen(onOpenDashboard = openDashboard, onTryDemo = tryDemo) },
        dashboard = { devices -> DashboardScreen(onDevices = devices) })
}

/** Screen slots keep the real graph testable without replacing its transitions or route arguments. */
@Composable
internal fun PhysicalLinkNavContent(
    navController: NavHostController,
    devices: @Composable (() -> Unit, () -> Unit) -> Unit,
    dashboard: @Composable (() -> Unit) -> Unit
) {
    // Destination fades temporarily expose their parent. Paint it outside the transition
    // so the Android window background cannot flash through (especially in dark mode).
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = Destinations.Devices) {
            composable(Destinations.Devices) {
                devices({
                    navController.navigate(Destinations.Dashboard) { launchSingleTop = true }
                }, {
                    navController.navigate("${Destinations.Dashboard}?demo=true") { launchSingleTop = true }
                })
            }
            composable("${Destinations.Dashboard}?demo={demo}", arguments = listOf(navArgument("demo") {
                type = NavType.BoolType; defaultValue = false
            })) {
                dashboard({
                    navController.popBackStack(Destinations.Devices, inclusive = false)
                })
            }
        }
    }
}
