import FontAwesomeSwiftUI
import Generated
import os.log
import shared
import SwiftUI

/// FlareTabBarV2 - 新的TabBar组件，基于Observable架构
/// 保持Flare现有的UI样式和交互逻辑，使用iOS 17+的现代架构
struct FlareTabBarV2: View {
    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var appState
    @Environment(FlareTheme.self) private var theme
    @Environment(\.appSettings) private var appSettings

    let accountType: AccountType
    @Binding var scrollToTopTrigger: Bool
    @Namespace private var tabBarNamespace

    init(accountType: AccountType, scrollToTopTrigger: Binding<Bool>) {
        self.accountType = accountType
        _scrollToTopTrigger = scrollToTopTrigger

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
            .fixedSize(horizontal: true, vertical: false) // 强制内层HStack根据内容收缩宽度
            .background(
                // 圆角矩形 + 毛玻璃背景
                .ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20)
            )

            Spacer()
        }
    }

    @ViewBuilder
    private func tabBarItem(for tab: FlareHomeTabs) -> some View {
        Button {
            FlareLog.debug("[FlareTabBarV2] Tab button tapped: \(tab), selectedTab: \(router.selectedTab)")

            if router.selectedTab == tab {
                FlareLog.debug("[FlareTabBarV2] Same tab tapped again: \(tab)")
                // 双击同一个Tab的逻辑
                router.popToRoot(for: tab)

                if tab == .timeline {
                    // Timeline特殊处理：触发滚动到顶部
                    let oldValue = scrollToTopTrigger
                    scrollToTopTrigger.toggle()
                    FlareLog.debug("[FlareTabBarV2] Timeline scroll trigger toggled: \(oldValue) -> \(scrollToTopTrigger)")
                }
            } else {
                // 切换到新Tab - 移除动画以提升响应速度
                router.selectedTab = tab
                FlareLog.debug("[FlareTabBarV2] Tab switched to: \(tab)")
            }
        } label: {
            VStack(spacing: 2) {
                icon(for: tab, isActive: router.selectedTab == tab)
                    .foregroundColor(
                        router.selectedTab == tab
                            ? theme.tintColor : Color(.secondaryLabel)
                    )
                    .frame(width: 24, height: 24)
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
                                    .animation(.easeOut(duration: 0.15), value: router.selectedTab) // 更快的动画
                            }
                        }
                    )
            }
        }
        .frame(height: 55) // TabBar项目高度
        .frame(maxWidth: .infinity)
        .buttonStyle(PlainButtonStyle())
    }

    @ViewBuilder
    private func icon(for tab: FlareHomeTabs, isActive _: Bool) -> some View {
        switch tab {
        case .menu:
            Text(AwesomeIcon.bars.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .timeline:
            Text(AwesomeIcon.home.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .notification:
            Text(AwesomeIcon.bell.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .discover:
            Text(AwesomeIcon.search.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .profile:
            Text(AwesomeIcon.user.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .compose:
            Text(AwesomeIcon.plus.rawValue)
                .font(.awesome(style: .solid, size: 20))
        }
    }

    @ViewBuilder
    private func composeButton() -> some View {
        Button {
            FlareLog.debug("[FlareTabBarV2] Compose button tapped")
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
        let totalTabs = !(accountType is AccountTypeGuest) ? 6 : 3 // 包含compose按钮
        return max(0, (UIScreen.main.bounds.width * 0.8 - 200) / CGFloat(totalTabs * 2))
    }
}

// MARK: - Preview

#Preview {
    @Previewable @State var scrollToTopTrigger = false
    @Previewable @State var router = FlareRouter()
    @Previewable @State var appState = FlareAppState()
    @Previewable @State var theme = FlareTheme.shared

    VStack {
        Spacer()
        FlareTabBarV2(
            accountType: AccountTypeGuest(),
            scrollToTopTrigger: $scrollToTopTrigger
        )
        .environment(router)
        .environment(appState)
        .environment(theme)
    }
    .background(Color(.systemBackground))
}
