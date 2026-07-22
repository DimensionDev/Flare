import Combine
import FlareAppleCore
import FlareAppleUI
import Kingfisher
import KotlinSharedUI
import SwiftUI

enum MacWindowID {
    static let main = "main"
    static let compose = "compose"
    static let media = "media"
    static let rssManagement = "rss-management"
    static let agentHistory = "agent-history"
    static let directMessages = "direct-messages"
}

@main
struct FlareApp: App {
    init() {
        configureKingfisherCache()

        let firebaseEnabled = FirebaseBootstrap.configureIfAvailable()
        if firebaseEnabled {
            AppleSharedHelper.shared.setupCrashlytics()
        }
        AppleSharedHelper.shared.initialize(
            inAppNotification: SwiftInAppNotification.shared,
            swiftFormatter: Formatter.shared,
            swiftPlatformTextRenderer: PlatformTextRenderer.shared,
            swiftOnDeviceAI: FoundationModelOnDeviceAI.shared
        )
    }

    var body: some Scene {
        Window("Flare", id: MacWindowID.main) {
            FlareTheme {
                RootView()
            }
        }
        .defaultSize(width: 480, height: 600)
        .windowResizability(.contentSize)
        .windowSizeFitContent()
        .commands {
            MacAppCommands()
        }
//        .windowToolbarStyle(.unified(showsTitle: false))
        WindowGroup("compose_title_new", id: MacWindowID.compose, for: UUID.self) { requestID in
            FlareTheme {
                MacComposeWindowRoot(requestID: requestID.wrappedValue)
            }
        }
        .windowResizability(.contentSize)
        .windowSizeFitContent()
        .defaultSize(width: 100, height: 80)
        .windowToolbarStyle(.unified)
        .disableRestorationBehavior()

        WindowGroup("Media", id: MacWindowID.media, for: MacMediaWindowValue.self) { request in
            FlareTheme {
                MacMediaWindowRoot(value: request.wrappedValue)
            }
        }
        .defaultSize(width: 960, height: 720)
        .mediaWindowPlacement()
        .windowToolbarStyle(.unified(showsTitle: false))
        .disableRestorationBehavior()

        WindowGroup("settings_rss_management_title", id: MacWindowID.rssManagement) {
            FlareTheme {
                RssScreen()
            }
        }
        .defaultSize(width: 1120, height: 760)
        .windowToolbarStyle(.unified)

        Window("direct_messages_title", id: MacWindowID.directMessages) {
            FlareTheme {
                MacDirectMessagesScreen()
            }
        }
        .defaultSize(width: 1120, height: 760)
        .windowToolbarStyle(.unified)
        .disableRestorationBehavior()

        Window("agent_history_title", id: MacWindowID.agentHistory) {
            FlareTheme {
                Router(
                    initialRoute: .agentHistory,
                    forwardsContentRoutesToMainWindow: true
                )
            }
        }
        .defaultSize(width: 760, height: 640)
        .windowToolbarStyle(.unified)
        .disableRestorationBehavior()

        Settings {
            FlareTheme {
                MacSettingsScreen()
            }
        }
        .windowToolbarStyle(.expanded)
//        .defaultSize(width: 1120, height: 760)
//        .commands {
//            SidebarCommands()
//        }
    }
}

private func configureKingfisherCache() {
    let megabyte: UInt64 = 1024 * 1024
    let minimumLimit = 128 * megabyte
    let maximumLimit = 512 * megabyte
    let scaledLimit = ProcessInfo.processInfo.physicalMemory / 64
    let memoryLimit = min(max(scaledLimit, minimumLimit), maximumLimit)

    KingfisherManager.shared.cache.memoryStorage.config.totalCostLimit = Int(memoryLimit)
}

extension Scene {
    func mediaWindowPlacement() -> some Scene {
        if #available(macOS 15.0, *) {
            return self.defaultWindowPlacement { _, context in
                    let visibleRect = context.defaultDisplay.visibleRect
                    let maxWindowSize = CGSize(
                        width: visibleRect.width * 0.8,
                        height: visibleRect.height * 0.8
                    )
                    return WindowPlacement(
                        .center,
                        size: MacMediaWindowCoordinator.shared.placementSize(
                            maxWindowSize: maxWindowSize
                        )
                    )
                }
        } else {
            return self
        }
    }
    func disableRestorationBehavior() -> some Scene {
        if #available(macOS 15.0, *) {
            return self.restorationBehavior(.disabled)
        } else {
            return self
        }
    }
    func windowSizeFitContent() -> some Scene {
        if #available(macOS 15.0, *) {
            return self.windowIdealSize(.fitToContent)
        } else {
            return self
        }
    }
}

struct MacMainWindowNavigationRequest: Identifiable {
    let id: UUID
    let route: Route

    init(id: UUID = UUID(), route: Route) {
        self.id = id
        self.route = route
    }
}

@MainActor
final class MacMainWindowCoordinator: ObservableObject {
    static let shared = MacMainWindowCoordinator()

    @Published private(set) var navigationRequest: MacMainWindowNavigationRequest?

    private init() {}

    func open(route: Route, openWindow: OpenWindowAction) {
        navigationRequest = MacMainWindowNavigationRequest(route: route)
        openWindow(id: MacWindowID.main)
    }
}

private struct MacAppCommands: Commands {
    @Environment(\.openWindow) private var openWindow
    @StateObject private var homeTimelineWithTabsPresenter = KotlinPresenter(
        presenter: HomeTimelineWithTabsPresenter()
    )
    @StateObject private var notificationAccountsPresenter = KotlinPresenter(
        presenter: NotificationAccountsPresenter()
    )
    @StateObject private var secondaryTabPresenter = KotlinPresenter(
        presenter: SecondaryTabsPresenter()
    )

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button {
                MacComposeWindowCoordinator.shared.openNew(openWindow: openWindow)
            } label: {
                Label {
                    Text("macos_menu_new_post")
                } icon: {
                    Image(fontAwesome: .penToSquare)
                }
            }
            .keyboardShortcut("n")
        }

        CommandGroup(after: .sidebar) {
            Menu {
                switch onEnum(of: homeTimelineWithTabsPresenter.state.tabState) {
                case .success(let data):
                    let tabs: [UiTimelineTabItem] = data.data.cast(UiTimelineTabItem.self)
                    ForEach(tabs, id: \.id) { tab in
                        Button {
                            openMainWindow(route: .timeline(tab, isHome: true))
                        } label: {
                            Text(tab.title.text)
                        }
                    }
                case .loading:
                    Button("Loading…") {}
                        .disabled(true)
                case .error:
                    Button("Unavailable") {}
                        .disabled(true)
                }
            } label: {
                Text("home_tab_home_title")
            }

            Menu {
                if notificationAccountsPresenter.state.notifications.isEmpty {
                    Button("Unavailable") {}
                        .disabled(true)
                } else {
                    ForEach(
                        notificationAccountsPresenter.state.notifications,
                        id: \.profile.key
                    ) { item in
                        Button {
                            openMainWindow(route: .accountNotification(item.profile.key))
                        } label: {
                            Text(verbatim: item.profile.handle.canonical)
                        }
                    }
                }
            } label: {
                Text("home_tab_notifications_title")
            }

            Divider()

            Button {
                openMainWindow(route: .discover)
            } label: {
                Text("discover_title")
            }

            Button {
                openMainWindow(route: .localHistory)
            } label: {
                Text("local_history_title")
            }
        }

        CommandMenu("account_management_title") {
            switch onEnum(of: secondaryTabPresenter.state.items) {
            case .success(let data):
                let accounts: [SecondaryTabsPresenter.Item] = data.data.cast(SecondaryTabsPresenter.Item.self)
                if accounts.isEmpty {
                    Button("Unavailable") {}
                        .disabled(true)
                } else {
                    ForEach(Array(accounts.enumerated()), id: \.offset) { _, account in
                        Menu {
                            ForEach(account.tabs, id: \.self) { tab in
                                if let route = route(for: tab) {
                                    Button {
                                        openMainWindow(route: route)
                                    } label: {
                                        Label {
                                            Text(tab.title.text)
                                        } icon: {
                                            Image(fontAwesome: tab.icon.fontAwesomeIcon)
                                        }
                                    }
                                }
                            }
                        } label: {
                            switch onEnum(of: account.user) {
                            case .success(let data):
                                Label {
                                    Text(verbatim: accountTitle(data.data))
                                } icon: {
                                    if let avatar = data.data.avatar {
                                        AvatarView(
                                            data: avatar.url,
                                            customHeader: avatar.customHeaders
                                        )
                                        .frame(width: 16, height: 16)
                                        .clipShape(Circle())
                                    } else {
                                        Image(systemName: "person.crop.circle")
                                    }
                                }
                            case .loading:
                                Text("Loading…")
                            case .error:
                                Text("Unavailable")
                            }
                        }
                    }
                }
            case .loading:
                Button("Loading…") {}
                    .disabled(true)
            case .error:
                Button("Unavailable") {}
                    .disabled(true)
            }
        }

        CommandGroup(after: .windowArrangement) {
            Button {
                openWindow(id: MacWindowID.rssManagement)
            } label: {
                Label {
                    Text("settings_rss_management_title")
                } icon: {
                    Image(fontAwesome: .squareRss)
                }
            }

            Button {
                MacDirectMessageWindowCoordinator.shared.open(route: .directMessages, openWindow: openWindow)
            } label: {
                Label {
                    Text("direct_messages_title")
                } icon: {
                    Image(fontAwesome: .message)
                }
            }

            Button {
                MacAgentWindowCoordinator.shared.open(route: .agentHistory, openWindow: openWindow)
            } label: {
                Label {
                    Text("agent_history_title")
                } icon: {
                    Image(fontAwesome: .robot)
                }
            }
        }
    }

    private func openMainWindow(route: Route) {
        MacMainWindowCoordinator.shared.open(route: route, openWindow: openWindow)
    }

    private func route(for tab: SecondaryTabsPresenter.Tab) -> Route? {
        switch onEnum(of: tab.destination) {
        case .route(let destination):
            guard let route = Route.fromDeepLinkRoute(deeplinkRoute: destination.route),
                  !route.isDirectMessageWindowRoute
            else {
                return nil
            }
            return route
        case .timeline(let destination):
            return .timeline(destination.tabItem)
        }
    }

    private func accountTitle(_ profile: UiProfile) -> String {
        let name = profile.name.raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? profile.handle.canonical : "\(name) (\(profile.handle.canonical))"
    }
}

struct MacAgentWindowRequest: Identifiable {
    let id: UUID
    let route: Route

    init(id: UUID = UUID(), route: Route) {
        self.id = id
        self.route = route
    }
}

@MainActor
final class MacAgentWindowCoordinator: ObservableObject {
    static let shared = MacAgentWindowCoordinator()

    @Published private(set) var request: MacAgentWindowRequest?

    private init() {}

    func open(route: Route, openWindow: OpenWindowAction) {
        guard route.isAgentWindowRoute else {
            return
        }

        request = MacAgentWindowRequest(route: route)
        openWindow(id: MacWindowID.agentHistory)
    }
}

struct MacDirectMessageWindowRequest: Identifiable {
    let id: UUID
    let route: Route

    init(id: UUID = UUID(), route: Route) {
        self.id = id
        self.route = route
    }
}

@MainActor
final class MacDirectMessageWindowCoordinator: ObservableObject {
    static let shared = MacDirectMessageWindowCoordinator()

    @Published private(set) var request: MacDirectMessageWindowRequest?

    private init() {}

    func open(route: Route = .directMessages, openWindow: OpenWindowAction) {
        guard route.isDirectMessageWindowRoute else {
            return
        }

        request = MacDirectMessageWindowRequest(route: route)
        openWindow(id: MacWindowID.directMessages)
    }
}
