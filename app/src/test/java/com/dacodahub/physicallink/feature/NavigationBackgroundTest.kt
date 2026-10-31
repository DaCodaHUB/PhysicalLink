package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.navigation.PhysicalLinkNavContent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private var expected = Color.Unspecified

    private fun setup(dark: Boolean) {
        compose.setContent {
            PhysicalLinkTheme(darkTheme = dark, dynamicColor = false) {
                nav = rememberNavController()
                expected = MaterialTheme.colorScheme.background
                // Represents a contrasting Android window behind the animated destinations.
                Box(Modifier.fillMaxSize().background(Color.Gray).testTag("navigation-frame")) {
                    PhysicalLinkNavContent(nav,
                        devices = { _, _ -> Scaffold { Text("Devices", Modifier.padding(it)) } },
                        dashboard = { _, _ -> Scaffold { Text("Dashboard", Modifier.padding(it)) } }, sensorDetails = { _, _, _ -> Text("Details") })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }
    private fun assertBackground() {
        // Draw the actual view tree with native Skia. PixelCopy's force-redraw handshake
        // is not delivered by the headless Robolectric window on this Windows runner.
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val color = Color(bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4))
        bitmap.recycle()
        assertEquals("Background red changed during navigation", expected.red, color.red, 0.015f)
        assertEquals("Background green changed during navigation", expected.green, color.green, 0.015f)
        assertEquals("Background blue changed during navigation", expected.blue, color.blue, 0.015f)
        assertEquals(1f, color.alpha, 0.001f)
    }
    private fun checkTransition(action: () -> Unit) {
        compose.runOnIdle(action)
        compose.mainClock.advanceTimeBy(32)
        repeat(7) {
            compose.mainClock.advanceTimeBy(80)
            assertBackground()
        }
        compose.mainClock.advanceTimeBy(1000)
        assertBackground()
    }
    private fun exercise(dark: Boolean) {
        setup(dark)
        assertBackground()
        checkTransition { nav.navigate("dashboard?demo=true") }
        compose.runOnIdle { assertEquals(true, nav.currentBackStackEntry!!.arguments!!.getBoolean("demo")) }
        checkTransition { nav.popBackStack() }
        checkTransition { nav.navigate("dashboard") }
        compose.runOnIdle { assertFalse(nav.currentBackStackEntry!!.arguments!!.getBoolean("demo")) }
    }
    @Test fun `dark theme stays opaque during demo exit and real dashboard transitions`() = exercise(true)
    @Test fun `light theme stays opaque during demo exit and real dashboard transitions`() = exercise(false)
}
