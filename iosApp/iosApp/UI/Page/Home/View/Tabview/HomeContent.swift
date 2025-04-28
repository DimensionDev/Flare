import Awesome
import FontAwesomeSwiftUI
import Generated
import os.log
import shared
import SwiftUI

struct HomeContent: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State private var selectedTab: FlareHomeTabs = .timeline
    let accountType: AccountType
    @State var showSettings = false
    @State var showLogin = false
    @StateObject private var appState = FlareAppState()
    @EnvironmentObject private var router: FlareRouter
    // 用于自定义 TabBar 选中项动画的命名空间
    @Namespace private var tabBarNamespace

    // State for Menu View user data (needed if Menu tab requires it)
    @State private var currentUser: UiUserV2? = nil // Assuming Menu might need user data

    // 为自定义 TabBar 计算可见标签项
    private var visibleTabs: [FlareHomeTabs] {
        var tabs: [FlareHomeTabs] = [.menu, .timeline]
        if !(accountType is AccountTypeGuest) { tabs.append(.notification) }
        tabs.append(.discover)
        if !(accountType is AccountTypeGuest) { tabs.append(.profile) }
        return tabs
    }

    var body: some View {
        let routerId = ObjectIdentifier(router)

        os_log("[HomeContent] Using router: %{public}@, selectedTab: %{public}@",
               log: .default, type: .debug,
               String(describing: routerId),
               String(describing: selectedTab))

        return FlareTheme {
            // 使用 ZStack 将自定义 TabBar 覆盖在功能性的 TabView 之上
            ZStack(alignment: .bottom) {
                // 第1层: 功能性的 TabView (处理状态保持和导航)
                TabView(selection: $selectedTab) {
                    Tab(value: .menu) {
                        FlareTabItem(router: router, tabType: .menu) { _ in FlareMenuContainer() }
                            .environmentObject(appState)
                    }
                    // .tag 在 selection 类型为 Hashable 时不需要

                    // 时间线 Tab
                    Tab(value: .timeline) {
                        FlareTabItem(router: router, tabType: .timeline) { _ in
                            HomeTabScreenSwiftUI(
                                accountType: accountType,
                                onSwitchToMenuTab: {
                                    withAnimation {
                                        selectedTab = .menu
                                    }
                                }
                            )
                        }
                        .environmentObject(appState)
                    }

                    // 通知 Tab (仅登录用户可见)
                    if !(accountType is AccountTypeGuest) {
                        Tab(value: .notification) {
                            FlareTabItem(router: router, tabType: .notification) { _ in NotificationTabScreen(accountType: accountType) }
                                .environmentObject(appState)
                        }
                    }

                    // 发现 Tab
                    Tab(value: .discover) {
                        FlareTabItem(router: router, tabType: .discover) { tabRouter in
                            DiscoverTabScreen(accountType: accountType, onUserClicked: { user in
                                tabRouter.navigate(to: .profile(accountType: accountType, userKey: user.key))
                            })
                        }
                        .environmentObject(appState)
                    }

                    // 个人资料 Tab (仅登录用户可见)
                    if !(accountType is AccountTypeGuest) {
                        Tab(value: .profile) {
                            FlareTabItem(router: router, tabType: .profile) { _ in ProfileTabScreen(accountType: accountType, userKey: nil, toProfileMedia: { _ in print("媒体标签已集成") }) }
                                .environmentObject(appState)
                        }
                    }
                    // --- Tab 结构结束 ---
                }

                .toolbar(.hidden, for: .tabBar)
                .padding(.bottom, -130) // 底部白背景

                // 第2层: 自定义的视觉 TabBar
                // menu 需要 隐藏 tabbar
                if !appState.isCustomTabBarHidden {
                    customTabBar() // 包含自适应宽度和毛玻璃背景
                        .padding(.horizontal) // 应用水平边距，营造悬浮感
                        // 设置底部 padding 为 0，使其紧贴底部安全区域边缘
                        .padding(.bottom, 0) // 根据需要调整此值
                        // 添加过渡动画，使显示/隐藏更平滑
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            // 给整个 ZStack 区域应用背景色
            .background(Colors.Background.swiftUIPrimary.edgesIgnoringSafeArea(.all))
            .onAppear {
                // checkAndUpdateUserState()
            }
        }
        // 登录 Sheet
        .sheet(isPresented: $showLogin) { ServiceSelectScreen(toHome: { showLogin = false }) }
        // 设置 Sheet
        .sheet(isPresented: $showSettings) { SettingsUIScreen() }
    }

    // 自定义悬浮 Tab Bar 视图
    @ViewBuilder
    private func customTabBar() -> some View {
        // 最外层 HStack 容器，用于水平居中 (使用 Spacer)
        HStack {
            Spacer()
            // 内层 HStack 包含实际的 Tab 项和发布按钮
            HStack(spacing: 0) {
                ForEach(visibleTabs, id: \.self) { tab in
                    // Render the standard tab item
                    tabBarItem(for: tab)
                        // 控制项之间的水平间距
                        .padding(.horizontal, calculateHorizontalPadding(for: visibleTabs.count + (accountType is AccountTypeGuest ? 0 : 1)))

                    // Insert compose button immediately after notification tab if logged in
                    if tab == .notification, !(accountType is AccountTypeGuest) {
                        composeButton()
                            // 控制项之间的水平间距 (Use the same padding logic)
                            .padding(.horizontal, calculateHorizontalPadding(for: visibleTabs.count + 1))
                    }
                }
            }
            // 控制悬浮元素区域内部的垂直 padding
            .padding(.vertical, 8)
            // 在背景胶囊内部添加水平 padding
            .padding(.horizontal, 12) // 调整此值以获得期望的内部边距
            // 应用 fixedSize 强制内层 HStack 根据内容收缩宽度
            .fixedSize(horizontal: true, vertical: false)
            // 给内层 HStack 应用毛玻璃背景，并裁剪为自定义圆角矩形
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
            Spacer()
        }
    }

    // 根据项的数量计算动态的水平 padding
    private func calculateHorizontalPadding(for itemCount: Int) -> CGFloat {
        // 使用更清晰的 switch 语句，值已略微增大
        switch itemCount {
        case 3: 22
        case 4: 18
        case 5: 15
        case 6: 12
        case 7: 10
        default: 10
        }
    }

    // 单个 Tab Bar 项视图 (保留最新有效版本)
    @ViewBuilder
    private func tabBarItem(for tab: FlareHomeTabs) -> some View {
        // Tab 按钮
        Button {
            // 带动画效果切换选中 Tab
            withAnimation(.easeInOut(duration: 0.2)) { selectedTab = tab }
            // 日志输出
            os_log("[HomeContent] Tab selected: %{public}@", log: .default, type: .debug, String(describing: tab))
        } label: {
            // 图标和选中状态背景
            VStack(spacing: 2) {
                icon(for: tab, isActive: selectedTab == tab)
                    .foregroundColor(selectedTab == tab ? Color.accentColor : Color(.secondaryLabel))
                    .frame(width: 24, height: 24)
                    .background(
                        // 选中项的背景：圆角矩形
                        ZStack {
                            if selectedTab == tab {
                                RoundedRectangle(cornerRadius: 12) // 减小圆角半径
                                    .fill(Color.accentColor.opacity(0.20))
                                    // 使用 matchedGeometryEffect 实现平滑过渡动画
                                    .matchedGeometryEffect(id: "selectedTabIndicator", in: tabBarNamespace)
                                    // 保持方形尺寸
                                    .frame(width: 40, height: 40)
                            }
                        }
                    )
            }
        }
        // 设置 Button 的固定高度
        .frame(height: 49) // 49 是 tabbar 的高度
        .frame(maxWidth: .infinity)
    }

    // 发布按钮视图 (悬浮样式, 保留最新有效版本)
    @ViewBuilder
    private func composeButton() -> some View {
        Button {
            ComposeManager.shared.showNewCompose(accountType: accountType)

            os_log("[HomeContent] Compose button tapped", log: .default, type: .debug)
        } label: {
            Asset.Tab.compose.swiftUIImage
                .resizable()
                .renderingMode(.template)
                .foregroundColor(.white)
                .scaledToFit()
                .frame(width: 20, height: 20)
                .padding(12)
                .background(Circle().fill(Color.accentColor))
                .shadow(radius: 3)
        }
    }

    // 图标逻辑 (保留最新有效版本)
    @ViewBuilder
    private func icon(for tab: FlareHomeTabs, isActive: Bool) -> some View {
        // 根据 Tab 类型和是否选中返回对应图标
        switch tab {
        case .menu:
            Text(AwesomeIcon.bars.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .timeline:
            Text(AwesomeIcon.home.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .notification:
            // Apply template rendering mode to allow foregroundColor to work
            (isActive ? Asset.Tab.trendingActive.swiftUIImage : Asset.Tab.trendingInactive.swiftUIImage)
                .renderingMode(.template)
        case .discover:
            Text(AwesomeIcon.search.rawValue)
                .font(.awesome(style: .solid, size: 20))
        case .profile:
            // Apply template rendering mode to allow foregroundColor to work
            (isActive ? Asset.Tab.profileActive.swiftUIImage : Asset.Tab.profileInactive.swiftUIImage)
                .renderingMode(.template)
        case .compose:
            Asset.Tab.compose.swiftUIImage
                .renderingMode(.template) // Also apply here if compose icon needs tinting elsewhere
        }
    }

    // 用于获取底部安全区域边距的环境变量
    @Environment(\.safeAreaInsets) private var safeAreaInsets
}

// 获取安全区域边距的环境键
struct SafeAreaInsetsKey: EnvironmentKey {
    static var defaultValue: EdgeInsets {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?
            .windows.first?.safeAreaInsets.toSwiftUIInsets() ?? EdgeInsets()
    }
}

// 为 EnvironmentValues 添加 safeAreaInsets 便捷访问属性
extension EnvironmentValues {
    var safeAreaInsets: EdgeInsets {
        self[SafeAreaInsetsKey.self]
    }
}

// 将 UIEdgeInsets 转换为 SwiftUI 的 EdgeInsets
extension UIEdgeInsets {
    func toSwiftUIInsets() -> EdgeInsets {
        EdgeInsets(top: top, leading: left, bottom: bottom, trailing: right)
    }
}
