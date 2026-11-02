package dev.dimension.flare.data.network

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
public data class WebsiteIcon(
    val url: String,
    val rel: String,
    val type: String,
    val sizes: String,
    val media: String,
)

/** Resolves relative links against the page URL and its first HTML base element. */
public fun websiteIcons(
    pageUrl: String,
    html: String,
): List<WebsiteIcon> =
    Ksoup
        .parse(html, pageUrl)
        .head()
        .select("link[href]")
        .mapNotNull { link ->
            val rel = link.attr("rel").lowercase().split(Regex("\\s+"))
            if (rel.none { it in setOf("icon", "apple-touch-icon", "apple-touch-icon-precomposed", "mask-icon") }) {
                return@mapNotNull null
            }
            val url = link.absUrl("href")
            if (url.isBlank() || url.length > 4096 ||
                runCatching { Url(url).protocol in setOf(URLProtocol.HTTP, URLProtocol.HTTPS) }.getOrDefault(false).not()
            ) {
                return@mapNotNull null
            }
            WebsiteIcon(
                url,
                rel.joinToString(" ").take(128),
                link.attr("type").take(128),
                link.attr("sizes").take(128),
                link.attr("media").take(128),
            )
        }.distinctBy(WebsiteIcon::url)
        .take(32)
