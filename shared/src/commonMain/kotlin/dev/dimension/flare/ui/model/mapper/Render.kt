package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.render.toUi
import kotlin.time.Instant

// internal fun DbPagingTimelineWithStatus.render(
//    event: StatusEvent?,
//    useDbKey: Boolean = false,
// ): UiTimeline =
//    status.status.data.content
//        .render(
//            event,
//            references =
//                status.references
//                    .groupBy {
//                        it.reference.referenceType
//                    }.mapValues { (_, references) ->
//                        references.mapNotNull { it.status?.data?.content }
//                    },
//        ).let {
//            if (useDbKey) {
//                it.copy(dbKey = timeline.accountType.toString())
//            } else {
//                it
//            }
//        }
//
// internal fun DbStatusWithReference.render(event: StatusEvent?): UiTimeline =
//    status.data.content.render(
//        event,
//        references =
//            references
//                .groupBy { it.reference.referenceType }
//                .mapValues { (_, references) ->
//                    references.mapNotNull { it.status?.data?.content }
//                },
//    )
//
// internal fun StatusContent.render(
//    event: StatusEvent?,
//    references: Map<ReferenceType, List<StatusContent>> = emptyMap(),
// ) = when (this) {
//    is StatusContent.Mastodon ->
//        data.render(
//            event = event as StatusEvent.Mastodon,
//            references = references,
//            host = event.accountKey.host,
//        )
//
//    is StatusContent.MastodonNotification ->
//        data.render(
//            event = event as StatusEvent.Mastodon,
//            accountKey = event.accountKey,
//            references = references,
//        )
//
//    is StatusContent.Misskey ->
//        data.render(
//            event = event as StatusEvent.Misskey,
//            accountKey = event.accountKey,
//            references = references,
//            pinned = pinned,
//        )
//
//    is StatusContent.MisskeyNotification ->
//        data.render(
//            event = event as StatusEvent.Misskey,
//            accountKey = event.accountKey,
//            references = references,
//        )
//
//    is StatusContent.BlueskyReason ->
//        reason.render(
//            event = event as StatusEvent.Bluesky,
//            accountKey = event.accountKey,
//            references = references,
//        )
//
//    is StatusContent.Bluesky ->
//        data.render(
//            event = event as StatusEvent.Bluesky,
//            accountKey = event.accountKey,
//            references = references,
//        )
//
//    is StatusContent.BlueskyNotification ->
//        renderBlueskyNotification(
//            event = event as StatusEvent.Bluesky,
//            accountKey = event.accountKey,
//            references = references,
//        )
//
//    is StatusContent.XQT ->
//        data.render(
//            event = event as StatusEvent.XQT,
//            accountKey = event.accountKey,
//            references = references,
//        )
//
//    is StatusContent.VVO ->
//        data.render(
//            event = event as StatusEvent.VVO,
//            accountKey = event.accountKey,
//        )
//
//    is StatusContent.VVOComment ->
//        data.render(
//            event = event as StatusEvent.VVO,
//            accountKey = event.accountKey,
//        )
//
//    is StatusContent.Rss -> data.render()
//    is StatusContent.Test -> error("Test content cannot be rendered")
// }
//
// internal fun DbUser.render(accountKey: MicroBlogKey) =
//    when (content) {
//        is UserContent.Bluesky -> content.data.render(accountKey = accountKey)
//        is UserContent.BlueskyLite -> content.data.render(accountKey = accountKey)
//        is UserContent.Mastodon ->
//            content.data.render(
//                accountKey = accountKey,
//                host = accountKey.host,
//            )
//
//        is UserContent.Misskey -> content.data.render(accountKey = accountKey)
//        is UserContent.MisskeyLite -> content.data.render(accountKey = accountKey)
//        is UserContent.VVO -> content.data.render(accountKey = accountKey)
//        is UserContent.XQT -> content.data.render(accountKey = accountKey)
//        is UserContent.Test -> error("Test content cannot be rendered")
//    }

internal fun DbRssSources.render() =
    UiRssSource(
        url = url,
        title = title,
        lastUpdate = Instant.fromEpochMilliseconds(lastUpdate).toUi(),
        id = id,
        favIcon = icon,
        displayMode = displayMode,
        type = type,
    )
