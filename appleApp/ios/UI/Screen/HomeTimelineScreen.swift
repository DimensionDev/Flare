import SwiftUI
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore
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
    @State private var selectedTabId: String?
    @Namespace private var selectedTabIndicatorNamespace
    @StateObject private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())
    @StateObject private var canComposePresenter = KotlinPresenter(presenter: CanComposePresenter())

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
                let tabs: [UiTimelineTabItem] = state.cast(UiTimelineTabItem.self)
                if tabs.isEmpty {
                    ContentUnavailableView("tab_settings_title", systemImage: "square.grid.2x2")
                        .toolbar {
                            ToolbarItem(placement: .topBarLeading) {
                                Image(fontAwesome: .gear)
                                    .onTapGesture {
                                        toSecondaryMenu()
                                    }
                            }
                            ToolbarItem(placement: .primaryAction) {
                                Button {
                                    toTabSetting()
                                } label: {
                                    Image(fontAwesome: .plus)
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
                                    Image(fontAwesome: .sliders)
                                }
                                composeToolbarButton
                            }
                        }
                    } else {
                        let tab = selectedTabId.flatMap { id in
                            tabs.first { $0.id == id }
                        } ?? tabs[0]
                        ZStack {
                            TimelineScreen(tabItem: tab, allowGalleryMode: true, isHomeTimeline: true)
                                .environment(\.timelineAppearance, tab.resolveTimelineAppearance(base: timelineAppearance))
                                .id(tab.id)
                        }
                        .onChange(of: tabs.map { $0.id }, initial: true) { _, tabIds in
                            if let selectedTabId, tabIds.contains(selectedTabId) {
                                return
                            }
                            selectedTabId = tabIds.first
                        }
                        .toolbar {
                            leadingToolbarContent
                            if horizontalSizeClass == .compact {
                                ToolbarItem(placement: .title) {
                                    Label {
                                        TimelineTabTitle(title: tab.title)
                                    } icon: {
                                        TabIcon(tabItem: tab)
                                    }
                                    .labelStyle(.titleAndIcon)
                                    .id(tab.id)
                                }
                                ToolbarTitleMenu {
                                    ForEach(tabs, id: \.id) { item in
                                        Toggle(isOn: Binding(get: {
                                            tab.id == item.id
                                        }, set: { value in
                                            if value {
                                                selectedTabId = item.id
                                            }
                                        })) {
                                            Label {
                                                TimelineTabTitle(title: item.title)
                                            } icon: {
                                                TabIcon(tabItem: item)
                                                    .frame(width: 24)
                                                    .scaledToFit()
                                            }
                                            .labelStyle(.titleAndIcon)
                                        }
                                    }
                                    Divider()
                                    Button {
                                        toTabSetting()
                                    } label: {
                                        Label {
                                            Text("tab_settings_add_tab")
                                        } icon: {
                                            Image(fontAwesome: .plus)
                                        }
                                    }
                                }
                            } else {
                                ToolbarItem(placement: .automatic) {
                                    ScrollView(.horizontal) {
                                        HStack {
                                            ForEach(tabs, id: \.id) { item in
                                                Button {
                                                    selectedTabId = item.id
                                                } label: {
                                                    Label {
                                                        TimelineTabTitle(title: item.title)
                                                    } icon: {
                                                        TabIcon(tabItem: item)
                                                            .frame(width: 24)
                                                            .scaledToFit()
                                                    }
                                                    .labelStyle(.titleAndIcon)
                                                }
                                                .padding(.bottom, 9)
                                                .ignoresSafeArea(edges: .bottom)
                                                .safeAreaInset(edge: .bottom) {
                                                    if tab.id == item.id {
                                                        Capsule()
                                                            .fill(Color.accentColor)
                                                            .frame(width: 18, height: 3)
                                                            .matchedGeometryEffect(id: "selectedTabIndicator", in: selectedTabIndicatorNamespace)
                                                    } else {
                                                        Capsule()
                                                            .frame(width: 0, height: 3)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(.horizontal)
                                        .animation(.spring(response: 0.25, dampingFraction: 0.85), value: selectedTabId)
                                    }
                                }
                                if #available(iOS 26.0, *) {
                                    ToolbarSpacer()
                                }
                                ToolbarItem(placement: .primaryAction) {
                                    Button {
                                        toTabSetting()
                                    } label: {
                                        Image(fontAwesome: .sliders)
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
                if user.avatar == nil {
                    Image(fontAwesome: .gear)
                } else {
                    if #available(iOS 26.0, *) {
                        AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                    } else {
                        AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                            .frame(width: 24, height: 24)
                    }
                }
            } errorContent: { _ in
                Image(fontAwesome: .gear)
            } loadingContent: {
                Image(fontAwesome: .gear)
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
        } else if case .success(let canCompose) = onEnum(of: canComposePresenter.state.canCompose), canCompose.data.boolValue {
            Button {
                toCompose()
            } label: {
                Image(fontAwesome: .penToSquare)
                    .font(.title2)
            }
        }
    }
}

private struct DeckTimelineLayout: View {
    let tabs: [UiTimelineTabItem]
    let baseTimelineAppearance: TimelineAppearance
    let columnWidth: CGFloat
    let toTabSetting: () -> Void
    let onGlobalRoute: (Route) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(alignment: .top, spacing: 0) {
                ForEach(tabs, id: \.id) { tab in
                    DeckTimelineColumnRoot(
                        tabItem: tab,
                        baseTimelineAppearance: baseTimelineAppearance,
                        toTabSetting: toTabSetting
                    )
                    .environment(\.horizontalSizeClass, .compact)
                    .ignoresSafeArea()
                    .frame(width: columnWidth)
                    .frame(maxHeight: .infinity)
                    if tab.id != tabs.last?.id {
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
    let tabItem: UiTimelineTabItem
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
