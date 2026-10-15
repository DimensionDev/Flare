package dev.dimension.flare.data.network.rss

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import moe.tlaster.readability.Readability
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

internal class Readability(
    private val client: HttpClient = ktorClient(),
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun extract(url: String): DocumentData =
        withTimeoutOrNull(15_000) {
            val response = client.get(url) { expectSuccess = true }
            val html = response.bodyAsText()
            withContext(parserDispatcher) {
                val article =
                    Readability(
                        html,
                        response.call.request.url
                            .toString(),
                    ).parse()
                        ?: throw ArticleExtractionException()
                if (!article.content.hasRssContent()) throw ArticleExtractionException()
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
            }
        } ?: throw ArticleTimeoutException()

    suspend fun load(
        url: String,
        descriptionHtml: String? = null,
        title: String? = null,
        fallbackHtml: String? = null,
    ): DocumentData {
        if (descriptionHtml != null) {
            return withContext(parserDispatcher) { embeddedDocument(descriptionHtml, title) } ?: throw ArticleExtractionException()
        }
        return try {
            extract(url)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            withContext(parserDispatcher) { embeddedDocument(fallbackHtml, title) } ?: throw exception
        }
    }
}

internal class ArticleExtractionException : Exception("Unable to extract article content")

internal class ArticleTimeoutException : Exception("Article request timed out")

internal fun String?.hasRssContent(): Boolean {
    if (isNullOrBlank()) return false
    val body = Ksoup.parse(this).body()
    body.select("script, style, template").remove()
    return body.text().isNotBlank() || body.select("img[src], video[src], audio[src], source[src]").isNotEmpty()
}

private fun embeddedDocument(
    html: String?,
    title: String?,
): DocumentData? =
    html?.takeIf { it.hasRssContent() }?.let {
        DocumentData(
            title = title.orEmpty(),
            content = it,
            textContent = Ksoup.parse(it).apply { select("script, style, template").remove() }.text(),
            length = null,
            excerpt = null,
            byline = null,
            dir = null,
            siteName = null,
            lang = null,
            publishedTime = null,
        )
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
