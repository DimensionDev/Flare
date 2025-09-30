import SwiftUI
import KotlinSharedUI

struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @State var selectedTab: String?

    var body: some View {
        if case .success(let success) = onEnum(of: homeTabsPresenter.state.tabs) {
            let tabs = success.data
            HStack(
                spacing: 0,
            ) {
                if horizontalSizeClass == .regular {
                    ScrollView {
                        VStack(
                            spacing: 24,
                        ) {
                            ForEach(tabs.all, id: \.tabItem.key) { data in
                                Button {
                                    selectedTab = data.tabItem.key
                                } label: {
                                    TabIcon(icon: data.tabItem.metaData.icon, accountType: data.tabItem.account, size: 36)
                                }
                                .foregroundStyle(selectedTab == data.tabItem.key ? .primary : .secondary)
                            }
                            Button {
                                selectedTab = "settings"
                            } label: {
                                Image("fa-gear")
                            }
                            .foregroundStyle(selectedTab == "settings" ? .primary : .secondary)
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 24)
                        .frame(maxHeight: .infinity, alignment: .top)
                        .font(.title)
                    }
                    .background(Color(.systemBackground))
                }
                TabView(selection: $selectedTab) {
                    if horizontalSizeClass == .regular {
                        ForEach(tabs.all, id: \.tabItem.key) { data in
                            Tab(value: data.tabItem.key) {
                                Router { onNavigate in
                                    data.tabItem.view(onNavigate: onNavigate)
                                }
                                .toolbarVisibility(.hidden, for: .tabBar)
                            }
                            Tab(value: "settings") {
                                Router { _ in
                                    SettingsScreen()
                                }
                                .toolbarVisibility(.hidden, for: .tabBar)
                            }
                        }
                    } else {
                        ForEach(tabs.primary, id: \.tabItem.key) { data in
                            let badge = if case .success(let count) = onEnum(of: data.badgeCountState) {
                                count.data.intValue
                            } else {
                                0
                            }
                            Tab(value: data.tabItem.key) {
                                Router { onNavigate in
                                    data.tabItem.view(onNavigate: onNavigate)
                                }
                            } label: {
                                Label {
                                    TabTitle(title: data.tabItem.metaData.title)
                                } icon: {
                                    TabIcon(icon: data.tabItem.metaData.icon, accountType: data.tabItem.account)
                                }
                            }
                            .badge(badge)
                        }
                    }
                    if case .success = onEnum(of: activeAccountPresenter.state.user) {
                        Tab(value: "more", role: .search) {
                            SecondaryTabsScreen(tabs: tabs.secondary)
                        } label: {
                            Label {
                                Text("More")
                            } icon: {
                                Image("fa-ellipsis")
                            }
                        }
                    }
                }
                .tabViewStyle(.tabBarOnly)
                .tabBarMinimizeBehavior(.onScrollDown)
            }
            .background(Color(.systemGroupedBackground))
        }
    }
}
