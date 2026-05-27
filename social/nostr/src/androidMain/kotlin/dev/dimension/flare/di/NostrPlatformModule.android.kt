package dev.dimension.flare.di

import dev.dimension.flare.data.network.nostr.AmberIntentLauncherRegistry
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.AndroidAmberSignerBridge
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal actual val nostrPlatformModule: Module =
    module {
        singleOf(::AmberIntentLauncherRegistry)
        single<AmberSignerBridge> { AndroidAmberSignerBridge(androidContext(), get()) }
    }
