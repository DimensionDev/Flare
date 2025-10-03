import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @State var selectedTab: String?

    var body: some View {
        if case .success(let success) = onEnum(of: homeTabsPresenter.state.tabs) {
            let tabs = success.data
            TabView(selection: $selectedTab) {
                if horizontalSizeClass == .regular {
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
                    ForEach(tabs.secondary, id: \.tabItem.key) { data in
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
                        .tabPlacement(.sidebarOnly)
                    }
                    if let profileRoute = tabs.extraProfileRoute {
                        Tab(value: profileRoute.tabItem.key) {
                            Router { onNavigate in
                                profileRoute.tabItem.view(onNavigate: onNavigate)
                            }
                        } label: {
                            Label {
                                TabTitle(title: profileRoute.tabItem.metaData.title)
                            } icon: {
                                TabIcon(icon: profileRoute.tabItem.metaData.icon, accountType: profileRoute.tabItem.account)
                            }
                        }
                        .tabPlacement(.sidebarOnly)
                        .defaultVisibility(.hidden, for: .sidebar)
                    }
                    Tab(value: "settings") {
                        Router { _ in
                            SettingsScreen()
                        }
                    } label: {
                        Label {
                            Text("settings_title")
                        } icon: {
                            Image("fa-gear")
                        }
                    }
                    .tabPlacement(.sidebarOnly)
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
            .tabViewStyle(.sidebarAdaptable)
            .backport
            .tabBarMinimizeBehavior(.onScrollDown)
            .tabViewSidebarHeader {
                if let profileRoute = tabs.extraProfileRoute {
                    StateView(state: activeAccountPresenter.state.user) { user in
                        UserCompatView(data: user)
                            .onTapGesture {
                                selectedTab = profileRoute.tabItem.key
                            }
                    }
                }
            }
            .background(Color(.systemGroupedBackground))
        }
    }
}
