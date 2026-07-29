package dev.dimension.flare.data.network

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.repository.tryRun
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url

internal object FaviconService {
    private val client = ktorClient { }

    suspend fun fetchIcon(url: String): String? {
        val actualUrl =
            if (url.startsWith("http", ignoreCase = true)) {
                url
            } else {
                "https://$url"
            }
        val webContent =
            tryRun {
                client.get(actualUrl).bodyAsText()
            }.getOrNull() ?: return null
        findFaviconUrl(actualUrl, webContent)?.let {
            return it
        }
        val parsedUrl = Url(actualUrl)
        val favIcon = "https://${parsedUrl.host}/favicon.ico"
        val hasFavIcon =
            tryRun {
                val response = client.get(favIcon)
                if (response.status.value !in 200..299) {
                    throw Exception("Failed to fetch favicon: ${response.status}")
                }
            }
        return if (hasFavIcon.isSuccess) {
            favIcon
        } else {
            null
        }
    }
}

internal fun findFaviconUrl(
    pageUrl: String,
    webContent: String,
): String? {
    val parsedUrl = Url(pageUrl)
    val document = Ksoup.parse(webContent)
    val iconLink =
        document
            .select(
                """
                link[rel~=(?i)(?:^|\s)icon(?:\s|$)],
                link[rel~=(?i)^(?=.*\bshortcut\b)(?=.*\bicon\b).*$]
                """.trimIndent(),
            ).largestIcon()
            ?: document
                .select(
                    """
                    link[rel~=(?i)(?:^|\s)(?:apple-touch-icon(?:-precomposed)?|mask-icon)(?:\s|$)]
                    """.trimIndent(),
                ).largestIcon()
            ?: return null
    val iconHref = iconLink.attr("href").ifBlank { return null }
    return when {
        iconHref.startsWith("http", ignoreCase = true) -> iconHref
        iconHref.startsWith("//") -> "https:$iconHref"
        iconHref.startsWith("/") -> "https://${parsedUrl.host}$iconHref"
        else -> "https://${parsedUrl.host}/$iconHref"
    }
}

private fun com.fleeksoft.ksoup.select.Elements.largestIcon() =
    maxByOrNull {
        it
            .attribute("sizes")
            ?.value
            ?.split('x')
            ?.firstOrNull()
            ?.toIntOrNull() ?: 0
    }
