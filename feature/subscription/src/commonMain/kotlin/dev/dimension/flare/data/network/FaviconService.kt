package dev.dimension.flare.data.network

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
    val icons = websiteIcons(pageUrl, webContent)
    val standard = icons.filter { "icon" in it.rel.split(' ') }
    return (standard.ifEmpty { icons })
        .maxByOrNull { icon ->
            icon.sizes.split(Regex("\\s+")).maxOfOrNull { it.substringBefore('x').toIntOrNull() ?: 0 } ?: 0
        }?.url
}
