import SwiftUI
@preconcurrency import KotlinSharedUI
import SwiftUIBackports

struct HomeTimelineScreen: View {
    let toServiceSelect: () -> Void
    let toCompose: () -> Void
    let toTabSetting: () -> Void
    let toSecondaryMenu: () -> Void
    let onNavigate: (Route) -> Void
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @StateObject private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())

    init(
        toServiceSelect: @escaping () -> Void,
        toCompose: @escaping () -> Void,
        toTabSetting: @escaping () -> Void,
        toSecondaryMenu: @escaping () -> Void,
        onNavigate: @escaping (Route) -> Void
    ) {
        self.toCompose = toCompose
        self.toServiceSelect = toServiceSelect
        self.toTabSetting = toTabSetting
        self.toSecondaryMenu = toSecondaryMenu
        self.onNavigate = onNavigate
        self._presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter()))
    }

    var body: some View {
        GeometryReader { proxy in
            StateView(state: presenter.state.tabState) { state in
                let tabs: [TimelineTabItemV2] = state.cast(TimelineTabItemV2.self)
                if tabs.isEmpty {
                    ContentUnavailableView("tab_settings_title", systemImage: "square.grid.2x2")
                        .toolbar {
                            ToolbarItem(placement: .topBarLeading) {
                                Image(.faGear)
                                    .onTapGesture {
                                        toSecondaryMenu()
                                    }
                            }
                            ToolbarItem(placement: .primaryAction) {
                                Button {
                                    toTabSetting()
                                } label: {
                                    Image("fa-plus")
                                }
                            }
                        }
                } else {
                    if globalAppearance.deckMode && horizontalSizeClass == .regular {
                        DeckTimelineLayout(
                            tabs: tabs,
                            baseTimelineAppearance: timelineAppearance,
                            columnWidth: min(max(proxy.size.width * 0.42, 320), 420),
                            toTabSetting: toTabSetting,
                            onGlobalRoute: onNavigate
                        )
                        .toolbar {
                            leadingToolbarContent
                            ToolbarItemGroup(placement: .topBarTrailing) {
                                Button {
                                    toTabSetting()
                                } label: {
                                    Image("fa-sliders")
                                }
                                composeToolbarButton
                            }
                        }
                    } else {
                        let tab = tabs[min(max(selectedTabIndex, 0), tabs.count - 1)]
                        ZStack {
                            TimelineScreen(tabItem: tab, allowGalleryMode: true)
                                .environment(\.timelineAppearance, tab.resolveTimelineAppearance(base: timelineAppearance))
                                .id(tab.id)
                        }
                        .onChange(of: state.count, { oldValue, newValue in
                            if !(tabs.indices.contains(selectedTabIndex)) {
                                selectedTabIndex = 0
                            }
                        })
                        .toolbar {
                            leadingToolbarContent
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
                                                TimelineTabTitle(title: item.title)
                                            } icon: {
                                                TabIcon(tabItem: item)
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
                                        TimelineTabTitle(title: tab.title)
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
                                composeToolbarButton
                            }
                        }
                        .navigationBarTitleDisplayMode(.inline)
                    }
                }
            }
        }
    }

    @ToolbarContentBuilder
    private var leadingToolbarContent: some ToolbarContent {
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
    }

    @ViewBuilder
    private var composeToolbarButton: some View {
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

private struct DeckTimelineLayout: View {
    let tabs: [TimelineTabItemV2]
    let baseTimelineAppearance: TimelineAppearance
    let columnWidth: CGFloat
    let toTabSetting: () -> Void
    let onGlobalRoute: (Route) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 0) {
                ForEach(0..<tabs.count, id: \.self) { index in
                    let tab = tabs[index]
                    DeckTimelineColumnRoot(
                        tabItem: tab,
                        baseTimelineAppearance: baseTimelineAppearance,
                        toTabSetting: toTabSetting
                    )
                    .environment(\.horizontalSizeClass, .compact)
                    .ignoresSafeArea()
                    .frame(width: columnWidth)
                    .frame(maxHeight: .infinity)
                    if index < tabs.count - 1 {
                        Divider()
                            .ignoresSafeArea()
                    }
                }
            }
        }
        .scrollIndicators(.hidden)
    }
}

private struct DeckTimelineColumnRoot: View {
    let tabItem: TimelineTabItemV2
    let baseTimelineAppearance: TimelineAppearance
    let toTabSetting: () -> Void

    var body: some View {
        TimelineScreen(tabItem: tabItem, allowGalleryMode: true)
            .safeAreaInset(edge: .bottom) {
                Label {
                    TimelineTabTitle(title: tabItem.title)
                } icon: {
                    TabIcon(tabItem: tabItem)
                }
                .padding()
                .backport
                .glassEffect()
            }
            .environment(\.timelineAppearance, tabItem.resolveTimelineAppearance(base: baseTimelineAppearance))
            .id(tabItem.id)
    }
}
