
package com.dacodahub.physicallink.ble

import java.util.UUID

sealed interface GattOperation {

    data class ReadCharacteristic(
        val characteristicUuid: UUID
    ) : GattOperation

    data class EnableNotifications(
        val characteristicUuid: UUID
    ) : GattOperation
}
