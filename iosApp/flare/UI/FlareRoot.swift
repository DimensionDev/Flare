import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

@available(iOS 18.0, *)
struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @State var selectedTab: String?
    
    var body: some View {
        if !activeAccountPresenter.state.user.isLoading {
            StateView(state: homeTabsPresenter.state.tabs) { tabs in
                TabView(selection: $selectedTab) {
                    if horizontalSizeClass == .regular {
                        ForEach(tabs.primary, id: \.key) { data in
                            let badge = if data is NotificationTabItem || data is AllNotificationTabItem {
                                Int(notificationBadgePresenter.state.count)
                            } else {
                                0
                            }
                            Tab(value: data.key) {
                                Router { onNavigate in
                                    data.view(onNavigate: onNavigate)
                                }
                            } label: {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account)
                                }
                            }
                            .badge(badge)
                        }
                        ForEach(tabs.secondary, id: \.key) { data in
                            Tab(value: data.key) {
                                Router { onNavigate in
                                    data.view(onNavigate: onNavigate)
                                }
                            } label: {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account)
                                }
                            }
                            .tabPlacement(.sidebarOnly)
                        }
                        if let profileRoute = tabs.extraProfileRoute {
                            Tab(value: profileRoute.key) {
                                Router { onNavigate in
                                    profileRoute.view(onNavigate: onNavigate)
                                }
                            } label: {
                                Label {
                                    TabTitle(title: profileRoute.metaData.title)
                                } icon: {
                                    TabIcon(icon: profileRoute.metaData.icon, accountType: profileRoute.account)
                                }
                            }
                            .tabPlacement(.sidebarOnly)
                            .defaultVisibility(.hidden, for: .sidebar)
                        }
                        Tab(value: "settings") {
                            Router { _ in
                                SettingsScreen()
                            }
                        } label: {
                            Label {
                                Text("settings_title")
                            } icon: {
                                Image("fa-gear")
                            }
                        }
                        .tabPlacement(.sidebarOnly)
                    } else {
                        ForEach(tabs.primary, id: \.key) { data in
                            let badge = if data is NotificationTabItem || data is AllNotificationTabItem {
                                Int(notificationBadgePresenter.state.count)
                            } else {
                                0
                            }
                            Tab(value: data.key) {
                                Router { onNavigate in
                                    data.view(onNavigate: onNavigate)
                                }
                            } label: {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account)
                                }
                            }
                            .badge(badge)
                        }
                    }
                    if case .success = onEnum(of: activeAccountPresenter.state.user) {
                        Tab(value: "more", role: .search) {
                            SecondaryTabsScreen(tabs: tabs.secondary)
                        } label: {
                            Label {
                                Text("More")
                            } icon: {
                                Image("fa-ellipsis")
                            }
                        }
                    }
                }
                .tabViewStyle(.sidebarAdaptable)
                .backport
                .tabBarMinimizeBehavior(.onScrollDown)
                .tabViewSidebarHeader {
                    if let profileRoute = tabs.extraProfileRoute {
                        StateView(state: activeAccountPresenter.state.user) { user in
                            UserCompatView(data: user)
                                .onTapGesture {
                                    selectedTab = profileRoute.key
                                }
                        }
                    }
                }
                .background(Color(.systemGroupedBackground))
            }
        } else {
            SplashScreen()
        }
    }
}

struct BackportFlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @State var selectedTab: String?
    
    var body: some View {
        if !activeAccountPresenter.state.user.isLoading {
            StateView(state: homeTabsPresenter.state.tabs) { tabs in
                TabView(selection: $selectedTab) {
                    if horizontalSizeClass == .regular {
                        ForEach(tabs.primary, id: \.key) { data in
                            let badge = if data is NotificationTabItem || data is AllNotificationTabItem {
                                Int(notificationBadgePresenter.state.count)
                            } else {
                                0
                            }
                            Router { onNavigate in
                                data.view(onNavigate: onNavigate)
                            }
                            .tabItem {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account)
                                }
                            }
                            .badge(badge)
                            .tag(data.key)
                        }
                        ForEach(tabs.secondary, id: \.key) { data in
                            Router { onNavigate in
                                data.view(onNavigate: onNavigate)
                            }
                            .tabItem {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account)
                                }
                            }
                            .tag(data.key)
                        }
                        if let profileRoute = tabs.extraProfileRoute {
                            Router { onNavigate in
                                profileRoute.view(onNavigate: onNavigate)
                            }
                            .tabItem {
                                Label {
                                    TabTitle(title: profileRoute.metaData.title)
                                } icon: {
                                    TabIcon(icon: profileRoute.metaData.icon, accountType: profileRoute.account)
                                }
                            }
                            .tag(profileRoute.key)
                        }
                        Router { _ in
                            SettingsScreen()
                        }
                        .tabItem {
                            Label {
                                Text("settings_title")
                            } icon: {
                                Image("fa-gear")
                            }
                        }
                        .tag("settings")
                    } else {
                        ForEach(tabs.primary, id: \.key) { data in
                            let badge = if data is NotificationTabItem || data is AllNotificationTabItem {
                                Int(notificationBadgePresenter.state.count)
                            } else {
                                0
                            }
                            Router { onNavigate in
                                data.view(onNavigate: onNavigate)
                            }
                            .tabItem {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account)
                                }
                            }
                            .badge(badge)
                            .tag(data.key)
                        }
                    }
                    if case .success = onEnum(of: activeAccountPresenter.state.user) {
                        SecondaryTabsScreen(tabs: tabs.secondary)
                            .tabItem {
                                Label {
                                    Text("More")
                                } icon: {
                                    Image("fa-ellipsis")
                                }
                            }
                            .tag("more")
                    }
                }
                .background(Color(.systemGroupedBackground))
            }
        } else {
            SplashScreen()
        }
    }
}
