import SwiftUI
import SwiftfulRouting
import shared
import Generated

// MARK: - AnyRouter扩展
extension AnyRouter {
    /// 显示一个简单的警告弹窗
    func showBasicAlert(title: String = "提示", text: String) {
        showAlert(.alert, title: title, subtitle: text) {
            Button("确定") {}
        }
    }
    
    /// 显示一个确认警告弹窗
    func showConfirmAlert(title: String = "确认", text: String, onConfirm: @escaping () -> Void) {
        showAlert(.alert, title: title, subtitle: text) {
            VStack {
                Button("确定") {
                    onConfirm()
                }
                Button("取消", role: .cancel) {}
            }
        }
    }
    
    /// 显示FlareRouteDestination
    func showFlareDestination(_ destination: FlareRouteDestination, with segue: SegueOption) {
        showScreen(segue) { router in
            destination.view(with: router)
        }
    }
    
    /// 显示FlareRouteDestination序列
    func enterFlareFlow(_ destinations: [FlareRouteDestination]) {
        let routes = destinations.map { destination in
            AnyRoute(.push) { router in
                destination.view(with: router)
            }
        }
        
        enterScreenFlow(routes)
    }
    
    /// 显示主标签栏
    func showTabBar(accountType: AccountType = AccountTypeGuest(), with segue: SegueOption = .push) {
        showFlareDestination(.tabBar(accountType: accountType), with: segue)
    }
    
    /// 回到主标签栏
    func popToTabBar() {
        // 由于SwiftfulRouting中没有直接回到根视图的方法，我们使用dismissScreen
        dismissScreen()
    }
}

// MARK: - View扩展
extension View {
    /// 添加导航修饰符
    func navigationModifers(title: String) -> some View {
        self
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.large)
    }
    
    /// 使用当前环境中的路由器导航到目标
    func navigateTo(_ destination: FlareRouteDestination) -> some View {
        self.modifier(NavigateToModifier(destination: destination))
    }
    
    /// 导航到个人资料页面
    func navigateToProfile(accountId: String? = nil) -> some View {
        self.navigateTo(.profile(accountId: accountId))
    }
    
    /// 导航到帖子详情
    func navigateToPostDetail(statusKey: String) -> some View {
        self.navigateTo(.postDetail(statusKey: statusKey))
    }
}

// MARK: - 导航修饰符
struct NavigateToModifier: ViewModifier {
    @Environment(\.router) private var router
    let destination: FlareRouteDestination
    
    func body(content: Content) -> some View {
        content
            .onTapGesture {
                router.showFlareDestination(destination, with: .push)
            }
    }
}

// MARK: - 导航辅助方法
extension EnvironmentValues {
    /// 获取应用状态
    var appState: FlareAppState? {
        get { self[AppStateKey.self] }
        set { self[AppStateKey.self] = newValue }
    }
}

private struct AppStateKey: EnvironmentKey {
    static let defaultValue: FlareAppState? = nil
}

// MARK: - FlareAppState扩展
extension FlareAppState {
    /// 切换到指定标签
    func switchToTab(_ tab: FlareTabs) {
        self.currentTab = tab.rawValue
    }
}