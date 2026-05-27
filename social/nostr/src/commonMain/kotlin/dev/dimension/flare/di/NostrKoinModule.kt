package dev.dimension.flare.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@Configuration
@ComponentScan("dev.dimension.flare.data.datasource.nostr", "dev.dimension.flare.data.network.nostr")
internal class NostrKoinModule
