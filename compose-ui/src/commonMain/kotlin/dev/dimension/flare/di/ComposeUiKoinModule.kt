package dev.dimension.flare.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@Configuration
@ComponentScan("dev.dimension.flare.ui.component")
internal class ComposeUiKoinModule
