package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.util.GZipEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

public class ExportAccountPresenter(
    private val accountKeys: ImmutableList<MicroBlogKey>,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : PresenterBase<ExportAccountPresenter.State>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    public interface State {
        public val data: UiState<String>
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val dataFlow by lazy {
        appDatabase.accountDao().get(accountKeys).map { accounts ->
            val protoBuf = ProtoBuf.encodeToByteArray(accounts)
            val channel = ByteReadChannel(protoBuf)
            val gzip = GZipEncoder.encode(source = channel, coroutineContext = coroutineContext)
            gzip.toByteArray().toHexString()
        }
    }

    @Composable
    override fun body(): State {
        val data by dataFlow.collectAsUiState()
        return object : State {
            override val data = data
        }
    }
}
