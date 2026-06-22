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
    @State private var selectedTab: Route?
    @State private var homeExpanded: Bool = true

    var body: some View {
        NavigationSplitView {
            List(selection: $selectedTab) {
                StateView(state: homeTabsPresenter.state.tabs) { tabs in
                    let homeTabs: [HomeTabsPresenterStateHomeTabs] = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
                    ForEach(homeTabs, id: \.name) { tab in
                        if tab == .home, case .success(let data) = onEnum(of: homeTimelineWithTabsPresenter.state.tabState) {
                            let tabs: [UiTimelineTabItem] = data.data.cast(UiTimelineTabItem.self)
                            DisclosureGroup(isExpanded: $homeExpanded) {
                                ForEach(tabs, id: \.id) { tab in
                                    Label {
                                        Text(tab.title.text)
                                    } icon: {
                                        TabIcon(tabItem: tab)
                                    }
                                    .tag(Route.timeline(tab))
                                }
                            } label: {
                                Label {
                                    Text(tab.macOSTitle)
                                } icon: {
                                    Image(fontAwesome: tab.macOSIcon)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .overlay(alignment: .trailing) {
                                    Button {
                                        
                                    } label: {
                                        Image(fontAwesome: .sliders)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .onAppear {
                                selectedTab = .timeline(tabs.first!)
                            }
                        } else if tab == .notifications {
                            DisclosureGroup {
                                ForEach(notificationPresenter.state.notifications, id: \.profile.key) { item in
                                    UserOnelineView(data: item.profile)
                                        .badge(Int(item.badge))
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
                        openWindow(id: MacWindowID.rssManagement)
                    } label: {
                        Label {
                            Text("settings_rss_management_title")
                        } icon: {
                            Image(fontAwesome: .squareRss)
                        }
                    }
                    .buttonStyle(.plain)
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
            .listStyle(.sidebar)
            .toolbar(removing: .sidebarToggle)
        } detail: {
            if let selectedTab {
                Router(initialRoute: selectedTab)
                    .id(selectedTab)
            }
        }
    }

    @TabContentBuilder<String?>
    private func homeTab(_ tab: HomeTabsPresenterStateHomeTabs) -> some TabContent<String?> {
        Tab(value: "home:\(tab.name)") {
            Router(initialRoute: tab.macOSInitialRoute)
        } label: {
            Label {
                Text(tab.macOSTitle)
            } icon: {
                Image(fontAwesome: tab.macOSIcon)
            }
        }
        .tabPlacement(.sidebarOnly)
    }

    @TabContentBuilder<String?>
    private func secondaryTab(_ tab: SecondaryTabsPresenter.Tab, route: Route) -> some TabContent<String?> {
        Tab(value: "secondary:\(tab.hashValue)") {
            Router(initialRoute: route)
        } label: {
            Label {
                Text(tab.title.text)
            } icon: {
                Image(fontAwesome: tab.icon.fontAwesomeIcon)
            }
        }
        .tabPlacement(.sidebarOnly)
    }
}


func route(for tab: SecondaryTabsPresenter.Tab) -> Route? {
    switch onEnum(of: tab.destination) {
    case .route(let destination):
        return Route.fromDeepLinkRoute(deeplinkRoute: destination.route)
    case .timeline(let destination):
        return .timeline(destination.tabItem)
    }
}
