import SwiftUI
import shared

struct HomeScreen: View {
    @State var viewModel = HomeViewModel()
    @State var showSettings = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        FlareTheme {
            AdativeTabView(
                items: [
                    TabModel(
                        title: String(localized: "home_timeline_title"),
                        image: "house",
                        destination: TabItem(accountType: .active) { _ in
                            HomeTimelineScreen(accountType: AccountTypeActive())
                                .toolbar {
#if os(iOS)
                                    ToolbarItem(placement: .navigation) {
                                        Button {
                                            showSettings = true
                                        } label: {
                                            if case .success(let data) = onEnum(of: viewModel.model.user) {
                                                UserAvatar(data: data.data.avatarUrl, size: 36)
                                            } else {
                                                userAvatarPlaceholder(size: 36)
                                            }
                                        }
                                    }
#endif
                                }
                        }
                    ),
                    TabModel(
                        title: String(localized: "home_notification_title"),
                        image: "bell",
                        destination: TabItem(accountType: .active) { _ in
                            NotificationScreen(accountType: AccountTypeActive())
                                .toolbar {
#if os(iOS)
                                    ToolbarItem(placement: .navigation) {
                                        Button {
                                            showSettings = true
                                        } label: {
                                            if case .success(let data) = onEnum(of: viewModel.model.user) {
                                                UserAvatar(data: data.data.avatarUrl, size: 36)
                                            } else {
                                                userAvatarPlaceholder(size: 36)
                                            }
                                        }
                                    }
#endif
                                }
                        }
                    ),
                    TabModel(
                        title: String(localized: "home_discover_title"),
                        image: "magnifyingglass",
                        destination: TabItem(accountType: .active) { router in
                            DiscoverScreen(
                                accountType: AccountTypeActive(),
                                onUserClicked: { user in
                                    router.navigate(to: .profileMedia(accountType: .active, userKey: user.userKey.description()))
                                }
                            )
                        }
                    ),
                    TabModel(
                        title: String(localized: "home_profile_title"),
                        image: "person.circle",
                        destination: TabItem(accountType: .active) { router in
                            ProfileScreen(
                                accountType: AccountTypeActive(),
                                userKey: nil,
                                toProfileMedia: { userKey in
                                    router.navigate(to: .profileMedia(accountType: .active, userKey: userKey.description()))
                                }
                            )
                        }
                    )
                ],
                secondaryItems: [
                ],
                leading: VStack {
                    Button {
                        showSettings = true
                    } label: {
                        AccountItem(userState: viewModel.model.user)
                        Spacer()
                        Image(systemName: "gear")
                            .opacity(0.5)
                    }
#if os(iOS)
                    .padding([.horizontal, .top])
#endif
                    .buttonStyle(.plain)
                }
                    .listRowInsets(EdgeInsets())
            )
        }
        .sheet(isPresented: $showSettings, content: {
            SettingsScreen()
#if os(macOS)
                .frame(minWidth: 600, minHeight: 400)
#endif
        })
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class HomeViewModel: MoleculeViewModelBase<UserState, ActiveAccountPresenter> {
}

struct TabItem<Content: View>: View {
    let accountType: SwiftAccountType
    @State var showCompose = false
    @State var statusEvent = StatusEvent()
    @State var router = Router<TabDestination>()
    let content: (Router<TabDestination>) -> Content
    var body: some View {
        NavigationStack(path: $router.navPath) {
            content(router)
                .withTabRouter(router: router)
        }
        .sheet(isPresented: $showCompose, content: {
            NavigationStack {
                ComposeScreen(onBack: {
                    showCompose = false
                }, accountType: accountType.toKotlin())
            }
#if os(macOS)
            .frame(minWidth: 600, minHeight: 400)
#endif
        })
        .sheet(isPresented: Binding(
            get: {statusEvent.composeStatus != nil},
            set: { value in
                if !value {
                    statusEvent.composeStatus = nil
                }
            }
        )
        ) {
            if let status = statusEvent.composeStatus {
                NavigationStack {
                    ComposeScreen(onBack: {
                        statusEvent.composeStatus = nil
                    }, accountType: accountType.toKotlin(), status: status)
                }
#if os(macOS)
                .frame(minWidth: 500, minHeight: 400)
#endif
            }
        }
#if os(iOS)
        .fullScreenCover(
            isPresented: Binding(get: { statusEvent.mediaClickData != nil }, set: { value in if !value { statusEvent.mediaClickData = nil }}),
            onDismiss: { statusEvent.mediaClickData = nil }
        ) {
            ZStack {
                Color.black.ignoresSafeArea()
                if let data = statusEvent.mediaClickData {
                    StatusMediaScreen(accountType: accountType.toKotlin(), statusKey: data.statusKey, index: data.index, dismiss: { statusEvent.mediaClickData = nil })
                }
            }
        }
#endif
        .environment(\.openURL, OpenURLAction { url in
            if let event = AppDeepLink.shared.parse(url: url.absoluteString) {
                switch onEnum(of: event) {
                case .profile(let data):
                    router.navigate(to: .profile(accountType: accountType, userKey: data.userKey.description()))
                case .profileWithNameAndHost(let data):
                    router.navigate(to: .profileWithUserNameAndHost(accountType: accountType, userName: data.userName, host: data.host))
                case .search(let data):
                    router.navigate(to: .search(accountType: accountType, query: data.keyword))
                case .statusDetail(let data):
                    router.navigate(to: .statusDetail(accountType: accountType, statusKey: data.statusKey.description()))
                case .compose:
                    showCompose = true
                }
                return .handled
            } else {
                return .systemAction
            }
        })
        .environment(statusEvent)
    }
}

@Observable
class StatusEvent: MastodonStatusEvent, MisskeyStatusEvent, BlueskyStatusEvent, XQTStatusEvent {
    let accountRepository = KoinHelper.shared.accountRepository
    var composeStatus: ComposeStatus?
    var mediaClickData: MediaClickData?
    func onReplyClick(status: UiStatus.Mastodon) {
        self.composeStatus = ComposeStatusReply(statusKey: status.statusKey)
    }
    func onReblogClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.reblog(status: status)
            }
        }
    }
    func onLikeClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.like(status: status)
            }
        }
    }
    func onBookmarkClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.bookmark(status: status)
            }
        }
    }
    func onMediaClick(statusKey: MicroBlogKey, index: Int, preview: String?) {
        self.mediaClickData = MediaClickData(statusKey: statusKey, index: index, preview: preview)
    }
    func onReportClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.report(userKey: status.user
                    .userKey, statusKey: status.statusKey)
            }
        }
    }
    func onReactionClick(data: UiStatus.Misskey, reaction: UiStatus.MisskeyEmojiReaction) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountMisskey {
                try? await account.dataSource.react(status: data, reaction: reaction.name)
            }
        }
    }
    func onReplyClick(data: UiStatus.Misskey) {
        composeStatus = ComposeStatusReply(statusKey: data.statusKey)
    }
    func onReblogClick(data: UiStatus.Misskey) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountMisskey {
                try? await account.dataSource.renote(status: data)
            }
        }
    }
    func onQuoteClick(data: UiStatus.Misskey) {
        composeStatus = ComposeStatusQuote(statusKey: data.statusKey)
    }
    func onAddReactionClick(data: UiStatus.Misskey) {
    }
    func onReportClick(data: UiStatus.Misskey) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountMisskey {
                try? await account.dataSource.report(userKey: data.user.userKey, statusKey: data.statusKey)
            }
        }
    }
    func onReplyClick(data: UiStatus.Bluesky) {
        composeStatus = ComposeStatusReply(statusKey: data.statusKey)
    }
    func onReblogClick(data: UiStatus.Bluesky) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountBluesky {
                try? await account.dataSource.reblog(data: data)
            }
        }
    }
    func onQuoteClick(data: UiStatus.Bluesky) {
        composeStatus = ComposeStatusQuote(statusKey: data.statusKey)
    }
    func onLikeClick(data: UiStatus.Bluesky) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountBluesky {
                try? await account.dataSource.like(data: data)
            }
        }
    }
    func onReportClick(data: UiStatus.Bluesky, reason: BlueskyReportStatusStateReportReason) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountBluesky {
                try? await account.dataSource.report(data: data, reason: reason)
            }
        }
    }
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey) {
        Task {
            if let account = accountRepository.get(accountKey: accountKey) {
                switch onEnum(of: account) {
                case .bluesky(let bluesky):
                    try? await bluesky.dataSource.deleteStatus(statusKey: statusKey)
                case .mastodon(let mastodon):
                    try? await mastodon.dataSource.deleteStatus(statusKey: statusKey)
                case .misskey(let misskey):
                    try? await misskey.dataSource.deleteStatus(statusKey: statusKey)
                case .xQT(let xqt):
                    try? await xqt.dataSource.deleteStatus(statusKey: statusKey)
                case .guest(_):
                    ()
                }
            }
        }
    }
    func onReplyClick(status: UiStatus.XQT) {
        composeStatus = ComposeStatusReply(statusKey: status.statusKey)
    }
    func onReblogClick(status: UiStatus.XQT) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountXQT {
                try? await account.dataSource.retweet(status: status)
            }
        }
    }
    func onLikeClick(status: UiStatus.XQT) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountXQT {
                try? await account.dataSource.like(status: status)
            }
        }
    }
    func onBookmarkClick(status: UiStatus.XQT) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountXQT {
                try? await account.dataSource.bookmark(status: status)
            }
        }
    }
    func onReportClick(status: UiStatus.XQT) {
    }
    func onQuoteClick(status: UiStatus.XQT) {
        composeStatus = ComposeStatusQuote(statusKey: status.statusKey)
    }
}

class EmptyStatusEvent: MastodonStatusEvent, MisskeyStatusEvent, BlueskyStatusEvent, XQTStatusEvent {
    static let shared = EmptyStatusEvent()
    private init() {
    }
    func onReplyClick(status: UiStatus.Mastodon) {
    }
    func onReblogClick(status: UiStatus.Mastodon) {
    }
    func onLikeClick(status: UiStatus.Mastodon) {
    }
    func onBookmarkClick(status: UiStatus.Mastodon) {
    }
    func onMediaClick(statusKey: MicroBlogKey, index: Int, preview: String?) {
    }
    func onReportClick(status: UiStatus.Mastodon) {
    }
    func onReactionClick(data: UiStatus.Misskey, reaction: UiStatus.MisskeyEmojiReaction) {
    }
    func onReplyClick(data: UiStatus.Misskey) {
    }
    func onReblogClick(data: UiStatus.Misskey) {
    }
    func onQuoteClick(data: UiStatus.Misskey) {
    }
    func onAddReactionClick(data: UiStatus.Misskey) {
    }
    func onReportClick(data: UiStatus.Misskey) {
    }
    func onReplyClick(data: UiStatus.Bluesky) {
    }
    func onReblogClick(data: UiStatus.Bluesky) {
    }
    func onQuoteClick(data: UiStatus.Bluesky) {
    }
    func onLikeClick(data: UiStatus.Bluesky) {
    }
    func onReportClick(data: UiStatus.Bluesky, reason: BlueskyReportStatusStateReportReason) {
    }
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey) {
    }
    func onReplyClick(status: UiStatus.XQT) {
    }
    func onReblogClick(status: UiStatus.XQT) {
    }
    func onLikeClick(status: UiStatus.XQT) {
    }
    func onBookmarkClick(status: UiStatus.XQT) {
    }
    func onReportClick(status: UiStatus.XQT) {
    }
    func onQuoteClick(status: UiStatus.XQT) {
    }
}

struct MediaClickData {
    let statusKey: MicroBlogKey
    let index: Int
    let preview: String?
}
