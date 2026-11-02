package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.model.ProxySettingsException
import dev.dimension.flare.data.network.createPlatformHttpClientEngine
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareHead
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

public class ProxyEditorPresenter : PresenterBase<ProxyEditorState>() {
    private val repository by koinInject<ProxyRepository>()

    @Composable
    override fun body(): ProxyEditorState {
        val scope = rememberCoroutineScope()
        var saveResult by remember { mutableStateOf<UiState<Unit>?>(null) }
        var testResult by remember { mutableStateOf<UiState<ProxyProfile>?>(null) }
        return object : ProxyEditorState {
            override val saveState = saveResult
            override val testState = testResult

            override fun save(profile: ProxyProfile) {
                if (saveResult is UiState.Loading || testResult is UiState.Loading) return
                saveResult = UiState.Loading()
                testResult = null
                scope.launch {
                    try {
                        repository.save(profile)
                        saveResult = UiState.Success(Unit)
                    } catch (cancelled: CancellationException) {
                        saveResult = null
                        throw cancelled
                    } catch (exception: Exception) {
                        if (exception !is ProxySettingsException) exception.printStackTrace()
                        saveResult = UiState.Error(exception)
                    }
                }
            }

            override fun test(profile: ProxyProfile) {
                if (saveResult is UiState.Loading || testResult is UiState.Loading) return
                saveResult = null
                testResult = UiState.Loading()
                scope.launch {
                    try {
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
                            testResult = UiState.Success(profile)
                        } finally {
                            client.close()
                            engine.close()
                        }
                    } catch (cancelled: CancellationException) {
                        testResult = null
                        throw cancelled
                    } catch (exception: Exception) {
                        if (exception !is ProxySettingsException) exception.printStackTrace()
                        testResult = UiState.Error(exception)
                    }
                }
            }
        }
    }
}

@Immutable
public interface ProxyEditorState {
    public val saveState: UiState<Unit>?
    public val testState: UiState<ProxyProfile>?
    public val error: ProxySettingsError?
        get() =
            ((saveState as? UiState.Error)?.throwable ?: (testState as? UiState.Error)?.throwable)
                ?.let { (it as? ProxySettingsException)?.reason ?: ProxySettingsError.REQUEST_FAILED }

    public fun isTested(profile: ProxyProfile): Boolean =
        testState?.takeSuccess()?.let {
            it.id == profile.id && it.protocol == profile.protocol && it.host == profile.host && it.port == profile.port
        } == true

    public fun save(profile: ProxyProfile)

    public fun test(profile: ProxyProfile)
}
