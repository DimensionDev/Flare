package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.rss.model.Feed
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import io.ktor.util.encodeBase64
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Instant

internal fun StatusContent.Rss.RssContent.render(): UiTimeline =
    when (this) {
        is StatusContent.Rss.RssContent.Atom -> this.render()
        is StatusContent.Rss.RssContent.Rss20 -> this.render()
        is StatusContent.Rss.RssContent.RDF -> this.render()
    }

internal val Feed.title: String
    get() =
        when (this) {
            is Feed.Atom -> this.title.value
            is Feed.Rss20 -> this.channel.title
            is Feed.RDF -> this.channel.title
        }

internal fun StatusContent.Rss.RssContent.Atom.render(): UiTimeline =
    with(data) {
        val descHtml =
            content?.value?.let {
                Ksoup.parse(it)
            }
        val img = descHtml?.select("img")?.firstOrNull()?.attr("src") ?: media?.thumbnail?.url
        return UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = title.value,
                    description = descHtml?.text(),
                    url = links.first().href,
                    image = img,
                    source = this@render.source,
                    createdAt =
                        published
                            ?.let { input -> parseRssDateToInstant(input) }
                            ?.toUi(),
                ),
        )
    }

internal fun StatusContent.Rss.RssContent.Rss20.render(): UiTimeline =
    with(data) {
        val descHtml =
            description?.let {
                Ksoup.parse(it)
            }
        val img = descHtml?.select("img")?.firstOrNull()
        return UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = title,
                    description = descHtml?.text(),
                    url = link,
                    image = img?.attr("src"),
                    source = this@render.source,
                    createdAt =
                        pubDate
                            ?.let { input -> parseRssDateToInstant(input) }
                            ?.toUi(),
                ),
        )
    }

internal fun StatusContent.Rss.RssContent.RDF.render(): UiTimeline =
    with(data) {
        val descHtml =
            description.let {
                Ksoup.parse(it)
            }
        val img = descHtml.select("img").firstOrNull()
        return UiTimeline(
            topMessage = null,
            content =
                UiTimeline.ItemContent.Feed(
                    title = title,
                    description = descHtml.text(),
                    url = link,
                    image = img?.attr("src"),
                    source = source,
                    createdAt =
                        date
                            ?.let { input -> parseRssDateToInstant(input) }
                            ?.toUi(),
                ),
        )
    }

internal fun MicroBlogKey.Companion.fromRss(url: String) =
    MicroBlogKey(
        id = url.encodeBase64(),
        host = "RSS",
    )

private fun parseRssDateToInstant(input: String): Instant? =
    tryRun {
        Instant.parse(input)
    }.getOrNull() ?: tryRun {
        parseRfc2822LikeToInstant(input)
    }.getOrNull()

private fun parseRfc2822LikeToInstant(input: String): Instant {
    var text = input.trim()
    val commaIdx = text.indexOf(',')
    if (commaIdx in 1..5) {
        text = text.substring(commaIdx + 1).trimStart()
    }
    val parts =
        text
            .split(' ', '\t')
            .filter { it.isNotEmpty() }

    require(parts.size >= 5) { "Invalid datetime: $input" }

    val dayStr = parts[0]
    val monStr = parts[1]
    val yearStr = parts[2]
    val timeStr = parts[3]
    val zoneStr = parts[4]

    val day = dayStr.toIntOrNull() ?: error("Invalid day: $dayStr")
    val year = yearStr.toIntOrNull() ?: error("Invalid year: $yearStr")
    val month = parseMonthAbbr(monStr) ?: error("Unknown month: $monStr")

    val (hour, minute, seconds) = parseTimeHms(timeStr)
    val offset = parseZoneOffset(zoneStr)

    val ldt = LocalDateTime(year, month, day, hour, minute, seconds)
    return ldt.toInstant(offset)
}

private fun parseMonthAbbr(m: String): Month? =
    when (m.uppercase()) {
        "JAN" -> Month.JANUARY
        "FEB" -> Month.FEBRUARY
        "MAR" -> Month.MARCH
        "APR" -> Month.APRIL
        "MAY" -> Month.MAY
        "JUN" -> Month.JUNE
        "JUL" -> Month.JULY
        "AUG" -> Month.AUGUST
        "SEP", "SEPT" -> Month.SEPTEMBER
        "OCT" -> Month.OCTOBER
        "NOV" -> Month.NOVEMBER
        "DEC" -> Month.DECEMBER
        else -> null
    }

private fun parseTimeHms(t: String): Triple<Int, Int, Int> {
    val seg = t.split(':')
    require(seg.size == 2 || seg.size == 3) { "Invalid time: $t" }
    val h = seg[0].toIntOrNull() ?: error("Invalid hour: ${seg[0]}")
    val m = seg[1].toIntOrNull() ?: error("Invalid minute: ${seg[1]}")
    val s = if (seg.size == 3) seg[2].toIntOrNull() ?: error("Invalid second: ${seg[2]}") else 0
    require(h in 0..23 && m in 0..59 && s in 0..59) { "Out-of-range time: $t" }
    return Triple(h, m, s)
}

private fun parseZoneOffset(z: String): UtcOffset {
    require(z.isNotEmpty() && (z[0] == '+' || z[0] == '-')) { "Invalid zone: $z" }
    val sign = if (z[0] == '-') -1 else 1
    val body = z.substring(1)
    val (h, m) =
        if (body.contains(':')) {
            val (hs, ms) = body.split(':').also { require(it.size == 2) { "Invalid zone: $z" } }
            (hs.toIntOrNull() ?: error("Invalid zone hour: $hs")) to (
                ms.toIntOrNull()
                    ?: error("Invalid zone minute: $ms")
            )
        } else {
            require(body.length in 2..4) { "Invalid zone: $z" }
            val padded = body.padStart(4, '0')
            val hs = padded.substring(0, 2)
            val ms = padded.substring(2, 4)
            (hs.toIntOrNull() ?: error("Invalid zone hour: $hs")) to (
                ms.toIntOrNull()
                    ?: error("Invalid zone minute: $ms")
            )
        }
    require(h in 0..18 && m in 0..59) { "Out-of-range zone: $z" }
    return UtcOffset(hours = sign * h, minutes = sign * m)
}
