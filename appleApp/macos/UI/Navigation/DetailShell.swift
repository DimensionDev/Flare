import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import AppleFontAwesome
import SwiftUIIntrospect

struct DetailShell: View {
    let destination: HomeTabsPresenterStateHomeTabs
    @Binding var selection: HomeTabsPresenterStateHomeTabs?
    @ObservedObject var homeTabsPresenter: KotlinPresenter<HomeTabsPresenterState>

    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            TabView(selection: $selection) {
                ForEach(tabs.cast(HomeTabsPresenterStateHomeTabs.self), id: \.name) { tab in
                    Router(
                        initialRoute: tab.macOSInitialRoute,
                        isActive: selection?.name == tab.name
                    )
                        .tag(tab)
                        .tabItem {
                            Label {
                                Text(tab.macOSTitle)
                            } icon: {
                                Image(fontAwesome: tab.macOSIcon)
                            }
                        }
                }
            }
            .introspect(.window, on: .macOS(.v15, .v26, .v27)) { window in
                    guard let toolbar = window.toolbar else { return }
                    for item in toolbar.items {
                        if item.label == "Navigation Tab Bar" {
                            // Remove border
                            item.isBordered = false
                            // Hide item instead of removing to avoid system recreation
                            item.view?.isHidden = true
                            // Set width to 0 using constraints to prevent occupying toolbar space
                            item.view?.widthAnchor.constraint(equalToConstant: 0).isActive = true
                        }
                    }
                }
        } errorContent: { _ in
            PlaceholderPanel(destination: selectedTab)
        } loadingContent: {
            PlaceholderPanel(destination: selectedTab)
                .redacted(reason: .placeholder)
        }
    }

    private var selectedTab: HomeTabsPresenterStateHomeTabs {
        selection ?? destination
    }


}
