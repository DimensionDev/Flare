import Foundation
import SwiftUI
import Combine
import SwiftfulRouting
import shared

/// 应用状态管理
@MainActor
class FlareAppState: FLNewAppState {
    // 用户状态
    @Published var userState: UserState?
    @Published var accountType: AccountType?
    
    // 导航路径
    @Published var navigationPath = [FlareRouteDestination]()
    @Published var sheetDestination: FlareRouteDestination?
    @Published var fullScreenDestination: FlareRouteDestination?
    @Published var alertDestination: FlareRouteDestination?
    
    // 通知中心订阅
    private var cancellables = Set<AnyCancellable>()
    
    // 匹配父类初始化方法
    override init(tabProvider: TabStateProvider?, gestureState: FLNewGestureState? = nil) {
        super.init(tabProvider: tabProvider, gestureState: gestureState)
        
        // 监听菜单打开/关闭通知
        NotificationCenter.default.publisher(for: .flShowNewMenu)
            .sink { [weak self] _ in
                // 确保在主线程上执行
                Task { @MainActor [weak self] in
                    withAnimation {
                        self?.isMenuOpen = true
                    }
                }
            }
            .store(in: &cancellables)
        
        // 监听设置界面通知
        NotificationCenter.default.publisher(for: .showSettings)
            .sink { [weak self] _ in
                // 确保在主线程上执行
                Task { @MainActor [weak self] in
                    self?.showSettings()
                }
            }
            .store(in: &cancellables)
        
        // 监听标签设置通知
        NotificationCenter.default.publisher(for: .showTabSettings)
            .sink { [weak self] _ in
                // 确保在主线程上执行
                Task { @MainActor [weak self] in
                    self?.showTabSettings()
                }
            }
            .store(in: &cancellables)
        
        // 监听登录界面通知
        NotificationCenter.default.publisher(for: .showLogin)
            .sink { [weak self] _ in
                // 确保在主线程上执行
                Task { @MainActor [weak self] in
                    self?.showLogin()
                }
            }
            .store(in: &cancellables)
    }
    
    // 提供一个便利初始化方法，与之前的初始化方法保持一致
    convenience init(tabProvider: AppBarTabSettingStore) {
        self.init(tabProvider: tabProvider, gestureState: nil)
    }
    
    // MARK: - 导航方法
    
    /// 导航到指定目标
    func navigate(to destination: FlareRouteDestination, using router: AnyRouter) {
        router.showScreen(.push) { routerInner in
            destination.view(with: routerInner)
        }
    }
    
    /// 以弹窗形式显示目标
    func presentSheet(destination: FlareRouteDestination, using router: AnyRouter) {
        router.showScreen(.sheet) { routerInner in
            destination.view(with: routerInner)
        }
    }
    
    /// 以全屏形式显示目标
    func presentFullScreen(destination: FlareRouteDestination, using router: AnyRouter) {
        router.showScreen(.fullScreenCover) { routerInner in
            destination.view(with: routerInner)
        }
    }
    
    // MARK: - 快捷导航方法
    
    func showSettings() {
        NotificationCenter.default.post(name: .flMenuStateDidChange, object: nil)
        sheetDestination = .settings
    }
    
    func showTabSettings() {
        NotificationCenter.default.post(name: .flMenuStateDidChange, object: nil)
        sheetDestination = .tabSettings
    }
    
    func showLogin() {
        NotificationCenter.default.post(name: .flMenuStateDidChange, object: nil)
        fullScreenDestination = .login
    }
    
    func showCompose() {
        sheetDestination = .compose
    }
    
    func showProfile(accountId: String?) {
        navigationPath.append(.profile(accountId: accountId))
    }
    
    func showPostDetail(statusKey: String) {
        navigationPath.append(.postDetail(statusKey: statusKey))
    }
    
    // MARK: - 更新用户状态
    
    func updateUserState(_ state: UserState) {
        userState = state
        
        switch onEnum(of: state.user) {
        case let .success(data):
            accountType = AccountTypeSpecific(accountKey: data.data.key)
        case .loading:
            #if os(macOS)
            accountType = AccountTypeGuest()
            #else
            accountType = nil
            #endif
        case .error:
            accountType = AccountTypeGuest()
        }
    }
}

// MARK: - 导航管理扩展
extension FlareAppState {
    /// 创建路由流
    func createFlow(destinations: [FlareRouteDestination], using router: AnyRouter) {
        let routes = destinations.map { destination in
            AnyRoute(.push) { routerInner in
                destination.view(with: routerInner)
            }
        }
        
        router.enterScreenFlow(routes)
    }
    
    /// 显示图片查看器
    func showImageViewer(urls: [String], initialIndex: Int, using router: AnyRouter) {
        router.showScreen(.fullScreenCover) { routerInner in
            FlareRouteDestination.imageViewer(urls: urls, initialIndex: initialIndex)
                .view(with: routerInner)
        }
    }
    
    /// 显示视频播放器
    func showVideoPlayer(url: String, using router: AnyRouter) {
        router.showScreen(.fullScreenCover) { routerInner in
            FlareRouteDestination.videoPlayer(url: url)
                .view(with: routerInner)
        }
    }
} 