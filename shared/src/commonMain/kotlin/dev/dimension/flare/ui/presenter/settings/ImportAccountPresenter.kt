package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.util.GZipEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

public class ImportAccountPresenter(
    private val data: String,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : PresenterBase<ImportAccountPresenter.State>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    public interface State {
        public val importedAccount: UiState<ImmutableList<MicroBlogKey>>
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val dataFlow by lazy {
        flow {
            val byteArray = data.hexToByteArray()
            val channel = ByteReadChannel(byteArray)
            val gzip = GZipEncoder.decode(source = channel, coroutineContext = coroutineContext)
            val gzipByteArray = gzip.toByteArray()
            val accounts = ProtoBuf.decodeFromByteArray<List<DbAccount>>(gzipByteArray)
            appDatabase.accountDao().insert(accounts)
            emit(accounts.map { it.account_key }.toImmutableList())
        }
    }

    @Composable
    override fun body(): State {
        val importedAccount by dataFlow.collectAsUiState()
        return object : State {
            override val importedAccount = importedAccount
        }
    }
}
