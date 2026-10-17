package com.dacodahub.physicallink.di

import com.dacodahub.physicallink.data.repository.BleDeviceRepository
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {
    @Binds
    abstract fun bindDeviceRepository(repository: BleDeviceRepository): DeviceRepository
}
