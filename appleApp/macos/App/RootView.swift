import AppleFontAwesome
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct RootView: View {
    @Environment(\.openWindow) private var openWindow
    @Environment(\.openSettings) private var openSettings
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var secondaryTabPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @StateObject private var homeTimelineWithTabsPresenter = KotlinPresenter(presenter: HomeTimelineWithTabsPresenter())
    @StateObject private var notificationPresenter = KotlinPresenter(presenter: AllNotificationPresenter())
    @StateObject private var allNotificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())
    @State private var selectedTab: Route?
    @State private var homeExpanded: Bool = true
    @State private var showDraftBoxPopover = false
    @State private var showLogin = false

    var body: some View {
        NavigationSplitView {
            List(selection: $selectedTab) {
                StateView(state: homeTabsPresenter.state.tabs) { tabs in
                    let homeTabs: [HomeTabsPresenterStateHomeTabs] = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
                    ForEach(homeTabs, id: \.name) { tab in
                        if tab == .home, case .success(let data) = onEnum(of: homeTimelineWithTabsPresenter.state.tabState) {
                            let tabs: [UiTimelineTabItem] = data.data.cast(UiTimelineTabItem.self)
                            HomeSidebarTabsSection(
                                title: tab.macOSTitle,
                                icon: tab.macOSIcon,
                                liveTabs: tabs,
                                selectedTab: $selectedTab,
                                isExpanded: $homeExpanded
                            )
                        } else if tab == .notifications {
                            DisclosureGroup {
                                ForEach(notificationPresenter.state.notifications, id: \.profile.key) { item in
                                    UserOnelineView(data: item.profile)
                                        .badge(Int(item.badge))
                                        .tag(Route.accountNotification(item.profile.key))
                                }
                            } label: {
                                Label {
                                    Text(tab.macOSTitle)
                                } icon: {
                                    Image(fontAwesome: tab.macOSIcon)
                                }
                                .badge(Int(allNotificationBadgePresenter.state.count))
                            }
                        } else {
                            Label {
                                Text(tab.macOSTitle)
                            } icon: {
                                Image(fontAwesome: tab.macOSIcon)
                            }
                            .tag(tab.macOSInitialRoute)
                        }
                    }
                }

                Section {
                    Button {
                        showDraftBoxPopover.toggle()
                    } label: {
                        Label {
                            Text("draft_box_title")
                        } icon: {
                            Image(fontAwesome: .inbox)
                        }
                    }
                    .buttonStyle(.plain)
                    .popover(isPresented: $showDraftBoxPopover, arrowEdge: .trailing) {
                        NavigationStack {
                            DraftBoxScreen { groupId in
                                showDraftBoxPopover = false
                                MacComposeWindowCoordinator.shared.openDraft(
                                    groupId: groupId,
                                    openWindow: openWindow
                                )
                            }
                        }
                        .frame(width: 380, height: 480)
                    }

                    Button {
                        openWindow(id: MacWindowID.rssManagement)
                    } label: {
                        Label {
                            Text("settings_rss_management_title")
                        } icon: {
                            Image(fontAwesome: .squareRss)
                        }
                    }
                    .buttonStyle(.plain)

                    Label {
                        Text("local_history_title")
                    } icon: {
                        Image(fontAwesome: .clockRotateLeft)
                    }
                    .tag(Route.localHistory)

                    Button {
                        openSettings()
                    } label: {
                        Label {
                            Text("settings_title")
                        } icon: {
                            Image(fontAwesome: .gear)
                        }
                    }
                    .buttonStyle(.plain)
                }

                if case .success(let data) = onEnum(of: secondaryTabPresenter.state.items) {
                    let items: [SecondaryTabsPresenter.Item] = data.data.cast(SecondaryTabsPresenter.Item.self)
                    if !items.isEmpty {
                        Section {
                            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                                DisclosureGroup {
                                    ForEach(item.tabs, id: \.self) { (tab: SecondaryTabsPresenter.Tab) in
                                        if let route = route(for: tab) {
                                            Label {
                                                Text(tab.title.text)
                                            } icon: {
                                                Image(fontAwesome: tab.icon.fontAwesomeIcon)
                                            }
                                            .tag(route)
                                        }
                                    }
                                } label: {
                                    StateView(state: item.user) { user in
                                        UserOnelineView(data: user)
                                    }
                                }
                            }
                        } header: {
                            Text("macos_sidebar_accounts")
                        }
                    }
                }
            }
            .listStyle(.sidebar)
        } detail: {
            if let selectedTab {
                Router(initialRoute: selectedTab)
                    .id(selectedTab)
                    .toolbar {
                        if case .success(let data) = onEnum(of: loggedInPresenter.state.isLoggedIn), !data.data.boolValue {
                            ToolbarItem(placement: .primaryAction) {
                                Button {
                                    showLogin = true
                                } label: {
                                    Text("login_button")
                                }
                            }
                        }
                    }
            }
        }
        .sheet(isPresented: $showLogin) {
            NavigationStack {
                ServiceSelectionScreen(toHome: { showLogin = false })
            }
        }
    }
}

private func route(for tab: SecondaryTabsPresenter.Tab) -> Route? {
    switch onEnum(of: tab.destination) {
    case .route(let destination):
        return Route.fromDeepLinkRoute(deeplinkRoute: destination.route)
    case .timeline(let destination):
        return .timeline(destination.tabItem)
    }
}
