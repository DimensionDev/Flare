import Awesome
import Generated
import shared
import SwiftUI

enum HomeTabs: Int, Equatable, Hashable, Identifiable {
    var id: Self { self }
    case timeline = 0
    case notification = 1
    case compose = 2
    case discover = 3
    case profile = 4

    var customizationID: String {
        switch self {
        case .timeline: "home_timeline"
        case .notification: "home_notification"
        case .compose: "home_compose"
        case .discover: "home_discover"
        case .profile: "home_profile"
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
    @ObservedObject var timelineStore: TimelineStore
    @ObservedObject var tabSettingsStore: TabSettingsStore

    init(router: Router,
         accountType: AccountType,
         showSettings: Binding<Bool>,
         showLogin: Binding<Bool>,
         selectedHomeTab: Binding<Int>,
         timelineStore: TimelineStore,
         tabSettingsStore: TabSettingsStore)
    {
        self.router = router
        self.accountType = accountType
        _showSettings = showSettings
        _showLogin = showLogin
        _selectedHomeTab = selectedHomeTab
        self.timelineStore = timelineStore
        self.tabSettingsStore = tabSettingsStore
    }

    var body: some View {
        TimelineScreen(timelineStore: timelineStore)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                HomeAppBar(
                    router: router,
                    accountType: accountType,
                    showSettings: $showSettings,
                    showLogin: $showLogin,
                    selectedHomeTab: $selectedHomeTab,
                    timelineStore: timelineStore,
                    tabSettingsStore: tabSettingsStore
                )
            }
            // appbar 背景色
            .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
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
    @StateObject private var timelineStore: TimelineStore
    @StateObject private var tabSettingsStore: TabSettingsStore

    init(accountType: AccountType) {
        self.accountType = accountType
        let timelineStore = TimelineStore(accountType: accountType)
        _timelineStore = StateObject(wrappedValue: timelineStore)
        _tabSettingsStore = StateObject(wrappedValue: TabSettingsStore(timelineStore: timelineStore))

        if accountType is AccountTypeGuest {
            // 未登录
            timelineStore.currentPresenter = HomeTimelinePresenter(accountType: accountType)
        }
    }

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
                                selectedHomeTab: $selectedHomeTab,
                                timelineStore: timelineStore,
                                tabSettingsStore: tabSettingsStore
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
                        TabItem { _ in
                            ProfileNewScreen(
                                accountType: accountType,
                                userKey: nil,
                                toProfileMedia: { _ in
                                    print("Media tab is now integrated in Profile page")
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
            .background(Colors.Background.swiftUIPrimary)
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
