import Foundation
import SwiftUI
import shared

struct RouterView: View {
    let presenter = SplashPresenter(toHome: {}, toLogin: {})
    @State var appSettings = AppSettings()
    var body: some View {
        Observing(presenter.models) { state in
            ZStack {
                switch state.toSwiftEnum() {
                case .home:
                    HomeScreen()
                case .login:
                    SplashScreen()
                case .splash:
                    SplashScreen()
                }
            }.sheet(isPresented: Binding(get: {
                if case .login = state.toSwiftEnum() {
                    true
                } else {
                    false
                }
            }, set: { _ in
            }), content: {
                if case .login = state.toSwiftEnum() {
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
        }
    }
}

@Observable
final class Router: ObservableObject {
    var navPath = NavigationPath()
    var sheet: AppleRoute?
    var fullScreenCover: AppleRoute?
    var dialog: AppleRoute?
    func navigate(to route: AppleRoute) {
        switch route.routeType {
        case .screen:
            dialog = nil
            sheet = nil
            fullScreenCover = nil
            navPath.append(route)
        case .dialog:
            dialog = route
        case .sheet:
            sheet = route
        case .fullScreen:
            fullScreenCover = route
        }
    }
    func hideSheet() {
        sheet = nil
    }
    func hideFullScreenCover() {
        fullScreenCover = nil
    }
    func navigateBack() {

    }
}

struct RouteView: View {
    let route: AppleRoute
    let onBack: () -> Void
    let onNavigate: (_ route: AppleRoute) -> Void
    var body: some View {
        switch onEnum(of: route) {
        case .bluesky:
            EmptyView()
        case .callback:
            EmptyView()
        case .compose(let data):
            switch onEnum(of: data) {
            case .new(let data):
                ComposeScreen(onBack: onBack, accountType: AccountTypeSpecific(accountKey: data.accountKey), status: nil)
            case .quote(let data): EmptyView()
            case .reply(let data): EmptyView()
            }
        case .deleteStatus:
            EmptyView()
        case .mastodon:
            EmptyView()
        case .misskey:
            EmptyView()
        case .profile(let data):
            ProfileScreen(
                accountType: AccountTypeSpecific(accountKey: data.accountKey),
                userKey: data.userKey,
                toProfileMedia: { userKey in
                    onNavigate(AppleRoute.ProfileMedia(accountKey: data.accountKey, userKey: userKey))
                }
            )
        case .profileMedia(let data):
            ProfileMediaListScreen(
                accountType: AccountTypeSpecific(accountKey: data.accountKey),
                userKey: data.userKey
            )
        case .profileWithNameAndHost(let data):
            ProfileWithUserNameScreen(
                accountType: AccountTypeSpecific(accountKey: data.accountKey),
                userName: data.userName,
                host: data.host
            ) { userKey in
                onNavigate(AppleRoute.ProfileMedia(accountKey: data.accountKey, userKey: userKey))
            }
        case .rawImage:
            EmptyView()
        case .search(let data):
            SearchScreen(
                accountType: AccountTypeSpecific(accountKey: data.accountKey),
                initialQuery: data.keyword,
                onUserClicked: { user in
                    onNavigate(AppleRoute.Profile(accountKey: data.accountKey, userKey: user.key))
                }
            )
        case .statusDetail(let data):
            StatusDetailScreen(
                accountType: AccountTypeSpecific(accountKey: data.accountKey),
                statusKey: data.statusKey
            )
        case .statusMedia(let data):
            StatusMediaScreen(accountType: AccountTypeSpecific(accountKey: data.accountKey), statusKey: data.statusKey, index: data.index, dismiss: onBack)
        case .vVO:
            EmptyView()
        }
    }
}

extension AppleRoute: Identifiable {
}

struct TabItem<Content: View>: View {
    @State var router = Router()
    let content: (Router) -> Content
    var body: some View {
        NavigationStack(path: $router.navPath) {
            content(router)
                .navigationDestination(for: AppleRoute.self) { route in
                    RouteView(route: route, onBack: {}, onNavigate: {route in router.navigate(to: route)})
                }
                .sheet(item: $router.sheet) { route in
                    RouteView(route: route, onBack: {router.hideSheet()}, onNavigate: {route in router.navigate(to: route)})
#if os(macOS)
                        .frame(minWidth: 500, minHeight: 400)
#endif
                }
                .fullScreenCover(item: $router.fullScreenCover) { route in
                    RouteView(route: route, onBack: {router.hideFullScreenCover()}, onNavigate: {route in router.navigate(to: route)})
                }

        }
        .environment(\.openURL, OpenURLAction { url in
            if let event = AppDeepLinkHelper.shared.parse(url: url.absoluteString) {
                router.navigate(to: event)
                return .handled
            } else {
                return .systemAction
            }
        })
    }
}
