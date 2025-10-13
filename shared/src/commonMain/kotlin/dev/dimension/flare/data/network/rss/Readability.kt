package dev.dimension.flare.data.network.rss

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal class Readability(
    private val scraper: NativeWebScraper,
) {
    fun parse(url: String): Flow<Result<DocumentData>> =
        callbackFlow {
            try {
                scraper.parse(
                    url = url,
                    scriptToInject = ReadabilityJS,
                    callback = {
                        try {
                            trySend(Result.success(it.decodeJson()))
                        } catch (e: Throwable) {
                            trySend(Result.failure(e))
                        }
                    },
                )
            } catch (e: Throwable) {
                trySend(Result.failure(e))
            }
            awaitClose { }
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
    val richText: UiRichText by lazy {
        parseHtml(content).toUi()
    }
}
