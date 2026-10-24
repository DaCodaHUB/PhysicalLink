
package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.*
import java.util.UUID

sealed interface GattOperation {
    data class WriteControl(val value: ByteArray) : GattOperation

    data class ReadCharacteristic(
        val characteristicUuid: UUID
    ) : GattOperation

    data class EnableNotifications(
        val characteristicUuid: UUID
    ) : GattOperation
}
