import FontAwesomeSwiftUI
import Generated
import os.log
import shared
import SwiftUI

struct FlareTabBarV2: View {
    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var appState
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings
    @EnvironmentObject private var timelineState: TimelineExtState

    let accountType: AccountType
    @Namespace private var tabBarNamespace

    init(accountType: AccountType) {
        self.accountType = accountType

        os_log("[FlareTabBarV2] Initialized for account type: %{public}@",
               log: .default, type: .debug,
               String(describing: accountType))
    }

    var body: some View {
        HStack {
            Spacer()

            HStack(spacing: 0) {
                // Menu Tab
                tabBarItem(for: .menu)
                    .padding(.horizontal, calculateHorizontalPadding())

                // Timeline Tab
                tabBarItem(for: .timeline)
                    .padding(.horizontal, calculateHorizontalPadding())

                // Notification Tab (仅非访客用户显示)
                if !(accountType is AccountTypeGuest) {
                    tabBarItem(for: .notification)
                        .padding(.horizontal, calculateHorizontalPadding())

                    // Compose按钮 (在Notification之后)
                    composeButton()
                        .padding(.horizontal, calculateHorizontalPadding())
                }

                // Discover Tab
                tabBarItem(for: .discover)
                    .padding(.horizontal, calculateHorizontalPadding())

                // Profile Tab (仅非访客用户显示)
                if !(accountType is AccountTypeGuest) {
                    tabBarItem(for: .profile)
                        .padding(.horizontal, calculateHorizontalPadding())
                }
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .fixedSize(horizontal: true, vertical: false)
            .background(
                .ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20)
            )

            Spacer()
        }
    }

    @ViewBuilder
    private func tabBarItem(for tab: FlareHomeTabs) -> some View {
        Button {
            if router.selectedTab == tab {
                router.popToRoot(for: tab)
                if tab == .timeline {
                    timelineState.scrollToTopTrigger.toggle()
                }
            } else {
                router.selectedTab = tab
            }
        } label: {
            VStack(spacing: 2) {
                icon(for: tab, isActive: router.selectedTab == tab)
                    .foregroundColor(
                        router.selectedTab == tab
                            ? theme.tintColor : Color(.secondaryLabel)
                    )
                    .frame(width: 28, height: 28)
                    .background(
                        ZStack {
                            if router.selectedTab == tab {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(theme.tintColor.opacity(0.20))
                                    .frame(width: 40, height: 40)
                                    .matchedGeometryEffect(
                                        id: "selectedTabIndicator",
                                        in: tabBarNamespace
                                    )
                                    .animation(.easeOut(duration: 0.1), value: router.selectedTab) // 更快的动画
                            }
                        }
                    )
            }
        }
        .frame(height: 65)
        .frame(maxWidth: .infinity)
        .buttonStyle(PlainButtonStyle())
    }

    @ViewBuilder
    private func icon(for tab: FlareHomeTabs, isActive _: Bool) -> some View {
        switch tab {
        case .menu:
            Text(AwesomeIcon.bars.rawValue)
                .font(.awesome(style: .solid, size: 24))
        case .timeline:
            Text(AwesomeIcon.home.rawValue)
                .font(.awesome(style: .solid, size: 24))
        case .notification:
            Text(AwesomeIcon.bell.rawValue)
                .font(.awesome(style: .solid, size: 24))
        case .discover:
            Text(AwesomeIcon.search.rawValue)
                .font(.awesome(style: .solid, size: 24))
        case .profile:
            Text(AwesomeIcon.user.rawValue)
                .font(.awesome(style: .solid, size: 24))
        case .compose:
            Text(AwesomeIcon.plus.rawValue)
                .font(.awesome(style: .solid, size: 24))
        }
    }

    @ViewBuilder
    private func composeButton() -> some View {
        Button {
            ComposeManager.shared.showNewCompose(accountType: accountType)
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
        .buttonStyle(PlainButtonStyle())
    }

    private func calculateHorizontalPadding() -> CGFloat {
        let totalTabs = !(accountType is AccountTypeGuest) ? 6 : 3
        return max(0, (UIScreen.main.bounds.width * 0.8 - 200) / CGFloat(totalTabs * 2))
    }
}
