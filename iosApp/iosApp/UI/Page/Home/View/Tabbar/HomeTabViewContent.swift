import Awesome
import FontAwesomeSwiftUI
import Generated
import os
import os.log
import shared
import SwiftUI

struct HomeTabViewContent: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State private var selectedTab: FlareHomeTabs = .timeline

    let accountType: AccountType

    @StateObject private var appState = FlareAppState()
    @EnvironmentObject private var router: FlareRouter

    @Namespace private var tabBarNamespace

    @Environment(FlareTheme.self) private var theme

    @State private var scrollToTopTrigger = false

    @State private var showFloatingButton = false

    private var visibleTabs: [FlareHomeTabs] {
        var tabs: [FlareHomeTabs] = [.menu, .timeline]
        if !(accountType is AccountTypeGuest) { tabs.append(.notification) }
        tabs.append(.discover)
        if !(accountType is AccountTypeGuest) { tabs.append(.profile) }
        return tabs
    }

    var body: some View {
        os_log(
            "[HomeContent] Using router: %{public}@, selectedTab: %{public}@",
            log: .default, type: .debug,
            String(describing: ObjectIdentifier(router)),
            String(describing: selectedTab)
        )

        return ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                Tab(value: .menu) {
                    FlareTabItem(router: router, tabType: .menu) { _ in
                        FlareMenuView()
                    }.id("FlareTabItem_menu")
                        .environmentObject(appState)
                }.customizationID("tabview_menu")

                Tab(value: .timeline) {
                    FlareTabItem(router: router, tabType: .timeline) { _ in
                        HomeTabScreenSwiftUI(
                            accountType: accountType,
                            scrollToTopTrigger: $scrollToTopTrigger,
                            showFloatingButton: $showFloatingButton,
                            onSwitchToMenuTab: {
                                withAnimation {
                                    selectedTab = .menu
                                }
                            }
                        )
                    }.id("FlareTabItem_home")
                        .environmentObject(appState)
                }.customizationID("tabview_home")

                if !(accountType is AccountTypeGuest) {
                    Tab(value: .notification) {
                        FlareTabItem(router: router, tabType: .notification) {
                            _ in NotificationTabScreen(accountType: accountType)
                        }
                        .id("FlareTabItem_notification")
                        .environmentObject(
                            appState)
                    }.customizationID("tabview_notification")
                }

                Tab(value: .discover) {
                    FlareTabItem(router: router, tabType: .discover) { _ in
                        DiscoverTabScreen(
                            accountType: accountType
                        )
                    }.id("FlareTabItem_discover")
                        .environmentObject(appState)
                }.customizationID("tabview_discover")

                if !(accountType is AccountTypeGuest) {
                    Tab(value: .profile) {
                        FlareTabItem(router: router, tabType: .profile) { _ in
                            ProfileTabScreenUikit(
                                accountType: accountType, userKey: nil,
                                toProfileMedia: { _ in }
                            )
                        }
                        .id("FlareTabItem_profile")
                        .environmentObject(appState)
                    }.customizationID("tabview_profile")
                }
            }
            .toolbar(.hidden, for: .tabBar)
            .padding(.bottom, -120) // bottom bar 的 高度

            if !appState.isCustomTabBarHidden {
                customTabBar()
                    .padding(.horizontal)
                    .padding(.bottom, 0)
            }

            if selectedTab == .timeline {
                FloatingScrollToTopButton(
                    isVisible: $showFloatingButton,
                    scrollToTopTrigger: $scrollToTopTrigger
                )
            }
        }

        .background(theme.primaryBackgroundColor)
        .foregroundColor(theme.labelColor)
    }

    @ViewBuilder
    private func customTabBar() -> some View {
        HStack {
            Spacer()

            HStack(spacing: 0) {
                ForEach(visibleTabs, id: \.self) { tab in
                    tabBarItem(for: tab)
                        .padding(
                            .horizontal,
                            calculateHorizontalPadding(
                                for: visibleTabs.count
                                    + (accountType is AccountTypeGuest ? 0 : 1))
                        )

                    if tab == .notification, !(accountType is AccountTypeGuest) {
                        composeButton()
                            .id("tabview_compose")
                            .padding(
                                .horizontal,
                                calculateHorizontalPadding(
                                    for: visibleTabs.count + 1)
                            )
                    }
                }
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            // 应用 fixedSize 强制内层 HStack 根据内容收缩宽度
            .fixedSize(horizontal: true, vertical: false)
            // 圆角矩形 + 毛玻璃背景
            .background(
                .ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20)
            )
            Spacer()
        }
    }

    @ViewBuilder
    private func tabBarItem(for tab: FlareHomeTabs) -> some View {
        Button {
            print("[HomeTabContent] Tab button tapped: \(tab), selectedTab: \(selectedTab)")

            if selectedTab == tab {
                print("[HomeTabContent] Same tab tapped again: \(tab)")
                if tab == .timeline {
                    let oldValue = scrollToTopTrigger
                    scrollToTopTrigger.toggle()
                    print("[HomeTabContent] Timeline scroll trigger toggled: \(oldValue) -> \(scrollToTopTrigger)")
                }
            } else {
                selectedTab = tab
                print("[HomeTabContent] Tab switched to: \(tab)")
            }
        } label: {
            VStack(spacing: 2) {
                icon(for: tab, isActive: selectedTab == tab)
                    .foregroundColor(
                        selectedTab == tab
                            ? Color.accentColor : Color(.secondaryLabel)
                    )
                    .frame(width: 24, height: 24)
                    .background(
                        ZStack {
                            if selectedTab == tab {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.accentColor.opacity(0.20))
                                    .frame(width: 40, height: 40)
                                    // 平滑过渡动画 感觉卡
                                    .matchedGeometryEffect(
                                        id: "selectedTabIndicator",
                                        in: tabBarNamespace
                                    )
                            }
                        }
                    )
            }
        }
        .frame(height: 55) //   tabbar 的高度
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func composeButton() -> some View {
        Button {
            ComposeManager.shared.showNewCompose(accountType: accountType)
            os_log(
                "[HomeContent] Compose button tapped", log: .default,
                type: .debug
            )
        } label: {
            Asset.Tab.compose.swiftUIImage
                .resizable()
                .renderingMode(.template)
                .foregroundColor(.white)
                .scaledToFit()
                .frame(width: 13, height: 13)
                .padding(12)
                .background(Circle().fill(Color.accentColor))
                .shadow(radius: 3)
        }
    }

    @ViewBuilder
    private func icon(for tab: FlareHomeTabs, isActive _: Bool) -> some View {
        switch tab {
        case .menu:
            Text(AwesomeIcon.bars.rawValue).font(
                .awesome(style: .solid, size: 20))
        case .timeline:
            Text(AwesomeIcon.home.rawValue).font(
                .awesome(style: .solid, size: 20))
        case .notification:
            Text(AwesomeIcon.bell.rawValue).font(
                .awesome(style: .solid, size: 20))
        case .discover:
            Text(AwesomeIcon.search.rawValue).font(
                .awesome(style: .solid, size: 20))
        case .profile:
            Text(AwesomeIcon.user.rawValue).font(
                .awesome(style: .solid, size: 20))
        case .compose:
            Asset.Tab.compose.swiftUIImage.renderingMode(.template).font(
                .awesome(style: .solid, size: 20))
        }
    }

    private func calculateHorizontalPadding(for itemCount: Int) -> CGFloat {
        switch itemCount {
        case 3: 22
        case 4: 18
        case 5: 15
        case 6: 12
        case 7: 10
        default: 10
        }
    }
}
