package dev.dimension.flare.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication
internal class AndroidKoinApplication

@Module
@Configuration
@ComponentScan("dev.dimension.flare.common", "dev.dimension.flare.di")
internal class AndroidKoinModule
