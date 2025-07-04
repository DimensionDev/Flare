import Awesome
import FontAwesomeSwiftUI
import Generated
import os
import os.log
import shared
import SwiftUI

/// HomeTabViewContentV2 - 使用FlareTabBarV2的新版本主TabView容器
/// 基于Observable架构，集成FlareTabBarV2组件
struct HomeTabViewContentV2: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var appState
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings

    let accountType: AccountType
    @State private var scrollToTopTrigger = false
    @State private var showFloatingButton = false

    private var visibleTabs: [FlareHomeTabs] {
        var tabs: [FlareHomeTabs] = [.menu, .timeline]
        if !(accountType is AccountTypeGuest) { tabs.append(.notification) }
        tabs.append(.discover)
        if !(accountType is AccountTypeGuest) { tabs.append(.profile) }
        return tabs
    }

    init(accountType: AccountType) {
        self.accountType = accountType

        os_log("[HomeTabViewContentV2] Initialized for account type: %{public}@",
               log: .default, type: .debug,
               String(describing: accountType))
    }

    var body: some View {
        os_log(
            "[HomeTabViewContentV2] Using router: %{public}@, selectedTab: %{public}@",
            log: .default, type: .debug,
            String(describing: ObjectIdentifier(router)),
            String(describing: router.selectedTab)
        )

        return ZStack(alignment: .bottom) {
            // 主TabView内容 - 恢复TabView架构以保持状态
            TabView(selection: Binding(
                get: { router.selectedTab },
                set: { router.selectedTab = $0 }
            )) {
                // Menu Tab
                Tab(value: FlareHomeTabs.menu) {
                    FlareTabItem(tabType: .menu) {
                        FlareMenuView()
                    }
                    .environment(appState)
                }
                .customizationID("tabview_menu")

                // Timeline Tab
                Tab(value: FlareHomeTabs.timeline) {
                    FlareTabItem(tabType: .timeline) {
                        HomeTabScreenSwiftUI(
                            accountType: accountType,
                            scrollToTopTrigger: $scrollToTopTrigger,
                            showFloatingButton: $showFloatingButton,
                            onSwitchToMenuTab: {
                                router.selectedTab = .menu
                            }
                        )
                    }
                    .environment(appState)
                }
                .customizationID("tabview_timeline")

                // Notification Tab (仅非访客用户)
                if !(accountType is AccountTypeGuest) {
                    Tab(value: FlareHomeTabs.notification) {
                        FlareTabItem(tabType: .notification) {
                            NotificationTabScreen(accountType: accountType)
                        }
                        .environment(appState)
                    }
                    .customizationID("tabview_notification")
                }

                // Discover Tab
                Tab(value: FlareHomeTabs.discover) {
                    FlareTabItem(tabType: .discover) {
                        DiscoverTabScreen(accountType: accountType)
                    }
                    .environment(appState)
                }
                .customizationID("tabview_discover")

                // Profile Tab (仅非访客用户)
                if !(accountType is AccountTypeGuest) {
                    Tab(value: FlareHomeTabs.profile) {
                        FlareTabItem(tabType: .profile) {
                            ProfileTabScreenUikit(
                                accountType: accountType,
                                userKey: nil,
                                toProfileMedia: { _ in }
                            )
                        }
                        .environment(appState)
                    }
                    .customizationID("tabview_profile")
                }
            }
            .toolbar(.hidden, for: .tabBar) // 隐藏系统TabBar

            // 自定义TabBar - 使用FlareTabBarV2
            if !appState.isCustomTabBarHidden {
                VStack(spacing: 0) {
                    FlareTabBarV2(
                        accountType: accountType,
                        scrollToTopTrigger: $scrollToTopTrigger
                    )

                    // 底部安全区域
                    Rectangle()
                        .fill(theme.primaryBackgroundColor)
                        .frame(height: 0)
                        .ignoresSafeArea(.container, edges: .bottom)
                }
            }

            // 浮动滚动到顶部按钮
            if router.selectedTab == .timeline, !appSettings.appearanceSettings.hideScrollToTopButton {
                FloatingScrollToTopButton(
                    isVisible: $showFloatingButton,
                    scrollToTopTrigger: $scrollToTopTrigger
                )
            }
        }
        .background(theme.primaryBackgroundColor)
        .foregroundColor(theme.labelColor)
        .onAppear {
            // 确保router的selectedTab与当前状态同步
            if router.selectedTab != .timeline {
                router.selectedTab = .timeline
            }
        }
    }
}

// MARK: - Preview

#Preview {
    @Previewable @State var router = FlareRouter()
    @Previewable @State var appState = FlareAppState()
    @Previewable @State var theme = FlareTheme.shared

    HomeTabViewContentV2(accountType: AccountTypeGuest())
        .environment(router)
        .environment(appState)
        .environment(theme)
}
