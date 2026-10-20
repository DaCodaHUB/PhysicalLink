package com.dacodahub.physicallink.data.repository

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import androidx.core.content.ContextCompat
import com.dacodahub.physicallink.data.ble.BleConnectionManager
import com.dacodahub.physicallink.data.ble.BleScanner
import com.dacodahub.physicallink.domain.model.ConnectionState
import com.dacodahub.physicallink.domain.model.MonotonicClock
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Application-scoped owner of the single BLE session; independent of screen lifetimes. */
@Singleton
class BleDeviceRepository @Inject constructor(
    @ApplicationContext context: Context
) : DeviceRepository {
    private val handler = Handler(Looper.getMainLooper())
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) action() else handler.post { action() }
    }
    private val scanner = BleScanner(context)
    override fun monotonicNowMs(): Long = SystemClock.elapsedRealtime()
    private val history = SessionSensorHistory(MonotonicClock { monotonicNowMs() })
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectionManager = BleConnectionManager(context, history::record, history::startSession, history::endSession)
    override val sensorHistory = history.state

    override val connectedDeviceId = connectionManager.deviceId

    init {
        historyScope.launch {
            history.state.map { it.active }.distinctUntilChanged().collectLatest { active ->
                if (active) while (isActive) { delay(250); history.publish() }
            }
        }
        // Application-scoped receiver; broadcasts invalidate sessions, never reconnect them.
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refreshBluetoothState() }
        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    override val scanState = scanner.state
    override val devices = scanner.devices
    override val connectionState = connectionManager.connectionState
    override val capabilities = connectionManager.capabilities
    override val sensorValues = connectionManager.sensorValues

    override fun startScan() = onMain {
        if (connectionState.value == ConnectionState.Connected || connectionState.value == ConnectionState.Connecting ||
            connectionState.value == ConnectionState.Discovering) return@onMain
        connectionManager.clearConnectionError()
        scanner.startScan()
    }

    override fun stopScan() = scanner.stopScan()

    override fun connect(address: String) = onMain {
        scanner.stopScan(clearError = true)
        connectionManager.connect(address)
    }

    override fun refreshBluetoothState() {
        scanner.refreshEnvironment()
        connectionManager.refreshEnvironment()
    }

    override fun disconnect() = connectionManager.disconnect()
}
