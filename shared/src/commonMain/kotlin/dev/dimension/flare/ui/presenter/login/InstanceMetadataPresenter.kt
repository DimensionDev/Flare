package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.spec
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flow

public class InstanceMetadataPresenter(
    private val host: String,
    private val platformType: PlatformType = PlatformType.Mastodon,
) : PresenterBase<InstanceMetadataPresenter.State>() {
    @Immutable
    public interface State {
        public val data: UiState<UiInstanceMetadata>
    }

    @Composable
    override fun body(): State {
        val data by remember(host, platformType) {
            flow {
                tryRun {
                    emit(UiState.Loading())
                    platformType.spec.instanceMetadata(host)
                }.fold(
                    onSuccess = {
                        emit(UiState.Success<UiInstanceMetadata>(it))
                    },
                    onFailure = {
                        emit(UiState.Error<UiInstanceMetadata>(it))
                    },
                )
            }
        }.collectAsState(UiState.Loading())
        return object : State {
            override val data = data
        }
    }
}
