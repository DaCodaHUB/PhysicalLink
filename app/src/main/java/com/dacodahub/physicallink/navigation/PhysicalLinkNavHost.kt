package com.dacodahub.physicallink.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dacodahub.physicallink.domain.model.SensorSource
import com.dacodahub.physicallink.feature.sensordetails.SensorDetailsTarget
import com.dacodahub.physicallink.feature.sensordetails.SensorDetailsScreen
import com.dacodahub.physicallink.feature.dashboard.DashboardViewModel
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
        dashboard = { devices, sensor -> DashboardScreen(onDevices = devices, onOpenSensor = sensor) },
        sensorDetails = { source, parent, back -> SensorDetailsScreen(source, back, hiltViewModel<DashboardViewModel>(parent)) })
}

/** Screen slots keep the real graph testable without replacing its transitions or route arguments. */
@Composable
internal fun PhysicalLinkNavContent(
    navController: NavHostController,
    devices: @Composable (() -> Unit, () -> Unit) -> Unit,
    dashboard: @Composable (() -> Unit, (SensorDetailsTarget) -> Unit) -> Unit,
    sensorDetails: @Composable (SensorDetailsTarget, NavBackStackEntry, () -> Unit) -> Unit
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
                }, { source -> navController.navigate(sensorDetailsRoute(source)) { launchSingleTop = true } })
            }
            composable("sensor/{deviceId}/{sensorId}/{widgetId}", arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }, navArgument("sensorId") { type = NavType.StringType }, navArgument("widgetId") { type = NavType.StringType }
            )) { entry ->
                val parent = remember(entry) { navController.getBackStackEntry("dashboard?demo={demo}") }
                val source = SensorSource(requireNotNull(entry.arguments?.getString("deviceId")), requireNotNull(entry.arguments?.getString("sensorId")))
                sensorDetails(SensorDetailsTarget(source, requireNotNull(entry.arguments?.getString("widgetId"))), parent) { navController.popBackStack() }
            }
            composable("sensor/{deviceId}/{sensorId}", arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }, navArgument("sensorId") { type = NavType.StringType }
            )) { entry ->
                val parent = remember(entry) { navController.getBackStackEntry("dashboard?demo={demo}") }
                val source = SensorSource(requireNotNull(entry.arguments?.getString("deviceId")), requireNotNull(entry.arguments?.getString("sensorId")))
                // Older saved navigation entries have no instance context. Show the safe missing-widget state.
                sensorDetails(SensorDetailsTarget(source, ""), parent) { navController.popBackStack() }
            }
        }
    }
}

internal fun sensorDetailsRoute(target: SensorDetailsTarget): String = "sensor/${Uri.encode(target.source.deviceId)}/${Uri.encode(target.source.capabilityId)}/${Uri.encode(target.widgetId)}"
