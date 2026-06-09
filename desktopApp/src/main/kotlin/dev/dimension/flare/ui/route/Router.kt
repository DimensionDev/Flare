package dev.dimension.flare.ui.route

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.dimension.flare.common.OnDeepLink
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.AccountType.Specific
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.compose.ComposeStatus.Quote
import dev.dimension.flare.ui.presenter.compose.ComposeStatus.Reply
import dev.dimension.flare.ui.presenter.compose.ComposeStatus.VVOComment
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.RssTimelinePresenter
import dev.dimension.flare.ui.presenter.home.rss.SubscriptionTimelinePresenter
import dev.dimension.flare.ui.presenter.list.AntennasTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ChannelTimelinePresenter
import dev.dimension.flare.ui.presenter.list.ListTimelinePresenter
import dev.dimension.flare.ui.route.FluentDialogSceneStrategy.Companion.dialog
import dev.dimension.flare.ui.route.Route.EditRssSource
import dev.dimension.flare.ui.route.Route.Profile
import dev.dimension.flare.ui.route.Route.RssTimeline
import dev.dimension.flare.ui.route.Route.Search
import dev.dimension.flare.ui.route.Route.Timeline
import dev.dimension.flare.ui.route.WindowSceneStrategy.Companion.window
import dev.dimension.flare.ui.screen.compose.ComposeDialog
import dev.dimension.flare.ui.screen.compose.DraftBoxScreen
import dev.dimension.flare.ui.screen.dm.DmConversationScreen
import dev.dimension.flare.ui.screen.dm.DmListScreen
import dev.dimension.flare.ui.screen.dm.UserDMConversationScreen
import dev.dimension.flare.ui.screen.feeds.FeedListScreen
import dev.dimension.flare.ui.screen.home.BlockUserDialog
import dev.dimension.flare.ui.screen.home.DeckTimelineScreen
import dev.dimension.flare.ui.screen.home.DeepLinkAccountPicker
import dev.dimension.flare.ui.screen.home.DiscoverScreen
import dev.dimension.flare.ui.screen.home.FansScreen
import dev.dimension.flare.ui.screen.home.FollowingScreen
import dev.dimension.flare.ui.screen.home.GroupConfigScreen
import dev.dimension.flare.ui.screen.home.HomeTimelineScreen
import dev.dimension.flare.ui.screen.home.MuteUserDialog
import dev.dimension.flare.ui.screen.home.NotificationScreen
import dev.dimension.flare.ui.screen.home.ProfileInsightDialog
import dev.dimension.flare.ui.screen.home.ProfileScreen
import dev.dimension.flare.ui.screen.home.ProfileWithUserNameAndHostDeeplinkRoute
import dev.dimension.flare.ui.screen.home.ReportUserDialog
import dev.dimension.flare.ui.screen.home.SearchScreen
import dev.dimension.flare.ui.screen.home.TabSettingScreen
import dev.dimension.flare.ui.screen.home.TimelineScreen
import dev.dimension.flare.ui.screen.home.UnblockUserDialog
import dev.dimension.flare.ui.screen.home.UnmuteUserDialog
import dev.dimension.flare.ui.screen.list.AllListScreen
import dev.dimension.flare.ui.screen.media.RawMediaScreen
import dev.dimension.flare.ui.screen.media.StatusMediaScreen
import dev.dimension.flare.ui.screen.misskey.AntennasListScreen
import dev.dimension.flare.ui.screen.misskey.ChannelListScreen
import dev.dimension.flare.ui.screen.rss.EditRssSourceScreen
import dev.dimension.flare.ui.screen.rss.ImportOPMLScreen
import dev.dimension.flare.ui.screen.rss.RssListScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
import dev.dimension.flare.ui.screen.serviceselect.WebViewLoginScreen
import dev.dimension.flare.ui.screen.settings.AgentChatScreen
import dev.dimension.flare.ui.screen.settings.AgentHistoryScreen
import dev.dimension.flare.ui.screen.settings.AppLoggingScreen
import dev.dimension.flare.ui.screen.settings.LocalCacheScreen
import dev.dimension.flare.ui.screen.settings.LocalHistoryAgentScreen
import dev.dimension.flare.ui.screen.settings.NostrRelaysScreen
import dev.dimension.flare.ui.screen.settings.SettingsScreen
import dev.dimension.flare.ui.screen.status.StatusScreen
import dev.dimension.flare.ui.screen.status.VVOCommentScreen
import dev.dimension.flare.ui.screen.status.VVOStatusScreen
import dev.dimension.flare.ui.screen.status.action.AddReactionSheet
import dev.dimension.flare.ui.screen.status.action.BlueskyReportStatusDialog
import dev.dimension.flare.ui.screen.status.action.DeleteStatusConfirmDialog
import dev.dimension.flare.ui.screen.status.action.MastodonReportDialog
import dev.dimension.flare.ui.screen.status.action.MisskeyReportDialog
import dev.dimension.flare.ui.screen.status.action.StatusInsightDialog
import dev.dimension.flare.ui.screen.status.action.StatusShareSheet
import dev.dimension.flare.ui.screen.xqt.TwitterArticleScreen
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.Flyout
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun Router(
    backStack: ImmutableList<Route>,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    enableDeepLinkHandler: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val listDetailStrategy = rememberListDetailSceneStrategy<Route>()

    val isBigScreen = isBigScreen()
    val navigateToAgentChat: (String?) -> Unit = { initialMessage ->
        navigate(
            Route.AgentChat(
                conversationId = "generic-chat:${kotlin.time.Clock.System.now().toEpochMilliseconds()}",
                initialMessage = initialMessage,
            ),
        )
    }
    if (enableDeepLinkHandler) {
        OnDeepLink {
            val route = Route.parse(it)
            if (route != null) {
                navigate(route)
            }
            route != null
        }
    }
    NavDisplay(
        modifier = modifier,
        sceneStrategies =
            remember(listDetailStrategy) {
                listOf(
                    FluentDialogSceneStrategy(),
                    WindowSceneStrategy(),
                    listDetailStrategy,
                )
            },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                remember {
                    NavEntryDecorator {
                        if (
                            it.metadata.containsKey(FluentDialogSceneStrategy.DIALOG_KEY) ||
                            it.metadata.containsKey(WindowSceneStrategy.WINDOW_KEY)
                        ) {
                            it.Content()
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(FluentTheme.colors.background.solid.base),
                            ) {
                                it.Content()
                            }
                        }
                    }
                },
            ),
        backStack = backStack,
        onBack = { onBack() },
        transitionSpec = {
            if (isBigScreen) {
                fadeIn(
                    initialAlpha = 0.85f,
                    animationSpec = tween(),
                ) + scaleIn(initialScale = 0.85f, animationSpec = tween()) togetherWith
                    fadeOut(
                        targetAlpha = 0f,
                        animationSpec = tween(),
                    ) + scaleOut(targetScale = 1.15f, animationSpec = tween())
            } else {
                slideIntoContainer(SlideDirection.Start, animationSpec = tween()) togetherWith
                    slideOutOfContainer(
                        SlideDirection.Start,
                        targetOffset = { it / 2 },
                        animationSpec = tween(),
                    )
            }
        },
        popTransitionSpec = {
            if (isBigScreen) {
                fadeIn(
                    initialAlpha = 0f,
                    animationSpec = tween(),
                ) + scaleIn(initialScale = 1.15f, animationSpec = tween()) togetherWith
                    fadeOut(
                        targetAlpha = 0f,
                        animationSpec = tween(),
                    ) + scaleOut(targetScale = 0.85f, animationSpec = tween())
            } else {
                slideIntoContainer(
                    SlideDirection.End,
                    initialOffset = { it / 2 },
                    animationSpec = tween(),
                ) togetherWith
                    slideOutOfContainer(SlideDirection.End, animationSpec = tween())
            }
        },
        predictivePopTransitionSpec = {
            if (isBigScreen) {
                fadeIn(
                    initialAlpha = 0f,
                    animationSpec = tween(),
                ) + scaleIn(initialScale = 0.85f, animationSpec = tween()) togetherWith
                    fadeOut(
                        targetAlpha = 0f,
                        animationSpec = tween(),
                    ) + scaleOut(targetScale = 1.15f, animationSpec = tween())
            } else {
                slideIntoContainer(SlideDirection.Start, animationSpec = tween()) togetherWith
                    slideOutOfContainer(
                        SlideDirection.Start,
                        targetOffset = { it / 2 },
                        animationSpec = tween(),
                    )
            }
        },
        entryProvider =
            entryProvider {
                entry<Route.DeepLinkAccountPicker>(
                    metadata = dialog(),
                ) { args ->
                    DeepLinkAccountPicker(
                        originalUrl = args.originalUrl,
                        data = args.data,
                        onNavigate = navigate,
                        onDismissRequest = onBack,
                    )
                }
                entry<Route.TwitterArticle> { args ->
                    TwitterArticleScreen(
                        accountType = args.accountType,
                        tweetId = args.tweetId,
                        articleId = args.articleId,
                        onBack = onBack,
                    )
                }
                entry<Route.RssDetail> { args ->
                    dev.dimension.flare.ui.screen.rss.RssDetailScreen(
                        url = args.url,
                        descriptionHtml = args.descriptionHtml,
                        descriptionTitle = args.title,
                        onBack = onBack,
                    )
                }
                entry<Route.AddReaction>(
                    metadata = dialog(),
                ) { args ->
                    AddReactionSheet(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        onBack = onBack,
                    )
                }
                entry<Route.BlueskyReport>(
                    metadata = dialog(),
                ) { args ->
                    BlueskyReportStatusDialog(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        onBack = onBack,
                    )
                }

                entry<Route.DeleteStatus>(
                    metadata = dialog(),
                ) { args ->
                    DeleteStatusConfirmDialog(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        onBack = onBack,
                    )
                }

                entry<Route.MastodonReport>(
                    metadata = dialog(),
                ) { args ->
                    MastodonReportDialog(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        onBack = onBack,
                        userKey = args.userKey,
                    )
                }

                entry<Route.MisskeyReport>(
                    metadata = dialog(),
                ) { args ->
                    MisskeyReportDialog(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        onBack = onBack,
                        userKey = args.userKey,
                    )
                }

                entry<Route.AltText>(
                    metadata = dialog(),
                ) { args ->
                    Flyout(
                        visible = true,
                        onDismissRequest = {
                            onBack()
                        },
                    ) {
                        Text(
                            text = args.text,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                entry<Route.StatusShareSheet>(
                    metadata = dialog(),
                ) { args ->
                    StatusShareSheet(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        shareUrl = args.shareUrl,
                        fxShareUrl = args.fxShareUrl,
                        fixvxShareUrl = args.fixvxShareUrl,
                        onBack = onBack,
                    )
                }

                entry<Route.StatusInsight>(
                    metadata = dialog(),
                ) { args ->
                    StatusInsightDialog(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        onBack = onBack,
                        navigate = navigate,
                    )
                }

                entry<Route.ProfileInsight>(
                    metadata = dialog(),
                ) { args ->
                    ProfileInsightDialog(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                        navigate = navigate,
                    )
                }

                entry<Route.CreateRssSource>(
                    metadata = dialog(),
                ) { args ->
                    EditRssSourceScreen(
                        onDismissRequest = onBack,
                        id = null,
                        onImport = {
                            navigate(
                                Route.ImportOPML(it),
                            )
                        },
                    )
                }

                entry<EditRssSource>(
                    metadata = dialog(),
                ) { args ->
                    EditRssSourceScreen(
                        onDismissRequest = onBack,
                        id = args.id,
                        onImport = {
                            navigate(
                                Route.ImportOPML(it),
                            )
                        },
                    )
                }

                entry<Route.ImportOPML>(
                    metadata = dialog(),
                ) { args ->
                    ImportOPMLScreen(
                        onDismissRequest = onBack,
                        filePath = args.filePath,
                    )
                }

                entry<Route.Compose.New>(
                    metadata = dialog(),
                ) { args ->
                    FluentDialog(
                        visible = true,
                    ) {
                        ComposeDialog(
                            onBack = onBack,
                            accountType = null,
                            onOpenDraftBox = {
                                navigate(Route.DraftBox)
                            },
                        )
                    }
                }

                entry<Route.Compose.Draft>(
                    metadata = dialog(),
                ) { args ->
                    FluentDialog(visible = true) {
                        ComposeDialog(
                            onBack = onBack,
                            accountType = null,
                            draftGroupId = args.draftGroupId,
                            onOpenDraftBox = {
                                navigate(Route.DraftBox)
                            },
                        )
                    }
                }

                entry<Route.Compose.Quote>(
                    metadata = dialog(),
                ) { args ->
                    FluentDialog(visible = true) {
                        ComposeDialog(
                            onBack = onBack,
                            status = Quote(args.statusKey),
                            accountType = Specific(accountKey = args.accountKey),
                            onOpenDraftBox = {
                                navigate(Route.DraftBox)
                            },
                        )
                    }
                }

                entry<Route.Compose.Reply>(
                    metadata = dialog(),
                ) { args ->
                    FluentDialog(visible = true) {
                        ComposeDialog(
                            onBack = onBack,
                            status = Reply(args.statusKey),
                            accountType = Specific(accountKey = args.accountKey),
                            onOpenDraftBox = {
                                navigate(Route.DraftBox)
                            },
                        )
                    }
                }

                entry<Route.Compose.VVOReplyComment>(
                    metadata = dialog(),
                ) { args ->
                    FluentDialog(visible = true) {
                        ComposeDialog(
                            onBack = onBack,
                            accountType = Specific(accountKey = args.accountKey),
                            status = VVOComment(args.replyTo, args.rootId),
                            onOpenDraftBox = {
                                navigate(Route.DraftBox)
                            },
                        )
                    }
                }

                entry<Route.AllLists> { args ->
                    AllListScreen(
                        accountType = args.accountType,
                        onAddList = {
                        },
                        toList = {
                            navigate(
                                Timeline(
                                    SourceTimelineTabItemV2.runtime(
                                        id = "desktop.common.list:${args.accountType}:${it.id}",
                                        title = UiText.Raw(it.title),
                                        icon = UiIcon.List.asType(),
                                        createPresenter = {
                                            ListTimelinePresenter(
                                                accountType = args.accountType,
                                                listId = it.id,
                                            )
                                        },
                                    ),
                                ),
                            )
                        },
                        editList = {
                        },
                        deleteList = {
                        },
                    )
                }

                entry<Route.BlueskyFeeds> { args ->
                    FeedListScreen(
                        accountType = args.accountType,
                        toFeed = {
                            navigate(
                                Timeline(
                                    SourceTimelineTabItemV2.runtime(
                                        id = "desktop.bluesky.feed:${args.accountType}:${it.id}",
                                        title = UiText.Raw(it.title),
                                        icon = UiIcon.Feeds.asType(),
                                        createPresenter = {
                                            BlueskyFeedTimelinePresenter(
                                                accountType = args.accountType,
                                                uri = it.id,
                                            )
                                        },
                                    ),
                                ),
                            )
                        },
                    )
                }

                entry<Route.Discover> { args ->
                    DiscoverScreen(
                        toUser = { accountType, userKey ->
                            navigate(
                                Profile(
                                    accountType = accountType,
                                    userKey = userKey,
                                ),
                            )
                        },
                        toSearch = { accountType, keyword ->
                            navigate(
                                Search(
                                    accountType = accountType,
                                    keyword = keyword,
                                ),
                            )
                        },
                        toAskAi = navigateToAgentChat,
                    )
                }

                entry<Search> { args ->
                    SearchScreen(
                        initialQuery = args.keyword,
                        accountType = args.accountType,
                        toUser = { accountType, userKey ->
                            navigate(
                                Profile(
                                    accountType = accountType,
                                    userKey = userKey,
                                ),
                            )
                        },
                        toAskAi = navigateToAgentChat,
                    )
                }

                entry<Route.MeRoute> { args ->
                    ProfileScreen(
                        accountType = args.accountType,
                        userKey = null,
                        onMediaClick = { statusKey, index, preview ->
                            navigate(
                                Route.StatusMedia(
                                    accountType = args.accountType,
                                    statusKey = statusKey,
                                    index = index,
                                    preview = preview,
                                ),
                            )
                        },
                        onRawMediaClick = {
                            navigate(
                                Route.RawImage(
                                    rawImage = it.url,
                                    customHeaders = it.customHeaders,
                                ),
                            )
                        },
                        onProfileInsightClick = {
                            navigate(
                                Route.ProfileInsight(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.Notification> {
                    NotificationScreen()
                }

                entry<Profile> { args ->
                    ProfileScreen(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onFollowListClick = {
                            navigate(
                                Route.Following(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                        onFansListClick = {
                            navigate(
                                Route.Fans(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                        onMediaClick = { statusKey, index, preview ->
                            navigate(
                                Route.StatusMedia(
                                    accountType = args.accountType,
                                    statusKey = statusKey,
                                    index = index,
                                    preview = preview,
                                ),
                            )
                        },
                        onRawMediaClick = {
                            navigate(
                                Route.RawImage(
                                    rawImage = it.url,
                                    customHeaders = it.customHeaders,
                                ),
                            )
                        },
                        onProfileInsightClick = {
                            navigate(
                                Route.ProfileInsight(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.ServiceSelect> {
                    ServiceSelectScreen(
                        onBack = onBack,
                        onWebViewLogin = { url, callback ->
                            navigate(
                                Route.WebViewLogin(
                                    url = url,
                                    callback = callback,
                                ),
                            )
                        },
                    )
                }

                entry<Route.Settings> {
                    SettingsScreen(
                        toLogin = {
                            navigate(Route.ServiceSelect)
                        },
                        toDraftBox = {
                            navigate(Route.DraftBox)
                        },
                        toLocalCache = {
                            navigate(Route.LocalCache)
                        },
                        toAgentHistory = {
                            navigate(Route.AgentHistory)
                        },
                        toAppLog = {
                            navigate(Route.AppLogging)
                        },
                        toRSSManagement = {
                            navigate(Route.RssList)
                        },
                        toNostrRelays = {
                            navigate(Route.NostrRelays(it))
                        },
                    )
                }

                entry<Route.DraftBox> {
                    DraftBoxScreen(
                        onEdit = { groupId ->
                            navigate(Route.Compose.Draft(draftGroupId = groupId))
                        },
                    )
                }

                entry<Route.AppLogging> {
                    AppLoggingScreen()
                }

                entry<Timeline> { args ->
                    TimelineScreen(
                        args.tabItem,
                    )
                }

                entry<Route.Home> { args ->
                    HomeTimelineScreen(
                        args.accountType,
                        onAddTab = {
                            navigate(
                                Route.TabSetting,
                            )
                        },
                    )
                }

                entry<Route.DeckTimeline> { args ->
                    DeckTimelineScreen(
                        id = args.id,
                        toTabSettings = {
                            navigate(Route.TabSetting)
                        },
                    )
                }

                entry<Route.TabSetting> {
                    TabSettingScreen(
                        toAddRssSource = {
                            navigate(
                                Route.CreateRssSource,
                            )
                        },
                        toGroupConfig = {
                            navigate(
                                Route.TabGroupConfig(it?.id),
                            )
                        },
                    )
                }

                entry<Route.TabGroupConfig> { args ->
                    GroupConfigScreen(
                        groupId = args.groupId,
                        toAddRssSource = {
                            navigate(
                                Route.CreateRssSource,
                            )
                        },
                    )
                }

                entry<Route.StatusDetail> { args ->
                    StatusScreen(
                        statusKey = args.statusKey,
                        accountType = args.accountType,
                    )
                }

                entry<Route.VVO.CommentDetail> { args ->
                    VVOCommentScreen(
                        commentKey = args.statusKey,
                        accountType = args.accountType,
                    )
                }

                entry<Route.VVO.StatusDetail> { args ->
                    VVOStatusScreen(
                        statusKey = args.statusKey,
                        accountType = args.accountType,
                    )
                }

                entry<Route.ProfileWithNameAndHost> { args ->
                    ProfileWithUserNameAndHostDeeplinkRoute(
                        userName = args.userName,
                        host = args.host,
                        accountType = args.accountType,
                        onBack = onBack,
                        onFollowListClick = {
                            navigate(
                                Route.Following(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                        onFansListClick = {
                            navigate(
                                Route.Fans(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                        onMediaClick = { statusKey, index, preview ->
                            navigate(
                                Route.StatusMedia(
                                    accountType = args.accountType,
                                    statusKey = statusKey,
                                    index = index,
                                    preview = preview,
                                ),
                            )
                        },
                        onRawMediaClick = {
                            navigate(
                                Route.RawImage(
                                    rawImage = it.url,
                                    customHeaders = it.customHeaders,
                                ),
                            )
                        },
                        onProfileInsightClick = {
                            navigate(
                                Route.ProfileInsight(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.RssList>(
                    metadata =
                        ListDetailSceneStrategy.listPane(
                            "Rss",
                            detailPlaceholder = {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(FluentTheme.colors.background.solid.base),
                                )
                            },
                        ),
                ) { args ->
                    RssListScreen(
                        toItem = {
                            navigate(
                                RssTimeline(it),
                            )
                        },
                        onEdit = {
                            navigate(
                                EditRssSource(
                                    id = it,
                                ),
                            )
                        },
                        onAdd = {
                            navigate(Route.CreateRssSource)
                        },
                    )
                }

                entry<RssTimeline>(
                    metadata =
                        ListDetailSceneStrategy.detailPane(
                            "Rss",
                        ),
                ) { args ->
                    TimelineScreen(
                        args.data.toTimelineTabItem(),
                    )
                }

                entry<Route.RawImage>(
                    metadata = window(isDarkTheme = true),
                ) { args ->
                    RawMediaScreen(
                        url = args.rawImage,
                        customHeaders = args.customHeaders,
                    )
                }
                entry<Route.StatusMedia>(
                    metadata = window(isDarkTheme = true),
                ) { args ->
                    StatusMediaScreen(
                        accountType = args.accountType,
                        statusKey = args.statusKey,
                        index = args.index,
                    )
                }

                entry<Route.DmList>(
                    metadata =
                        ListDetailSceneStrategy.listPane(
                            "DMs",
                            detailPlaceholder = {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(FluentTheme.colors.background.solid.base),
                                )
                            },
                        ),
                ) { args ->
                    DmListScreen(
                        accountType = args.accountType,
                        onItemClicked = {
                            navigate(
                                Route.DmConversation(
                                    accountType = args.accountType,
                                    roomKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.DmConversation>(
                    metadata =
                        ListDetailSceneStrategy.detailPane(
                            "DMs",
                        ),
                ) { args ->
                    DmConversationScreen(
                        accountType = args.accountType,
                        roomKey = args.roomKey,
                        onBack = onBack,
                        toProfile = {
                            navigate(
                                Profile(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.DmUserConversation>(
                    metadata =
                        ListDetailSceneStrategy.detailPane(
                            "DMs",
                        ),
                ) { args ->
                    UserDMConversationScreen(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                        toProfile = {
                            navigate(
                                Profile(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.MisskeyAntennas> { args ->
                    AntennasListScreen(
                        accountType = args.accountType,
                        toTimeline = {
                            navigate(
                                Timeline(
                                    SourceTimelineTabItemV2.runtime(
                                        id = "desktop.misskey.antenna:${args.accountType}:${it.id}",
                                        title = UiText.Raw(it.title),
                                        icon = UiIcon.Rss.asType(),
                                        createPresenter = {
                                            AntennasTimelinePresenter(
                                                accountType = args.accountType,
                                                id = it.id,
                                            )
                                        },
                                    ),
                                ),
                            )
                        },
                    )
                }

                entry<Route.LocalCache> {
                    LocalCacheScreen(
                        toAskAi = { query, target ->
                            navigate(
                                Route.LocalHistoryAgent(
                                    conversationId = "local-history:${kotlin.time.Clock.System.now().toEpochMilliseconds()}",
                                    query = query,
                                    target = target,
                                ),
                            )
                        },
                    )
                }

                entry<Route.AgentHistory> {
                    AgentHistoryScreen(
                        onConversationClick = { conversationId ->
                            navigate(Route.AgentChat(conversationId = conversationId))
                        },
                        onNewConversationClick = {
                            navigate(
                                Route.AgentChat(
                                    conversationId = "generic-chat:${kotlin.time.Clock.System.now().toEpochMilliseconds()}",
                                ),
                            )
                        },
                    )
                }

                entry<Route.AgentChat> { args ->
                    AgentChatScreen(
                        conversationId = args.conversationId,
                        initialMessage = args.initialMessage,
                        onBack = onBack,
                        navigate = navigate,
                    )
                }

                entry<Route.LocalHistoryAgent> { args ->
                    LocalHistoryAgentScreen(
                        conversationId = args.conversationId,
                        query = args.query,
                        target = args.target,
                        onBack = onBack,
                        navigate = navigate,
                    )
                }

                entry<Route.NostrRelays> { args ->
                    NostrRelaysScreen(
                        accountKey = args.accountKey,
                    )
                }

                entry<Route.Following> { args ->
                    FollowingScreen(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onUserClick = {
                            navigate(
                                Profile(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.Fans> { args ->
                    FansScreen(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onUserClick = {
                            navigate(
                                Profile(
                                    accountType = args.accountType,
                                    userKey = it,
                                ),
                            )
                        },
                    )
                }

                entry<Route.WebViewLogin> { args ->
                    WebViewLoginScreen(
                        url = args.url,
                        callback = args.callback,
                        onBack = onBack,
                    )
                }

                entry<Route.BlockUser>(
                    metadata = dialog(),
                ) { args ->
                    BlockUserDialog(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                    )
                }
                entry<Route.MuteUser>(
                    metadata = dialog(),
                ) { args ->
                    MuteUserDialog(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                    )
                }
                entry<Route.UnblockUser>(
                    metadata = dialog(),
                ) { args ->
                    UnblockUserDialog(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                    )
                }
                entry<Route.UnmuteUser>(
                    metadata = dialog(),
                ) { args ->
                    UnmuteUserDialog(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                    )
                }
                entry<Route.ReportUser>(
                    metadata = dialog(),
                ) { args ->
                    ReportUserDialog(
                        accountType = args.accountType,
                        userKey = args.userKey,
                        onBack = onBack,
                    )
                }
                entry<Route.MisskeyChannelList>(
                    metadata =
                        ListDetailSceneStrategy.listPane(
                            sceneKey = "misskey_channels_list",
                            detailPlaceholder = {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(FluentTheme.colors.background.solid.base),
                                )
                            },
                        ),
                ) { args ->
                    ChannelListScreen(
                        accountType = args.accountType,
                        toTimeline = {
                            navigate(
                                Route.MisskeyChannelTimeline(
                                    args.accountType,
                                    it.id,
                                    it.title,
                                ),
                            )
                        },
                    )
                }
                entry<Route.MisskeyChannelTimeline>(
                    metadata =
                        ListDetailSceneStrategy.detailPane(
                            sceneKey = "misskey_channels_list",
                        ),
                ) { args ->
                    TimelineScreen(
                        tabItem =
                            remember(args) {
                                SourceTimelineTabItemV2.runtime(
                                    id = "desktop.misskey.channel:${args.accountType}:${args.channelId}",
                                    title = UiText.Raw(args.title),
                                    icon = UiIcon.Channel.asType(),
                                    createPresenter = {
                                        ChannelTimelinePresenter(
                                            accountType = args.accountType,
                                            id = args.channelId,
                                        )
                                    },
                                )
                            },
                    )
                }
            },
    )
}

private fun UiRssSource.toTimelineTabItem(): TimelineTabItemV2 =
    SourceTimelineTabItemV2.runtime(
        id = "desktop.rss:${type.name}:$url",
        title = UiText.Raw(title ?: host),
        icon = favIcon?.let { IconType.Url(it) } ?: UiIcon.Rss.asType(),
        createPresenter = {
            when (type) {
                SubscriptionType.RSS -> RssTimelinePresenter(url)
                else -> SubscriptionTimelinePresenter(type, url)
            }
        },
    )
