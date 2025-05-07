package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

public class InstanceMetadataPresenter(
    private val host: String,
    private val platformType: PlatformType,
) : PresenterBase<InstanceMetadataPresenter.State>() {
    private val presenterScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiInstanceMetadataFlow = MutableStateFlow<UiState<UiInstanceMetadata>>(UiState.Loading())

    @NativeCoroutinesState
    public val uiInstanceMetadataFlow: StateFlow<UiState<UiInstanceMetadata>> = _uiInstanceMetadataFlow.asStateFlow()

    public interface State {
        public val data: UiState<UiInstanceMetadata>
    }

    @Composable
    override fun body(): State {
        val currentData by uiInstanceMetadataFlow.collectAsState()
        return object : State {
            override val data: UiState<UiInstanceMetadata> = currentData
        }
    }

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        presenterScope.launch {
            _uiInstanceMetadataFlow.value = UiState.Loading()
            try {
                val result =
                    dev.dimension.flare.data.repository.tryRun {
                        when (platformType) {
                            PlatformType.Mastodon ->
                                MastodonInstanceService("https://$host/").instance().render()
                            PlatformType.Misskey ->
                                MisskeyService("https://$host/api/", null).meta(MetaRequest()).render()
                            PlatformType.Bluesky -> throw UnsupportedOperationException(
                                "Bluesky is not supported yet",
                            )
                            PlatformType.xQt -> throw UnsupportedOperationException(
                                "xQt is not supported yet",
                            )
                            PlatformType.VVo -> throw UnsupportedOperationException(
                                "VVo is not supported yet",
                            )
                        }
                    }

                result.fold(
                    onSuccess = { metadata ->
                        _uiInstanceMetadataFlow.value = UiState.Success(metadata)
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiInstanceMetadataFlow.value = UiState.Error(error)
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    println("InstanceMetadataPresenter: Metadata loading cancelled for host $host.")
                } else {
                    _uiInstanceMetadataFlow.value = UiState.Error(e)
                }
            }
        }
    }

    public fun cancelScope() {
        presenterScope.cancel()
        println("InstanceMetadataPresenter: Scope cancelled for host $host.")
    }
}
