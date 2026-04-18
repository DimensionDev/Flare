import SwiftUI
@preconcurrency import KotlinSharedUI
import SwiftUIBackports

struct HomeTimelineScreen: View {
    let toServiceSelect: () -> Void
    let toCompose: () -> Void
    let toTabSetting: () -> Void
    let accountType: AccountType
    let toSecondaryMenu: () -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @StateObject private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())

    init(accountType: AccountType, toServiceSelect: @escaping () -> Void, toCompose: @escaping () -> Void, toTabSetting: @escaping () -> Void, toSecondaryMenu: @escaping () -> Void) {
        self.accountType = accountType
        self.toCompose = toCompose
        self.toServiceSelect = toServiceSelect
        self.toTabSetting = toTabSetting
        self.toSecondaryMenu = toSecondaryMenu
        self._presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter(accountType: accountType)))
    }

    var body: some View {
        GeometryReader { proxy in
            StateView(state: presenter.state.tabState) { state in
                let tabs: [TimelineTabItem] = state.cast(TimelineTabItem.self)
                let tab = tabs[min(max(selectedTabIndex, 0), tabs.count - 1)]
                ZStack {
                    TimelineScreen(tabItem: tab, allowGalleryMode: true)
                        .id(tab.key)
                }
                .onChange(of: state.count, { oldValue, newValue in
                    if !(tabs.indices.contains(selectedTabIndex)) {
                        selectedTabIndex = 0
                    }
                })
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        StateView(state: activeAccountPresenter.state.user) { user in
                            if user.avatar.isEmpty {
                                Image(.faGear)
                            } else {
                                if #available(iOS 26.0, *) {
                                    AvatarView(data: user.avatar)
                                } else {
                                    AvatarView(data: user.avatar)
                                        .frame(width: 24, height: 24)
                                }
                            }
                        } errorContent: { _ in
                            Image(.faGear)
                        } loadingContent: {
                            Image(.faGear)
                        }.onTapGesture {
                            toSecondaryMenu()
                        }
                    }
                    ToolbarItem(placement: horizontalSizeClass == .regular ? .automatic : .title) {
                        Menu {
                            ForEach(0..<tabs.count, id: \.self) { index in
                                let item = tabs[index]
                                Toggle(isOn: Binding(get: {
                                    selectedTabIndex == index
                                }, set: { value in
                                    if value {
                                        selectedTabIndex = index
                                    }
                                })) {
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
                            HStack(spacing: 0) {
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
                                    .scaleEffect(0.66)
                            }
                        }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if case .success(let isLoggedIn) = onEnum(of: loggedInPresenter.state.isLoggedIn), !isLoggedIn.data.boolValue {
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
