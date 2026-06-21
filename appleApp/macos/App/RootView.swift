import AppleFontAwesome
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct RootView: View {
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var secondaryTabPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @State private var selectedTab: String?

    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            TabView(selection: $selectedTab) {
                let homeTabs: [HomeTabsPresenterStateHomeTabs] = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
                ForEach(homeTabs, id: \.name) { tab in
                    homeTab(tab)
                }

                if case .success(let data) = onEnum(of: secondaryTabPresenter.state.items) {
                    let items: [SecondaryTabsPresenter.Item] = data.data.cast(SecondaryTabsPresenter.Item.self)
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        TabSection {
                            ForEach(item.tabs, id: \.self) { (tab: SecondaryTabsPresenter.Tab) in
                                if let route = route(for: tab) {
                                    secondaryTab(tab, route: route)
                                }
                            }
                        } header: {
                            StateView(state: item.user) { user in
                                UserOnelineView(data: user)
                            }
                        }
                    }
                }
            }
            .tabViewStyle(.sidebarAdaptable)
//            .toolbar(removing: .title)
            .toolbar(removing: .sidebarToggle)
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
