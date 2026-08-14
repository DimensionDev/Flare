package dev.dimension.flare.feature.plugin

import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.login.PluginOAuthCallbackCoordinatorV1
import dev.dimension.flare.model.PlatformSpecSource
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
internal class PluginKoinModule {
    @Single
    fun subsystem(
        appFiles: AppFileStore,
        pendingRepository: PlatformOAuthPendingRepository,
    ): PluginSubsystemV1 = PluginSubsystemV1(appFiles, pendingRepository)

    @Single(binds = [PlatformSpecSource::class])
    fun platformSpecSource(
        subsystem: PluginSubsystemV1,
        scope: CoroutineScope,
    ): PlatformSpecSource = subsystem.platformSource(scope)

    @Single
    fun oauthCallbackCoordinator(
        subsystem: PluginSubsystemV1,
        accountService: AccountService,
    ): PluginOAuthCallbackCoordinatorV1 = PluginOAuthCallbackCoordinatorV1(subsystem.oauth, accountService)
}
