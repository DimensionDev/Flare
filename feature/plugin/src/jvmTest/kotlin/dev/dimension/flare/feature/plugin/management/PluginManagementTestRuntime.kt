package dev.dimension.flare.feature.plugin.management

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginClient
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

internal fun testPluginManagementPresenter(
    subsystem: PluginSubsystemV1,
    scope: CoroutineScope,
    github: GitHubPluginClient = subsystem.github,
    operationDispatcher: CoroutineDispatcher = Dispatchers.Default,
): PluginManagementPresenterV1 {
    val registry = testPluginRegistry(subsystem, scope)
    return PluginManagementPresenterV1(subsystem, scope, operationDispatcher, github = github, registry = {
        registry
    }, accounts = { PluginTestAccounts() })
}

internal fun testPluginRegistry(
    subsystem: PluginSubsystemV1,
    scope: CoroutineScope,
): PlatformRegistry = PlatformRegistry(PlatformRuntimeData(listOf(ManagementGuest), emptyList(), listOf(subsystem.platformSource(scope))))

internal class PluginTestAccounts : AccountService {
    val accounts = mutableMapOf<MicroBlogKey, Pair<UiAccount, String>>()
    var deletionFailure: Exception? = null

    override suspend fun deleteAccounts(
        platformId: String,
        ownsCredential: (String) -> Boolean,
    ) {
        deletionFailure?.let { throw it }
        accounts.entries.removeAll { it.value.first.platformId == platformId && ownsCredential(it.value.second) }
    }

    override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> = error("unused")

    override fun allAccountServicesFlow(): Flow<List<AccountMicroblogDataSource>> = error("unused")

    override suspend fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ) {
        accounts[account.accountKey] = account to PluginJsonV1.encodeToString(serializer, credential)
    }

    override fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> = error("unused")

    override fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ) = error("unused")
}

private object ManagementGuest : PlatformSpec {
    override val platformId = "BuiltIn"
    override val metadata = PlatformMetadata(platformId, UiIcon.World.asType())
    override val isDefaultGuest = true
    override val timelineSpecs = persistentListOf<TimelineSpec<out TimelineSpec.Data>>()

    override fun deepLinks(accountKey: MicroBlogKey) = persistentListOf<PlatformDeepLink<*>>()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("unused")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = error("unused")
}
