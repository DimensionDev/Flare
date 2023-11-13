import Foundation
import SwiftUI
import shared

struct RouterView : View {
    @Bindable var sheetRouter = Router<SheetDestination>()
    var body: some View {
        SplashScreen { type in
            ZStack {
                switch type {
                case .home:
                    HomeScreen()
                case .login:
                    Text("Flare")
                case .splash:
                    Text("Flare")
                }
            }.sheet(isPresented: Binding(get: {
                type == .login
            }, set: { Value in
                
            }), content: {
                if type == .login {
                    NavigationStack(path: $sheetRouter.navPath) {
                        ServiceSelectScreen(
                            toMisskey: {
                                sheetRouter.navigate(to: .misskey)
                            },
                            toMastodon: {
                                sheetRouter.navigate(to: .mastodon)
                            },
                            toBluesky: {
                                sheetRouter.navigate(to: .bluesky)
                            }
                        )
                        .withSheetRouter {
                            sheetRouter.clearBackStack()
                        } toMisskey: {
                            sheetRouter.navigate(to: .misskey)
                        } toMastodon: {
                            sheetRouter.navigate(to: .mastodon)
                        } toBluesky: {
                            sheetRouter.navigate(to: .bluesky)
                        }

                    }
                    .onOpenURL { url in
                        if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Mastodon)) {
                            if let range = url.absoluteString.range(of: "code=") {
                                let code = url.absoluteString.suffix(from: range.upperBound)
                                sheetRouter.navigate(to: .mastodonCallback(code: String(code)))
                            }
                        } else if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Misskey)) {
                            if let range = url.absoluteString.range(of: "session=") {
                                let session = url.absoluteString.suffix(from: range.upperBound)
                                sheetRouter.navigate(to: .misskeyCallback(session: String(session)))
                            }
                        }
                    }
                    .interactiveDismissDisabled()
                }
            })
        }
    }
}

@Observable
class RouterViewModel : MoleculeViewModelProto {
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
    case search(q: String)
}

public enum SheetDestination: Codable, Hashable {
    case settings
    case accountSettings
    case mastodon
    case mastodonCallback(code: String)
    case misskey
    case misskeyCallback(session: String)
    case serviceSelection
    case bluesky
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
    func withTabRouter() -> some View {
        navigationDestination(
            for: TabDestination.self
        ) { destination in
            switch destination {
            case let .profile(userKey):
                ProfileScreen(userKey: MicroBlogKey.companion.valueOf(str: userKey))
            case let .statusDetail(statusKey):
                ContentView()
            case let .profileWithUserNameAndHost(userName, host):
                ContentView()
            case let .search(data):
                ContentView()
            }
        }
    }
    
    func withSheetRouter(toHome:@escaping () -> Void, toMisskey: @escaping () -> Void, toMastodon: @escaping () -> Void, toBluesky: @escaping () -> Void) -> some View {
        navigationDestination(for: SheetDestination.self) { destination in
            switch destination {
            case .mastodon:
                MastodonOAuthScreen()
            case let .mastodonCallback(code):
                MastodonCallbackScreen(code: code, toHome: toHome)
            case .misskey:
                MisskeyOAuthScreen()
            case let .misskeyCallback(session):
                MisskeyCallbackScreen(session: session, toHome: toHome)
            case .serviceSelection:
                ServiceSelectScreen(
                    toMisskey: toMisskey,
                    toMastodon: toMastodon,
                    toBluesky: toBluesky
                )
            case .settings:
                SettingsScreen()
            case .accountSettings:
                AccountsScreen()
            case .bluesky:
                BlueskyLoginScreen(toHome: toHome)
            }
        }
    }
}
