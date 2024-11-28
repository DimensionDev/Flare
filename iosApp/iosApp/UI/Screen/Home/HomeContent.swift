import SwiftUI
import shared
import Awesome

struct HomeContent: View {
    // horizontal window size
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @AppStorage("homeSidebarCustomizations") var tabViewCustomization: TabViewCustomization
    @State private var selectedTab: HomeTabs = .timeline
    let accountType: AccountType
    @State var showSettings = false
    @State var showLogin = false
    @State var showCompose = false
    var body: some View {
            FlareTheme {
                TabView(selection: $selectedTab) {
                    Tab(value: .timeline) {
                        TabItem { router in
                            HomeTimelineScreen(
                                accountType: accountType
                            )
                            .toolbar {
                                HomeAppBar(
                                    router: router,
                                    accountType: accountType,
                                    showSettings: $showSettings,
                                    showLogin: $showLogin
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
                    Tab(value: .discover, role: .search) {
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
                .tabViewStyle(.sidebarAdaptable)
                .tabViewCustomization($tabViewCustomization)
            }
            .sheet(isPresented: $showLogin, content: {
                ServiceSelectScreen(toHome: {
                    showLogin = false
                })
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

enum HomeTabs: Equatable, Hashable, Identifiable {
    var id: Self { self }
    case timeline
    case notification
    case compose
    case discover
    case profile
    case list(String)
}

extension HomeTabs {
    var customizationID: String {
        switch self {
        case .timeline: return "home_timeline"
        case .notification: return "home_notification"
        case .compose: return "home_compose"
        case .discover: return "home_discover"
        case .profile: return "home_profile"
        case .list(let id): return "home_list_\(id)"
        }
    }
}
