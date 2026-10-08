package dev.dimension.flare.data.datasource.mastodon

import de.cketti.codepoints.codePointCount
import dev.dimension.flare.common.graphemeCount
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken

private val parser = TwitterParser(enableDomainDetection = true, enableNonAsciiInUrl = false, enableEscapeInUrl = true)
private val remoteMention =
    Regex(
        """(?<![=/\p{L}\p{M}\p{Nl}\p{Nd}\p{Pc}])@([a-zA-Z0-9_]+(?:[.-]+[a-zA-Z0-9_]+)*)@""" +
            """([\p{L}\p{M}\p{Nl}\p{Nd}\p{Pc}]+(?:[.-]+[\p{L}\p{M}\p{Nl}\p{Nd}\p{Pc}]+)*)""",
    )

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
                val text = plain.toString()
                append(
                    remoteMention.replace(text) { match ->
                        val end = match.range.last + 1
                        if (match.groupValues[2].codePointCount() > 253 || text.startsWith("@", end) || text.startsWith("＠", end) ||
                            text.startsWith("://", end)
                        ) {
                            match.value
                        } else {
                            "@" + match.groupValues[1]
                        }
                    },
                )
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
