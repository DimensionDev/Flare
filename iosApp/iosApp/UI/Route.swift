import Foundation
import SwiftUI
import shared

struct RouterView : View {
    @Bindable var router = Router<RootDestination>()
    var body: some View {
        NavigationStack(path: $router.navPath) {
            ServiceSelectScreen(
                toMisskey: {
                    router.navigate(to: .misskey)
                },
                toMastodon: {
                    router.navigate(to: .mastodon)
                }
            )
            .withOAuthRouter(router: router)
        }.onOpenURL { url in
            if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Mastodon)) {
                if let range = url.absoluteString.range(of: "code=") {
                    let code = url.absoluteString.suffix(from: range.upperBound)
                    router.navigate(to: .mastodonCallback(code: String(code)))
                }

            } else if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Misskey)) {
                if let range = url.absoluteString.range(of: "session=") {
                    let session = url.absoluteString.suffix(from: range.upperBound)
                    router.navigate(to: .misskeyCallback(session: String(session)))
                }
            }
        }
    }
}

public enum TabDestination: Codable, Hashable {
    case profile(userKey: String)
    case statusDetail(statusKey: String)
    case profileWithUserNameAndHost(userName: String, host: String)
    case settings
    case accountSettings
}

public enum RootDestination: Codable, Hashable {
    case mastodon
    case mastodonCallback(code: String)
    case misskey
    case misskeyCallback(session: String)
    case home
}

@Observable
final class Router<T: Hashable>: ObservableObject {
    
    var navPath = NavigationPath()
    
    func navigate(to destination: T) {
        navPath.append(destination)
    }
    
    func navigateBack() {
        navPath.removeLast()
    }
    
    func navigateToRoot() {
        navPath.removeLast(navPath.count)
    }
    
    func clearBackStack() {
        
    }
}

@MainActor
extension View {
    func withAppRouter() -> some View {
        navigationDestination(
            for: TabDestination.self
        ) { destination in
            switch destination {
                case let .profile(userKey):
                    ContentView()
                case let .statusDetail(statusKey):
                    ContentView()
                case let .profileWithUserNameAndHost(userName, host):
                    ContentView()
                case .settings:
                    ContentView()
                case .accountSettings:
                    ContentView()
            }
        }
    }
    
    func withOAuthRouter(router: Router<RootDestination>) -> some View {
        navigationDestination(for: RootDestination.self) { destination in
            switch destination {
                case .mastodon:
                    MastodonOAuthScreen()
                case let .mastodonCallback(code):
                    MastodonCallbackScreen(code: code, toHome: {router.navigate(to: .home)})
                case .misskey:
                    MisskeyOAuthScreen()
                case let .misskeyCallback(session):
                    MisskeyCallbackScreen(session: session, toHome: {router.navigate(to: .home)})
                case .home:
                    HomeScreen()
            }
        }
    }
}
