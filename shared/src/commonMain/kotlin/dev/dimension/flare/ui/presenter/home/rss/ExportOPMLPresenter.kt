package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.network.rss.model.Opml
import dev.dimension.flare.data.network.rss.model.OpmlBody
import dev.dimension.flare.data.network.rss.model.OpmlHead
import dev.dimension.flare.data.network.rss.model.OpmlOutline
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.first
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class ExportOPMLPresenter :
    PresenterBase<UiState<String>>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    @Composable
    override fun body(): UiState<String> {
        var state by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

        LaunchedEffect(Unit) {
            try {
                val sources = appDatabase.rssSourceDao().getAll().first()

                val outlines =
                    sources.map { source ->
                        OpmlOutline(
                            text = source.title ?: source.url,
                            title = source.title,
                            type = "rss",
                            xmlUrl = source.url,
                            htmlUrl = null,
                        )
                    }

                val opml =
                    Opml(
                        version = "2.0",
                        head =
                            OpmlHead(
                                title = "Flare Export",
                                dateCreated = Clock.System.now().toString(),
                            ),
                        body =
                            OpmlBody(
                                outlines = outlines,
                            ),
                    )

                val content =
                    XML {
                        indentString = "  "
                    }.encodeToString(Opml.serializer(), opml)
                state = UiState.Success(content)
            } catch (e: Exception) {
                state = UiState.Error(e)
            }
        }

        return state
    }
}
