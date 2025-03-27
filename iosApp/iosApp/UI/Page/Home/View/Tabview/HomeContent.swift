import Awesome
import Generated
import os.log
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

struct HomeContent: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @AppStorage("homeSidebarCustomizations") var tabViewCustomization: TabViewCustomization
    @State private var selectedTab: HomeTabs = .timeline
    let accountType: AccountType
    @State var showSettings = false
    @State var showLogin = false
    @State var showCompose = false
    @State private var selectedHomeTab = 0
    @StateObject private var appState = FlareAppState()

    // 使用来自FlareMenuContainer的router
    @EnvironmentObject private var router: FlareRouter

    var body: some View {
        let routerId = ObjectIdentifier(router)
        os_log("[HomeContent] Using router: %{public}@, depth: %{public}d",
               log: .default, type: .debug,
               String(describing: routerId),
               router.navigationDepth)

        return FlareTheme {
            TabView(selection: $selectedTab) {
                // 首页 Tab - 使用新的SwiftUI实现
                Tab(value: .timeline) {
                    FlareTabItem(router: router, tabType: .timeline) { _ in
                        // HomeTabScreen(accountType: accountType)
                        HomeTabScreenSwiftUI(accountType: accountType)
                    }
                    .environmentObject(appState)
                } label: {
                    Label {
                        Text("")
                    } icon: {
                        (selectedTab == .timeline ? Asset.Tab.feedActivie.swiftUIImage : Asset.Tab.feedInactive.swiftUIImage)
                            .foregroundColor(.init(.accentColor))
                    }
                }
                .customizationID(HomeTabs.timeline.customizationID)

                // 通知 Tab
                if !(accountType is AccountTypeGuest) {
                    Tab(value: .notification) {
                        FlareTabItem(router: router, tabType: .notification) { _ in
                            NotificationTabScreen(accountType: accountType)
                        }
                        .environmentObject(appState)
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

                // 发布 Tab
                if !(accountType is AccountTypeGuest) {
                    Tab(value: .compose) {
                        FlareTabItem(router: router, tabType: .compose) { tabRouter in
                            ComposeTabView(
                                router: tabRouter,
                                accountType: accountType,
                                selectedTab: $selectedTab
                            )
                        }
                        .environmentObject(appState)
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

                // 发现 Tab
                Tab(value: .discover) {
                    FlareTabItem(router: router, tabType: .discover) { tabRouter in
                        DiscoverTabScreen(
                            accountType: accountType,
                            onUserClicked: { user in
                                tabRouter.navigate(to: .profile(accountType: accountType, userKey: user.key))
                            }
                        )
                    }
                    .environmentObject(appState)
                } label: {
                    Label {
                        Text("")
                    } icon: {
                        (selectedTab == .discover ? Asset.Tab.searchActive.swiftUIImage : Asset.Tab.searchInactive.swiftUIImage)
                            .foregroundColor(.init(.accentColor))
                    }
                }
                .customizationID(HomeTabs.discover.customizationID)

                // 个人资料 Tab
                if !(accountType is AccountTypeGuest) {
                    Tab(value: .profile) {
                        FlareTabItem(router: router, tabType: .profile) { _ in
                            ProfileTabScreen(
                                accountType: accountType,
                                userKey: nil,
                                toProfileMedia: { _ in
                                    print("Media tab is now integrated in Profile page")
                                }
                            )
                        }
                        .environmentObject(appState)
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
    let router: FlareRouter
    let accountType: AccountType
    @Binding var selectedTab: HomeTabs

    var body: some View {
        Color.clear
            .onAppear {
                router.navigate(to: .compose(accountType: accountType, status: nil))
                selectedTab = .timeline
            }
    }
}
