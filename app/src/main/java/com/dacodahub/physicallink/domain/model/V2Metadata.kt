package com.dacodahub.physicallink.domain.model

data class V2Sensor(val id: String, val name: String, val unit: String)
data class V2Metadata(val deviceId: String, val device: String, val epoch: String, val sensors: List<V2Sensor>, val version: Int = 2)
