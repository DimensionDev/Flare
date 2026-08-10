package dev.dimension.flare.di

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
internal class BlueskyTestKoinModule {
    @Single
    fun platformFormatter(): PlatformFormatter = TestFormatter()
}
