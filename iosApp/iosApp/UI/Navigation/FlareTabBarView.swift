import SwiftUI
import SwiftfulRouting
import shared
import Generated
import Awesome

enum FlareTabs: Int, Equatable, Hashable, Identifiable {
    var id: Self { self }
    case timeline = 0
    case notification = 1
    case compose = 2
    case discover = 3
    case profile = 4

    var customizationID: String {
        switch self {
        case .timeline: "home_timeline"
        case .notification: "home_notification"
        case .compose: "home_compose"
        case .discover: "home_discover"
        case .profile: "home_profile"
        }
    }
    
    var icon: Image {
        switch self {
        case .timeline:
            return Asset.Tab.feedInactive.swiftUIImage
        case .notification:
            return Asset.Tab.trendingInactive.swiftUIImage
        case .compose:
            return Asset.Tab.compose.swiftUIImage
        case .discover:
            return Asset.Tab.discoverInactive.swiftUIImage
        case .profile:
            return Asset.Tab.profileInactive.swiftUIImage
        }
    }
    
    var activeIcon: Image {
        switch self {
        case .timeline:
            return Asset.Tab.feedActivie.swiftUIImage
        case .notification:
            return Asset.Tab.trendingActive.swiftUIImage
        case .compose:
            return Asset.Tab.compose.swiftUIImage
        case .discover:
            return Asset.Tab.discoverActive.swiftUIImage
        case .profile:
            return Asset.Tab.profileActive.swiftUIImage
        }
    }
    
    var title: String {
        switch self {
        case .timeline: return "首页"
        case .notification: return "通知"
        case .compose: return "发布"
        case .discover: return "发现"
        case .profile: return "我的"
        }
    }
}

struct FlareTabBarView: View {
    // 路由器和状态
    @Environment(\.router) var router
    @EnvironmentObject private var appState: FlareAppState
    
    // 账户类型
    let accountType: AccountType
    
    // 当前选中的标签
    @State private var selectedTab: FlareTabs = .timeline
    
    // 是否显示发布界面
    @State private var showComposeSheet = false
    
    // 用于存储上一个标签，当从发布页面返回时使用
    @State private var previousTab: FlareTabs = .timeline
    
    var body: some View {
        TabView(selection: $selectedTab) {
            // 首页标签
            createTabView(for: .timeline) { router in
                HomeTabScreen(accountType: accountType)
                    .environment(\.router, router)
            }
            
            // 通知标签 - 仅在用户登录时显示
            if !(accountType is AccountTypeGuest) {
                createTabView(for: .notification) { router in
                    NotificationTabScreen(accountType: accountType)
                        .environment(\.router, router)
                }
            }
            
            // 发布标签 - 仅在用户登录时显示
            if !(accountType is AccountTypeGuest) {
                createTabView(for: .compose) { _ in
                    Color.clear
                        .onAppear {
                            // 保存当前标签
                            previousTab = selectedTab
                            
                            // 显示发布页面
                            appState.showCompose()
                            
                            // 自动切换回之前的标签
                            withAnimation {
                                selectedTab = previousTab
                            }
                        }
                }
            }
            
            // 发现标签
            createTabView(for: .discover) { router in
                DiscoverTabScreen(
                    accountType: accountType,
                    onUserClicked: { user in
                        // 使用MicroBlogKey的id属性
                        router.showFlareDestination(.profile(accountId: user.key.id), with: .push)
                    }
                )
                .environment(\.router, router)
            }
            
            // 个人资料标签 - 仅在用户登录时显示
            if !(accountType is AccountTypeGuest) {
                createTabView(for: .profile) { router in
                    FlareRouteDestination.profile(accountId: nil)
                        .view(with: router)
                }
            }
        }
        .onChange(of: selectedTab) { newTab in
            // 更新应用状态中的当前标签
            appState.currentTab = newTab.rawValue
            
            // 不让发布标签保持选中状态
            if newTab == .compose {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    withAnimation {
                        selectedTab = previousTab
                    }
                }
            } else {
                previousTab = newTab
            }
        }
    }
    
    // 创建标签页视图
    @ViewBuilder
    private func createTabView<Content: View>(for tab: FlareTabs, @ViewBuilder content: @escaping (AnyRouter) -> Content) -> some View {
        SwiftfulRouting.RouterView { router in
            content(router)
        }
        .tabItem {
            VStack(spacing: 4) {
                (selectedTab == tab ? tab.activeIcon : tab.icon)
                    .foregroundColor(.accentColor)
                Text(tab.title)
                    .font(.caption)
            }
        }
        .tag(tab)
    }
}

// MARK: - 预览
struct FlareTabBarView_Previews: PreviewProvider {
    static var previews: some View {
        let appState = FlareAppState(tabProvider: AppBarTabSettingStore.shared)
        
        SwiftfulRouting.RouterView { router in
            FlareTabBarView(accountType: AccountTypeGuest())
                .environmentObject(appState)
        }
    }
} 