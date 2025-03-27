import Combine
import os.log
import shared
import SwiftUI
import UIKit

class FlareRouter: ObservableObject {
    public var appState: FlareAppState

    private var cancellables = Set<AnyCancellable>()

    @Published var activeDestination: FlareDestination?

    @Published var presentationType: FlarePresentationType = .push

    @Published var isSheetPresented: Bool = false

    @Published var isFullScreenPresented: Bool = false

    @Published var isDialogPresented: Bool = false

    @Published var previousPageSnapshot: UIImage? = nil

    var navigationDepth: Int {
        let depth = switch activeTab {
        case .timeline: timelineNavigationPath.count
        case .discover: discoverNavigationPath.count
        case .notification: notificationNavigationPath.count
        case .compose: composeNavigationPath.count
        case .profile: profileNavigationPath.count
        }

        os_log("[FlareRouter] Current navigationDepth for tab %{public}@: %{public}d",
               log: .default, type: .debug,
               String(describing: activeTab),
               depth)

        return depth
    }

    @Published var timelineNavigationPath = NavigationPath()
    @Published var discoverNavigationPath = NavigationPath()
    @Published var notificationNavigationPath = NavigationPath()
    @Published var composeNavigationPath = NavigationPath()
    @Published var profileNavigationPath = NavigationPath()

    @Published var activeTab: HomeTabs = .timeline

    var navigationPath: NavigationPath {
        get { currentNavigationPath }
        set { updateCurrentNavigationPath(newValue) }
    }

    private var currentNavigationPath: NavigationPath {
        switch activeTab {
        case .timeline: timelineNavigationPath
        case .discover: discoverNavigationPath
        case .notification: notificationNavigationPath
        case .compose: composeNavigationPath
        case .profile: profileNavigationPath
        }
    }

    private func updateCurrentNavigationPath(_ newPath: NavigationPath) {
        switch activeTab {
        case .timeline: timelineNavigationPath = newPath
        case .discover: discoverNavigationPath = newPath
        case .notification: notificationNavigationPath = newPath
        case .compose: composeNavigationPath = newPath
        case .profile: profileNavigationPath = newPath
        }
    }

    func navigationPathFor(_ tab: HomeTabs) -> Binding<NavigationPath> {
        switch tab {
        case .timeline: Binding(get: { self.timelineNavigationPath }, set: { self.timelineNavigationPath = $0 })
        case .discover: Binding(get: { self.discoverNavigationPath }, set: { self.discoverNavigationPath = $0 })
        case .notification: Binding(get: { self.notificationNavigationPath }, set: { self.notificationNavigationPath = $0 })
        case .compose: Binding(get: { self.composeNavigationPath }, set: { self.composeNavigationPath = $0 })
        case .profile: Binding(get: { self.profileNavigationPath }, set: { self.profileNavigationPath = $0 })
        }
    }

    init(appState: FlareAppState = FlareAppState()) {
        self.appState = appState
        os_log("[FlareRouter] Initialized router: %{public}@", log: .default, type: .debug, String(describing: ObjectIdentifier(self)))
    }

    func captureCurrentPageSnapshot() {
        guard let window = UIApplication.shared.windows.first(where: { $0.isKeyWindow }) else { return }

        UIGraphicsBeginImageContextWithOptions(window.bounds.size, false, UIScreen.main.scale)
        window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        previousPageSnapshot = image
        os_log("[FlareRouter] Captured page snapshot", log: .default, type: .debug)
    }

    func navigate(to destination: FlareDestination) {
        let routerId = ObjectIdentifier(self)
        os_log("[FlareRouter] Router %{public}@ navigating to %{public}@, current depth: %{public}d, activeTab: %{public}@",
               log: .default, type: .debug,
               String(describing: routerId),
               String(describing: destination),
               navigationDepth,
               String(describing: activeTab))

        captureCurrentPageSnapshot()

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
        previousPageSnapshot = nil
        dismissCurrentView()
    }

    func dismissSheet() {
        if isSheetPresented {
            isSheetPresented = false
            activeDestination = nil
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
               String(describing: activeTab))

        switch activeTab {
        case .timeline:
            timelineNavigationPath.append(destination)
        case .discover:
            discoverNavigationPath.append(destination)
        case .notification:
            notificationNavigationPath.append(destination)
        case .compose:
            composeNavigationPath.append(destination)
        case .profile:
            profileNavigationPath.append(destination)
        }
    }

    private func dismissCurrentView() {
        let routerId = ObjectIdentifier(self)
        os_log("[FlareRouter] Router %{public}@ dismissing view, current type: %{public}@, depth: %{public}d, activeTab: %{public}@",
               log: .default, type: .debug,
               String(describing: routerId),
               String(describing: presentationType),
               navigationDepth,
               String(describing: activeTab))

        switch presentationType {
        case .sheet:
            dismissSheet()
        case .fullScreen:
            dismissFullScreenCover()
        case .dialog:
            isDialogPresented = false
            activeDestination = nil
        case .push:

            switch activeTab {
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
            case .compose:
                if !composeNavigationPath.isEmpty {
                    composeNavigationPath.removeLast()
                }
            case .profile:
                if !profileNavigationPath.isEmpty {
                    profileNavigationPath.removeLast()
                }
            }

            activeDestination = nil

            os_log("[FlareRouter] After dismissing, new depth: %{public}d",
                   log: .default, type: .debug, navigationDepth)
        }
    }

    /// 处理外部URL链接
    @discardableResult
    func handleDeepLink(_ url: URL) -> Bool {
        // 使用KMP提供的AppDeepLinkHelper解析URL
        if let route = AppDeepLinkHelper().parse(url: url.absoluteString) {
            // 根据路由类型创建对应的FlareDestination
            let destination = convertRouteToDestination(route)
            navigate(to: destination)
            return true
        }

        let urlComponents = URLComponents(url: url, resolvingAgainstBaseURL: true)
        let path = url.path
        let queryItems = urlComponents?.queryItems ?? []

        var params: [String: String] = [:]
        for item in queryItems {
            params[item.name] = item.value
        }

//        switch path {
//        case "/profile":
//
//            if let username = params["username"], let host = params["host"] {
//
//                let accountType = AccountTypeGuest()
//
//                let userKey = MicroBlogKey(id: username, host: host)
//                let destination = FlareDestination.profile(accountType: accountType, userKey: userKey)
//                navigate(to: destination)
//                return true
//            }
//
//        case "/status":
//
//            if let statusId = params["id"], let host = params["host"] {
//
//                let accountType = AccountTypeGuest()
//
//                let statusKey = MicroBlogKey(id: statusId, host: host)
//                let destination = FlareDestination.statusDetail(accountType: accountType, statusKey: statusKey)
//                navigate(to: destination)
//                return true
//            }
//
//        case "/search":
//
//            if let query = params["q"] {
//
//                let accountType = AccountTypeGuest()
//                let destination = FlareDestination.search(accountType: accountType, keyword: query)
//                navigate(to: destination)
//                return true
//            }
//
//        default:
//
//            return false
//        }

        return false
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

            print("未能识别的Compose类型: \(type(of: route))")
            return .search(accountType: AccountTypeGuest(), keyword: "")

        default:
            print("未处理的路由类型: \(type(of: route))")
            return .search(accountType: AccountTypeGuest(), keyword: "")
        }
    }
}
