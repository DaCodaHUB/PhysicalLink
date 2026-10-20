package com.dacodahub.physicallink.domain.model

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Error(val message: String) : ScanState
}
