package dev.dimension.flare.data.database.cache.model

import androidx.room.DatabaseView
import androidx.room.Embedded
import dev.dimension.flare.data.database.cache.CACHE_DATABASE_VERSION

const val PAGING_TIMELINE_VIEW = "PagingTimelineView$CACHE_DATABASE_VERSION"

@DatabaseView(
    viewName = PAGING_TIMELINE_VIEW,
    value = """
SELECT
timeline.*,
status.content AS status_content,
user.*,
retweetStatus.content AS retweet_status_content,
quoteStatus.content AS quote_status_content,
replyStatus.content AS reply_status_content,
notificationStatus.content AS notification_status_content
FROM DbPagingTimeline AS timeline
JOIN DbStatus status ON timeline.statusKey = status.statusKey AND timeline.accountKey = status.accountKey
LEFT JOIN DbUser user ON status.userKey = user.userKey
LEFT JOIN status_reference retweet ON status.statusKey = retweet.statusKey AND retweet.referenceType = 'Retweet'
LEFT JOIN DbStatus retweetStatus ON retweet.referenceStatusKey = retweetStatus.statusKey
LEFT JOIN status_reference reply ON status.statusKey = reply.statusKey AND reply.referenceType = 'Reply'
LEFT JOIN DbStatus replyStatus ON reply.referenceStatusKey = replyStatus.statusKey
LEFT JOIN status_reference quote ON status.statusKey = quote.statusKey AND quote.referenceType = 'Quote'
LEFT JOIN DbStatus quoteStatus ON quote.referenceStatusKey = quoteStatus.statusKey
LEFT JOIN status_reference notification ON status.statusKey = notification.statusKey AND notification.referenceType = 'Notification'
LEFT JOIN DbStatus notificationStatus ON notification.referenceStatusKey = notificationStatus.statusKey
GROUP BY timeline._id
""",
)
data class DbPagingTimelineView(
    @Embedded
    val timeline: DbPagingTimeline,
    val status_content: StatusContent,
    @Embedded
    val user: DbUser?,
    val retweet_status_content: StatusContent?,
    val quote_status_content: StatusContent?,
    val reply_status_content: StatusContent?,
    val notification_status_content: StatusContent?,
)
