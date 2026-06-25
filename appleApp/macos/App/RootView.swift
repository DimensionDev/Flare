import AppKit
import FlareAppleCore
import FlareAppleUI
import Foundation
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports
import SwiftUIIntrospect

struct RootView: View {
    @Environment(\.openWindow) private var openWindow
    @Environment(\.openSettings) private var openSettings
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())
    @StateObject private var secondaryTabPresenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @StateObject private var homeTimelineWithTabsPresenter = KotlinPresenter(presenter: HomeTimelineWithTabsPresenter())
    @StateObject private var allNotificationBadgePresenter = KotlinPresenter(presenter: AllNotificationBadgePresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())
    @StateObject private var aiAgentEnabledPresenter = KotlinPresenter(presenter: AiAgentEnabledPresenter())
    @ObservedObject private var inAppNotification = SwiftInAppNotification.shared
    @State private var selectedTab: Route?
    @State private var homeExpanded: Bool = true
    @State private var showDraftBoxPopover = false
    @State private var showLogin = false
    @State private var homeSidebarTabEditor: HomeSidebarTabEditor?

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                List(selection: $selectedTab) {
                    StateView(state: homeTabsPresenter.state.tabs) { tabs in
                        let homeTabs: [HomeTabsPresenterStateHomeTabs] = tabs.cast(HomeTabsPresenterStateHomeTabs.self)
                        ForEach(homeTabs, id: \.name) { tab in
                            if tab == .home, case .success(let data) = onEnum(of: homeTimelineWithTabsPresenter.state.tabState) {
                                let tabs: [UiTimelineTabItem] = data.data.cast(UiTimelineTabItem.self)
                                HomeSidebarTabsSection(
                                    title: tab.macOSTitle,
                                    icon: tab.macOSIcon,
                                    liveTabs: tabs,
                                    selectedTab: $selectedTab,
                                    isExpanded: $homeExpanded,
                                    onEditTab: { tab, onSave in
                                        homeSidebarTabEditor = HomeSidebarTabEditor(
                                            tab: tab,
                                            onSave: onSave
                                        )
                                    }
                                )
                            } else if tab == .notifications {
                                DisclosureGroup {
                                    ForEach(allNotificationBadgePresenter.state.notifications, id: \.profile.key) { item in
                                        UserOnelineView(data: item.profile)
                                            .badge(Int(item.badge))
                                            .tag(Route.accountNotification(item.profile.key))
                                    }
                                } label: {
                                    Label {
                                        Text(tab.macOSTitle)
                                    } icon: {
                                        Image(fontAwesome: tab.macOSIcon)
                                    }
                                    .badge(Int(allNotificationBadgePresenter.state.count))
                                }
                            } else {
                                Label {
                                    Text(tab.macOSTitle)
                                } icon: {
                                    Image(fontAwesome: tab.macOSIcon)
                                }
                                .tag(tab.macOSInitialRoute)
                            }
                        }
                    }

                    Label {
                        Text("local_history_title")
                    } icon: {
                        Image(fontAwesome: .clockRotateLeft)
                    }
                    .tag(Route.localHistory)

                    if case .success(let data) = onEnum(of: secondaryTabPresenter.state.items) {
                        let items: [SecondaryTabsPresenter.Item] = data.data.cast(SecondaryTabsPresenter.Item.self)
                        if !items.isEmpty {
                            Section {
                                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                                    DisclosureGroup {
                                        ForEach(item.tabs, id: \.self) { (tab: SecondaryTabsPresenter.Tab) in
                                            if let route = route(for: tab) {
                                                Label {
                                                    Text(tab.title.text)
                                                } icon: {
                                                    Image(fontAwesome: tab.icon.fontAwesomeIcon)
                                                }
                                                .tag(route)
                                            }
                                        }
                                    } label: {
                                        StateView(state: item.user) { user in
                                            UserOnelineView(data: user)
                                        }
                                    }
                                }
                            } header: {
                                HStack {
                                    Text("macos_sidebar_accounts")
                                    Spacer()
                                    Button {
                                        showLogin = true
                                    } label: {
                                        Image(fontAwesome: .plus)
                                    }
                                    .buttonStyle(.plain)
                                    Spacer()
                                        .frame(width: 16)
                                }
                            }
                        }
                    }
                }
                .listStyle(.sidebar)

                MacSidebarPinnedActions(
                    showDraftBoxPopover: $showDraftBoxPopover,
                    showsAgentHistory: aiAgentEnabledPresenter.state.enabled,
                    openDraft: { groupId in
                        MacComposeWindowCoordinator.shared.openDraft(
                            groupId: groupId,
                            openWindow: openWindow
                        )
                    },
                    openRssManagement: {
                        openWindow(id: MacWindowID.rssManagement)
                    },
                    openAgentHistory: {
                        MacAgentWindowCoordinator.shared.open(route: .agentHistory, openWindow: openWindow)
                    },
                    openAppSettings: {
                        openSettings()
                    }
                )
            }
            .toolbar(removing: .sidebarToggle)
            .frame(minWidth: 100, maxWidth: 280)
            .navigationSplitViewColumnWidth(min: 100, ideal: 200, max: 280)
        } detail: {
            if let selectedTab {
                Router(initialRoute: selectedTab)
                    .navigationSplitViewColumnWidth(ideal: 380, max: 480)
                    .id(selectedTab)
                    .toolbar {
                        if case .success(let data) = onEnum(of: loggedInPresenter.state.isLoggedIn), !data.data.boolValue {
                            ToolbarItem(placement: .primaryAction) {
                                Button {
                                    showLogin = true
                                } label: {
                                    Text("login_button")
                                }
                            }
                        }
                    }
                    .overlay(alignment: .top) {
                        LoginExpiredToastOverlay(
                            toast: inAppNotification.loginExpiredToast,
                            onDismiss: { id in
                                inAppNotification.dismissLoginExpiredToast(id: id)
                            }
                        )
                    }
            }
        }
        .introspect(.navigationSplitView, on: .macOS(.v13, .v14, .v15, .v26, .v27)) { splitview in
            if let delegate = splitview.delegate as? NSSplitViewController {
                // Disables the ability to collapse the sidebar via dragging
                delegate.splitViewItems.first?.canCollapse = false
                delegate.splitViewItems.first?.canCollapseFromWindowResize = false
            }
        }
        .sheet(isPresented: $showLogin) {
            NavigationStack {
                ServiceSelectionScreen(toHome: { showLogin = false })
            }
        }
        .sheet(item: $homeSidebarTabEditor) { editor in
            HomeSidebarTabEditSheet(
                tab: editor.tab,
                onCancel: {
                    homeSidebarTabEditor = nil
                },
                onSave: { updated in
                    editor.onSave(updated)
                    homeSidebarTabEditor = nil
                }
            )
            .frame(minWidth: 820, idealWidth: 860, minHeight: 600, idealHeight: 660)
        }
    }
}

private struct HomeSidebarTabEditor: Identifiable {
    let id = UUID()
    let tab: UiTimelineTabItem
    let onSave: (UiTimelineTabItem) -> Void
}

private struct LoginExpiredToastOverlay: View {
    let toast: LoginExpiredToast?
    let onDismiss: (UUID) -> Void

    @State private var showNotification = false

    var body: some View {
        Group {
            if let toast, showNotification {
                LoginExpiredToastView(accountKey: toast.accountKey)
                .transition(.move(edge: .top).combined(with: .opacity))
                .onTapGesture {
                    dismiss(toast)
                }
            }
        }
        .padding(.top, 14)
        .padding(.horizontal, 16)
        .task(id: toast?.id) {
            guard toast != nil else {
                showNotification = false
                return
            }

            withAnimation {
                showNotification = true
            }

            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled else { return }
            guard let toast else { return }
            dismiss(toast)
        }
    }

    private func dismiss(_ toast: LoginExpiredToast) {
        withAnimation {
            showNotification = false
        }
        Task {
            try? await Task.sleep(nanoseconds: 180_000_000)
            guard !Task.isCancelled else { return }
            onDismiss(toast.id)
        }
    }
}

private struct LoginExpiredToastView: View {
    let accountKey: String?

    private var message: String {
        guard let accountKey, !accountKey.isEmpty else {
            return NSLocalizedString("notification_login_expired", comment: "")
        }

        return String.localizedStringWithFormat(
            NSLocalizedString("notification_login_expired %@", comment: ""),
            accountKey
        )
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(fontAwesome: .circleExclamation)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.red)
                .frame(width: 20, height: 20)

            Text(message)
                .font(.callout)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: 420, alignment: .leading)
        .background(.background, in: Capsule())
        .contentShape(Capsule())
        .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
    }
}

private struct MacSidebarPinnedActions: View {
    @Binding var showDraftBoxPopover: Bool
    let showsAgentHistory: Bool
    let openDraft: (String) -> Void
    let openRssManagement: () -> Void
    let openAgentHistory: () -> Void
    let openAppSettings: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            MacSidebarPinnedIconButton(
                title: "settings_title",
                icon: .gear,
                action: openAppSettings
            )

            Divider()
                .frame(height: 18)
                .padding(.vertical, 8)

            MacSidebarPinnedIconButton(
                title: "settings_rss_management_title",
                icon: .squareRss,
                action: openRssManagement
            )

            Divider()
                .frame(height: 18)
                .padding(.vertical, 8)

            if showsAgentHistory {
                MacSidebarPinnedIconButton(
                    title: "settings_agent_history_title",
                    icon: .robot,
                    action: openAgentHistory
                )

                Divider()
                    .frame(height: 18)
                    .padding(.vertical, 8)
            }

            MacSidebarPinnedIconButton(
                title: "draft_box_title",
                icon: .inbox,
                action: {
                    showDraftBoxPopover.toggle()
                }
            )
            .popover(isPresented: $showDraftBoxPopover, arrowEdge: .trailing) {
                NavigationStack {
                    DraftBoxScreen { groupId in
                        showDraftBoxPopover = false
                        openDraft(groupId)
                    }
                }
                .frame(width: 380, height: 480)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 34)
        .clipShape(actionGroupShape)
        .backport
        .glassEffect(.regularInteractive, in: actionGroupShape, fallbackBackground: .regularMaterial)
        .backport
        .glassEffectContainer(spacing: 0)
        .padding(.horizontal, 8)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .frame(maxWidth: .infinity)
    }

    private var actionGroupShape: RoundedRectangle {
        RoundedRectangle(cornerRadius: 100, style: .continuous)
    }
}

private struct MacSidebarPinnedIconButton: View {
    let title: LocalizedStringKey
    let icon: FontAwesomeIcon
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(fontAwesome: icon)
                .frame(maxWidth: .infinity, minHeight: 30)
                .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .frame(maxWidth: .infinity)
        .buttonStyle(.plain)
        .help(Text(title))
        .accessibilityLabel(Text(title))
    }
}

private func route(for tab: SecondaryTabsPresenter.Tab) -> Route? {
    switch onEnum(of: tab.destination) {
    case .route(let destination):
        return Route.fromDeepLinkRoute(deeplinkRoute: destination.route)
    case .timeline(let destination):
        return .timeline(destination.tabItem)
    }
}
