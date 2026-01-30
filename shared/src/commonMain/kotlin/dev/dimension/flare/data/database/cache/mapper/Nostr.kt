package dev.dimension.flare.data.database.cache.mapper

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal fun List<Event>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
): List<DbPagingTimelineWithStatus> =
    this.map { event ->
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = event.createdAt * 1000L,
            status = event.toDbStatusWithUser(accountKey),
            references = emptyMap(),
        )
    }

internal fun Event.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = this.toDbUser(accountKey.host)
    val status =
        DbStatus(
            statusKey =
                MicroBlogKey(
                    id = this.id,
                    host = accountKey.host,
                ),
            userKey = user.userKey,
            content =
                StatusContent.Nostr(
                    json = Json.encodeToString(this),
                ),
            accountType = AccountType.Specific(accountKey),
            text = this.content,
            createdAt = Instant.fromEpochSeconds(this.createdAt),
        )
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

internal fun Event.toDbUser(host: String): DbUser =
    DbUser(
        userKey =
            MicroBlogKey(
                id = this.pubKey,
                host = host,
            ),
        platformType = PlatformType.Nostr,
        name = this.pubKey.take(8),
        handle = this.pubKey.take(16),
        content =
            UserContent.Nostr(
                json = "",
            ),
        host = host,
    )

internal fun MetadataEvent.toDbUser(host: String): DbUser {
    val metadata = this.contactMetaData()
    return DbUser(
        userKey =
            MicroBlogKey(
                id = this.pubKey,
                host = host,
            ),
        platformType = PlatformType.Nostr,
        name = metadata?.name ?: metadata?.displayName ?: this.pubKey.take(8),
        handle = metadata?.name ?: this.pubKey.take(16),
        content =
            UserContent.Nostr(
                json = Json.encodeToString(this as Event),
            ),
        host = host,
    )
}
