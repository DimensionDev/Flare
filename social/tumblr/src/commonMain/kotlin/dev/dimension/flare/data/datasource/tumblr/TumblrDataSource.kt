package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.tumblr.TumblrCreatePostRequest
import dev.dimension.flare.data.network.tumblr.TumblrNpfBlock
import dev.dimension.flare.data.network.tumblr.TumblrNpfMedia
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.data.network.tumblr.toTumblrComposeMediaFile
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.TumblrCredential
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val TUMBLR_TEXT_BLOCK_MAX_LENGTH = 4096
private const val TUMBLR_MAX_MEDIA_COUNT = 10

internal class TumblrDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<TumblrCredential>,
    private val updateCredential: suspend (TumblrCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    ComposeDataSource,
    PostDataSource,
    RelationDataSource,
    TimelineTabConfigurationDataSource,
    UserDataSource,
    PostEventHandler.Handler {
    private val service =
        TumblrService(
            credentialFlow = credentialFlow,
            onCredentialRefreshed = updateCredential,
        )

    private val loader by lazy {
        TumblrLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    override val postHandler: PostHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val postEventHandler: PostEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override val relationHandler: RelationHandler by lazy {
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val userHandler: UserHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            tumblrHomeCandidate(
                title = UiText.Raw("Tumblr"),
            ).withFullWidthPost(),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            tumblrHomeCandidate().withFullWidthPost(),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Home,
                icon = UiIcon.Home,
                target =
                    ShortcutSpec.Target.Timeline(
                        tumblrHomeCandidate().withFullWidthPost(),
                    ),
            ),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        TumblrHomeTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        TumblrBlogTimelineLoader(
            service = service,
            accountKey = accountKey,
            blogKey = userKey,
            mediaOnly = mediaOnly,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        TumblrStatusDetailLoader(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        TumblrTaggedTimelineLoader(
            service = service,
            accountKey = accountKey,
            tag = query.removePrefix("#"),
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        TumblrBlogProfileLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        TumblrFollowingLoader(
            service = service,
            accountKey = accountKey,
            blogKey = userKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        TumblrFollowersLoader(
            service = service,
            accountKey = accountKey,
            blogKey = userKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                loader = userTimeline(userKey, mediaOnly = false),
            ),
            ProfileTab(
                name = UiStrings.Media,
                displayType = ProfileTab.DisplayType.Gallery,
                loader = userTimeline(userKey, mediaOnly = true),
            ),
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        require(data.poll == null) { "Tumblr poll compose is not supported" }
        val credential = credentialFlow.first()
        val referenceStatus = data.referenceStatus?.composeStatus
        require(referenceStatus !is ComposeStatus.Reply) {
            "Tumblr replies are not supported by the public API"
        }
        if (referenceStatus is ComposeStatus.Quote) {
            require(data.medias.isEmpty()) { "Tumblr reblog compose media is not supported" }
            val comment = data.content.trim()
            require(comment.isNotEmpty()) { "Tumblr reblog comment is empty" }
            reblogWithComment(
                credential = credential,
                statusKey = referenceStatus.statusKey,
                comment = comment,
                state = data.visibility.toTumblrState(),
            )
            return
        }
        val media =
            data.medias
                .take(TUMBLR_MAX_MEDIA_COUNT)
                .mapIndexed { index, media ->
                    val npfMedia =
                        TumblrNpfMedia(
                            identifier = "media$index",
                            type = media.file.mimeType,
                        )
                    val file = media.file.toTumblrComposeMediaFile()
                    progress()
                    Triple(npfMedia, file, media.altText)
                }
        val content =
            buildList {
                addAll(
                    data.content
                        .chunked(TUMBLR_TEXT_BLOCK_MAX_LENGTH)
                        .filter { it.isNotBlank() }
                        .map { text ->
                            TumblrNpfBlock(
                                type = "text",
                                text = text,
                            )
                        },
                )
                media.forEach { (npfMedia, _, altText) ->
                    val blockType = npfMedia.type.toNpfBlockType()
                    add(
                        TumblrNpfBlock(
                            type = blockType,
                            media = listOf(npfMedia),
                            altText = altText?.take(TUMBLR_TEXT_BLOCK_MAX_LENGTH),
                        ),
                    )
                }
            }
        require(content.isNotEmpty()) { "Tumblr post content is empty" }
        service.createPost(
            blogIdentifier = credential.blogIdentifier,
            request =
                TumblrCreatePostRequest(
                    content = content,
                    state = data.visibility.toTumblrState(),
                ),
            media = media.map { (npfMedia, file, _) -> npfMedia to file },
        )
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(TUMBLR_TEXT_BLOCK_MAX_LENGTH * 10),
            media =
                ComposeConfig
                    .Media(
                        maxCount = TUMBLR_MAX_MEDIA_COUNT,
                        canSensitive = false,
                        altTextMaxLength = TUMBLR_TEXT_BLOCK_MAX_LENGTH,
                        allowMediaOnly = true,
                    ).takeUnless { type == ComposeType.Quote || type == ComposeType.Reply },
            visibility =
                ComposeConfig.Visibility(
                    allVisibilities =
                        persistentListOf(
                            UiTimelineV2.Post.Visibility.Public,
                            UiTimelineV2.Post.Visibility.Private,
                        ),
                    defaultVisibility = UiTimelineV2.Post.Visibility.Public,
                ),
        )

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Tumblr)
        when (event) {
            is PostEvent.Tumblr.Like -> handleLike(event)
            is PostEvent.Tumblr.Repost -> handleRepost(event)
        }
    }

    private suspend fun handleLike(event: PostEvent.Tumblr.Like) {
        val parts = event.postKey.toTumblrPostKeyParts()
        val post = service.post(parts.blogName, parts.postId) ?: error("Tumblr post not found: ${event.postKey}")
        val key = post.reblogKey ?: error("Tumblr reblog key is missing")
        if (event.liked) {
            service.unlike(parts.postId, key)
        } else {
            service.like(parts.postId, key)
        }
    }

    private suspend fun handleRepost(event: PostEvent.Tumblr.Repost) {
        if (event.reposted) {
            return
        }
        val parts = event.postKey.toTumblrPostKeyParts()
        val post = service.post(parts.blogName, parts.postId) ?: error("Tumblr post not found: ${event.postKey}")
        val key = post.reblogKey ?: error("Tumblr reblog key is missing")
        val credential = credentialFlow.first()
        service.reblog(
            blogIdentifier = credential.blogIdentifier,
            postId = parts.postId,
            reblogKey = key,
        )
    }

    private suspend fun reblogWithComment(
        credential: TumblrCredential,
        statusKey: MicroBlogKey,
        comment: String,
        state: String,
    ) {
        val parts = statusKey.toTumblrPostKeyParts()
        val post = service.post(parts.blogName, parts.postId) ?: error("Tumblr post not found: $statusKey")
        val key = post.reblogKey ?: error("Tumblr reblog key is missing")
        service.reblog(
            blogIdentifier = credential.blogIdentifier,
            postId = parts.postId,
            reblogKey = key,
            comment = comment,
            state = state,
        )
    }
}

private fun TumblrDataSource.tumblrHomeCandidate(title: UiText = UiStrings.Home.asText()): TimelineCandidate<*> =
    CommonTimelineSpecs.home.candidate(
        data = TimelineSpec.AccountBasedData(accountKey),
        icon = IconType.Material(UiIcon.Tumblr),
        title = title,
    )

private fun TimelineCandidate<*>.withFullWidthPost(): TimelineCandidate<*> =
    copy(
        appearancePatch =
            (appearancePatch ?: AppearancePatch.EMPTY)
                .set(AppearanceKeys.FullWidthPost, true),
    )

private fun String?.toNpfBlockType(): String =
    when {
        this?.startsWith("video/") == true -> "video"
        this?.startsWith("audio/") == true -> "audio"
        else -> "image"
    }

internal fun UiTimelineV2.Post.Visibility.toTumblrState(): String =
    when (this) {
        UiTimelineV2.Post.Visibility.Public -> "published"
        UiTimelineV2.Post.Visibility.Private -> "private"
        else -> error("Tumblr does not support $this post visibility")
    }
