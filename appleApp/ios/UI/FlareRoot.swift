import SwiftUI
import Combine
import FlareAppleUI
import KotlinSharedUI
import FlareAppleCore
import SwiftUIBackports
import SwiftUIIntrospect
import UIKit

extension Notification.Name {
    static let homeTabDoubleTapped = Notification.Name("homeTabDoubleTapped")
    static let notificationsTabDoubleTapped = Notification.Name("notificationsTabDoubleTapped")
}

@available(iOS 18.0, *)
struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.globalAppearance) private var globalAppearance
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var secondaryTabsPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @StateObject private var aiAgentEnabledPresenter = KotlinPresenter(presenter: AiAgentEnabledPresenter())
    @StateObject private var inAppNotification = SwiftInAppNotification.shared
    @State var selectedTab: String?
    @State private var reloginRoute: Route?
    
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
                            Image(fontAwesome: homeTabIcon(tab))
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
                                    Text("account_management_title")
                                } loadingContent: {
                                    Text("account_management_title")
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
            .onTabBarDoubleTap {
                postTabDoubleTapRefresh(for: selectedTab)
            }
            .tabViewStyle(.sidebarAdaptable)
            .backport
            .tabBarMinimizeBehavior(.onScrollDown)
            .background(Color(.systemGroupedBackground))
            .sheet(item: $reloginRoute) { route in
                NavigationStack {
                    route.view(
                        onNavigate: { reloginRoute = $0 },
                        goBack: { reloginRoute = nil }
                    )
                }
            }
            .onAppear {
                inAppNotification.onRelogin = { toast in
                    reloginRoute = .relogin(toast.accountKey, toast.platformId)
                }
            }
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
                Image(fontAwesome: tab.icon.fontAwesomeIcon)
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
                Image(fontAwesome: route.icon)
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

    func onTabBarDoubleTap(perform action: @escaping () -> Void) -> some View {
        modifier(TabBarDoubleTapModifier(action: action))
    }
}
struct BackportFlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.globalAppearance) private var globalAppearance
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var inAppNotification = SwiftInAppNotification.shared
    @State var selectedTab: String?
    @State private var reloginRoute: Route?
    
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
                            Image(fontAwesome: homeTabIcon(tab))
                        }
                        .adaptiveLabelStyle(globalAppearance.showBottomBarLabels)
                    }
                    .badge(homeTabRoute(tab) == .notification ? Int(notificationBadgePresenter.state.count) : 0)
                    .tag(homeTabKey(tab))
                }
            }
            .onTabBarDoubleTap {
                postTabDoubleTapRefresh(for: selectedTab)
            }
            .background(Color(.systemGroupedBackground))
            .sheet(item: $reloginRoute) { route in
                NavigationStack {
                    route.view(
                        onNavigate: { reloginRoute = $0 },
                        goBack: { reloginRoute = nil }
                    )
                }
            }
            .onAppear {
                inAppNotification.onRelogin = { toast in
                    reloginRoute = .relogin(toast.accountKey, toast.platformId)
                }
            }
        } loadingContent: {
            SplashScreen()
        }
    }
}

@MainActor
private final class TabBarDoubleTapTarget: NSObject, ObservableObject, UIGestureRecognizerDelegate {
    private weak var tabBar: UITabBar?
    private var action: () -> Void = {}

    private lazy var recognizer: UITapGestureRecognizer = {
        let recognizer = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap))
        recognizer.numberOfTapsRequired = 2
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesBegan = false
        recognizer.delaysTouchesEnded = false
        recognizer.delegate = self
        return recognizer
    }()

    func install(on tabBar: UITabBar, action: @escaping () -> Void) {
        self.action = action
        guard self.tabBar !== tabBar else { return }

        self.tabBar?.removeGestureRecognizer(recognizer)
        tabBar.addGestureRecognizer(recognizer)
        self.tabBar = tabBar
    }

    @objc private func handleDoubleTap() {
        DispatchQueue.main.async { [weak self] in
            self?.action()
        }
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }
}

private struct TabBarDoubleTapModifier: ViewModifier {
    @StateObject private var target = TabBarDoubleTapTarget()
    let action: () -> Void

    func body(content: Content) -> some View {
        content.introspect(.tabView, on: .iOS(.v17, .v18, .v26, .v27)) { tabBarController in
            target.install(on: tabBarController.tabBar, action: action)
        }
    }
}

private func homeTabKey(_ tab: HomeTabsPresenterStateHomeTabs) -> String {
    tab.name.lowercased()
}

private func postTabDoubleTapRefresh(for selectedTab: String?) {
    let notificationName: Notification.Name
    switch selectedTab {
    case homeTabKey(.home):
        notificationName = .homeTabDoubleTapped
    case homeTabKey(.notifications):
        notificationName = .notificationsTabDoubleTapped
    default:
        return
    }
    NotificationCenter.default.post(name: notificationName, object: nil)
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
        return "discover_title"
    case .home:
        return "home_tab_home_title"
    }
}

private func homeTabIcon(_ tab: HomeTabsPresenterStateHomeTabs) -> FontAwesomeIcon {
    switch tab {
    case .notifications:
        return .bell
    case .discover:
        return .magnifyingGlass
    case .home:
        return .house
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
            return "agent_history_title"
        case .settings:
            return "settings_title"
        }
    }

    var icon: FontAwesomeIcon {
        switch self {
        case .drafts:
            return .penToSquare
        case .rssManagement:
            return .squareRss
        case .localHistory:
            return .clockRotateLeft
        case .agentHistory:
            return .robot
        case .settings:
            return .gear
        }
    }
}
