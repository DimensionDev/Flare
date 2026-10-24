package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.proxy.ProxySettingsStateHolder
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the proxy settings screen.
 *
 * The value lives in [AppSettings] and is read by the HTTP engine on the next connect, so saving
 * here applies immediately without restarting the app.
 *
 * Text fields on the screen are backed by a `TextFieldState` instead of the persisted value: the
 * IME must own the cursor while typing, otherwise every keystroke round-trips through the
 * datastore and the cursor jumps back to the beginning.
 */
public class ProxySettingsPresenter : PresenterBase<ProxySettingsPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        // `null` until the datastore has produced the persisted settings: the screen uses this to
        // seed its text fields exactly once, instead of overriding what the user just typed.
        val appSettings by settingsRepository.appSettings.collectAsState(initial = null)
        var testResult by remember { mutableStateOf<TestResult?>(null) }
        var testing by remember { mutableStateOf(false) }

        return object : State {
            override val proxy: AppSettings.ProxyConfig = (appSettings ?: AppSettings.default).proxy
            override val proxyLoaded: Boolean = appSettings != null
            override val testResult: TestResult? = testResult
            override val isTesting: Boolean = testing

            override fun update(proxy: AppSettings.ProxyConfig) {
                testResult = null
                scope.launch {
                    persistProxy { proxy }
                }
            }

            override fun updateHostPort(
                host: String,
                port: Int,
            ) {
                scope.launch {
                    persistProxy { current -> current.copy(host = host, port = port) }
                }
            }

            override fun test(
                host: String?,
                port: Int?,
            ) {
                testing = true
                testResult = null
                scope.launch {
                    if (host != null || port != null) {
                        // The verify button must test what is on screen, not the last debounced
                        // value, so persist the text input before running the probe.
                        persistProxy { current ->
                            current.copy(
                                host = host ?: current.host,
                                port = port ?: current.port,
                            )
                        }
                    }
                    val result = runTest()
                    testing = false
                    testResult = result
                }
            }

            private suspend fun persistProxy(block: (AppSettings.ProxyConfig) -> AppSettings.ProxyConfig) {
                var updated: AppSettings.ProxyConfig? = null
                settingsRepository.updateAppSettings {
                    val next = block(proxy)
                    updated = next
                    copy(proxy = next)
                }
                // Keep the proxy selector in sync synchronously: the coordinator would otherwise
                // only see the change on the next datastore emission, which races with the probe.
                updated?.let { ProxySettingsStateHolder.update(it) }
            }
        }
    }

    private suspend fun runTest(): TestResult {
        val proxy = ProxySettingsStateHolder.current
        if (proxy.type != AppSettings.ProxyConfig.Type.Http) {
            // Nothing is being routed through a proxy, so there is no proxy to verify. Reporting
            // this as a failure would blame the user's network for a setting they did not enable.
            return TestResult.ProxyNotUsed
        }
        return withContext(PlatformDispatchers.IO) {
            var status: Int? = null
            val failure =
                runCatching {
                    status = testClient().get(TEST_URL).status.value
                }.exceptionOrNull()

            val code = status
            when {
                code != null && code in 200..299 -> TestResult.Connected(code)
                code != null -> TestResult.HttpError(code)
                else -> TestResult.Failed(failure?.message ?: failure?.let { it::class.simpleName } ?: "unknown error")
            }
        }
    }

    public interface State {
        public val proxy: AppSettings.ProxyConfig

        /** False until the persisted settings have been read; used to seed the text fields once. */
        public val proxyLoaded: Boolean
        public val testResult: TestResult?
        public val isTesting: Boolean

        public fun update(proxy: AppSettings.ProxyConfig)

        /** Persists the text fields without resetting them, used for debounced edits. */
        public fun updateHostPort(
            host: String,
            port: Int,
        )

        /**
         * Runs the proxy probe. When [host]/[port] are given they are persisted first, so the probe
         * reflects the current text input.
         */
        public fun test(
            host: String? = null,
            port: Int? = null,
        )
    }

    public sealed interface TestResult {
        /**
         * The request went through the configured proxy and reached the probe endpoint.
         */
        public data class Connected(
            val status: Int,
        ) : TestResult

        public data class HttpError(
            val status: Int,
        ) : TestResult

        /**
         * No custom proxy is configured, so there is nothing to verify.
         */
        public data object ProxyNotUsed : TestResult

        public data class Failed(
            val message: String,
        ) : TestResult
    }

    internal companion object {
        /**
         * The probe intentionally targets an endpoint that is not reachable directly from some
         * networks. That is what makes the test meaningful: a success proves traffic really left
         * through the proxy, which a generally reachable endpoint could not show.
         *
         * It is HTTPS on purpose. A cleartext endpoint is refused by the platform's network
         * security policy before any request is made, which would make the test useless.
         */
        internal const val TEST_URL: String = "https://www.google.com/generate_204"
    }
}

/**
 * Reuses the app's own client so the test exercises the very engine the feature changes, including
 * its proxy selector, instead of a client that might silently pick a different engine.
 */
internal fun testClient(): HttpClient =
    ktorClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 15_000
        }
    }
