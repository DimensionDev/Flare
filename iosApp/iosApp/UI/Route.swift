import Foundation
import SwiftUI
import shared

struct RouterView: View {
    @State var activeAccountViewModel = ActiveAccountViewModel()
    @State var viewModel = RouterViewModel()
    @State var appSettings = AppSettings()
    var body: some View {
        ZStack {
            switch onEnum(of: viewModel.model) {
            case .home(_):
                switch onEnum(of: activeAccountViewModel.model.user) {
                case .success(let data):
                    HomeScreen(accountKey: data.data.userKey)
                case .error:
                    SplashScreen()
                case .loading:
                    SplashScreen()
                }
            case .login:
                SplashScreen()
            case .splash:
                SplashScreen()
            }
        }.sheet(isPresented: Binding(get: {
            if case .login = onEnum(of: viewModel.model) {
                true
            } else {
                false
            }
        }, set: { _ in
        }), content: {
            if case .login = onEnum(of: viewModel.model) {
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
        .activateViewModel(viewModel: activeAccountViewModel)
    }
}

@Observable
class ActiveAccountViewModel : MoleculeViewModelBase<UserState, ActiveAccountPresenter> {
}

@Observable
class RouterViewModel: MoleculeViewModelProto {
    typealias Model = SplashType
    typealias Presenter = SplashPresenter
    let presenter: Presenter
    var model: Model
    init() {
        presenter = SplashPresenter(toHome: {}, toLogin: {})
        model = presenter.models.value
    }
}

public enum TabDestination: Codable, Hashable {
    case profile(accountKey: String, userKey: String)
    case statusDetail(accountKey: String, statusKey: String)
    case profileWithUserNameAndHost(accountKey: String, userName: String, host: String)
    case search(accountKey: String, query: String)
    case profileMedia(accountKey: String, userKey: String)
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
            case let .profile(accountKey, userKey):
                ProfileScreen(
                    accountKey: MicroBlogKey.companion.valueOf(str: accountKey),
                    userKey: MicroBlogKey.companion.valueOf(str: userKey),
                    toProfileMedia: { userKey in
                        router.navigate(to: .profileMedia(accountKey: accountKey, userKey: userKey.description()))
                    }
                )
            case let .statusDetail(accountKey, statusKey):
                StatusDetailScreen(
                    accountKey: MicroBlogKey.companion.valueOf(str: accountKey),
                    statusKey: MicroBlogKey.companion.valueOf(str: statusKey)
                )
            case let .profileWithUserNameAndHost(accountKey, userName, host):
                ProfileWithUserNameScreen(
                    accountKey: MicroBlogKey.companion.valueOf(str: accountKey),
                    userName: userName,
                    host: host
                ) { userKey in
                    router.navigate(to: .profileMedia(accountKey: accountKey, userKey: userKey.description()))
                }
            case let .search(accountKey, data):
                SearchScreen(accountKey: MicroBlogKey.companion.valueOf(str: accountKey),initialQuery: data)
            case let .profileMedia(accountKey, userKey):
                ProfileMediaListScreen(accountKey: MicroBlogKey.companion.valueOf(str: accountKey),userKey: MicroBlogKey.companion.valueOf(str: userKey))
            }
        }
    }
}
