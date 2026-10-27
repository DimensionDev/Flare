package dev.dimension.flare.ui.presenter

import dev.dimension.flare.data.datasource.microblog.ArticleCapability
import dev.dimension.flare.data.datasource.microblog.ComposeCapability
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilityProvider
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilitySet
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.accountProfileFlow
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.uiArticleContentOf
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.presenter.article.articleFlow
import dev.dimension.flare.ui.presenter.compose.observeComposeUsers
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityPresentationTest {
    private val key = MicroBlogKey("me", "plugin.example")
    private val profile = createSampleUser().copy(key = key, nameInternal = "Login profile".toUiPlainText())

    @Test
    fun articleFlowBindsCapabilityProviderMediaToItsAccount() =
        runTest {
            val article = articleFlow(flowOf(CapabilityOnlySource(key, profile)), key).first()
            assertEquals(ProxyRef.Account(key), article.cover?.proxy)
            assertEquals(ProxyRef.Account(key), (article.author as UiArticleAuthor.Profile).profile.avatar?.proxy)
            val guest = articleFlow(flowOf(CapabilityOnlySource(null, profile)), key).first()
            assertEquals(ProxyRef.Global, guest.cover?.proxy)
        }

    @Test
    fun accountIdentityIsAvailableWithoutTheProfileCapability() =
        runTest {
            val source = CapabilityOnlySource(key, profile)
            assertNull(source.capabilitySet.profile)
            val identity = source.accountProfileFlow()!!.first().takeSuccess()!!
            assertEquals(key, identity.key)
            assertEquals("Login profile", identity.name.raw)
            assertEquals(ProxyRef.Account(key), identity.avatar?.proxy)
            assertNull(CapabilityOnlySource(null, profile).accountProfileFlow())
            assertNull(CapabilityOnlySource(MicroBlogKey("other", key.host), profile).accountProfileFlow())
        }

    @Test
    fun composeOnlyAccountsUseTheirLoginProfile() =
        runTest {
            val users = observeComposeUsers(flowOf(listOf(CapabilityOnlySource(key, profile)))).first()
            assertEquals(profile.withProxy(ProxyRef.Account(key)), users[key]?.takeSuccess())
            assertTrue(observeComposeUsers(flowOf(listOf(CapabilityOnlySource(null, profile)))).first().isEmpty())
        }
}

private class CapabilityOnlySource(
    override val authenticatedAccountKey: MicroBlogKey?,
    private val loginProfile: UiProfile,
) : MicroblogDataSource,
    DataSourceCapabilityProvider {
    override val authenticatedProfile = loginProfile

    override val capabilitySet: DataSourceCapabilitySet by lazy {
        DataSourceCapabilitySet(
            article =
                ArticleCapability(
                    object : ArticleDataSource {
                        override suspend fun article(articleKey: MicroBlogKey) =
                            UiArticle(
                                key = articleKey.id,
                                title = "Article",
                                content = uiArticleContentOf(emptyList()),
                                cover = UiMedia.Image("https://cdn.example/cover.png"),
                                author = UiArticleAuthor.Profile(loginProfile),
                            )

                        override fun articleComments(articleKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = error("Unused")
                    },
                ),
            compose =
                ComposeCapability(
                    object : ComposeDataSource, MicroblogDataSource by this {
                        override val accountKey = loginProfile.key

                        override fun composeConfig(type: ComposeType) = ComposeConfig()

                        override suspend fun publish(
                            data: ComposeData,
                            progress: () -> Unit,
                        ) = Unit
                    },
                ),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = error("Unused")

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = error("Unused")

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = error("Unused")

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = error("Unused")

    override fun searchUser(query: String): RemoteLoader<UiProfile> = error("Unused")

    override fun discoverUsers(): RemoteLoader<UiProfile> = error("Unused")

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = error("Unused")

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = error("Unused")

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Unused")

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Unused")

    override suspend fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
}
