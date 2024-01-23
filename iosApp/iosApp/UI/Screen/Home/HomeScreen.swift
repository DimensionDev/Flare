import SwiftUI
import shared

struct HomeScreen: View {
    @State var viewModel = HomeViewModel()
    @State var showSettings = false
    @State var showCompose = false
    @State var statusEvent = StatusEvent()
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        FlareTheme {
            AdativeTabView(
                items: [
                    TabModel(
                        title: "Home",
                        image: "house",
                        destination: TabItem { _ in
                            HomeTimelineScreen()
#if !os(macOS)
                                .if(horizontalSizeClass != .compact, transform: { view in
                                    view
                                        .toolbar(.hidden)
                                })
                                    .if(horizontalSizeClass == .compact, transform: { view in
                                        view
                                            .navigationBarTitleDisplayMode(.inline)
                                            .toolbar {
                                                ToolbarItem(placement: .principal) {
                                                    Text("Flare")
                                                }
                                                ToolbarItem(placement: .primaryAction) {
                                                    Button(action: {
                                                        showCompose = true
                                                    }, label: {
                                                        Image(systemName: "square.and.pencil")
                                                    })
                                                }
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
                                            }
                                    })
                                    #endif
                        }
                    ),
                    TabModel(
                        title: "Notification",
                        image: "bell",
                        destination: TabItem { _ in
                            NotificationScreen()
                        }
                    ),
                    TabModel(
                        title: "Discover",
                        image: "magnifyingglass",
                        destination: TabItem { _ in
                            DiscoverScreen()
                        }
                    ),
                    TabModel(
                        title: "Me",
                        image: "person.circle",
                        destination: TabItem { router in
                            ProfileScreen(
                                userKey: nil,
                                toProfileMedia: { userKey in
                                    router.navigate(to: .profileMedia(userKey: userKey.description()))
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
#if !os(macOS)
                    .padding([.horizontal, .top])
#endif
                    .buttonStyle(.plain)
                    Button(action: {
                        showCompose = true
                    }, label: {
                        HStack {
                            Image(systemName: "square.and.pencil")
                            Text("Write a post")
                            Spacer()
                        }
                        .padding(4)
                    })
                    .buttonStyle(.borderedProminent)
                }
                    .listRowInsets(EdgeInsets())
            )
        }
        .sheet(isPresented: $showCompose, content: {
            NavigationStack {
                ComposeScreen(onBack: {
                    showCompose = false
                })
            }
#if os(macOS)
            .frame(minWidth: 600, minHeight: 400)
#endif
        })
        .sheet(isPresented: $showSettings, content: {
            SettingsScreen()
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
                    }, status: status)
                }
#if os(macOS)
                .frame(minWidth: 500, minHeight: 400)
#endif
            }
        }
        .activateViewModel(viewModel: viewModel)
        .environment(statusEvent)
    }
}

@Observable
class HomeViewModel: MoleculeViewModelBase<ActiveAccountState, ActiveAccountPresenter> {
}

struct TabItem<Content: View>: View {
    @State var router = Router<TabDestination>()
    let content: (Router<TabDestination>) -> Content
    var body: some View {
        NavigationStack(path: $router.navPath) {
            content(router)
                .withTabRouter(router: router)
        }.environment(\.openURL, OpenURLAction { url in
            if let event = AppDeepLink.shared.parse(url: url.absoluteString) {
                switch onEnum(of: event) {
                case .profile(let data):
                    router.navigate(to: .profile(userKey: data.userKey.description()))
                case .profileWithNameAndHost(let data):
                    router.navigate(to: .profileWithUserNameAndHost(userName: data.userName, host: data.host))
                case .search(let data):
                    router.navigate(to: .search(query: data.keyword))
                case .statusDetail(let data):
                    router.navigate(to: .statusDetail(statusKey: data.statusKey.description()))
                }
                return .handled
            } else {
                return .systemAction
            }
        })
    }
}

@Observable
class StatusEvent: MastodonStatusEvent, MisskeyStatusEvent, BlueskyStatusEvent, XQTStatusEvent {
    let accountRepository = KoinHelper.shared.accountRepository
    var composeStatus: ComposeStatus?
    func onReplyClick(status: UiStatus.Mastodon) {
        composeStatus = ComposeStatusReply(statusKey: status.statusKey)
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
    func onMediaClick(media: UiMedia) {
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
    func onMediaClick(media: UiMedia) {
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
}
