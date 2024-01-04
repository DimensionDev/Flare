import Foundation
import SwiftUI
import shared

struct RouterView: View {
    var body: some View {
        SplashScreen { type in
            ZStack {
                switch type {
                case .home:
                    HomeScreen()
                case .login:
                    Image(.logo)
                        .resizable()
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .clipped()
                        .padding()
                case .splash:
                    Image(.logo)
                        .resizable()
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .clipped()
                        .padding()
                }
            }.sheet(isPresented: Binding(get: {
                type == .login
            }, set: { _ in
            }), content: {
                if type == .login {
                    ServiceSelectScreen(
                        toHome: {
                        }
                    )
                    .interactiveDismissDisabled()
                }
            })
        }
    }
}

@Observable
class RouterViewModel: MoleculeViewModelProto {
    let presenter: SplashPresenter
    var model: __SplashType
    typealias Model = __SplashType
    typealias Presenter = SplashPresenter
    init() {
        presenter = SplashPresenter(toHome: {}, toLogin: {})
        model = presenter.models.value
    }
}

public enum TabDestination: Codable, Hashable {
    case profile(userKey: String)
    case statusDetail(statusKey: String)
    case profileWithUserNameAndHost(userName: String, host: String)
    case search(query: String)
    case profileMedia(userKey: String)
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
            case let .profile(userKey):
                ProfileScreen(
                    userKey: MicroBlogKey.companion.valueOf(str: userKey),
                    toProfileMedia: { userKey in
                        router.navigate(to: .profileMedia(userKey: userKey.description()))
                    }
                )
            case let .statusDetail(statusKey):
                Text("todo")
            case let .profileWithUserNameAndHost(userName, host):
                Text("todo")
            case let .search(data):
                Text("todo")
            case let .profileMedia(userKey):
                ProfileMediaListScreen(userKey: MicroBlogKey.companion.valueOf(str: userKey))
            }
        }
    }
}
