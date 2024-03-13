import Foundation
import SwiftUI
import shared

struct RouterView: View {
    @State var viewModel = RouterViewModel()
    @State var appSettings = AppSettings()
    var body: some View {
        ZStack {
            switch viewModel.model.toSwiftEnum() {
            case .home:
                HomeScreen()
            case .login:
                SplashScreen()
            case .splash:
                SplashScreen()
            }
        }.sheet(isPresented: Binding(get: {
            if case .login = viewModel.model.toSwiftEnum() {
                true
            } else {
                false
            }
        }, set: { _ in
        }), content: {
            if case .login = viewModel.model.toSwiftEnum() {
                ServiceSelectScreen(
                    toHome: {
                    }
                )
#if os(macOS)
    .frame(minWidth: 600, minHeight: 400)
#endif
                    .interactiveDismissDisabled()
            }
        })
        .environment(\.appSettings, appSettings)
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class RouterViewModel: MoleculeViewModelProto {
    typealias Model = __SplashType
    typealias Presenter = SplashPresenter
    let presenter: Presenter
    var model: Model
    init() {
        presenter = SplashPresenter(toHome: {}, toLogin: {})
        model = presenter.models.value
    }
}

public enum TabDestination: Codable, Hashable {
    case profile(accountType: SwiftAccountType, userKey: String)
    case statusDetail(accountType: SwiftAccountType, statusKey: String)
    case profileWithUserNameAndHost(accountType: SwiftAccountType, userName: String, host: String)
    case search(accountType: SwiftAccountType, query: String)
    case profileMedia(accountType: SwiftAccountType, userKey: String)
}

public enum SwiftAccountType: Codable, Hashable {
    case active
    case specific(accountKey: String)
    func toKotlin() -> AccountType {
        return switch self {
        case .active: AccountTypeActive()
        case .specific(let accountKey): AccountTypeSpecific(accountKey: MicroBlogKey.companion.valueOf(str: accountKey))
        }
    }
}

@Observable
final class Router<T: Hashable>: ObservableObject {
    var navPath = NavigationPath()
    func navigate(to destination: T) {
        navPath.append(destination)
    }
    func navigateBack(count: Int = 1) {
        navPath.removeLast(count)
    }
    func navigateToRoot() {
        navPath.removeLast(navPath.count)
    }
    func clearBackStack() {
        navPath = NavigationPath()
    }
}

@MainActor
extension View {
    func withTabRouter(router: Router<TabDestination>) -> some View {
        navigationDestination(
            for: TabDestination.self
        ) { destination in
            switch destination {
            case let .profile(accountType, userKey):
                ProfileScreen(
                    accountType: accountType.toKotlin(),
                    userKey: MicroBlogKey.companion.valueOf(str: userKey),
                    toProfileMedia: { userKey in
                        router.navigate(to: .profileMedia(accountType: accountType, userKey: userKey.description()))
                    }
                )
            case let .statusDetail(accountType, statusKey):
                StatusDetailScreen(
                    accountType: accountType.toKotlin(),
                    statusKey: MicroBlogKey.companion.valueOf(str: statusKey)
                )
            case let .profileWithUserNameAndHost(accountType, userName, host):
                ProfileWithUserNameScreen(
                    accountType: accountType.toKotlin(),
                    userName: userName,
                    host: host
                ) { userKey in
                    router.navigate(to: .profileMedia(accountType: accountType, userKey: userKey.description()))
                }
            case let .search(accountType, data):
                SearchScreen(
                    accountType: accountType.toKotlin(),
                    initialQuery: data,
                    onUserClicked: { user in
                        router.navigate(to: .profileMedia(accountType: accountType, userKey: user.userKey.description()))
                    }
                )
            case let .profileMedia(accountType, userKey):
                ProfileMediaListScreen(
                    accountType: accountType.toKotlin(),
                    userKey: MicroBlogKey.companion.valueOf(str: userKey)
                )
            }
        }
    }
}
