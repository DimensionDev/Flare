package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.LinkOpenDefaultMethod
import dev.dimension.flare.data.datastore.model.methodFor
import dev.dimension.flare.data.datastore.model.remove
import dev.dimension.flare.data.datastore.model.upsert
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebIgnore
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class LinkOpenDefaultsActionsPresenter(
    originalUrl: String,
) : PresenterBase<LinkOpenDefaultsActionsPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()
    private val host = originalUrl.toLinkOpenDefaultHost()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        return object : State {
            override fun setBrowserDefault() {
                host?.let {
                    scope.setDefault(
                        settingsRepository = settingsRepository,
                        host = it,
                        method = LinkOpenDefaultMethod.Browser,
                    )
                }
            }

            override fun setAccountDefault(accountKey: MicroBlogKey) {
                host?.let {
                    scope.setDefault(
                        settingsRepository = settingsRepository,
                        host = it,
                        method = LinkOpenDefaultMethod.Account(accountKey),
                    )
                }
            }

            override fun clearDefault() {
                host?.let {
                    scope.clearDefault(
                        settingsRepository = settingsRepository,
                        host = it,
                    )
                }
            }
        }
    }

    @Immutable
    public interface State {
        @WebIgnore
        public fun setBrowserDefault()

        @WebIgnore
        public fun setAccountDefault(accountKey: MicroBlogKey)

        @WebIgnore
        public fun clearDefault()
    }
}

private fun String.toLinkOpenDefaultHost(): String? =
    runCatching {
        Url(this).host.takeIf { it.isNotBlank() }
    }.getOrNull()

public class LinkOpenDefaultsPresenter : PresenterBase<LinkOpenDefaultsPresenter.State>() {
    private val accountService: AccountService by koinInject()
    private val platformRegistry: PlatformRegistry by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()

    private val targetSourcesFlow by lazy {
        accountService
            .allAccountServicesFlow()
            .map { services ->
                services.mapNotNull { service ->
                    val spec = platformRegistry.get(service.platformType) ?: return@mapNotNull null
                    val hosts =
                        spec
                            .deepLinks(service.accountKey)
                            .mapNotNull { deepLink ->
                                Url(deepLink.uriPattern).host.takeIf { host ->
                                    host.isNotBlank() && !host.contains('{') && !host.contains('}')
                                }
                            }.distinct()
                    if (hosts.isEmpty()) {
                        return@mapNotNull null
                    }
                    val dataSource = service.dataSource
                    val profileFlow =
                        if (dataSource is UserDataSource && dataSource is AuthenticatedMicroblogDataSource) {
                            dataSource.userHandler
                                .userById(dataSource.accountKey.id)
                                .toUi()
                                .distinctUntilChangedBy { state ->
                                    state.takeSuccess()?.let { user ->
                                        buildString {
                                            append(user.key)
                                            append("-")
                                            append(user.name.raw)
                                            append("-")
                                            append(user.avatar)
                                            append("-")
                                            append(user.handle.raw)
                                        }
                                    }
                                }
                        } else {
                            flowOf(UiState.Error(IllegalStateException("Account service is not authenticated user data source")))
                        }
                    profileFlow.map { profile ->
                        hosts.map { host ->
                            TargetCandidate(
                                host = host,
                                platformName = spec.metadata.displayName,
                                account =
                                    Account(
                                        accountKey = service.accountKey,
                                        profile = profile,
                                    ),
                            )
                        }
                    }
                }
            }.combineLatestFlowLists()
            .map { candidateGroups ->
                candidateGroups
                    .flatten()
                    .groupBy { it.host }
                    .map { (host, candidates) ->
                        val first = candidates.first()
                        TargetSource(
                            id = host,
                            host = host,
                            title = host,
                            platformName = first.platformName,
                            accounts =
                                candidates
                                    .map { it.account }
                                    .distinctBy { it.accountKey }
                                    .toImmutableList(),
                        )
                    }.sortedWith(
                        compareBy(
                            { it.platformName },
                            { it.title },
                        ),
                    ).toImmutableList()
            }.distinctUntilChanged()
    }

    private val targetsFlow by lazy {
        combine(
            targetSourcesFlow,
            settingsRepository.appSettings,
        ) { targets, appSettings ->
            targets
                .map { target -> target.toTarget(appSettings) }
                .toImmutableList()
        }.distinctUntilChanged()
    }

    private data class TargetCandidate(
        val host: String,
        val platformName: String,
        val account: Account,
    )

    private data class TargetSource(
        val id: String,
        val host: String,
        val title: String,
        val platformName: String,
        val accounts: ImmutableList<Account>,
    ) {
        fun toTarget(appSettings: AppSettings): Target {
            val options =
                buildList {
                    add(Option.Ask)
                    add(Option.Browser)
                    accounts.forEach { account ->
                        add(Option.Account(account))
                    }
                }.toImmutableList()
            val selectedOption =
                when (val method = appSettings.linkOpenDefaults.methodFor(host)) {
                    LinkOpenDefaultMethod.Browser -> {
                        Option.Browser
                    }

                    is LinkOpenDefaultMethod.Account -> {
                        accounts
                            .firstOrNull { it.accountKey == method.accountKey }
                            ?.let { Option.Account(it) }
                            ?: Option.Ask
                    }

                    null -> {
                        Option.Ask
                    }
                }
            return Target(
                id = id,
                title = title,
                platformName = platformName,
                options = options,
                selectedOption = selectedOption,
            )
        }
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val targets by targetsFlow.collectAsUiState()
        return object : State {
            override val targets: UiState<ImmutableList<Target>> = targets

            override fun select(
                target: Target,
                option: Option,
            ) {
                when (option) {
                    Option.Ask -> {
                        scope.clearDefault(
                            settingsRepository = settingsRepository,
                            host = target.id,
                        )
                    }

                    Option.Browser -> {
                        scope.setDefault(
                            settingsRepository = settingsRepository,
                            host = target.id,
                            method = LinkOpenDefaultMethod.Browser,
                        )
                    }

                    is Option.Account -> {
                        scope.setDefault(
                            settingsRepository = settingsRepository,
                            host = target.id,
                            method = LinkOpenDefaultMethod.Account(option.account.accountKey),
                        )
                    }
                }
            }
        }
    }

    @Immutable
    public interface State {
        @WebIgnore
        public val targets: UiState<ImmutableList<Target>>

        @WebIgnore
        public fun select(
            target: Target,
            option: Option,
        )
    }

    @Immutable
    public data class Target(
        val id: String,
        val title: String,
        val platformName: String,
        val options: ImmutableList<Option>,
        val selectedOption: Option,
    )

    @Immutable
    public data class Account(
        val accountKey: MicroBlogKey,
        val profile: UiState<UiProfile>,
    )

    @Immutable
    public sealed interface Option {
        public val id: String
        public val isAsk: Boolean
        public val isBrowser: Boolean
        public val account: LinkOpenDefaultsPresenter.Account?

        public data object Ask : Option {
            override val id: String = "ask"
            override val isAsk: Boolean = true
            override val isBrowser: Boolean = false
            override val account: LinkOpenDefaultsPresenter.Account? = null
        }

        public data object Browser : Option {
            override val id: String = "browser"
            override val isAsk: Boolean = false
            override val isBrowser: Boolean = true
            override val account: LinkOpenDefaultsPresenter.Account? = null
        }

        public data class Account(
            override val account: LinkOpenDefaultsPresenter.Account,
        ) : Option {
            override val id: String = "account:${account.accountKey}"
            override val isAsk: Boolean = false
            override val isBrowser: Boolean = false
        }
    }
}

private fun CoroutineScope.setDefault(
    settingsRepository: SettingsRepository,
    host: String,
    method: LinkOpenDefaultMethod,
) {
    launch {
        withContext(Dispatchers.Main) {
            settingsRepository.updateAppSettings {
                copy(linkOpenDefaults = linkOpenDefaults.upsert(host, method))
            }
        }
    }
}

private fun CoroutineScope.clearDefault(
    settingsRepository: SettingsRepository,
    host: String,
) {
    launch {
        withContext(Dispatchers.Main) {
            settingsRepository.updateAppSettings {
                copy(linkOpenDefaults = linkOpenDefaults.remove(host))
            }
        }
    }
}
