package dev.dimension.flare.di

import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.model.PlatformSpec
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
@ComponentScan(
    "dev.dimension.flare.data.datasource.nostr",
    "dev.dimension.flare.data.network.nostr",
    "dev.dimension.flare.data.platform",
)
internal class NostrKoinModule {
    @Single(binds = [PlatformSpec::class])
    fun platformSpec(): NostrPlatformSpec = NostrPlatformSpec
}
