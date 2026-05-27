package dev.dimension.flare.di

import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.datasource.nostr.SharedNostrCache
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

internal val nostrCommonModule: Module =
    module {
        single<NostrCache> { SharedNostrCache(get()) }
    }

internal expect val nostrPlatformModule: Module

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public object NostrModule {
    public fun modules(): List<Module> = listOf(nostrCommonModule, nostrPlatformModule)
}
