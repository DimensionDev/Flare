package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.network.createPlatformHttpClientEngine
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.invoke
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareHead
import io.ktor.http.isSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

public class ProxySettingsPresenter : PresenterBase<ProxySettingsState>() {
    private val repository by koinInject<ProxyRepository>()
    private val accountRepository by koinInject<AccountRepository>()

    @Composable
    override fun body(): ProxySettingsState {
        val settings by repository.settings.collectAsState(null)
        val accounts by accountRepository.allAccounts.collectAsState(persistentListOf())
        val accountState = remember { AccountsPresenter() }.invoke()
        val accountProfiles =
            accountState.accounts
                .takeSuccess()
                .orEmpty()
                .mapNotNull { item ->
                    item.profile.takeSuccess()?.let { item.account.accountKey to it }
                }.toMap()
                .toImmutableMap()
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var editorErrorMessage by remember { mutableStateOf<String?>(null) }
        var tested by remember { mutableStateOf<ProxyProfile?>(null) }

        fun action(
            fromEditor: Boolean = false,
            block: suspend () -> Unit,
        ) {
            if (busy) return
            busy = true
            error = null
            editorErrorMessage = null
            tested = null
            scope.launch {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    val message = exception.message ?: "Proxy request failed"
                    if (fromEditor) editorErrorMessage = message else error = message
                } finally {
                    busy = false
                }
            }
        }
        return object : ProxySettingsState {
            override val settings = settings
            override val accounts = accounts
            override val accountProfiles = accountProfiles
            override val busy = busy
            override val error = error
            override val editorError = editorErrorMessage
            override val tested = tested

            override fun select(
                accountKey: MicroBlogKey?,
                policy: ProxyPolicy,
            ) = action {
                if (accountKey == null) repository.selectGlobal(policy) else repository.selectAccount(accountKey, policy)
            }

            override fun save(
                profile: ProxyProfile,
                onSaved: () -> Unit,
            ) = action(fromEditor = true) {
                repository.save(profile)
                onSaved()
            }

            override fun remove(id: String) = action { repository.remove(id) }

            override fun clearEditorError() {
                editorErrorMessage = null
            }

            override fun test(
                profile: ProxyProfile,
                fromEditor: Boolean,
            ) = action(fromEditor) {
                val engine = createPlatformHttpClientEngine(profile.connection())
                val client =
                    HttpClient(engine) {
                        followRedirects = false
                        install(HttpTimeout) {
                            requestTimeoutMillis = 15_000
                            connectTimeoutMillis = 15_000
                            socketTimeoutMillis = 15_000
                        }
                    }
                try {
                    client.prepareHead("https://example.com/").execute { response ->
                        check(response.status.isSuccess()) { "Proxy test failed: HTTP ${response.status.value}" }
                    }
                    tested = profile
                } finally {
                    client.close()
                    engine.close()
                }
            }
        }
    }
}

@Immutable
public interface ProxySettingsState {
    public val settings: ProxySettings?
    public val accounts: ImmutableList<UiAccount>
    public val accountProfiles: ImmutableMap<MicroBlogKey, UiProfile>
    public val busy: Boolean
    public val error: String?
    public val editorError: String?
    public val tested: ProxyProfile?

    public fun isTested(profile: ProxyProfile): Boolean =
        tested?.let {
            it.id == profile.id && it.protocol == profile.protocol && it.host == profile.host && it.port == profile.port
        } == true

    public fun select(
        accountKey: MicroBlogKey?,
        policy: ProxyPolicy,
    )

    public fun save(
        profile: ProxyProfile,
        onSaved: () -> Unit,
    )

    public fun remove(id: String)

    public fun clearEditorError()

    public fun test(
        profile: ProxyProfile,
        fromEditor: Boolean = false,
    )
}
