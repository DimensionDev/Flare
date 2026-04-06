package dev.dimension.flare.data.network.rss

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.readability.Readability
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

internal class Readability {
    private val client = ktorClient()

    fun parse(url: String): Flow<Result<DocumentData>> =
        flow {
            runCatching {
                val response = client.get(url).bodyAsText()
                val article = Readability(response, url).parse() ?: throw Exception("Failed to parse the article")
                DocumentData(
                    title = article.title,
                    content = article.content,
                    textContent = article.textContent,
                    length = article.length,
                    excerpt = article.excerpt,
                    byline = article.byline,
                    dir = article.dir,
                    siteName = article.siteName,
                    lang = article.lang,
                    publishedTime = article.publishedTime,
                )
            }.fold(
                onSuccess = {
                    emit(Result.success(it))
                },
                onFailure = {
                    emit(Result.failure(it))
                },
            )
        }
}

@Immutable
@Serializable
public data class DocumentData(
    val title: String,
    val content: String,
    val textContent: String,
    val length: Int?,
    val excerpt: String?,
    val byline: String?,
    val dir: String?,
    val siteName: String?,
    val lang: String?,
    val publishedTime: String?,
) {
    val publishDateTime: UiDateTime? by lazy {
        publishedTime?.let {
            tryRun {
                Instant.parse(it)
            }.getOrNull()?.toUi()
        }
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    val element: Element by lazy {
        parseHtml(content)
    }
}
