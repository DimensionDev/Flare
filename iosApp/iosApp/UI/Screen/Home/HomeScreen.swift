import SwiftUI
import shared

struct HomeScreen: View {
    let presenter = ActiveAccountPresenter()
    @State var showSettings = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        Observing(presenter.models) { userState in
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
                                                if case .success(let data) = onEnum(of: userState.user) {
                                                    UserAvatar(data: data.data.avatar, size: 36)
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
                                                if case .success(let data) = onEnum(of: userState.user) {
                                                    UserAvatar(data: data.data.avatar, size: 36)
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
                                        router.navigate(to: .profileMedia(accountType: .active, userKey: user.key.description()))
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
                            AccountItem(userState: userState.user)
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
        }
        .sheet(isPresented: $showSettings, content: {
            SettingsScreen()
#if os(macOS)
                .frame(minWidth: 600, minHeight: 400)
#endif
        })
    }
}


struct TabItem<Content: View>: View {
    let accountType: SwiftAccountType
    @State var showCompose = false
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
//        .sheet(isPresented: Binding(
//            get: {statusEvent.composeStatus != nil},
//            set: { value in
//                if !value {
//                    statusEvent.composeStatus = nil
//                }
//            }
//        )
//        ) {
//            if let status = statusEvent.composeStatus {
//                NavigationStack {
//                    ComposeScreen(onBack: {
//                        statusEvent.composeStatus = nil
//                    }, accountType: accountType.toKotlin(), status: status)
//                }
//#if os(macOS)
//                .frame(minWidth: 500, minHeight: 400)
//#endif
//            }
//        }
//#if os(iOS)
//        .fullScreenCover(
//            isPresented: Binding(get: { statusEvent.mediaClickData != nil }, set: { value in if !value { statusEvent.mediaClickData = nil }}),
//            onDismiss: { statusEvent.mediaClickData = nil }
//        ) {
//            ZStack {
//                Color.black.ignoresSafeArea()
//                if let data = statusEvent.mediaClickData {
//                    StatusMediaScreen(accountType: accountType.toKotlin(), statusKey: data.statusKey, index: data.index, dismiss: { statusEvent.mediaClickData = nil })
//                }
//            }
//        }
//#endif
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
                case .rawImage(let data): break
                }
                return .handled
            } else {
                return .systemAction
            }
        })
    }
}
struct MediaClickData {
    let statusKey: MicroBlogKey
    let index: Int
    let preview: String?
}
