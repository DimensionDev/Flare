import Foundation
import SwiftUI
import shared

struct RouterView : View {
    @Bindable var oauthRouter = Router<OAuthDestination>()
    var body: some View {
        NavigationStack(path: $oauthRouter.navPath) {
            SplashScreen { type in
                switch type {
                case .home:
                    HomeScreen()
                case .login:
                    ServiceSelectScreen(
                        toMisskey: {
                            oauthRouter.navigate(to: .misskey)
                        },
                        toMastodon: {
                            oauthRouter.navigate(to: .mastodon)
                        }
                    )
                    .withOAuthRouter(router: oauthRouter)
                case .splash:
                    Text("Flare")
                }
            }
        }.onOpenURL { url in
            if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Mastodon)) {
                if let range = url.absoluteString.range(of: "code=") {
                    let code = url.absoluteString.suffix(from: range.upperBound)
                    oauthRouter.navigate(to: .mastodonCallback(code: String(code)))
                }
                
            } else if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Misskey)) {
                if let range = url.absoluteString.range(of: "session=") {
                    let session = url.absoluteString.suffix(from: range.upperBound)
                    oauthRouter.navigate(to: .misskeyCallback(session: String(session)))
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

public enum OAuthDestination: Codable, Hashable {
    case mastodon
    case mastodonCallback(code: String)
    case misskey
    case misskeyCallback(session: String)
    case home
    case serviceSelection
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
        navPath = NavigationPath()
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
    
    func withOAuthRouter(router: Router<OAuthDestination>) -> some View {
        navigationDestination(for: OAuthDestination.self) { destination in
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
            case .serviceSelection:
                ServiceSelectScreen(
                    toMisskey: {
                        router.navigate(to: .misskey)
                    },
                    toMastodon: {
                        router.navigate(to: .mastodon)
                    }
                )
                
            }
        }
    }
}
