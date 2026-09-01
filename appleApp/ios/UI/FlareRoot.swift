import SwiftUI
import Combine
import FlareAppleUI
import KotlinSharedUI
import FlareAppleCore
import SwiftUIBackports
import SwiftUIIntrospect
import UIKit

extension Notification.Name {
    static let tabDoubleTapped = Notification.Name("tabDoubleTapped")
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
    @StateObject private var navigationModel = HomeNavigationModel()
    @State var selectedTab: String?
    @State private var reloginRoute: Route?
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            let items = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
            Group {
                if globalAppearance.largeScreenLayoutMode == .singleColumn && horizontalSizeClass == .regular {
                    SingleColumnFlareRoot(
                        tabs: items,
                        selectedTab: $selectedTab,
                        notificationCount: Int(notificationBadgePresenter.state.count),
                        navigationModel: navigationModel
                    )
                } else {
                    TabView(selection: $selectedTab) {
                        ForEach(items, id: \.name) { tab in
                            Tab(value: homeTabKey(tab), role: homeTabRoute(tab) == .discover ? .some(.search) : .none) {
                                Router(backStack: navigationModel.binding(for: homeTabRoute(tab))) { onNavigate in
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
                    .modifier(TabBarDoubleTapModifier {
                        NotificationCenter.default.post(name: .tabDoubleTapped, object: selectedTab)
                    })
                    .tabViewStyle(.sidebarAdaptable)
                    .backport
                    .tabBarMinimizeBehavior(.onScrollDown)
                }
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
}
struct BackportFlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.globalAppearance) private var globalAppearance
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var notificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var inAppNotification = SwiftInAppNotification.shared
    @StateObject private var navigationModel = HomeNavigationModel()
    @State var selectedTab: String?
    @State private var reloginRoute: Route?
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            let items = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
            Group {
                if globalAppearance.largeScreenLayoutMode == .singleColumn && horizontalSizeClass == .regular {
                    SingleColumnFlareRoot(
                        tabs: items,
                        selectedTab: $selectedTab,
                        notificationCount: Int(notificationBadgePresenter.state.count),
                        navigationModel: navigationModel
                    )
                } else {
                    TabView(selection: $selectedTab) {
                        ForEach(items, id: \.name) { tab in
                            Router(backStack: navigationModel.binding(for: homeTabRoute(tab))) { onNavigate in
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
                    .modifier(TabBarDoubleTapModifier {
                        NotificationCenter.default.post(name: .tabDoubleTapped, object: selectedTab)
                    })
                }
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
    private var action: () -> Void = {}

    private lazy var recognizer: UITapGestureRecognizer = {
        let recognizer = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap))
        recognizer.numberOfTapsRequired = 2
        recognizer.cancelsTouchesInView = false
        recognizer.delaysTouchesEnded = false
        recognizer.delegate = self
        return recognizer
    }()

    func install(on tabBar: UITabBar, action: @escaping () -> Void) {
        self.action = action
        guard recognizer.view !== tabBar else { return }
        tabBar.addGestureRecognizer(recognizer)
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

@MainActor
private final class HomeNavigationModel: ObservableObject {
    @Published private var backStacks: [Route: [Route]] = [:]
    @Published private(set) var selectedTopLevelRoute: Route?
    let navigator = RouterNavigator()

    func binding(for route: Route) -> Binding<[Route]> {
        Binding(
            get: { self.backStacks[route] ?? [] },
            set: { self.backStacks[route] = $0 }
        )
    }

    func selectTopLevel(_ route: Route?) {
        selectedTopLevelRoute = route
    }
}

private struct SingleColumnFlareRoot: View {
    let tabs: [HomeTabsPresenterStateHomeTabs]
    @Binding var selectedTab: String?
    let notificationCount: Int
    @ObservedObject var navigationModel: HomeNavigationModel
    @Environment(\.globalAppearance) private var globalAppearance

    private var activeTab: HomeTabsPresenterStateHomeTabs? {
        tabs.first { homeTabKey($0) == selectedTab } ?? tabs.first
    }

    private var activeRoute: Route? {
        navigationModel.selectedTopLevelRoute ?? activeTab.map(homeTabRoute)
    }

    var body: some View {
        GeometryReader { proxy in
            let showRightSidebar = proxy.size.width >= 900
            HStack(spacing: 0) {
                VStack(spacing: 8) {
                    ForEach(tabs, id: \.name) { tab in
                        let key = homeTabKey(tab)
                        let selected = navigationModel.selectedTopLevelRoute == nil && key == activeTab.map { homeTabKey($0) }
                        Button {
                            if selected {
                                NotificationCenter.default.post(name: .tabDoubleTapped, object: key)
                            } else {
                                navigationModel.selectTopLevel(nil)
                                selectedTab = key
                            }
                        } label: {
                            VStack(spacing: 4) {
                                ZStack(alignment: .topTrailing) {
                                    Image(fontAwesome: homeTabIcon(tab))
                                        .font(.title3)
                                    if homeTabRoute(tab) == .notification && notificationCount > 0 {
                                        Text(notificationCount.formatted())
                                            .font(.caption2)
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 5)
                                            .background(.red, in: Capsule())
                                            .offset(x: 12, y: -8)
                                    }
                                }
                                if globalAppearance.showBottomBarLabels {
                                    Text(homeTabTitle(tab))
                                        .font(.caption2)
                                        .lineLimit(1)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(selected ? Color.accentColor : Color.secondary)
                        .background(
                            selected ? Color.accentColor.opacity(0.12) : Color.clear,
                            in: RoundedRectangle(cornerRadius: 12)
                        )
                        .padding(.horizontal, 8)
                    }
                    Spacer(minLength: 0)
                    if !showRightSidebar {
                        Button {
                            navigationModel.navigator.navigate(.secondaryMenu)
                        } label: {
                            VStack(spacing: 4) {
                                Image(fontAwesome: .ellipsis)
                                    .font(.title3)
                                if globalAppearance.showBottomBarLabels {
                                    Text("more")
                                        .font(.caption2)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.secondary)
                        .padding(.horizontal, 8)
                    }
                }
                .padding(.top, 12)
                .frame(width: 80)
                .background(.bar)

                Divider()

                HStack(spacing: 0) {
                    if let activeRoute {
                        Router(
                            backStack: navigationModel.binding(for: activeRoute),
                            navigator: navigationModel.navigator
                        ) { onNavigate in
                            activeRoute.view(
                                onNavigate: onNavigate,
                                goBack: {},
                                showsSecondaryMenu: !showRightSidebar
                            )
                        }
                        .environment(\.horizontalSizeClass, .compact)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }

                    if showRightSidebar {
                        Divider()
                        SingleColumnSecondarySidebar { route in
                            navigationModel.selectTopLevel(route)
                        }
                            .frame(width: 320)
                    }
                }
            }
        }
        .onAppear {
            if !tabs.contains(where: { homeTabKey($0) == selectedTab }) {
                selectedTab = tabs.first.map { homeTabKey($0) }
            }
        }
    }
}

private struct SingleColumnSecondarySidebar: View {
    let navigate: (Route) -> Void
    @StateObject private var secondaryTabsPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())
    @StateObject private var aiAgentEnabledPresenter = KotlinPresenter(presenter: AiAgentEnabledPresenter())
    @State private var searchQuery = ""

    private var accounts: [SecondaryTabsPresenter.Item] {
        guard case .success(let data) = onEnum(of: secondaryTabsPresenter.state.items) else {
            return []
        }
        return data.data.cast(SecondaryTabsPresenter.Item.self)
    }

    private var searchAccount: AccountType {
        accounts.first?.accountType ?? AccountType.Guest.shared
    }

    var body: some View {
        VStack(spacing: 0) {
            TextField("search", text: $searchQuery)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.search)
                .onSubmit {
                    navigate(.search(searchAccount, searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)))
                }
                .padding(16)

            List {
                if case .success(let loggedIn) = onEnum(of: loggedInPresenter.state.isLoggedIn),
                   !loggedIn.data.boolValue {
                    Button {
                        navigate(.serviceSelect)
                    } label: {
                        Label("login_title", systemImage: "person.badge.plus")
                    }
                }

                if !accounts.isEmpty {
                    Section("account_management_title") {
                        ForEach(Array(accounts.enumerated()), id: \.offset) { _, account in
                            DisclosureGroup {
                                ForEach(account.tabs, id: \.self) { tab in
                                    if let route = route(for: tab) {
                                        Button {
                                            navigate(route)
                                        } label: {
                                            Label {
                                                Text(tab.title.text)
                                            } icon: {
                                                Image(fontAwesome: tab.icon.fontAwesomeIcon)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            } label: {
                                StateView(state: account.user) { user in
                                    UserCompatView(data: user)
                                }
                            }
                        }
                    }
                }

                Section {
                    ForEach(SecondarySidebarStaticRoute.allCases.filter { route in
                        route != .agentHistory || aiAgentEnabledPresenter.state.enabled
                    }, id: \.self) { item in
                        Button {
                            navigate(item.route)
                        } label: {
                            Label {
                                Text(item.title)
                            } icon: {
                                Image(fontAwesome: item.icon)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .listStyle(.sidebar)
        }
        .background(Color(.secondarySystemGroupedBackground))
    }
}
