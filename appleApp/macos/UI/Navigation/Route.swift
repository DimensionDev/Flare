import KotlinSharedUI
import SwiftUI
import FlareAppleCore
import FlareAppleUI

enum Route: Hashable, Identifiable {
    case empty
    case notification
    case accountNotification(MicroBlogKey)
    case discover
    case serviceSelect
    case relogin(MicroBlogKey, PlatformType)
    case localHistory
    case agentHistory
    case directMessages
    case agentChat(String, String?)
    case localHistoryAgent(String, String?, String)
    case timeline(UiTimelineTabItem, isHome: Bool = false)
    case composeNew
    case composeCrossPost(MacComposePrefill)
    case composeDraft(String)
    case composeQuote(AccountType, MicroBlogKey)
    case composeReply(AccountType, MicroBlogKey)
    case composeVVOReplyComment(AccountType, MicroBlogKey, String)
    case statusDetail(AccountType, MicroBlogKey)
    case statusInsight(AccountType, MicroBlogKey)
    case galleryDetail(AccountType, MicroBlogKey)
    case galleryComments(AccountType, MicroBlogKey)
    case statusVVOComment(AccountType, MicroBlogKey)
    case statusVVOStatus(AccountType, MicroBlogKey)
    case profileUser(AccountType, MicroBlogKey)
    case profileUserNameWithHost(AccountType, String, String)
    case profileInsight(AccountType, MicroBlogKey)
    case userFollowing(AccountType, MicroBlogKey)
    case userFans(AccountType, MicroBlogKey)
    case mediaImage(String, String?, [String: String]?)
    case mediaRaw([any UiMedia], Int, String?)
    case mediaStatusMedia(AccountType, MicroBlogKey, Int32, String?)
    case deepLinkAccountPicker(String, [MicroBlogKey: Route])
    case rssDetail(String, String?, String?)
    case article(AccountType, MicroBlogKey)
    case search(AccountType, String)
    case statusAddReaction(AccountType, MicroBlogKey)
    case statusShareSheet(AccountType, MicroBlogKey, String, String?, String?)
    case statusCrossPost(AccountType, MicroBlogKey, String)
    case statusBlueskyReport(AccountType, MicroBlogKey)
    case statusDeleteConfirm(AccountType, MicroBlogKey)
    case statusMastodonReport(AccountType, MicroBlogKey, MicroBlogKey?)
    case statusMisskeyReport(AccountType, MicroBlogKey, MicroBlogKey?)
    case blockUser(AccountType?, MicroBlogKey)
    case unblockUser(AccountType?, MicroBlogKey)
    case muteUser(AccountType?, MicroBlogKey)
    case unmuteUser(AccountType?, MicroBlogKey)
    case reportUser(AccountType?, MicroBlogKey)
    case editUserList(AccountType, MicroBlogKey)
    case dmConversation(AccountType, MicroBlogKey, String)
    case userDirectMessages(AccountType, MicroBlogKey)
    case allDirectMessages(AccountType)
    case allLists(AccountType)
    case allFeeds(AccountType)
    case allAntennas(AccountType)
    case allChannels(AccountType)
    case externalLink(String)

    var id: Int {
        return self.hashValue
    }
    
    static func == (lhs: Route, rhs: Route) -> Bool {
        switch (lhs, rhs) {
        case (.timeline(let lhs, let lhsIsHome), .timeline(let rhs, let rhsIsHome)):
            return lhs.id == rhs.id && lhsIsHome == rhsIsHome
        case (.mediaRaw(let lhsMedias, let lhsIndex, let lhsPreview), .mediaRaw(let rhsMedias, let rhsIndex, let rhsPreview)):
            return lhsIndex == rhsIndex &&
                lhsPreview == rhsPreview &&
                lhsMedias.map { $0.url } == rhsMedias.map { $0.url }
        case (.relogin(let lhsAccountKey, let lhsPlatformType), .relogin(let rhsAccountKey, let rhsPlatformType)):
            return lhsAccountKey.id == rhsAccountKey.id &&
                lhsAccountKey.host == rhsAccountKey.host &&
                lhsPlatformType == rhsPlatformType
        case (.composeCrossPost(let lhs), .composeCrossPost(let rhs)):
            return lhs == rhs
        default:
            return lhs.hashValue == rhs.hashValue
        }
    }

    func hash(into hasher: inout Hasher) {
        switch self {
        case .accountNotification(let accountKey):
            hasher.combine("accountNotification")
            hasher.combine(accountKey.host)
            hasher.combine(accountKey.id)
        case .timeline(let item, let isHome):
            hasher.combine("timeline")
            hasher.combine(item.id)
            hasher.combine(isHome)
        case .relogin(let accountKey, let platformType):
            hasher.combine("relogin")
            hasher.combine(accountKey.id)
            hasher.combine(accountKey.host)
            hasher.combine(platformType)
        case .composeCrossPost(let prefill):
            hasher.combine("composeCrossPost")
            hasher.combine(prefill)
        case .mediaRaw(let medias, let selectedIndex, let preview):
            hasher.combine("mediaRaw")
            hasher.combine(medias.map { $0.url })
            hasher.combine(selectedIndex)
            hasher.combine(preview)
        default:
            hasher.combine(String(describing: self))
        }
    }

    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (Route) -> Void,
        goBack: @escaping () -> Void
    ) -> some View {
        switch self {
        case .empty:
            EmptyView()
        case .notification:
            PlaceholderPanel(destination: .notifications)
        case .accountNotification(let accountKey):
            NotificationScreen(accountKey: accountKey)
        case .discover:
            DiscoverScreen { query in
                onNavigate(.agentChat(Self.newGenericChatConversationId(), query))
            }
        case .serviceSelect:
            ServiceSelectionScreen(toHome: goBack)
        case .relogin(let accountKey, let platformType):
            ReloginScreen(
                target: ReloginTarget(accountKey: accountKey, platformType: platformType),
                toHome: goBack
            )
        case .localHistory:
            LocalHistoryContentScreen(
                onAskAi: { query, target in
                    onNavigate(.localHistoryAgent(Self.newLocalHistoryAgentConversationId(), query, target))
                }
            ) { _, _ in
                EmptyView()
            }
        case .agentHistory:
            AgentChatHistoryScreen(onNavigate: onNavigate)
        case .directMessages,
                .dmConversation,
                .userDirectMessages,
                .allDirectMessages:
            EmptyView()
        case .agentChat(let conversationId, let initialMessage):
            AgentChatScreen(
                conversationId: conversationId,
                initialMessage: initialMessage,
                onNavigate: onNavigate
            )
        case .localHistoryAgent(let conversationId, let query, let target):
            LocalHistoryAgentScreen(
                conversationId: conversationId,
                query: query,
                target: target,
                onNavigate: onNavigate
            )
        case .timeline(let item, let isHome):
            TimelineScreen(tabItem: item, allowGalleryMode: true, isHomeTimeline: isHome)
                .navigationTitle(item.title.text)
        case .composeNew,
                .composeCrossPost,
                .composeDraft,
                .composeQuote,
                .composeReply,
                .composeVVOReplyComment,
                .mediaImage,
                .mediaRaw,
                .mediaStatusMedia,
                .statusDeleteConfirm,
                .statusMastodonReport,
                .blockUser,
                .unblockUser,
                .muteUser,
                .unmuteUser,
                .reportUser:
            EmptyView()
        case .statusDetail(let accountType, let statusKey):
            StatusDetailScreen(accountType: accountType, statusKey: statusKey)
        case .statusInsight(let accountType, let statusKey):
            StatusInsightScreen(
                accountType: accountType,
                statusKey: statusKey,
                onNavigate: onNavigate
            )
        case .galleryDetail(let accountType, let statusKey):
            GalleryDetailScreen(accountType: accountType, statusKey: statusKey, onNavigate: onNavigate)
        case .galleryComments(let accountType, let statusKey):
            GalleryCommentsScreen(accountType: accountType, statusKey: statusKey)
        case .statusVVOComment(let accountType, let statusKey):
            VVOCommentScreen(accountType: accountType, statusKey: statusKey)
        case .statusVVOStatus(let accountType, let statusKey):
            VVOStatusScreen(accountType: accountType, statusKey: statusKey)
        case .profileUser(let accountType, let userKey):
            ProfileScreen(
                accountType: accountType,
                userKey: userKey,
                onFollowingClick: { key in onNavigate(.userFollowing(accountType, key)) },
                onFansClick: { key in onNavigate(.userFans(accountType, key)) },
                onProfileInsight: { key in onNavigate(.profileInsight(accountType, key)) },
                goBack: goBack
            )
        case .profileUserNameWithHost(let accountType, let userName, let host):
            ProfileWithUserNameAndHostScreen(
                userName: userName,
                host: host,
                accountType: accountType,
                onFollowingClick: { key in onNavigate(.userFollowing(accountType, key)) },
                onFansClick: { key in onNavigate(.userFans(accountType, key)) },
                onProfileInsight: { key in onNavigate(.profileInsight(accountType, key)) },
                goBack: goBack
            )
        case .profileInsight(let accountType, let userKey):
            ProfileInsightScreen(
                accountType: accountType,
                userKey: userKey,
                onNavigate: onNavigate
            )
        case .userFollowing(let accountType, let userKey):
            UserListScreen(accountType: accountType, userKey: userKey, isFollowing: true)
        case .userFans(let accountType, let userKey):
            UserListScreen(accountType: accountType, userKey: userKey, isFollowing: false)
        case .deepLinkAccountPicker(let originalUrl, let data):
            DeepLinkAccountPickerView(
                originalUrl: originalUrl,
                data: data,
                onNavigate: onNavigate
            )
            .frame(width: 300, height: 400)
        case .rssDetail(let url, let descriptionHtml, let title):
            RssDetailScreen(url: url, descriptionHtml: descriptionHtml, descriptionTitle: title)
        case .article(let accountType, let articleKey):
            MacArticleScreen(
                accountType: accountType,
                articleKey: articleKey,
                onNavigate: onNavigate
            )
        case .search(let accountType, let query):
            SearchScreen(
                accountType: accountType,
                initialQuery: query,
                onAskAi: { query in
                    onNavigate(.agentChat(Self.newGenericChatConversationId(), query))
                }
            )
        case .statusAddReaction(let accountType, let statusKey):
            StatusAddReactionSheet(accountType: accountType, statusKey: statusKey)
                .frame(width: 400, height: 300)
        case .statusShareSheet(let accountType, let statusKey, _, _, _):
            MacStatusShareSheet(
                statusKey: statusKey,
                accountType: accountType
            )
        case .statusCrossPost(let accountType, let statusKey, let shareUrl):
            MacStatusShareSheet(
                statusKey: statusKey,
                accountType: accountType,
                purpose: .crossPost,
                onCrossPost: { imageData, fileName in
                    onNavigate(
                        .composeCrossPost(
                            MacComposePrefill(
                                text: "\n\n\(shareUrl)",
                                cursorPosition: 0,
                                imageData: imageData,
                                fileName: fileName
                            )
                        )
                    )
                }
            )
        case .statusBlueskyReport(let accountType, let statusKey):
            BlueskyReportSheet(accountType: accountType, statusKey: statusKey)
                .frame(width: 300, height: 400)
        case .statusMisskeyReport(let accountType, let userKey, let statusKey):
            MisskeyReportSheet(accountType: accountType, userKey: userKey, statusKey: statusKey)
                .frame(width: 300, height: 400)
        case .editUserList(let accountType, let userKey):
            EditUserInListScreen(accountType: accountType, userKey: userKey)
        case .allLists(let accountType):
            AllListScreen(
                accountType: accountType,
                timelineDestination: { Route.timeline($0) }
            )
        case .allFeeds(let accountType):
            AllFeedScreen(
                accountType: accountType,
                timelineDestination: { Route.timeline($0) }
            )
        case .allAntennas(let accountType):
            AntennasListScreen(
                accountType: accountType,
                timelineDestination: { Route.timeline($0) }
            )
        case .allChannels(let accountType):
            ChannelListScreen(
                accountType: accountType,
                timelineDestination: { Route.timeline($0) }
            )
        case .externalLink:
            EmptyView()
        }
    }

    static func newGenericChatConversationId() -> String {
        "generic-chat:\(Int64(Date().timeIntervalSince1970 * 1000))"
    }

    static func newLocalHistoryAgentConversationId() -> String {
        "local-history:\(Int64(Date().timeIntervalSince1970 * 1000))"
    }

    var isAgentWindowRoute: Bool {
        switch self {
        case .agentHistory,
                .agentChat,
                .localHistoryAgent,
                .statusInsight,
                .profileInsight:
            true
        default:
            false
        }
    }

    var agentConversationId: String? {
        switch self {
        case .agentChat(let conversationId, _),
                .localHistoryAgent(let conversationId, _, _):
            conversationId
        case .statusInsight(let accountType, let statusKey):
            "status-insight:\(String(describing: accountType)):\(statusKey.description())"
        case .profileInsight(let accountType, let userKey):
            "profile-insight:\(String(describing: accountType)):\(userKey.description())"
        default:
            nil
        }
    }

    var isDirectMessageWindowRoute: Bool {
        switch self {
        case .directMessages,
                .dmConversation,
                .userDirectMessages,
                .allDirectMessages:
            true
        default:
            false
        }
    }
}

private struct MacArticleScreen: View {
    let accountType: AccountType
    let articleKey: MicroBlogKey
    let onNavigate: (Route) -> Void

    @State private var downloadAlert: MacArticleDownloadAlert?

    var body: some View {
        ArticleScreen(
            accountType: accountType,
            articleKey: articleKey,
            onOpenProfile: { accountType, userKey in
                onNavigate(.profileUser(accountType, userKey))
            },
            onOpenMedia: { medias, index, preview in
                onNavigate(.mediaRaw(medias, index, preview))
            },
            onShareArticle: { accountType, articleKey, shareUrl in
                onNavigate(.statusShareSheet(accountType, articleKey, shareUrl, nil, nil))
            },
            onDownloadFile: downloadFile
        )
        .alert(item: $downloadAlert) { alert in
            Alert(
                title: Text("save_error"),
                message: Text(verbatim: alert.message),
                dismissButton: .default(Text("Ok"))
            )
        }
    }

    private func downloadFile(_ block: UiArticleBlockFile) {
        Task {
            do {
                _ = try await MacMediaFileExporter.saveRemoteFile(
                    url: block.url,
                    fileName: MediaFileNamePolicy.shared.articleFileName(
                        name: block.name,
                        url: block.url,
                        extensionName: block.extension
                    ),
                    customHeaders: block.customHeaders
                )
            } catch {
                downloadAlert = MacArticleDownloadAlert(message: error.localizedDescription)
            }
        }
    }
}

private struct MacArticleDownloadAlert: Identifiable {
    let id = UUID()
    let message: String
}

extension Route {
    static func fromDeepLink(url: String) -> Route? {
        if let deeplinkRoute = DeeplinkRoute.companion.parse(uri: url) {
            return fromDeepLinkRoute(deeplinkRoute: deeplinkRoute)
        } else {
            return nil
        }
    }

    static func fromDeepLinkRoute(deeplinkRoute: DeeplinkRoute) -> Route? {
        switch onEnum(of: deeplinkRoute) {
        case .relogin(let data):
            .relogin(data.accountKey, data.platformType)
        case .login:
            .serviceSelect
        case .timeline(let data):
            fromTimeline(data)
        case .status(let status):
            fromStatus(status)
        case .compose(let compose):
            fromCompose(compose)
        case .media(let media):
            fromMedia(media)
        case .gallery(let gallery):
            fromGallery(gallery)
        case .profile(let profile):
            fromProfile(profile)
        case .rss(let rss):
            fromRss(rss)
        case .article(let data):
            .article(data.accountType, data.articleKey)
        case .search(let search):
            .search(search.accountType, search.query)
        case .deepLinkAccountPicker(let picker):
            fromAccountPicker(picker)
        case .openLinkDirectly(let data):
            .externalLink(data.url)
        case .editUserList(let data):
            .editUserList(.Specific(accountKey: data.accountKey), data.userKey)
        case .blockUser(let data):
            if let accountKey = data.accountKey {
                .blockUser(.Specific(accountKey: accountKey), data.userKey)
            } else {
                .blockUser(nil, data.userKey)
            }
        case .unblockUser(let data):
            if let accountKey = data.accountKey {
                .unblockUser(.Specific(accountKey: accountKey), data.userKey)
            } else {
                .unblockUser(nil, data.userKey)
            }
        case .muteUser(let data):
            if let accountKey = data.accountKey {
                .muteUser(.Specific(accountKey: accountKey), data.userKey)
            } else {
                .muteUser(nil, data.userKey)
            }
        case .unmuteUser(let data):
            if let accountKey = data.accountKey {
                .unmuteUser(.Specific(accountKey: accountKey), data.userKey)
            } else {
                .unmuteUser(nil, data.userKey)
            }
        case .reportUser(let data):
            if let accountKey = data.accountKey {
                .reportUser(.Specific(accountKey: accountKey), data.userKey)
            } else {
                .reportUser(nil, data.userKey)
            }
        case .directMessage(let data):
            .userDirectMessages(.Specific(accountKey: data.accountKey), data.userKey)
        case .allDirectMessages(let data):
            .allDirectMessages(.Specific(accountKey: data.accountKey))
        case .allLists(let data):
            .allLists(.Specific(accountKey: data.accountKey))
        case .allFeeds(let data):
            .allFeeds(.Specific(accountKey: data.accountKey))
        case .allAntennas(let data):
            .allAntennas(.Specific(accountKey: data.accountKey))
        case .allChannels(let data):
            .allChannels(.Specific(accountKey: data.accountKey))
        default:
                .empty
        }
    }

    private static func fromCompose(_ compose: DeeplinkRoute.Compose) -> Route? {
        switch onEnum(of: compose) {
        case .new:
            .composeNew
        case .quote(let data):
            .composeQuote(AccountType.Specific(accountKey: data.accountKey), data.statusKey)
        case .reply(let data):
            .composeReply(AccountType.Specific(accountKey: data.accountKey), data.statusKey)
        case .vVOReplyComment(let data):
            .composeVVOReplyComment(
                AccountType.Specific(accountKey: data.accountKey),
                data.replyTo,
                data.rootId
            )
        }
    }

    private static func fromMedia(_ media: DeeplinkRoute.Media) -> Route? {
        switch onEnum(of: media) {
        case .image(let data):
            .mediaImage(data.uri, data.previewUrl, data.customHeaders)
        case .statusMedia(let data):
            .mediaStatusMedia(data.accountType, data.statusKey, Int32(data.index), data.preview)
        case .podcast:
            .empty
        }
    }

    private static func fromGallery(_ gallery: DeeplinkRoute.Gallery) -> Route? {
        switch onEnum(of: gallery) {
        case .detail(let data):
            .galleryDetail(data.accountType, data.statusKey)
        }
    }

    private static func fromTimeline(_ timeline: DeeplinkRoute.Timeline) -> Route? {
        switch onEnum(of: timeline) {
        case .xQTDeviceFollow(let data):
            if let tabItem = XQTUiTimelineTabItemHelpers.shared.xqtDeviceFollow(accountType: data.accountType) {
                .timeline(tabItem)
            } else {
                nil
            }
        }
    }

    private static func fromProfile(_ profile: DeeplinkRoute.Profile) -> Route? {
        switch onEnum(of: profile) {
        case .user(let data):
            .profileUser(data.accountType, data.userKey)
        case .userNameWithHost(let data):
            .profileUserNameWithHost(data.accountType, data.userName, data.host)
        }
    }

    private static func fromStatus(_ status: DeeplinkRoute.Status) -> Route? {
        switch onEnum(of: status) {
        case .addReaction(let data):
            .statusAddReaction(data.accountType, data.statusKey)
        case .blueskyReport(let data):
            .statusBlueskyReport(data.accountType, data.statusKey)
        case .deleteConfirm(let data):
            .statusDeleteConfirm(data.accountType, data.statusKey)
        case .detail(let data):
            .statusDetail(data.accountType, data.statusKey)
        case .insight(let data):
            .statusInsight(data.accountType, data.statusKey)
        case .mastodonReport(let data):
            .statusMastodonReport(data.accountType, data.userKey, data.statusKey)
        case .misskeyReport(let data):
            .statusMisskeyReport(data.accountType, data.userKey, data.statusKey)
        case .vVOComment(let data):
            .statusVVOComment(data.accountType, data.commentKey)
        case .vVOStatus(let data):
            .statusVVOStatus(data.accountType, data.statusKey)
        case .shareSheet(let data):
            .statusShareSheet(data.accountType, data.statusKey, data.shareUrl, data.fxShareUrl, data.fixvxShareUrl)
        default:
            .empty
        }
    }

    private static func fromRss(_ rss: DeeplinkRoute.Rss) -> Route? {
        switch onEnum(of: rss) {
        case .detail(let data):
            .rssDetail(data.url, data.descriptionHtml, data.title)
        }
    }

    private static func fromAccountPicker(_ picker: DeeplinkRoute.DeepLinkAccountPicker) -> Route? {
        let routes = picker.data.mapValues { route in
            fromDeepLinkRoute(deeplinkRoute: route) ?? .empty
        }
        return .deepLinkAccountPicker(picker.originalUrl, routes)
    }
}

extension Route {
    var alertTitle: LocalizedStringKey? {
        switch self {
        case .statusDeleteConfirm:
            "delete"
        case .statusMastodonReport:
            "bluesky_report"
        case .blockUser:
            "Block user"
        case .unblockUser:
            "unblock_user_title"
        case .muteUser:
            "Mute user"
        case .unmuteUser:
            "unmute_user_title"
        case .reportUser:
            "report_user_title"
        default:
            nil
        }
    }

    @ViewBuilder
    func alertMessage() -> some View {
        switch self {
        case .statusDeleteConfirm:
            Text("delete_status_alert_message")
        case .statusMastodonReport:
            Text("mastodon_report_status_alert_message")
        case .blockUser:
            Text("block_user_alert_message")
        case .unblockUser:
            Text("unblock_user_description")
        case .muteUser:
            Text("mute_user_alert_message")
        case .unmuteUser:
            Text("unmute_user_description")
        case .reportUser:
            Text("report_user_description")
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    func alertActions() -> some View {
        switch self {
        case .statusDeleteConfirm(let accountType, let statusKey):
            Button("Cancel", role: .cancel) {}
            Button("delete", role: .destructive) {
                DeleteStatusPresenter(accountType: accountType, statusKey: statusKey).models.value.delete()
            }
        case .statusMastodonReport(let accountType, let userKey, let statusKey):
            Button("Cancel", role: .cancel) {}
            Button("bluesky_report", role: .destructive) {
                MastodonReportPresenter(accountType: accountType, userKey: userKey, statusKey: statusKey).models.value.report()
            }
        case .blockUser(let accountType, let userKey):
            Button("Cancel", role: .cancel) {}
            Button("block", role: .destructive) {
                BlockUserPresenter(accountType: accountType, userKey: userKey).models.value.confirm()
            }
        case .unblockUser(let accountType, let userKey):
            Button("Cancel", role: .cancel) {}
            Button("unblock", role: .destructive) {
                UnblockUserPresenter(accountType: accountType, userKey: userKey).models.value.confirm()
            }
        case .muteUser(let accountType, let userKey):
            Button("Cancel", role: .cancel) {}
            Button("mute", role: .destructive) {
                MuteUserPresenter(accountType: accountType, userKey: userKey).models.value.confirm()
            }
        case .unmuteUser(let accountType, let userKey):
            Button("Cancel", role: .cancel) {}
            Button("unmute", role: .destructive) {
                UnmuteUserPresenter(accountType: accountType, userKey: userKey).models.value.confirm()
            }
        case .reportUser:
            Button("Cancel", role: .cancel) {}
            Button("ok_button") {}
        default:
            EmptyView()
        }
    }
}
