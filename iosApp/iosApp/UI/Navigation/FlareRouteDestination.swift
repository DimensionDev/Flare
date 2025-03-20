import Foundation
import SwiftUI
import SwiftfulRouting
import shared

/// 定义Flare应用中所有可能的导航目标
enum FlareRouteDestination: Identifiable, Hashable {
    // 主要页面
    case home
    case tabBar(accountType: AccountType)
    case search
    case notification
    case profile(accountId: String?)
    
    // 详情页面
    case postDetail(statusKey: String)
    case imageViewer(urls: [String], initialIndex: Int)
    case videoPlayer(url: String)
    
    // 功能页面
    case settings
    case tabSettings
    case login
    case compose
    
    // 标识符
    var id: String {
        switch self {
        case .home:
            return "home"
        case .tabBar:
            return "tab-bar"
        case .search:
            return "search"
        case .notification:
            return "notification"
        case .profile(let accountId):
            return "profile-\(accountId ?? "me")"
        case .postDetail(let statusKey):
            return "post-\(statusKey)"
        case .imageViewer:
            return "image-viewer-\(UUID().uuidString)"
        case .videoPlayer(let url):
            return "video-player-\(url.hashValue)"
        case .settings:
            return "settings"
        case .tabSettings:
            return "tab-settings"
        case .login:
            return "login"
        case .compose:
            return "compose"
        }
    }
    
    // 哈希
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    // 相等性
    static func == (lhs: FlareRouteDestination, rhs: FlareRouteDestination) -> Bool {
        lhs.id == rhs.id
    }
    
    // 视图构建器
    @ViewBuilder
    func view(with router: AnyRouter) -> some View {
        switch self {
        case .home:
            // 使用新的标签栏视图作为主页入口，使用访客账户
            FlareTabBarView(accountType: AccountTypeGuest())
        case .tabBar(let accountType):
            // 根据账户类型使用新的标签栏视图
            FlareTabBarView(accountType: accountType)
        case .search:
            Text("搜索页面") // 实现搜索页面
        case .notification:
            Text("通知页面") // 实现通知页面
        case .profile(let accountId):
            Text("个人资料页面: \(accountId ?? "me")") // 实现个人资料页面
        case .postDetail(let statusKey):
            Text("帖子详情: \(statusKey)") // 实现帖子详情页面
        case .imageViewer(let urls, let initialIndex):
            Text("图片查看器: \(urls.count)张，初始索引\(initialIndex)") // 实现图片查看器
        case .videoPlayer(let url):
            Text("视频播放器: \(url)") // 实现视频播放器
        case .settings:
            Text("设置页面") // 实现设置页面
        case .tabSettings:
            Text("标签设置页面") // 实现标签设置页面
        case .login:
            Text("登录页面") // 实现登录页面
        case .compose:
            Text("撰写帖子") // 实现撰写帖子页面
        }
    }
} 