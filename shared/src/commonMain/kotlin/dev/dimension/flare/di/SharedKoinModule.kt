package dev.dimension.flare.di

import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.repository.ProxyRepository
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
@ComponentScan("dev.dimension.flare")
internal class SharedKoinModule {
    @Single
    fun proxyRepository(fileStorage: FileStorage): ProxyRepository = ProxyRepository(fileStorage)
}
