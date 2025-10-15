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
                let tab = tabs[selectedTabIndex]
                ZStack {
                    TimelineScreen(tabItem: tab)
                        .id(tab.key)
                }
                .toolbar {
                    ToolbarItem(placement: .automatic) {
                        ScrollView(.horizontal) {
                            HStack(
                                spacing: 8,
                            ) {
                                ForEach(0..<tabs.count, id: \.self) { index in
                                    let tab = tabs[index]
                                    Label {
                                        TabTitle(title: tab.metaData.title)
                                            .font(.subheadline)
                                    } icon: {
                                        TabIcon(icon: tab.metaData.icon, accountType: tab.account, size: 20)
                                    }
                                    .onTapGesture {
                                        withAnimation(.spring) {
                                            selectedTabIndex = index
                                        }
                                    }
                                    .padding(.horizontal)
                                    .padding(.vertical, 8)
                                    .foregroundStyle(selectedTabIndex == index ? Color.white : .primary)
                                    .backport
                                    .glassEffect(selectedTabIndex == index ? .tinted(.accentColor) : .regular, in: .capsule, fallbackBackground: selectedTabIndex == index ? Color.accentColor : Color(.systemBackground))
                                }
                                if case .success = onEnum(of: presenter.state.user) {
                                    Button {
                                        toTabSetting()
                                    } label: {
                                        Image("fa-plus")
                                    }
                                    .backport
                                    .glassButtonStyle()
                                }
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                        }
                        .scrollIndicators(.hidden)
                    }
                    if #available(iOS 26.0, *) {
                        ToolbarSpacer(.fixed)
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
