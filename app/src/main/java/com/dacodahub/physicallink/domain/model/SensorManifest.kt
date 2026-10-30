package com.dacodahub.physicallink.domain.model

data class ManifestSensor(val id: String, val name: String, val unit: String)
data class SensorManifest(val deviceId: String, val device: String, val epoch: String, val sensors: List<ManifestSensor>, val version: Int = 2)
