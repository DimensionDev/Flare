package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DataSourceCapabilitySetTest {
    @Test
    fun infersCapabilitiesForLegacyDataSource() {
        val dataSource = LegacyDataSource()

        assertNotNull(dataSource.capabilities.timeline)
        assertNotNull(dataSource.capabilities.search)
        assertNotNull(dataSource.capabilities.profile)
        assertNotNull(dataSource.capabilities.compose)
        assertNull(dataSource.capabilities.post)
        assertEquals(dataSource.accountKey, dataSource.accountKeyOrNull)
    }

    @Test
    fun explicitProviderControlsVisibleCapabilities() {
        val dataSource = ExplicitDataSource()

        assertNotNull(dataSource.capabilities.timeline)
        assertNull(dataSource.capabilities.search)
        assertNull(dataSource.capabilities.profile)
        assertNull(dataSource.capabilities.compose)
        assertEquals(dataSource.accountKey, dataSource.accountKeyOrNull)
    }
}

private open class LegacyDataSource :
    EmptyDataSource(),
    UserDataSource,
    ComposeDataSource {
    override val accountKey: MicroBlogKey = MicroBlogKey("alice", "social.example")
    override val userHandler: UserHandler
        get() = error("Not used")

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) = error("Not used")

    override fun composeConfig(type: ComposeType): ComposeConfig = error("Not used")
}

private class ExplicitDataSource :
    LegacyDataSource(),
    DataSourceCapabilityProvider {
    override val capabilitySet: DataSourceCapabilitySet =
        DataSourceCapabilitySet(timeline = TimelineCapability(this))
    override val authenticatedAccountKey: MicroBlogKey = accountKey
}

private abstract class EmptyDataSource : MicroblogDataSource {
    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun searchUser(query: String): RemoteLoader<UiProfile> = error("Not used")

    override fun discoverUsers(): RemoteLoader<UiProfile> = error("Not used")

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = error("Not used")

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = error("Not used")

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Not used")

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Not used")

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
}
