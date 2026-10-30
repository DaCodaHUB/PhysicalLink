package com.dacodahub.physicallink

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.dacodahub.physicallink.core.designsystem.theme.Appearance
import com.dacodahub.physicallink.core.designsystem.theme.AppearanceSettings
import javax.inject.Inject
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.navigation.PhysicalLinkNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var devices: DeviceRepository

    override fun onStart() {
        super.onStart()
        devices.refreshBluetoothState()
    }

    override fun onStop() {
        devices.stopScan()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings = remember { AppearanceSettings(applicationContext) }
            val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance.SYSTEM)
            val dark = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PhysicalLinkTheme(darkTheme = dark) { PhysicalLinkNavHost() }
        }
    }
}
