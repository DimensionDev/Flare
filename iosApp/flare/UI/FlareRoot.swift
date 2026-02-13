import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

private struct TabKeyKey: EnvironmentKey {
    static let defaultValue: String? = nil
}

private struct IsActiveKey: EnvironmentKey {
    static let defaultValue: Bool = true
}

extension EnvironmentValues {
    var tabKey: String? {
        get { self[TabKeyKey.self] }
        set { self[TabKeyKey.self] = newValue }
    }
    
    var isActive: Bool {
        get { self[IsActiveKey.self] }
        set { self[IsActiveKey.self] = newValue }
    }
}

@available(iOS 18.0, *)
struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @State var selectedTab: String?
    
    var body: some View {
        let tabBinding = Binding<String?>(
            get: { selectedTab },
            set: { newValue in
                if newValue == selectedTab {
                    NotificationCenter.default.post(name: .scrollToTop, object: nil, userInfo: ["tab": newValue ?? ""])
                }
                selectedTab = newValue
            }
        )
        if !activeAccountPresenter.state.user.isLoading {
            StateView(state: homeTabsPresenter.state.tabs) { tabs in
                TabView(selection: tabBinding) {
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
                                .environment(\.tabKey, data.key)
                            } label: {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                                }
                            }
                            .badge(badge)
                        }
                        ForEach(tabs.secondary, id: \.key) { data in
                            Tab(value: data.key) {
                                Router { onNavigate in
                                    data.view(onNavigate: onNavigate)
                                }
                                .environment(\.tabKey, data.key)
                            } label: {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                                }
                            }
                            .tabPlacement(.sidebarOnly)
                        }
                        if let profileRoute = tabs.extraProfileRoute {
                            Tab(value: profileRoute.key) {
                                Router { onNavigate in
                                    profileRoute.view(onNavigate: onNavigate)
                                }
                                .environment(\.tabKey, profileRoute.key)
                            } label: {
                                Label {
                                    TabTitle(title: profileRoute.metaData.title)
                                } icon: {
                                    TabIcon(icon: profileRoute.metaData.icon, accountType: profileRoute.account, iconOnly: true)
                                }
                            }
                            .tabPlacement(.sidebarOnly)
                            .defaultVisibility(.hidden, for: .sidebar)
                        }
                        Tab(value: "settings") {
                            Router { _ in
                                SettingsScreen()
                            }
                            .environment(\.tabKey, "settings")
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
                                .environment(\.tabKey, data.key)
                            } label: {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                                }
                            }
                            .badge(badge)
                        }
                    }
                    if case .success = onEnum(of: activeAccountPresenter.state.user) {
                        Tab(value: "more", role: .search) {
                            SecondaryTabsScreen(tabs: tabs.secondary)
                                .environment(\.tabKey, "more")
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
        let tabBinding = Binding<String?>(
            get: { selectedTab },
            set: { newValue in
                if newValue == selectedTab {
                    NotificationCenter.default.post(name: .scrollToTop, object: nil, userInfo: ["tab": newValue ?? ""])
                }
                selectedTab = newValue
            }
        )
        if !activeAccountPresenter.state.user.isLoading {
            StateView(state: homeTabsPresenter.state.tabs) { tabs in
                TabView(selection: tabBinding) {
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
                            .environment(\.tabKey, data.key)
                            .tabItem {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                                }
                            }
                            .badge(badge)
                            .tag(data.key)
                        }
                        ForEach(tabs.secondary, id: \.key) { data in
                            Router { onNavigate in
                                data.view(onNavigate: onNavigate)
                            }
                            .environment(\.tabKey, data.key)
                            .tabItem {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                                }
                            }
                            .tag(data.key)
                        }
                        if let profileRoute = tabs.extraProfileRoute {
                            Router { onNavigate in
                                profileRoute.view(onNavigate: onNavigate)
                            }
                            .environment(\.tabKey, profileRoute.key)
                            .tabItem {
                                Label {
                                    TabTitle(title: profileRoute.metaData.title)
                                } icon: {
                                    TabIcon(icon: profileRoute.metaData.icon, accountType: profileRoute.account, iconOnly: true)
                                }
                            }
                            .tag(profileRoute.key)
                        }
                        Router { _ in
                            SettingsScreen()
                        }
                        .environment(\.tabKey, "settings")
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
                            .environment(\.tabKey, data.key)
                            .tabItem {
                                Label {
                                    TabTitle(title: data.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                                }
                            }
                            .badge(badge)
                            .tag(data.key)
                        }
                    }
                    if case .success = onEnum(of: activeAccountPresenter.state.user) {
                        SecondaryTabsScreen(tabs: tabs.secondary)
                            .environment(\.tabKey, "more")
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
