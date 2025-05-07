package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flow

public class InstanceMetadataPresenter(
    private val host: String,
    private val platformType: PlatformType = PlatformType.Mastodon,
) : PresenterBase<InstanceMetadataPresenter.State>() {
    public interface State {
        public val data: UiState<UiInstanceMetadata>
    }

    @Composable
    override fun body(): State {
        val data by remember(host, platformType) {
            flow {
                tryRun {
                    emit(UiState.Loading())
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
