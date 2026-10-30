package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.feature.devices.sensorCountLabel
import com.dacodahub.physicallink.domain.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SprintTwoPolishTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `short unit stays near the reading on a full width card`() {
        compose.setContent { PhysicalLinkTheme {
            Box(Modifier.width(320.dp)) { SensorReading("-3.72", "u") }
        } }
        val value = compose.onNodeWithText("-3.72", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val unit = compose.onNodeWithText("u", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(unit.left >= value.right)
        assertTrue("Unit should sit beside its value", unit.left - value.right <= 8.dp)
    }
    @Test fun `normal range message remains available to screen readers without repeated visible text`() {
        val state = DashboardWidgetState(DashboardWidget("id", WidgetType.RANGE, SensorSource("board", "id"),
            settings = WidgetSettings(minimum = -10.0, maximum = 10.0)),
            DeviceCapability("id", "fixture", "u", "number", "Arbitrary reading"), "-0.000000084",
            SensorTimeline(lastSuccessfulMs = 0, latestNumeric = -0.000000084, latestValid = true), ReadingFreshness.LIVE)
        compose.setContent { PhysicalLinkTheme { DashboardWidgetCard(state) } }
        compose.onNodeWithText("Within configured bounds").assertDoesNotExist()
        compose.onNodeWithContentDescription("Within configured bounds").assertExists()
        compose.onNodeWithContentDescription("-8.4E-8 u").assertExists()
        compose.onNodeWithText("Live").assertExists()
    }
    @Test fun `sensor counts use singular only for one`() {
        assertEquals("0 sensors", sensorCountLabel(0))
        assertEquals("1 sensor", sensorCountLabel(1))
        assertEquals("2 sensors", sensorCountLabel(2))
    }
}
