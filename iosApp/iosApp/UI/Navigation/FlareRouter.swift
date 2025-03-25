import shared
import SwiftUI

class FlareRouter: ObservableObject {
    public var appState: FlareAppState

    @Published var activeDestination: FlareDestination?

    @Published var presentationType: FlarePresentationType = .push

    @Published var isSheetPresented: Bool = false

    @Published var isFullScreenPresented: Bool = false

    @Published var isDialogPresented: Bool = false

    @Published var navigationDepth: Int = 0

    @Published var navigationPath = NavigationPath()

    init(appState: FlareAppState = FlareAppState()) {
        self.appState = appState
    }

    func navigate(to destination: FlareDestination) {
        // 根据目标类型选择不同的导航方式
        presentationType = destination.navigationType
        activeDestination = destination

        switch presentationType {
        case .push:
            navigatePush(to: destination)
            navigationDepth += 1
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
        navigationPath.append(destination)
        print("导航至: \(destination)")
    }

    private func dismissCurrentView() {
        switch presentationType {
        case .sheet:
            dismissSheet()
        case .fullScreen:
            dismissFullScreenCover()
        case .dialog:
            isDialogPresented = false
            activeDestination = nil
        case .push:
            // 如果有导航路径，则弹出最后一个
            if !navigationPath.isEmpty {
                navigationPath.removeLast()
            }
            activeDestination = nil
            if navigationDepth > 0 {
                navigationDepth -= 1
            }
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
