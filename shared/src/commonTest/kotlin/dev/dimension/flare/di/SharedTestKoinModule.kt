package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.InMemoryFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.unavailableAccountService
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin as startKoinWithModules

@KoinApplication(configurations = ["test"])
internal class SharedTestKoinApplication

internal fun startKoin(appDeclaration: KoinAppDeclaration? = null) = startKoinWithModules<SharedTestKoinApplication>(appDeclaration)

@Module
@Configuration("test")
internal class SharedTestKoinModule {
    @Single
    fun platformRuntimeData(): PlatformRuntimeData = testPlatformRuntimeData()

    @Single
    fun fileStorage(): FileStorage = InMemoryFileStorage()

    @Single
    fun timelineResolver(): TimelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())

    @Single
    fun inAppNotification(): InAppNotification = TestInAppNotification
}

private object TestInAppNotification : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) = Unit

    override fun onSuccess(message: Message) = Unit

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) = Unit
}
