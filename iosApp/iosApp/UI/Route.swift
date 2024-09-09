import Foundation
import SwiftUI
import shared

struct RouterView: View {
    let presenter = SplashPresenter(toHome: {}, toLogin: {})
    @State var appSettings = AppSettings()
    var body: some View {
        HomeScreen()
            .environment(\.appSettings, appSettings)
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
            break
//            dialog = route
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
                    getView(route: route, onBack: {}, onNavigate: {route in router.navigate(to: route)})
                }
        }
        .sheet(item: $router.sheet) { route in
            NavigationStack {
                getView(route: route, onBack: {router.hideSheet()}, onNavigate: {route in router.navigate(to: route)})
#if os(macOS)
                    .frame(minWidth: 500, minHeight: 400)
#endif
            }
        }
        #if os(iOS)
        .fullScreenCover(item: $router.fullScreenCover) { route in
            NavigationStack {
                getView(route: route, onBack: {router.hideFullScreenCover()}, onNavigate: {route in router.navigate(to: route)})
            }
        }
        #endif
        .environment(\.openURL, OpenURLAction { url in
            if let event = AppDeepLinkHelper.shared.parse(url: url.absoluteString) {
                router.navigate(to: event)
                return .handled
            } else {
                return .systemAction
            }
        })
    }
    @ViewBuilder
    func getView(route: AppleRoute, onBack: @escaping () -> Void, onNavigate: @escaping (_ route: AppleRoute) -> Void) -> some View {
        switch onEnum(of: route) {
        case .bluesky(let data):
            switch onEnum(of: data) {
            case .reportStatus(let data): EmptyView()
            }
        case .callback:
            EmptyView()
        case .compose(let data):
            switch onEnum(of: data) {
            case .new(let data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: nil)
            case .quote(let data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: ComposeStatusQuote(statusKey: data.statusKey))
            case .reply(let data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: ComposeStatusReply(statusKey: data.statusKey))
            }
        case .deleteStatus:
            EmptyView()
        case .mastodon(let data):
            switch onEnum(of: data) {
            case .reportStatus(let data): EmptyView()
            }
        case .misskey(let data):
            switch onEnum(of: data) {
            case .addReaction(let data): MisskeyReactionSheet(accountType: data.accountType, statusKey: data.statusKey, onBack: { router.hideSheet() })
            case .reportStatus(let data): EmptyView()
            }
        case .profile(let data):
            ProfileScreen(
                accountType: data.accountType,
                userKey: data.userKey,
                toProfileMedia: { userKey in
                    onNavigate(AppleRoute.ProfileMedia(accountType: data.accountType, userKey: userKey))
                }
            )
        case .profileMedia(let data):
            ProfileMediaListScreen(
                accountType: data.accountType,
                userKey: data.userKey
            )
        case .profileWithNameAndHost(let data):
            ProfileWithUserNameScreen(
                accountType: data.accountType,
                userName: data.userName,
                host: data.host
            ) { userKey in
                onNavigate(AppleRoute.ProfileMedia(accountType: data.accountType, userKey: userKey))
            }
        case .rawImage:
            EmptyView()
        case .search(let data):
            SearchScreen(
                accountType: data.accountType,
                initialQuery: data.keyword,
                onUserClicked: { user in
                    onNavigate(AppleRoute.Profile(accountType: data.accountType, userKey: user.key))
                }
            )
        case .statusDetail(let data):
            StatusDetailScreen(
                accountType: data.accountType,
                statusKey: data.statusKey
            )
        case .statusMedia(let data):
            StatusMediaScreen(accountType: data.accountType, statusKey: data.statusKey, index: data.index, dismiss: onBack)
        case .vVO(let data):
            switch onEnum(of: data) {
            case .commentDetail(let data): VVOCommentScreen(accountType: data.accountType, commentKey: data.statusKey)
            case .replyToComment(let data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: ComposeStatusVVOComment(statusKey: data.replyTo, rootId: data.rootId))
            case .statusDetail(let data): VVOStatusDetailScreen(accountType: data.accountType, statusKey: data.statusKey)
            }
        }
    }
}
