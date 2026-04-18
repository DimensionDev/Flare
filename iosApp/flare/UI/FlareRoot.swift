import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import FlareUI

@available(iOS 18.0, *)
struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var secondaryTabsPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @State var selectedTab: String?
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            TabView(selection: $selectedTab) {
                ForEach(tabs.primary, id: \.key) { data in
                    let badge = if data is NotificationTabItem || data is AllNotificationTabItem {
                        Int(notificationBadgePresenter.state.count)
                    } else {
                        0
                    }
                    Tab(value: data.key, role: data is DiscoverTabItem ? .some(.search) : .none) {
                        Router { onNavigate in
                            data.view(onNavigate: onNavigate)
                        }
                    } label: {
                        Label {
                            TabTitle(title: data.metaData.title)
                        } icon: {
                            TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                        }
                    }
                    .badge(badge)
                }
                if horizontalSizeClass == .regular {
                    if case .success(let data) = onEnum(of: secondaryTabsPresenter.state.items) {
                        let items = data.data.cast(SecondaryTabsPresenter.Item.self)
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            TabSection {
                                ForEach(item.tabs, id: \.key) { tab in
                                    secondarySidebarTab(tab)
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
                    ForEach(SecondarySidebarStaticRoute.allCases, id: \.self) { route in
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
    private func secondarySidebarTab(_ tab: TabItem) -> some TabContent<String?> {
        Tab(value: "secondary:\(tab.key)") {
            Router { onNavigate in
                tab.view(onNavigate: onNavigate)
            }
        } label: {
            Label {
                TabTitle(title: tab.metaData.title)
            } icon: {
                TabIcon(icon: tab.metaData.icon, accountType: tab.account, iconOnly: true)
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

struct BackportFlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @State var selectedTab: String?
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            TabView(selection: $selectedTab) {
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
                            TabIcon(icon: data.metaData.icon, accountType: data.account, iconOnly: true)
                        }
                    }
                    .badge(badge)
                    .tag(data.key)
                }
            }
            .background(Color(.systemGroupedBackground))
        } loadingContent: {
            SplashScreen()
        }
    }
}

private struct SidebarRouteScreen: View {
    let route: Route

    var body: some View {
        Router { onNavigate in
            route.view(
                onNavigate: onNavigate,
                clearToHome: {}
            )
        }
    }
}

private enum SecondarySidebarStaticRoute: CaseIterable {
    case drafts
    case rssManagement
    case localHistory
    case settings

    var selectionValue: String {
        switch self {
        case .drafts:
            return "route:drafts"
        case .rssManagement:
            return "route:rssManagement"
        case .localHistory:
            return "route:localHistory"
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
        case .settings:
            return "fa-gear"
        }
    }
}
