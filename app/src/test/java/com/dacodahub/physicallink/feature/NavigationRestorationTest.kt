package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.navigation.PhysicalLinkNavContent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavigationRestorationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `saved navigation restores demo argument and Devices back stack`() {
        lateinit var nav: NavHostController
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PhysicalLinkTheme(dynamicColor = false) {
            nav = rememberNavController()
            PhysicalLinkNavContent(nav, devices = { _, _ -> Text("Devices destination") }, dashboard = { _, _ -> Text("Dashboard destination") }, sensorDetails = { _, _, _ -> Text("Details") })
        } }
        compose.runOnIdle { nav.navigate("dashboard?demo=true") }
        compose.onNodeWithText("Dashboard destination").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Dashboard destination").assertExists()
        compose.runOnIdle {
            assertTrue(nav.currentBackStackEntry!!.arguments!!.getBoolean("demo"))
            assertEquals("devices", nav.previousBackStackEntry!!.destination.route)
            assertTrue(nav.popBackStack())
        }
        compose.onNodeWithText("Devices destination").assertExists()
    }
}
