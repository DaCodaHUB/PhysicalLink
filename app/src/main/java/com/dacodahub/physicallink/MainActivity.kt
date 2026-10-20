package com.dacodahub.physicallink

import android.os.Bundle
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
            PhysicalLinkTheme { PhysicalLinkNavHost() }
        }
    }
}
