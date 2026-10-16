package com.dacodahub.physicallink.ble

data class DeviceCapabilities(
    val version: Int,
    val device: String,
    val capabilities: List<DeviceCapability>
)

data class DeviceCapability(
    val id: String,
    val uuid: String,
    val unit: String,
    val type: String
)