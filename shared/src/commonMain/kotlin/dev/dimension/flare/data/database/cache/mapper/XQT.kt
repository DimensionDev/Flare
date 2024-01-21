package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineCursor
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost

object XQT

internal fun User.toDbUser() =
    DbUser(
        user_key =
            MicroBlogKey(
                id = restId,
                host = xqtHost,
            ),
        platform_type = PlatformType.xQt,
        name = legacy.name,
        handle = legacy.screenName,
        host = xqtHost,
        content = UserContent.XQT(this),
    )

data class XQTTimeline(
    val tweets: TimelineTweet,
    val sortedIndex: Long,
)

internal fun List<InstructionUnion>.tweets(): List<XQTTimeline> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries ->
                union.propertyEntries.flatMap { entry ->
                    when (entry.content) {
                        is TimelineTimelineCursor -> emptyList()
                        is TimelineTimelineItem -> listOf(entry.content.itemContent to entry.sortIndex.toLong())
                        is TimelineTimelineModule ->
                            entry.content.items?.mapIndexed { index, it ->
                                it.item.itemContent to (entry.sortIndex.toLong() - index)
                            }.orEmpty()
                    }
                }.mapNotNull { pair ->
                    pair.first.let {
                        when (it) {
                            is TimelineTweet -> {
                                XQTTimeline(
                                    tweets = it,
                                    sortedIndex = pair.second,
                                )
                            }
                            else -> null
                        }
                    }
                }
            else -> emptyList()
        }
    }

internal fun List<InstructionUnion>.cursor() =
    flatMap {
        when (it) {
            is TimelineAddEntries ->
                it.propertyEntries.mapNotNull {
                    when (it.content) {
                        is TimelineTimelineCursor ->
                            if (it.content.cursorType == CursorType.bottom) {
                                it.content.value
                            } else {
                                null
                            }
                        else -> null
                    }
                }
            else -> emptyList()
        }
    }.firstOrNull()
