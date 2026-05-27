package dev.dimension.flare.di

import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.AppleAmberSignerBridge
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val nostrPlatformModule: Module =
    module {
        singleOf(::AppleAmberSignerBridge) bind AmberSignerBridge::class
    }
