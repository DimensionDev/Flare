package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.network.rss.model.Opml
import dev.dimension.flare.data.network.rss.model.OpmlOutline
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class ImportOPMLPresenter(
    private val opmlContent: String,
    private val fetchIcon: suspend (String) -> String? = { RssService.fetchIcon(it) },
) : PresenterBase<ImportOPMLPresenter.State>(),
    KoinComponent {
    private val appDatabase: AppDatabase by inject()

    @Immutable
    public interface State {
        public val importing: Boolean
        public val progress: Float
        public val importedCount: Int
        public val totalCount: Int
        public val error: String?
    }

    @Composable
    override fun body(): State {
        var importing by remember { mutableStateOf(true) }
        var importedCount by remember { mutableStateOf(0) }
        var totalCount by remember { mutableStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val opml = XML.decodeFromString<Opml>(opmlContent)
                val outlines =
                    opml.body.outlines
                        .flatMap { flattenOutlines(it) }
                        .filter { it.type == "rss" && !it.xmlUrl.isNullOrBlank() }

                totalCount = outlines.size

                val sources =
                    outlines
                        .map { outline ->
                            async {
                                val url = outline.xmlUrl ?: return@async null
                                val icon =
                                    try {
                                        fetchIcon(url)
                                    } catch (e: Exception) {
                                        null
                                    }

                                DbRssSources(
                                    url = url,
                                    title = outline.title ?: outline.text,
                                    icon = icon,
                                    lastUpdate = Clock.System.now().toEpochMilliseconds(),
                                )
                            }
                        }.awaitAll()
                        .filterNotNull()

                appDatabase.rssSourceDao().insertAll(sources)
                importedCount = sources.size
            } catch (e: Exception) {
                error = e.message
            } finally {
                importing = false
            }
        }

        return object : State {
            override val importing = importing
            override val progress = if (totalCount > 0) importedCount.toFloat() / totalCount else 0f
            override val importedCount = importedCount
            override val totalCount = totalCount
            override val error = error
        }
    }

    private fun flattenOutlines(outline: OpmlOutline): List<OpmlOutline> {
        val result = mutableListOf<OpmlOutline>()
        result.add(outline)
        outline.outlines.forEach { result.addAll(flattenOutlines(it)) }
        return result
    }
}
