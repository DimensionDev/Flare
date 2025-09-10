import SwiftUI
import KotlinSharedUI
import Awesome

struct FlareRoot: View {
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @State var selectedTab: String? = nil
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            TabView(selection: $selectedTab) {
                ForEach(tabs.primary, id: \.tabItem.key) { data in
                    let badge = if case .success(let count) = onEnum(of: data.badgeCountState) {
                        count.data.intValue
                    } else {
                        0
                    }
                    Tab(value: data.tabItem.key) {
                        Router {
                            TabItemView(tabItem: data.tabItem)
                        }
                        .id(data.tabItem.key)
                        .toolbarVisibility(tabs.secondary.contains(data) ? .hidden : .visible, for: .tabBar)
                    } label: {
                        Label {
                            TabTitle(title: data.tabItem.metaData.title)
                        } icon: {
                            TabIcon(icon: data.tabItem.metaData.icon, accountType: data.tabItem.account)
                        }
                    }
                    .badge(badge)
                }
                Tab(value: "more", role: .search) {
                    Text("More")
                } label: {
                    Label {
                        Text("More")
                    } icon: {
                        Awesome.Classic.Solid.ellipsis.image
                    }
                }
            }
        }
    }
}
