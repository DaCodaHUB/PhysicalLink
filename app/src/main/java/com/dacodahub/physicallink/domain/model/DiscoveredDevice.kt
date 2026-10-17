package com.dacodahub.physicallink.domain.model

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int
)
