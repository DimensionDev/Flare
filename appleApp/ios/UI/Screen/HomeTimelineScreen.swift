import SwiftUI
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore
import SwiftUIBackports
import UIKit
import Combine

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
    @StateObject private var changeLogPresenter: KotlinPresenter<ChangeLogPresenterState>
    @StateObject private var changeLogAccessoryHost: ChangeLogAccessoryHost
    private let currentVersion: String

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
        let currentVersion =
            Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        self.currentVersion = currentVersion
        let changeLogPresenter = KotlinPresenter<ChangeLogPresenterState>(
            presenter: ChangeLogPresenter(currentVersion: currentVersion)
        )
        self._changeLogPresenter = .init(
            wrappedValue: changeLogPresenter
        )
        self._changeLogAccessoryHost = .init(
            wrappedValue: ChangeLogAccessoryHost(version: currentVersion) {
                changeLogPresenter.state.dismissChangeLog()
            }
        )
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
                        let resolvedTimelineAppearance =
                            tab.resolveTimelineAppearance(base: timelineAppearance)
                        ZStack {
                            TimelineScreen(
                                tabItem: tab,
                                allowGalleryMode: true,
                                isHomeTimeline: true,
                                accessoryItems: resolvedTimelineAppearance.timelineDisplayMode == .gallery
                                    ? []
                                    : changeLogAccessoryItems
                            )
                                .environment(\.timelineAppearance, resolvedTimelineAppearance)
                                .id(tab.id)
                        }
                        .safeAreaInset(edge: .top, spacing: 0) {
                            if resolvedTimelineAppearance.timelineDisplayMode == .gallery,
                               shouldShowChangeLog {
                                ChangeLogNotice(version: currentVersion) {
                                    changeLogPresenter.state.dismissChangeLog()
                                }
                                .transition(.move(edge: .top).combined(with: .opacity))
                            }
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
                                                .safeAreaInset(edge: .bottom, spacing: 3) {
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

    private var shouldShowChangeLog: Bool {
        guard case .success(let state) = onEnum(
            of: changeLogPresenter.state.shouldShowChangeLog
        ) else {
            return false
        }
        return state.data.boolValue
    }

    private var changeLogAccessoryItems: [UITimelineCollectionViewAccessoryItem] {
        guard shouldShowChangeLog else { return [] }
        return [
            UITimelineCollectionViewAccessoryItem(
                id: "change_log_\(currentVersion)",
                view: changeLogAccessoryHost.view
            ),
        ]
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

private struct ChangeLogNotice: View {
    let version: String
    let onDismiss: () -> Void

    private var message: String {
        let format = String(
            localized: "changelog_current",
            defaultValue: "Version %@:\n• Bug fixes and performance improvements."
        )
        return String(format: format, locale: .current, version)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("changelog_title")
                .font(.headline)
            Text("changelog_message")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(message)
                .font(.body)
                .fixedSize(horizontal: false, vertical: true)
            Button("Ok", action: onDismiss)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(uiColor: .separator).opacity(0.35), lineWidth: 0.5)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
}

private final class ChangeLogAccessoryHost: ObservableObject {
    let view = ChangeLogHostedAccessoryView()

    init(version: String, onDismiss: @escaping () -> Void) {
        view.update(
            AnyView(
                ChangeLogNotice(
                    version: version,
                    onDismiss: onDismiss
                )
            )
        )
    }
}

private final class ChangeLogHostedAccessoryView: UIView {
    private let host = UIHostingController(rootView: AnyView(EmptyView()))

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    func update(_ rootView: AnyView) {
        host.rootView = rootView
        host.view.invalidateIntrinsicContentSize()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            host.willMove(toParent: nil)
            host.removeFromParent()
        } else if let parent = findParentViewController(), host.parent !== parent {
            host.willMove(toParent: nil)
            host.removeFromParent()
            parent.addChild(host)
            host.didMove(toParent: parent)
        }
    }

    private func commonInit() {
        backgroundColor = .clear
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 16.0, *) {
            host.sizingOptions = [.intrinsicContentSize]
        }
        addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: topAnchor),
            host.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            host.view.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    private func findParentViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let viewController = current as? UIViewController {
                return viewController
            }
            responder = current.next
        }
        return nil
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
