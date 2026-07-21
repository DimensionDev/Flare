package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequestVariables
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequestVariables
import dev.dimension.flare.data.network.xqt.model.LiveVideoStreamStatusResponse
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestFeatures
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesMedia
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesMediaMediaEntitiesInner
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequestVariablesReply
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostMediaMetadataCreateRequest
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.TweetUnion
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.data.platform.toTimelineCandidate
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.renderArticle
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

private const val BULK_SIZE: Long = 512 * 1024L // 512 Kib
private const val MAX_ASYNC_UPLOAD_SIZE = 10

@OptIn(ExperimentalPagingApi::class)
internal class XQTDataSource(
    override val accountKey: MicroBlogKey,
    sourceCredentialFlow: Flow<XQTCredential>,
) : AuthenticatedMicroblogDataSource,
    NotificationTimelineDataSource,
    ComposeDataSource,
    NotificationDataSource,
    UserDataSource,
    PostDataSource,
    ArticleDataSource,
    ListDataSource,
    PinnableTimelineTabDataSource,
    TimelineTabConfigurationDataSource,
    DirectMessageDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val coroutineScope: CoroutineScope by koinInject()
    private val imageCompressor: ImageCompressor by koinInject()
    private val credentialFlow by lazy {
        sourceCredentialFlow.distinctUntilChanged()
    }
    private val service by lazy {
        XQTService(
            accountKey = accountKey,
            chocolateFlow =
                credentialFlow
                    .map { it.chocolate },
        )
    }
    private val loader by lazy {
        XQTLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    private val listLoader = XQTListLoader(service, accountKey)

    private val listMemberLoader = XQTListMemberLoader(service, accountKey)

    private val directMessageLoader by lazy {
        XQTDirectMessageLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
        )
    }

    override val directMessageHandler by lazy {
        DirectMessageHandler(
            accountKey = accountKey,
            loader = directMessageLoader,
            coroutineScope = coroutineScope,
        )
    }

    internal suspend fun getTweetResultByRestId(tweetId: String): TweetUnion? =
        service
            .getTweetResultByRestId(
                variables =
                    TweetDetailWithRestIdRequest(
                        tweetID = tweetId,
                        withCommunity = true,
                        includePromotedContent = true,
                        withVoice = true,
                        withBirdwatchNotes = true,
                    ).encodeJson(),
                features =
                    PostCreateTweetRequestFeatures(
                        responsiveWebTwitterArticleTweetConsumptionEnabled = true,
                    ).encodeJson(),
            ).body()
            ?.data
            ?.tweetResult
            ?.result

    override suspend fun article(articleKey: MicroBlogKey): UiArticle =
        getTweetResultByRestId(articleKey.id)
            ?.renderArticle(accountKey = accountKey)
            ?: error("Twitter article not found")

    override fun articleComments(articleKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        ArticleCommentsRemoteMediator(
            statusKey = articleKey,
            service = service,
            accountKey = accountKey,
        )

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType>
        get() = loader.supportedTypes

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.XQT)
        when (event) {
            is PostEvent.XQT.Retweet -> {
                if (event.retweeted) {
                    service.postDeleteRetweet(
                        postDeleteRetweetRequest =
                            PostDeleteRetweetRequest(
                                variables = PostDeleteRetweetRequestVariables(sourceTweetId = event.postKey.id),
                            ),
                    )
                } else {
                    service.postCreateRetweet(
                        postCreateRetweetRequest =
                            PostCreateRetweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                }
            }

            is PostEvent.XQT.Like -> {
                if (event.liked) {
                    service.postUnfavoriteTweet(
                        postUnfavoriteTweetRequest =
                            PostUnfavoriteTweetRequest(
                                variables = PostCreateRetweetRequestVariables(tweetId = event.postKey.id),
                            ),
                    )
                } else {
                    service.postFavoriteTweet(
                        postFavoriteTweetRequest =
                            PostFavoriteTweetRequest(
                                variables =
                                    PostCreateRetweetRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                }
            }

            is PostEvent.XQT.Bookmark -> {
                if (event.bookmarked) {
                    service.postDeleteBookmark(
                        postDeleteBookmarkRequest =
                            DeleteBookmarkRequest(
                                variables =
                                    DeleteBookmarkRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                } else {
                    service.postCreateBookmark(
                        postCreateBookmarkRequest =
                            CreateBookmarkRequest(
                                variables =
                                    CreateBookmarkRequestVariables(
                                        tweetId = event.postKey.id,
                                    ),
                            ),
                    )
                }
            }
        }
    }

    override val listHandler: ListHandler<UiList.List> =
        ListHandler(
            pagingKey = "list_$accountKey",
            accountKey = accountKey,
            loader = listLoader,
        )

    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> by lazy {
        listOf(
            PinnableTimelineTabSection(
                title = UiStrings.List,
                data =
                    listHandler.data.map { paging ->
                        paging.map { it.toTimelineCandidate(accountKey) }
                    },
            ),
        )
    }

    override val defaultTabs by lazy {
        persistentListOf(
            CommonTimelineSpecs.home
                .candidate(
                    data = TimelineSpec.AccountBasedData(accountKey),
                    icon = IconType.FavIcon(accountKey.host),
                    title = UiText.Raw("X"),
                ),
            XqtPlatformSpec.featuredTimelineSpec
                .candidate(
                    data = TimelineSpec.AccountBasedData(accountKey),
                    icon = IconType.FavIcon(accountKey.host),
                ),
        )
    }

    override val builtInTimelineTabs by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ),
            XqtPlatformSpec.featuredTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ),
            XqtPlatformSpec.bookmarkTimelineSpec.candidate(TimelineSpec.AccountBasedData(accountKey)),
            XqtPlatformSpec.deviceFollowTimelineSpec.candidate(TimelineSpec.AccountBasedData(accountKey)),
        )
    }

    override val shortcuts by lazy {
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Featured,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        XqtPlatformSpec.featuredTimelineSpec.candidate(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Bookmark,
                icon = UiIcon.Bookmark,
                target =
                    ShortcutSpec.Target.Timeline(
                        XqtPlatformSpec.bookmarkTimelineSpec.candidate(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.List,
                icon = UiIcon.List,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.AllLists(accountKey),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.DirectMessage,
                icon = UiIcon.Messages,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.AllDirectMessages(accountKey),
                    ),
            ),
        )
    }

    override val listMemberHandler =
        ListMemberHandler(
            pagingKey = "list_member_$accountKey",
            accountKey = accountKey,
            loader = listMemberLoader,
        )

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun featuredTimelineLoader() =
        FeaturedTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun bookmarkTimelineLoader() =
        BookmarkTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun deviceFollowTimelineLoader() =
        DeviceFollowRemoteMediator(
            service,
            accountKey,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        if (type == NotificationFilter.All) {
            NotificationPagingSource(
                locale = "en",
                service = service,
                accountKey = accountKey,
                onClearMarker = {
                    notificationHandler.clear()
                },
            )
        } else {
            MentionRemoteMediator(
                service,
                accountKey,
            )
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = listOf(NotificationFilter.All, NotificationFilter.Mention)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = if (mediaOnly) {
        UserMediaTimelineRemoteMediator(
            userKey,
            service,
            accountKey,
        )
    } else {
        UserTimelineRemoteMediator(
            userKey,
            service,
            accountKey,
        )
    }

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey = statusKey,
            service = service,
            accountKey = accountKey,
            statusOnly = false,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        val inReplyToID =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Reply
                }?.statusKey
                ?.id
        val quoteId =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Quote
                }?.statusKey
        val quoteUserName =
            quoteId
                ?.let { statusKey ->
                    loader.status(statusKey).contentPostOrNull()
                }?.user
                ?.handle
                ?.normalizedRaw
        val mediaIds =
            data.medias.mapIndexed { index, (item, altText) ->
                val bytes = item.readBytes()
                val isImage = item.type == FileType.Image

                val finalBytes =
                    if (isImage) {
                        imageCompressor.compress(
                            imageBytes = bytes,
                            maxSize = 5 * 1024 * 1024,
                            maxDimensions = 4096 to 4096,
                        )
                    } else {
                        bytes
                    }

                uploadMedia(
                    mediaType = getMeidaTypeFromName(item.name),
                    mediaData = finalBytes,
                ).also {
                    if (data.sensitive || !altText.isNullOrEmpty()) {
                        service.postMediaMetadataCreate(
                            body =
                                PostMediaMetadataCreateRequest(
                                    mediaId = it,
                                    sensitiveMediaWarning =
                                        if (data.sensitive) {
                                            listOf(
                                                PostMediaMetadataCreateRequest.SensitiveMediaWarning.Other,
                                            )
                                        } else {
                                            null
                                        },
                                    altText =
                                        if (!altText.isNullOrEmpty()) {
                                            PostMediaMetadataCreateRequest.AltText(altText)
                                        } else {
                                            null
                                        },
                                ),
                        )
                    }
                    progress()
                }
            }
        service.postCreateTweet(
            postCreateTweetRequest =
                PostCreateTweetRequest(
                    features = PostCreateTweetRequestFeatures(),
                    variables =
                        PostCreateTweetRequestVariables(
                            media =
                                PostCreateTweetRequestVariablesMedia(
                                    mediaEntities =
                                        mediaIds.map {
                                            PostCreateTweetRequestVariablesMediaMediaEntitiesInner(
                                                mediaId = it,
                                                taggedUsers = emptyList(),
                                            )
                                        },
                                ),
                            tweetText = data.content,
                            reply =
                                inReplyToID?.let {
                                    PostCreateTweetRequestVariablesReply(
                                        inReplyToTweetId = it,
                                        excludeReplyUserIds = emptyList(),
                                    )
                                },
                            semanticAnnotationIds = emptyList(),
                            attachmentUrl =
                                quoteId?.let {
                                    "https://${accountKey.host}/$quoteUserName/status/${it.id}"
                                },
                        ),
                ),
        )
    }

    private fun getMeidaTypeFromName(name: String?): String =
        when {
            name == null -> "image/jpeg"
            name.endsWith(".jpg") -> "image/jpeg"
            name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            else -> "image/jpeg"
        }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadMedia(
        mediaType: String,
        mediaData: ByteArray,
    ): String =
        coroutineScope {
            val totalBytes = mediaData.size.toLong()
            val mediaId =
                service
                    .initUpload(
                        mediaType = mediaType,
                        totalBytes = totalBytes.toString(),
                        category = if (mediaType.contains("video")) "tweet_video" else "tweet_image",
                    ).mediaIDString ?: throw Error("init upload failed")

            var streamReadLength = 0
            val uploadChunks = mutableListOf<ByteArray>()
            var uploadTimes = 0
            var uploadBytes = 0L

            suspend fun uploadAll() {
                uploadChunks
                    .mapIndexed { index, array ->
                        async {
                            service.appendUpload(
                                mediaId = mediaId,
                                segmentIndex = (uploadTimes * MAX_ASYNC_UPLOAD_SIZE + index.toLong()).toString(),
                                mediaData = Base64.encode(array),
                            )
                            uploadBytes += array.size
                        }
                    }.awaitAll()
                uploadTimes++
                uploadChunks.clear()
            }

            while (streamReadLength < totalBytes) {
                val currentBulkSize = BULK_SIZE.coerceAtMost(totalBytes - streamReadLength).toInt()
                val chunk =
                    mediaData.slice(streamReadLength until streamReadLength + currentBulkSize)
                uploadChunks.add(chunk.toByteArray())
                if (uploadChunks.size >= MAX_ASYNC_UPLOAD_SIZE) {
                    uploadAll()
                }
                streamReadLength += currentBulkSize
            }
            if (uploadChunks.isNotEmpty()) {
                uploadAll()
            }

            var checkCount = 0
            var response = service.finalizeUpload(mediaId)
            var awaitTime = response.processingInfo?.checkAfterSecs
            while (awaitTime != null) {
                delay(awaitTime.seconds)
                checkCount += 1
                response = service.uploadStatus(mediaId)
                awaitTime = response.processingInfo?.checkAfterSecs
            }

            val mediaIdString = checkNotNull(response.mediaIDString) { "upload failed" }
            mediaIdString
        }

    override fun searchStatus(query: String) =
        SearchStatusPagingSource(
            service,
            accountKey,
            query,
        )

    override fun searchUser(query: String) =
        SearchUserPagingSource(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers() =
        TrendsUserPagingSource(
            service,
            accountKey,
        )

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags() =
        TrendHashtagPagingSource(
            service,
        )

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text.withLength(280, String::xWeightedLength),
            media =
                ComposeConfig.Media(
                    maxCount = 4,
                    canSensitive = true,
                    altTextMaxLength = 1000,
                    allowMediaOnly = true,
                ),
        )

    override fun following(userKey: MicroBlogKey) =
        FollowingPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun fans(userKey: MicroBlogKey) =
        FansPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        listOfNotNull(
            ProfileTab(
                name = UiStrings.Posts,
                loader = userTimeline(userKey, false),
            ),
            ProfileTab(
                name = UiStrings.PostsWithReplies,
                loader =
                    UserRepliesTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        userKey = userKey,
                    ),
            ),
            ProfileTab(
                name = UiStrings.Highlights,
                loader =
                    UserHighlightsTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        userKey = userKey,
                    ),
            ),
            ProfileTab(
                name = UiStrings.Media,
                displayType = ProfileTab.DisplayType.Gallery,
                loader = userTimeline(userKey, mediaOnly = true),
            ),
            if (userKey == accountKey) {
                ProfileTab(
                    name = UiStrings.Liked,
                    loader =
                        UserLikesTimelineRemoteMediator(
                            service = service,
                            accountKey = accountKey,
                            userKey = userKey,
                        ),
                )
            } else {
                null
            },
        ).toPersistentList()

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            listId,
            service,
            accountKey,
        )

    suspend fun podcast(id: String): Result<UiPodcast> =
        tryRun {
            val data =
                service
                    .getAudioSpaceById(
                        variables =
                            """
                            {
                                "id": "$id",
                                "isMetatagsQuery": false,
                                "withReplays": true,
                                "withListeners": true
                            }
                            """.trimIndent(),
                    ).data
                    ?.audioSpace
            val mediaKey = data?.metadata?.mediaKey ?: throw Exception("Media key not found")
            val mediaData =
                runCatching {
                    // when podcast.state == ended , then podcast type is replay, so need return podcast info.
                    // if (data.metadata.state == "Ended") {
                    //     null
                    // } else {
                    service
                        .getLiveVideoStreamStatus(mediaKey = mediaKey)
                        .decodeJson<LiveVideoStreamStatusResponse>()
                        .source
                        ?.noRedirectPlaybackURL
                    // }
                }.getOrNull()
            data.render(
                accountKey = accountKey,
                url = mediaData,
            )
        }

    suspend fun getFleets(): Result<ImmutableList<UiPodcast>> {
        return tryRun {
            val fleet = service.getFleets()
            fleet.threads
                .orEmpty()
                .mapNotNull {
                    val title = it.liveContent?.audiospace?.title
                    val id = it.liveContent?.audiospace?.broadcastID ?: return@mapNotNull null
                    val ended = it.liveContent.audiospace.endedAt != null
                    val creator =
                        it.liveContent.audiospace.creatorTwitterUserID?.let {
                            service
                                .userById(it.toString())
                                .body()
                                ?.data
                                ?.user
                                ?.render(accountKey = accountKey)
                        } ?: return@mapNotNull null
                    val hosts =
                        it.liveContent.audiospace.adminTwitterUserIDS
                            .orEmpty()
                            .mapNotNull { host ->
                                service
                                    .userById(host.toString())
                                    .body()
                                    ?.data
                                    ?.user
                                    ?.render(accountKey = accountKey)
                            }.toImmutableList()
                    val speakers =
                        it.liveContent.audiospace.guests
                            .orEmpty()
                            .mapNotNull { speaker ->
                                service
                                    .userById(speaker.toString())
                                    .body()
                                    ?.data
                                    ?.user
                                    ?.render(accountKey = accountKey)
                            }.toImmutableList()
                    val listeners =
                        it.liveContent.audiospace.listeners
                            .orEmpty()
                            .mapNotNull { listener ->
                                service
                                    .userById(listener.toString())
                                    .body()
                                    ?.data
                                    ?.user
                                    ?.render(accountKey = accountKey)
                            }.toImmutableList()
                    UiPodcast(
                        id = id,
                        title = title ?: "",
                        playbackUrl = null,
                        ended = ended,
                        creator = creator,
                        hosts = hosts,
                        speakers = speakers,
                        listeners = listeners,
                    )
                }.toImmutableList()
        }
    }
}
