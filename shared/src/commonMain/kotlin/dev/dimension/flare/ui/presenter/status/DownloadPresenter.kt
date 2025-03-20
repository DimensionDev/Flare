package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

public class DownloadPresenter : PresenterBase<DownloadPresenter.State>() {
    public interface State {
        public suspend fun download(uri: String): Result<ByteArray>
    }

    @Composable
    override fun body(): State =
        object : State {
            override suspend fun download(uri: String): Result<ByteArray> =
                runCatching {
                    ktorClient().get(uri).bodyAsBytes()
                }
        }
}
