import Awesome
import FontAwesomeSwiftUI
import Generated
import os
import os.log
import shared
import SwiftUI

struct HomeTabViewContentV2: View {
    @Environment(FlareRouter.self) private var router
    @Environment(FlareMenuState.self) private var menuState
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings
    @EnvironmentObject private var timelineState: TimelineExtState

    let accountType: AccountType

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
            TabView(selection: Binding(
                get: { router.selectedTab },
                set: { router.selectedTab = $0 }
            )) {
                Tab(value: FlareHomeTabs.menu) {
                    FlareTabItem(tabType: .menu) {
                        FlareMenuView()
                    }
                    .environment(menuState)
                }
                .customizationID("tabview_menu")

                Tab(value: FlareHomeTabs.timeline) {
                    FlareTabItem(tabType: .timeline) {
                        HomeScreenSwiftUI(
                            accountType: accountType,
                            onSwitchToMenuTab: {
                                router.selectedTab = .menu
                            }
                        )
                    }
                    .environment(menuState)
                }
                .customizationID("tabview_timeline")

                // Notification Tab (仅非访客用户)
                if !(accountType is AccountTypeGuest) {
                    Tab(value: FlareHomeTabs.notification) {
                        FlareTabItem(tabType: .notification) {
                            NotificationTabScreen(accountType: accountType)
                        }
                        .environment(menuState)
                    }
                    .customizationID("tabview_notification")
                }

                // Discover Tab
                Tab(value: FlareHomeTabs.discover) {
                    FlareTabItem(tabType: .discover) {
                        DiscoverTabScreen(accountType: accountType)
                    }
                    .environment(menuState)
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
                        .environment(menuState)
                    }
                    .customizationID("tabview_profile")
                }
            }
            .toolbar(.hidden, for: .tabBar)
            .ignoresSafeArea(.container, edges: .bottom)
            .padding(.bottom, -120)

            if !menuState.isCustomTabBarHidden {
                VStack(spacing: 0) {
                    FlareTabBarV2(
                        accountType: accountType
                    )

                    Rectangle()
                        .fill(.clear)
                        .frame(height: 0)
                        .ignoresSafeArea(.container, edges: .bottom)
                }
            }

            if router.selectedTab == .timeline, router.navigationDepth == 0 {
                VStack(spacing: 12) {
                    if !appSettings.appearanceSettings.hideScrollToTopButton {
                        FloatingScrollToTopButton()
                    }

                    FloatingDisplayTypeButton(isVisible: .constant(true))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, FloatingButtonConfig.screenPadding)
                .padding(.bottom, FloatingButtonConfig.bottomExtraMargin)
            }
        }
        .background(theme.primaryBackgroundColor)
        .foregroundColor(theme.labelColor)
        .onAppear {
            if router.selectedTab != .timeline {
                router.selectedTab = .timeline
            }
        }
    }
}
