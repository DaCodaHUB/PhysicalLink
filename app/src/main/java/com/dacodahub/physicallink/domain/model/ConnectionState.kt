package com.dacodahub.physicallink.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Discovering : ConnectionState
    data object Connected : ConnectionState
    data object ConnectionLost : ConnectionState
    data class Error(val message: String) : ConnectionState
}
