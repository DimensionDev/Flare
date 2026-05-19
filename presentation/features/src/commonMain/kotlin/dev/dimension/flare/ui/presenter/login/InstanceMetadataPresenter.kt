package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.tryRun
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class InstanceMetadataPresenter(
    private val host: String,
    private val platformType: PlatformType = PlatformType.Mastodon,
) : PresenterBase<InstanceMetadataPresenter.State>(),
    KoinComponent {
    private val platformRegistry: SocialPlatformRegistry by inject()

    @Immutable
    public interface State {
        public val data: UiState<UiInstanceMetadata>
    }

    @Composable
    override fun body(): State {
        val data by remember(
            host,
            platformType,
            platformRegistry,
        ) {
            flow {
                tryRun {
                    emit(UiState.Loading())
                    platformRegistry.instanceMetadata(platformType, host)
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
