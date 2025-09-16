import SwiftUI
import KotlinSharedUI
import Awesome

struct FlareRoot: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @State private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @State var selectedTab: String? = nil
    
    var body: some View {
        StateView(state: homeTabsPresenter.state.tabs) { tabs in
            HStack(
                spacing: 0
            ) {
                if horizontalSizeClass == .regular {
                    VStack(
                        spacing: 24
                    ) {
                        ForEach(tabs.all, id: \.tabItem.key) { data in
                            Button {
                                selectedTab = data.tabItem.key
                            } label: {
                                TabIcon(icon: data.tabItem.metaData.icon, accountType: data.tabItem.account, size: 36)
                            }
//                            .if(selectedTab == data.tabItem.key) { button in
//                                button.buttonStyle(.glassProminent)
//                            } else: { button in
//                                button.buttonStyle(.glass)
//                            }

                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 24)
                    .glassEffect()
                    .padding()
                }
                TabView(selection: $selectedTab) {
                    if horizontalSizeClass == .regular {
                        ForEach(tabs.all, id:\.tabItem.key) { data in
                            Tab(value: data.tabItem.key) {
                                Router { onNavigate in
                                    TabItemView(tabItem: data.tabItem, onNavigate: onNavigate)
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
                                    TabItemView(tabItem: data.tabItem, onNavigate: onNavigate)
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
                    if case .success(_) = onEnum(of: activeAccountPresenter.state.user) {
                        Tab(value: "more", role: .search) {
                            Text("More")
                                .if(horizontalSizeClass == .regular) { view in
                                    view.toolbarVisibility(.hidden, for: .tabBar)
                                } else: { view in
                                    view
                                }
                        } label: {
                            Label {
                                Text("More")
                            } icon: {
                                Awesome.Classic.Solid.ellipsis.image
                            }
                        }
                    }
                }
                .tabViewStyle(.tabBarOnly)
                .tabBarMinimizeBehavior(.onScrollDown)
            }
        }
        .background(Color(.systemGroupedBackground))
    }
}
