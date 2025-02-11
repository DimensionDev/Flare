package dev.dimension.flare.data.network.rss

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.toUi
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class Readability(
    private val scraper: NativeWebScraper,
) {
    suspend fun parse(url: String): DocumentData =
        suspendCoroutine { continuation ->
            try {
                scraper.parse(
                    url = url,
                    scriptToInject = ReadabilityJS,
                    callback = {
                        try {
                            println(it)
                            continuation.resume(it.decodeJson<DocumentData>())
                        } catch (e: Throwable) {
                            continuation.resumeWithException(e)
                        }
                    },
                )
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
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
        publishedTime?.let { Instant.parse(it).toUi() }
    }
}
