package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.model.withProxy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class DataSourceCapabilitySet(
    public val timeline: TimelineCapability? = null,
    public val search: SearchCapability? = null,
    public val profile: ProfileCapability? = null,
    public val post: PostCapability? = null,
    public val relation: RelationDataSource? = null,
    public val compose: ComposeDataSource? = null,
    public val notification: NotificationCapability? = null,
    public val list: ListDataSource? = null,
    public val directMessage: DirectMessageDataSource? = null,
    public val article: ArticleDataSource? = null,
    public val gallery: GalleryDataSource? = null,
    public val tabCatalog: TabCatalogCapability? = null,
)

@HiddenFromObjC
public interface DataSourceCapabilityProvider {
    public val capabilitySet: DataSourceCapabilitySet

    public val authenticatedAccountKey: MicroBlogKey?
        get() = null

    public val authenticatedProfile: UiProfile?
        get() = null
}

public val MicroblogDataSource.capabilities: DataSourceCapabilitySet
    get() = (this as? DataSourceCapabilityProvider)?.capabilitySet ?: legacyCapabilitySet()

public val MicroblogDataSource.accountKeyOrNull: MicroBlogKey?
    get() =
        (this as? DataSourceCapabilityProvider)?.authenticatedAccountKey
            ?: (this as? AuthenticatedMicroblogDataSource)?.accountKey

// An account identity does not imply support for arbitrary profile lookups.
@HiddenFromObjC
public fun MicroblogDataSource.accountProfileFlow(): Flow<UiState<UiProfile>>? {
    val accountKey = accountKeyOrNull ?: return null
    capabilities.profile?.let { return it.userHandler.userById(accountKey.id).toUi() }
    val profile =
        (this as? DataSourceCapabilityProvider)?.authenticatedProfile?.takeIf {
            it.key.id == accountKey.id && it.key.host.equals(accountKey.host, ignoreCase = true)
        } ?: return null
    return flowOf(UiState.Success(profile.copy(key = accountKey).withProxy(ProxyRef.Account(accountKey))))
}

@HiddenFromObjC
public class TimelineCapability(
    private val delegate: MicroblogDataSource,
) {
    public fun homeTimeline(): RemoteLoader<UiTimelineV2> = delegate.homeTimeline()
}

@HiddenFromObjC
public class SearchCapability(
    private val delegate: MicroblogDataSource,
) {
    public fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = delegate.searchStatus(query)

    public fun searchUser(query: String): RemoteLoader<UiProfile> = delegate.searchUser(query)

    public fun discoverUsers(): RemoteLoader<UiProfile> = delegate.discoverUsers()

    public fun discoverStatuses(): RemoteLoader<UiTimelineV2> = delegate.discoverStatuses()

    public fun discoverHashtags(): RemoteLoader<UiHashtag> = delegate.discoverHashtags()
}

@HiddenFromObjC
public class ProfileCapability(
    private val delegate: MicroblogDataSource,
    userDataSource: UserDataSource,
) : UserDataSource by userDataSource {
    public fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean = false,
    ): RemoteLoader<UiTimelineV2> = delegate.userTimeline(userKey, mediaOnly)

    public fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = delegate.following(userKey)

    public fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = delegate.fans(userKey)

    public suspend fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = delegate.profileTabs(userKey)
}

@HiddenFromObjC
public class PostCapability(
    private val delegate: MicroblogDataSource,
    postDataSource: PostDataSource,
) : PostDataSource by postDataSource {
    public fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = delegate.context(statusKey)
}

@HiddenFromObjC
public data class NotificationCapability(
    public val timeline: NotificationTimelineDataSource?,
    public val events: NotificationDataSource?,
)

@HiddenFromObjC
public data class TabCatalogCapability(
    public val configuration: TimelineTabConfigurationDataSource?,
    public val pinnable: PinnableTimelineTabDataSource?,
)

private fun MicroblogDataSource.legacyCapabilitySet(): DataSourceCapabilitySet {
    val notificationTimeline = this as? NotificationTimelineDataSource
    val notificationEvents = this as? NotificationDataSource
    val tabConfiguration = this as? TimelineTabConfigurationDataSource
    val pinnableTabs = this as? PinnableTimelineTabDataSource
    return DataSourceCapabilitySet(
        timeline = TimelineCapability(this),
        search = SearchCapability(this),
        profile = (this as? UserDataSource)?.let { ProfileCapability(this, it) },
        post = (this as? PostDataSource)?.let { PostCapability(this, it) },
        relation = this as? RelationDataSource,
        compose = this as? ComposeDataSource,
        notification =
            if (notificationTimeline != null || notificationEvents != null) {
                NotificationCapability(notificationTimeline, notificationEvents)
            } else {
                null
            },
        list = this as? ListDataSource,
        directMessage = this as? DirectMessageDataSource,
        article = this as? ArticleDataSource,
        gallery = this as? GalleryDataSource,
        tabCatalog =
            if (tabConfiguration != null || pinnableTabs != null) {
                TabCatalogCapability(tabConfiguration, pinnableTabs)
            } else {
                null
            },
    )
}
