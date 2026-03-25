import SwiftUI
@preconcurrency import KotlinSharedUI
import SwiftUIBackports

struct HomeTimelineScreen: View {
    let toServiceSelect: () -> Void
    let toCompose: () -> Void
    let toTabSetting: () -> Void
    let accountType: AccountType
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @StateObject private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>

    init(accountType: AccountType, toServiceSelect: @escaping () -> Void, toCompose: @escaping () -> Void, toTabSetting: @escaping () -> Void) {
        self.accountType = accountType
        self.toCompose = toCompose
        self.toServiceSelect = toServiceSelect
        self.toTabSetting = toTabSetting
        self._presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter(accountType: accountType)))
    }

    var body: some View {
        GeometryReader { proxy in
            StateView(state: presenter.state.tabState) { state in
                let tabs: [TimelineTabItem] = state.cast(TimelineTabItem.self)
                let tab = tabs[min(max(selectedTabIndex, 0), tabs.count - 1)]
                ZStack {
                    TimelineScreen(tabItem: tab)
                        .id(tab.key)
                }
                .onChange(of: state.count, { oldValue, newValue in
                    if !(tabs.indices.contains(selectedTabIndex)) {
                        selectedTabIndex = 0
                    }
                })
                .toolbar {
                    ToolbarItem(placement: .title) {
                        Menu {
                            ForEach(0..<tabs.count, id: \.self) { index in
                                let item = tabs[index]
                                Button {
                                    selectedTabIndex = index
                                } label: {
                                    Label {
                                        TabTitle(title: item.metaData.title)
                                    } icon: {
                                        TabIcon(icon: item.metaData.icon, accountType: item.account)
                                            .frame(width: 24)
                                            .scaledToFit()
                                    }
                                }
                            }
                            Divider()
                            Button {
                                toTabSetting()
                            } label: {
                                Label {
                                    Text("tab_settings_add_tab")
                                } icon: {
                                    Image("fa-plus")
                                }
                            }
                        } label: {
                            TabTitle(title: tab.metaData.title)
                            Image("fa-chevron-down")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                .scaledToFit()
                                .frame(width: 8, height: 8)
                                .padding(8)
                                .background(
                                    Circle()
                                        .fill(Color.secondary.opacity(0.2))
                                )
                        }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if case .error = onEnum(of: presenter.state.user) {
                            Button {
                                toServiceSelect()
                            } label: {
                                Text("Login")
                            }
                        } else {
                            Button {
                                toCompose()
                            } label: {
                                Image("fa-pen-to-square")
                                    .font(.title2)
                            }
                        }
                    }
                }
                .navigationBarTitleDisplayMode(.inline)
            }
        }
    }
}
