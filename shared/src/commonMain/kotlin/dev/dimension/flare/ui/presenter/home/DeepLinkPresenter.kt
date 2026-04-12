package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.data.database.cache.model.TranslationDisplayMode
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.spec
import dev.dimension.flare.ui.model.DeeplinkEvent
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.route.APPSCHEMA
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.URLProtocol
import io.ktor.http.buildUrl
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DeepLinkPresenter(
    private val onRoute: (DeeplinkRoute) -> Unit,
    private val onLink: (String) -> Unit,
) : PresenterBase<DeepLinkPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val preTranslationService: PreTranslationService by inject()

    @androidx.compose.runtime.Immutable
    public interface State {
        public fun handle(url: String)
    }

    private val patternFlow by lazy {
        accountRepository.allAccounts.map {
            it
                .associateWith {
                    it.platformType.spec.deepLinkPatterns(it.accountKey.host)
                }.toImmutableMap()
        }
    }

    @Composable
    override fun body(): State {
        var pendingUrl by remember { mutableStateOf<String?>(null) }
        pendingUrl?.let { url ->
            LaunchedEffect(url) {
                if (DeeplinkEvent.isDeeplinkEvent(url)) {
                    val event = DeeplinkEvent.parse(url)
                    if (event != null) {
                        val translationEvent = event.translationEvent
                        val statusMutation = event.statusMutation
                        when {
                            statusMutation != null ->
                                accountServiceFlow(
                                    accountType = AccountType.Specific(event.accountKey),
                                    repository = accountRepository,
                                ).firstOrNull()?.let { service ->
                                    if (service is PostDataSource) {
                                        service.postEventHandler.handleMutation(statusMutation)
                                    }
                                }

                            translationEvent is DeeplinkEvent.TranslationEvent.RetryTranslation ->
                                with(translationEvent) {
                                    preTranslationService.setStatusDisplayMode(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                        mode = TranslationDisplayMode.Translated,
                                    )
                                    preTranslationService.retryStatus(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                    )
                                }

                            translationEvent is DeeplinkEvent.TranslationEvent.Translate ->
                                with(translationEvent) {
                                    preTranslationService.setStatusDisplayMode(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                        mode = TranslationDisplayMode.Translated,
                                    )
                                    preTranslationService.retryStatus(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                    )
                                }

                            translationEvent is DeeplinkEvent.TranslationEvent.ShowOriginal ->
                                preTranslationService.setStatusDisplayMode(
                                    accountType = AccountType.Specific(event.accountKey),
                                    statusKey = translationEvent.statusKey,
                                    mode = TranslationDisplayMode.Original,
                                )
                        }
                    }
                    pendingUrl = null
                } else if (DeeplinkRoute.isDeeplink(url)) {
                    DeeplinkRoute.parse(url)?.let {
                        when (it) {
                            is DeeplinkRoute.OpenLinkDirectly ->
                                withContext(Dispatchers.Main) {
                                    onLink(it.url)
                                }

                            else ->
                                withContext(Dispatchers.Main) {
                                    onRoute(it)
                                }
                        }
                    }
                    pendingUrl = null
                } else {
                    patternFlow.collect { pattern ->
                        val matches = DeepLinkMapping.matches(url, pattern)
                        if (matches.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                onLink.invoke(url)
                            }
                        } else {
                            val route =
                                DeeplinkRoute.DeepLinkAccountPicker(
                                    originalUrl =
                                        buildUrl {
                                            protocol = URLProtocol(APPSCHEMA, 0)
                                            host = "OpenLinkDirectly"
                                            parameters.append("url", url)
                                        }.toString(),
                                    data =
                                        matches
                                            .map {
                                                it.key.accountKey to it.value.deepLink(it.key.accountKey)
                                            }.toMap()
                                            .toImmutableMap(),
                                )
                            withContext(Dispatchers.Main) {
                                onRoute.invoke(route)
                            }
                        }
                        pendingUrl = null
                    }
                }
            }
        }

        return object : State {
            override fun handle(url: String) {
                pendingUrl = url
            }
        }
    }
}
