package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.common.graphemeCount
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken

private val parser = TwitterParser(enableDomainDetection = true, enableNonAsciiInUrl = false, enableEscapeInUrl = true)
private val remoteMention = Regex("(?<![\\w@])@([a-zA-Z0-9_]+)@[a-zA-Z0-9.-]+(?::[0-9]+)?")

internal fun mastodonTextLength(
    content: String,
    spoilerText: String?,
    urlCharacters: Int,
): Long {
    var urlWeight = 0L
    val countable =
        buildString {
            append(spoilerText.orEmpty())
            val plain = StringBuilder()

            fun flushPlain() {
                append(remoteMention.replace(plain.toString()) { "@" + it.groupValues[1] })
                plain.clear()
            }
            parser.parse(content).forEach { token ->
                if (token is UrlToken && (token.value.startsWith("https://", true) || token.value.startsWith("http://", true))) {
                    flushPlain()
                    if (urlCharacters > 0) {
                        append('x')
                        urlWeight += urlCharacters.toLong() - 1
                    }
                } else {
                    plain.append(token.value)
                }
            }
            flushPlain()
        }
    return countable.graphemeCount().toLong() + urlWeight
}
