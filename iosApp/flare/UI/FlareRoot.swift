import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

@available(iOS 18.0, *)
struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.globalAppearance) private var globalAppearance
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var secondaryTabsPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @StateObject private var aiAgentEnabledPresenter = KotlinPresenter(presenter: AiAgentEnabledPresenter())
    @State var selectedTab: String?
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            let items = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
            TabView(selection: $selectedTab) {
                ForEach(items, id: \.name) { tab in
                    Tab(value: homeTabKey(tab), role: homeTabRoute(tab) == .discover ? .some(.search) : .none) {
                        Router { onNavigate in
                            homeTabRoute(tab).view(onNavigate: onNavigate, goBack: {})
                        }
                    } label: {
                        Label {
                            Text(homeTabTitle(tab))
                        } icon: {
                            Image(homeTabIconName(tab))
                        }
                        .adaptiveLabelStyle(globalAppearance.showBottomBarLabels || horizontalSizeClass == .regular)
                    }
                    .badge(homeTabRoute(tab) == .notification ? Int(notificationBadgePresenter.state.count) : 0)
                }
                if horizontalSizeClass == .regular {
                    if case .success(let data) = onEnum(of: secondaryTabsPresenter.state.items) {
                        let items = data.data.cast(SecondaryTabsPresenter.Item.self)
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            TabSection {
                                ForEach(item.tabs, id: \.self) { tab in
                                    if let route = route(for: tab) {
                                        secondarySidebarShortcut(tab, route: route)
                                    }
                                }
                            } header: {
                                StateView(state: item.user) { user in
                                    UserOnelineView(data: user)
                                } errorContent: { _ in
                                    Text("Accounts")
                                } loadingContent: {
                                    Text("Accounts")
                                }
                            }
                            .tabPlacement(.sidebarOnly)
                        }
                    }
                    ForEach(SecondarySidebarStaticRoute.allCases.filter { route in
                        route != .agentHistory || aiAgentEnabledPresenter.state.enabled
                    }, id: \.self) { route in
                        secondarySidebarStaticRoute(route)
                    }
                }
            }
            .tabViewStyle(.sidebarAdaptable)
            .backport
            .tabBarMinimizeBehavior(.onScrollDown)
            .background(Color(.systemGroupedBackground))
        } loadingContent: {
            SplashScreen()
        }
    }

    @TabContentBuilder<String?>
    private func secondarySidebarShortcut(_ tab: SecondaryTabsPresenter.Tab, route: Route) -> some TabContent<String?> {
        Tab(value: "secondary:\(tab.hashValue)") {
            SidebarRouteScreen(route: route)
        } label: {
            Label {
                Text(tab.title.text)
            } icon: {
                Image(tab.icon.imageName)
            }
        }
        .tabPlacement(.sidebarOnly)
    }

    @TabContentBuilder<String?>
    private func secondarySidebarStaticRoute(_ route: SecondarySidebarStaticRoute) -> some TabContent<String?> {
        Tab(value: route.selectionValue) {
            SidebarRouteScreen(route: route.route)
        } label: {
            Label {
                Text(route.title)
            } icon: {
                Image(route.iconName)
            }
        }
        .tabPlacement(.sidebarOnly)
    }
}
private extension View {
    @ViewBuilder
    func adaptiveLabelStyle(_ showLabel: Bool) -> some View {
        if showLabel {
            self.labelStyle(.automatic)
        } else {
            self.labelStyle(.iconOnly)
        }
    }
}
struct BackportFlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.globalAppearance) private var globalAppearance
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @State var selectedTab: String?
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            let items = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
            TabView(selection: $selectedTab) {
                ForEach(items, id: \.name) { tab in
                    Router { onNavigate in
                        homeTabRoute(tab).view(onNavigate: onNavigate, goBack: {})
                    }
                    .tabItem {
                        Label {
                            Text(homeTabTitle(tab))
                        } icon: {
                            Image(homeTabIconName(tab))
                        }
                        .adaptiveLabelStyle(globalAppearance.showBottomBarLabels)
                    }
                    .badge(homeTabRoute(tab) == .notification ? Int(notificationBadgePresenter.state.count) : 0)
                    .tag(homeTabKey(tab))
                }
            }
            .background(Color(.systemGroupedBackground))
        } loadingContent: {
            SplashScreen()
        }
    }
}

private func homeTabKey(_ tab: HomeTabsPresenterStateHomeTabs) -> String {
    tab.name.lowercased()
}

private func homeTabRoute(_ tab: HomeTabsPresenterStateHomeTabs) -> Route {
    switch tab {
    case .notifications:
        return .notification
    case .discover:
        return .discover
    case .home:
        return .home
    }
}

private func homeTabTitle(_ tab: HomeTabsPresenterStateHomeTabs) -> LocalizedStringKey {
    switch tab {
    case .notifications:
        return "home_tab_notifications_title"
    case .discover:
        return "home_tab_discover_title"
    case .home:
        return "home_tab_home_title"
    }
}

private func homeTabIconName(_ tab: HomeTabsPresenterStateHomeTabs) -> String {
    switch tab {
    case .notifications:
        return "fa-bell"
    case .discover:
        return "fa-magnifying-glass"
    case .home:
        return "fa-house"
    }
}

private struct SidebarRouteScreen: View {
    let route: Route

    var body: some View {
        Router { onNavigate in
            route.view(
                onNavigate: onNavigate,
                goBack: {}
            )
        }
    }
}

private enum SecondarySidebarStaticRoute: CaseIterable {
    case drafts
    case rssManagement
    case localHistory
    case agentHistory
    case settings

    var selectionValue: String {
        switch self {
        case .drafts:
            return "route:drafts"
        case .rssManagement:
            return "route:rssManagement"
        case .localHistory:
            return "route:localHistory"
        case .agentHistory:
            return "route:agentHistory"
        case .settings:
            return "route:settings"
        }
    }

    var route: Route {
        switch self {
        case .drafts:
            return .draftBox
        case .rssManagement:
            return .rssManagement
        case .localHistory:
            return .localHostory
        case .agentHistory:
            return .agentHistory
        case .settings:
            return .settings
        }
    }

    var title: LocalizedStringKey {
        switch self {
        case .drafts:
            return "Drafts"
        case .rssManagement:
            return "settings_rss_management_title"
        case .localHistory:
            return "local_history_title"
        case .agentHistory:
            return "settings_agent_history_title"
        case .settings:
            return "settings_title"
        }
    }

    var iconName: String {
        switch self {
        case .drafts:
            return "fa-pen-to-square"
        case .rssManagement:
            return "fa-square-rss"
        case .localHistory:
            return "fa-clock-rotate-left"
        case .agentHistory:
            return "fa-robot"
        case .settings:
            return "fa-gear"
        }
    }
}
