import Combine
import os.log
import SafariServices
import shared
import SwiftUI
import UIKit

@Observable
class FlareRouter: ObservableObject {
    public static let shared = FlareRouter()

    public var appState: FlareAppState

    private var cancellables = Set<AnyCancellable>()

    var activeDestination: FlareDestination?

    var presentationType: FlarePresentationType = .push

    var isSheetPresented: Bool = false

    var isFullScreenPresented: Bool = false

    var isDialogPresented: Bool = false

    var selectedTab: FlareHomeTabs = .timeline {
        didSet {
            let oldTab = oldValue
            FlareLog.debug("[FlareRouter] Tab changed: \(oldTab) -> \(selectedTab)")
        }
    }

    var navigationDepth: Int {
        let depth = switch selectedTab {
        case .menu: menuNavigationPath.count
        case .timeline: timelineNavigationPath.count
        case .discover: discoverNavigationPath.count
        case .notification: notificationNavigationPath.count
        case .profile: profileNavigationPath.count
        case .compose: 0
        }

        os_log("[FlareRouter] Current navigationDepth for tab %{public}@: %{public}d",
               log: .default, type: .debug,
               String(describing: selectedTab),
               depth)

        return depth
    }

    var menuNavigationPath = NavigationPath()
    var timelineNavigationPath = NavigationPath()
    var discoverNavigationPath = NavigationPath()
    var notificationNavigationPath = NavigationPath()
    var profileNavigationPath = NavigationPath()

    var navigationPath: NavigationPath {
        get { currentNavigationPath }
        set { updateCurrentNavigationPath(newValue) }
    }

    private var currentNavigationPath: NavigationPath {
        switch selectedTab {
        case .menu: menuNavigationPath
        case .timeline: timelineNavigationPath
        case .discover: discoverNavigationPath
        case .notification: notificationNavigationPath
        case .profile: profileNavigationPath
        case .compose: NavigationPath() // 为compose返回空路径
        }
    }

    private func updateCurrentNavigationPath(_ newPath: NavigationPath) {
        switch selectedTab {
        case .menu: menuNavigationPath = newPath
        case .timeline: timelineNavigationPath = newPath
        case .discover: discoverNavigationPath = newPath
        case .notification: notificationNavigationPath = newPath
        case .profile: profileNavigationPath = newPath
        case .compose: break // 不存储导航路径
        }
    }

    func navigationPathFor(_ tab: FlareHomeTabs) -> Binding<NavigationPath> {
        switch tab {
        case .menu: Binding(get: { self.menuNavigationPath }, set: { self.menuNavigationPath = $0 })
        case .timeline: Binding(get: { self.timelineNavigationPath }, set: { self.timelineNavigationPath = $0 })
        case .discover: Binding(get: { self.discoverNavigationPath }, set: { self.discoverNavigationPath = $0 })
        case .notification: Binding(get: { self.notificationNavigationPath }, set: { self.notificationNavigationPath = $0 })
        case .profile: Binding(get: { self.profileNavigationPath }, set: { self.profileNavigationPath = $0 })
        case .compose: Binding(get: { NavigationPath() }, set: { _ in }) // 为compose返回空绑定
        }
    }

    subscript(tab: FlareHomeTabs) -> Binding<NavigationPath> {
        navigationPathFor(tab)
    }

    func popToRoot(for tab: FlareHomeTabs) {
        switch tab {
        case .menu: menuNavigationPath = NavigationPath()
        case .timeline: timelineNavigationPath = NavigationPath()
        case .discover: discoverNavigationPath = NavigationPath()
        case .notification: notificationNavigationPath = NavigationPath()
        case .profile: profileNavigationPath = NavigationPath()
        case .compose: break // Compose不需要导航路径
        }

        os_log("[FlareRouter] Popped to root for tab %{public}@",
               log: .default, type: .debug, String(describing: tab))
    }

    init(appState: FlareAppState = FlareAppState()) {
        self.appState = appState
        os_log("[FlareRouter] Initialized router: %{public}@", log: .default, type: .debug, String(describing: ObjectIdentifier(self)))
    }

    func navigate(to destination: FlareDestination) {
        if case let .compose(accountType, status) = destination {
            showComposeSheet(accountType: accountType, status: status)
            return
        }

        let routerId = ObjectIdentifier(self)
        os_log("[FlareRouter] Router %{public}@ navigating to %{public}@, current depth: %{public}d, selectedTab: %{public}@",
               log: .default, type: .debug,
               String(describing: routerId),
               String(describing: destination),
               navigationDepth,
               String(describing: selectedTab))

        presentationType = destination.navigationType
        activeDestination = destination

        switch presentationType {
        case .push:
            navigatePush(to: destination)

            os_log("[FlareRouter] After push, new depth: %{public}d",
                   log: .default, type: .debug, navigationDepth)
        case .sheet:
            isSheetPresented = true
        case .fullScreen:
            isFullScreenPresented = true
        case .dialog:
            isDialogPresented = true
        }
    }

    func goBack() {
        dismissCurrentView()
    }

    func dismissSheet() {
        if isSheetPresented {
            isSheetPresented = false
            activeDestination = nil // Clear destination when sheet is dismissed
        }
    }

    func dismissFullScreenCover() {
        if isFullScreenPresented {
            isFullScreenPresented = false
            activeDestination = nil
        }
    }

    func dismissAll() {
        dismissCurrentView()
    }

    private func navigatePush(to destination: FlareDestination) {
        let routerId = ObjectIdentifier(self)
        os_log("[FlareRouter] Router %{public}@ pushing to %{public}@ for tab %{public}@",
               log: .default, type: .debug,
               String(describing: routerId),
               String(describing: destination),
               String(describing: selectedTab))

        switch selectedTab {
        case .menu:
            menuNavigationPath.append(destination)
        case .timeline:
            timelineNavigationPath.append(destination)
        case .discover:
            discoverNavigationPath.append(destination)
        case .notification:
            notificationNavigationPath.append(destination)
        case .profile:
            profileNavigationPath.append(destination)
        case .compose:
            break
        }
    }

    private func dismissCurrentView() {
        let routerId = ObjectIdentifier(self)
        os_log("[FlareRouter] Router %{public}@ dismissing view, current type: %{public}@, depth: %{public}d, selectedTab: %{public}@",
               log: .default, type: .debug,
               String(describing: routerId),
               String(describing: presentationType),
               navigationDepth,
               String(describing: selectedTab))

        switch presentationType {
        case .sheet:
            dismissSheet()
        case .fullScreen:
            dismissFullScreenCover()
        case .dialog:
            isDialogPresented = false
            activeDestination = nil
        case .push:
            switch selectedTab {
            case .menu:
                if !menuNavigationPath.isEmpty {
                    menuNavigationPath.removeLast()
                }
            case .timeline:
                if !timelineNavigationPath.isEmpty {
                    timelineNavigationPath.removeLast()
                }
            case .discover:
                if !discoverNavigationPath.isEmpty {
                    discoverNavigationPath.removeLast()
                }
            case .notification:
                if !notificationNavigationPath.isEmpty {
                    notificationNavigationPath.removeLast()
                }
            case .profile:
                if !profileNavigationPath.isEmpty {
                    profileNavigationPath.removeLast()
                }
            case .compose:
                break
            }

            activeDestination = nil

            os_log("[FlareRouter] After dismissing, new depth: %{public}d",
                   log: .default, type: .debug, navigationDepth)
        }
    }

    /// 处理所有URL请求的统一入口
    @discardableResult
    func handleDeepLink(_ url: URL) -> Bool {
        // 使用KMP提供的AppDeepLinkHelper解析URL
        if let route = AppDeepLinkHelper().parse(url: url.absoluteString) {
            // 根据路由类型创建对应的FlareDestination
            let destination = convertRouteToDestination(route)
            navigate(to: destination)
            return true
        }
//        let urlComponents = URLComponents(url: url, resolvingAgainstBaseURL: true)
//                let path = url.path
//                let queryItems = urlComponents?.queryItems ?? []
//
//                var params: [String: String] = [:]
//                for item in queryItems {
//                    params[item.name] = item.value
//                }
//
        // 2. 处理外部URL
        // 检查是否是http/https链接
        guard let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme)
        else {
            return false
        }

        // 根据设置决定使用哪种浏览器打开
        // 注意：@Observable类中无法直接使用@Environment，暂时使用默认的应用内Safari
        Task { @MainActor in
            _ = await SafariManager.shared.open(url)
        }
        return true
    }

    private func convertRouteToDestination(_ route: AppleRoute) -> FlareDestination {
        switch route {
        case let route as AppleRoute.Search:
            return .search(accountType: route.accountType, keyword: route.keyword)

        case let route as AppleRoute.Profile:
            return .profile(accountType: route.accountType, userKey: route.userKey)

        case let route as AppleRoute.ProfileWithNameAndHost:
            return .profileWithNameAndHost(accountType: route.accountType, userName: route.userName, host: route.host)

        case let route as AppleRoute.StatusDetail:
            return .statusDetail(accountType: route.accountType, statusKey: route.statusKey)

        case let route as AppleRoute.ProfileMedia:
            return .profileMedia(accountType: route.accountType, userKey: route.userKey)

        case let route as AppleRoute.StatusMedia:
            return .statusMedia(accountType: route.accountType, statusKey: route.statusKey, index: Int(route.index))

        case let route as AppleRoute.Podcast:
            return .podcastSheet(accountType: route.accountType, podcastId: route.id)

        case let route as AppleRoute.DeleteStatus:
            // SharedAppleRouteDeleteStatus 删除推文
            return .deleteStatus(accountType: route.accountType, statusKey: route.statusKey)

        case is AppleRoute.Compose:
            let routeType = String(describing: type(of: route))

            if routeType.contains("New") {
                if let accountType = (route as AnyObject).value(forKey: "accountType") as? AccountType {
                    return .compose(accountType: accountType, status: nil)
                }
            } else if routeType.contains("Reply") {
                if let accountType = (route as AnyObject).value(forKey: "accountType") as? AccountType,
                   let statusKey = (route as AnyObject).value(forKey: "statusKey") as? MicroBlogKey
                {
                    return .compose(accountType: accountType, status: .reply(statusKey: statusKey))
                }
            } else if routeType.contains("Quote") {
                if let accountType = (route as AnyObject).value(forKey: "accountType") as? AccountType,
                   let statusKey = (route as AnyObject).value(forKey: "statusKey") as? MicroBlogKey
                {
                    return .compose(accountType: accountType, status: .quote(statusKey: statusKey))
                }
            }

            FlareLog.warning("FlareRouter 未能识别的Compose类型: \(type(of: route))")
            return .search(accountType: AccountTypeGuest(), keyword: "")

        default:
            FlareLog.warning("FlareRouter Unhandled AppleRoute type: \(type(of: route)) - Route Value: \(route)")
            return .search(accountType: AccountTypeGuest(), keyword: "")
        }
    }

    func showComposeSheet(accountType: AccountType, status: FlareComposeStatus? = nil) {
        if status == nil {
            ComposeManager.shared.showNewCompose(accountType: accountType)
        } else {
            switch status {
            case let .reply(statusKey):
                ComposeManager.shared.showReply(accountType: accountType, statusKey: statusKey)
            case let .quote(statusKey):
                ComposeManager.shared.showQuote(accountType: accountType, statusKey: statusKey)
            case let .vvoComment(statusKey, rootId):
                ComposeManager.shared.showVVOComment(accountType: accountType, statusKey: statusKey, rootId: rootId)
            case .none:
                ComposeManager.shared.showNewCompose(accountType: accountType)
            }
        }
    }
}
