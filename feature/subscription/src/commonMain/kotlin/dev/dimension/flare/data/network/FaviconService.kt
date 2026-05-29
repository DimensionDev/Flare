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
        val parsedUrl = Url(actualUrl)
        val document = Ksoup.parse(webContent)
        val icons =
            document
                .select(
                    """
                    link[rel~=(?i)(?:^|\s)(?:icon|apple-touch-icon(?:-precomposed)?|mask-icon)(?:\s|$)],
                    link[rel~=(?i)^(?=.*\bshortcut\b)(?=.*\bicon\b).*$]
                    """.trimIndent(),
                )
        val iconLink =
            icons.maxByOrNull {
                it
                    .attribute("sizes")
                    ?.value
                    ?.split('x')
                    ?.firstOrNull()
                    ?.toIntOrNull() ?: 0
            }
        if (iconLink == null) {
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

        val iconHref = iconLink.attr("href").ifBlank { return null }
        return if (iconHref.startsWith("http")) {
            iconHref
        } else if (iconHref.startsWith("/")) {
            if (iconHref.startsWith("//")) {
                "https:$iconHref"
            } else {
                "https://${parsedUrl.host}$iconHref"
            }
        } else {
            "https://${parsedUrl.host}/$iconHref"
        }
    }
}
