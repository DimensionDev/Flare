import SwiftUI
import shared
import Awesome

enum HomeTabs: Int, Equatable, Hashable, Identifiable {
    var id: Self { self }
    case timeline = 0
    case notification = 1
    case compose = 2
    case discover = 3
    case profile = 4
    
    var customizationID: String {
        switch self {
        case .timeline: return "home_timeline"
        case .notification: return "home_notification"
        case .compose: return "home_compose"
        case .discover: return "home_discover"
        case .profile: return "home_profile"
        }
    }
}

// 新建一个视图来处理首页的内容
struct HomeTimelineView: View {
    let router: Router
    let accountType: AccountType
    @Binding var showSettings: Bool
    @Binding var showLogin: Bool
    @Binding var selectedHomeTab: Int
    @State private var currentPresenter: TimelinePresenter?
    @State private var homeTimelinePresenter: HomeTimelinePresenter
    
    init(router: Router, accountType: AccountType, showSettings: Binding<Bool>, showLogin: Binding<Bool>, selectedHomeTab: Binding<Int>) {
        self.router = router
        self.accountType = accountType
        self._showSettings = showSettings
        self._showLogin = showLogin
        self._selectedHomeTab = selectedHomeTab
        self._homeTimelinePresenter = State(initialValue: HomeTimelinePresenter(accountType: accountType))
    }
    
    var body: some View {
        let presenter = currentPresenter ?? homeTimelinePresenter
        TimelineScreen(presenter: presenter)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                HomeAppBar(
                    router: router,
                    accountType: accountType,
                    showSettings: $showSettings,
                    showLogin: $showLogin,
                    selectedHomeTab: $selectedHomeTab,
                    currentPresenter: $currentPresenter
                )
            }
    }
}

struct HomeContent: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @AppStorage("homeSidebarCustomizations") var tabViewCustomization: TabViewCustomization
    @State private var selectedTab: HomeTabs = .timeline
    let accountType: AccountType
    @State var showSettings = false
    @State var showLogin = false
    @State var showCompose = false
    @State private var selectedHomeTab = 0
    
    var body: some View {
        FlareTheme {
            TabView(selection: $selectedTab) {
                // 首页 Tab
                Tab(value: .timeline) {
                    TabItem { router in
                        NavigationView {
                            HomeTimelineView(
                                router: router,
                                accountType: accountType,
                                showSettings: $showSettings,
                                showLogin: $showLogin,
                                selectedHomeTab: $selectedHomeTab
                            )
                        }
                    }
                } label: {
                    Label {
                        Text("")
                    } icon: {
                        (selectedTab == .timeline ? Asset.Tab.feedActivie.swiftUIImage : Asset.Tab.feedInactive.swiftUIImage)
                            .foregroundColor(.init(.accentColor))
                    }
                }
                .customizationID(HomeTabs.timeline.customizationID)
                
                if !(accountType is AccountTypeGuest) {
                    Tab(value: .notification) {
                        TabItem { _ in
                            NotificationScreen(accountType: accountType)
                        }
                    } label: {
                        Label {
                            Text("")
                        } icon: {
                            (selectedTab == .notification ? Asset.Tab.trendingActive.swiftUIImage : Asset.Tab.trendingInactive.swiftUIImage)
                                .foregroundColor(.init(.accentColor))
                        }
                    }
                    .customizationID(HomeTabs.notification.customizationID)
                }
                
                if !(accountType is AccountTypeGuest) {
                    Tab(value: .compose) {
                        TabItem { router in
                            ComposeTabView(
                                router: router,
                                accountType: accountType,
                                selectedTab: $selectedTab
                            )
                        }
                    } label: {
                        Label {
                            Text("")
                        } icon: {
                            Asset.Tab.compose.swiftUIImage
                                .foregroundColor(.init(.accentColor))
                        }
                    }
                    .customizationID(HomeTabs.compose.customizationID)
                }
                
                Tab(value: .discover) {
                    TabItem { router in
                        DiscoverScreen(
                            accountType: accountType,
                            onUserClicked: { user in
                                router.navigate(to: AppleRoute.Profile(accountType: accountType, userKey: user.key))
                            }
                        )
                    }
                } label: {
                    Label {
                        Text("")
                    } icon: {
                        (selectedTab == .discover ? Asset.Tab.searchActive.swiftUIImage : Asset.Tab.searchInactive.swiftUIImage)
                            .foregroundColor(.init(.accentColor))
                    }
                }
                .customizationID(HomeTabs.discover.customizationID)
                
                if !(accountType is AccountTypeGuest) {
                    Tab(value: .profile) {
                        TabItem { router in
                            ProfileScreen(
                                accountType: accountType,
                                userKey: nil,
                                toProfileMedia: { key in
                                    router.navigate(to: AppleRoute.ProfileMedia(accountType: accountType, userKey: key))
                                }
                            )
                        }
                    } label: {
                        Label {
                            Text("")
                        } icon: {
                            (selectedTab == .profile ? Asset.Tab.profileActive.swiftUIImage : Asset.Tab.profileInactive.swiftUIImage)
                                .foregroundColor(.init(.accentColor))
                        }
                    }
                    .customizationID(HomeTabs.profile.customizationID)
                }
            }
            .tabViewStyle(.automatic)
            .tabViewCustomization($tabViewCustomization)
        }
        .sheet(isPresented: $showLogin) {
            ServiceSelectScreen(toHome: {
                showLogin = false
            })
#if os(macOS)
            .frame(minWidth: 600, minHeight: 400)
#endif
        }
        .sheet(isPresented: $showSettings) {
            SettingsScreen()
        }
    }
}

struct ComposeTabView: View {
    let router: Router
    let accountType: AccountType
    @Binding var selectedTab: HomeTabs
    
    var body: some View {
        Color.clear
            .onAppear {
                router.navigate(to: AppleRoute.ComposeNew(accountType: accountType))
                selectedTab = .timeline
            }
    }
}
