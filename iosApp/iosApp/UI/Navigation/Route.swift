import Foundation
import shared
import SwiftUI

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

extension AppleRoute: Identifiable {}

struct TabItem<Content: View>: View {
    @State var router = Router()
    let content: (Router) -> Content
    var body: some View {
        NavigationStack(path: $router.navPath) {
            content(router)
                .navigationDestination(for: AppleRoute.self) { route in
                    getView(route: route, onBack: {}, onNavigate: { route in router.navigate(to: route) })
                }
        }
        .sheet(item: $router.sheet) { route in
            NavigationStack {
                getView(route: route, onBack: { router.hideSheet() }, onNavigate: { route in router.navigate(to: route) })
                #if os(macOS)
                    .frame(minWidth: 500, minHeight: 400)
                #endif
            }
        }
        #if os(iOS)
        .fullScreenCover(item: $router.fullScreenCover) { route in
            NavigationStack {
                getView(route: route, onBack: { router.hideFullScreenCover() }, onNavigate: { route in router.navigate(to: route) })
            }
            .modifier(SwipeToDismissModifier(onDismiss: {
                router.hideFullScreenCover()
            }))
            .presentationBackground(.black)
            .environment(\.colorScheme, .dark)
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
        case let .bluesky(data):
            switch onEnum(of: data) {
            case let .reportStatus(data): EmptyView()
            }
        case .callback:
            EmptyView()
        case let .compose(data):
            switch onEnum(of: data) {
            case let .new(data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: nil)
            case let .quote(data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: ComposeStatusQuote(statusKey: data.statusKey))
            case let .reply(data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: ComposeStatusReply(statusKey: data.statusKey))
            }
        case .deleteStatus:
            EmptyView()
        case let .mastodon(data):
            switch onEnum(of: data) {
            case let .reportStatus(data): EmptyView()
            }
        case let .misskey(data):
            switch onEnum(of: data) {
            case let .addReaction(data): MisskeyReactionSheet(accountType: data.accountType, statusKey: data.statusKey, onBack: { router.hideSheet() })
            case let .reportStatus(data): EmptyView()
            }
        case let .profile(data):
            ProfileNewScreen(
                accountType: data.accountType,
                userKey: data.userKey,
                toProfileMedia: { _ in
                    print("Media tab is now integrated in Profile page")
                }
            )
        case let .profileMedia(data):
            // 已集成到 Profile 页面的 tab 中，不再需要单独导航
            ProfileMediaListScreen(
                accountType: data.accountType,
                userKey: data.userKey,
                tabStore: ProfileTabSettingStore(timelineStore: TimelineStore(accountType: data.accountType), userKey: data.accountType as! MicroBlogKey)
            )
        case let .profileWithNameAndHost(data):
            EmptyView()
            ProfileWithUserNameScreen(
                accountType: data.accountType,
                userName: data.userName,
                host: data.host
            ) { userKey in
                onNavigate(AppleRoute.ProfileMedia(accountType: data.accountType, userKey: userKey))
            }
        case .rawImage:
            EmptyView()
        case let .search(data):
            SearchScreen(
                accountType: data.accountType,
                initialQuery: data.keyword,
                onUserClicked: { user in
                    onNavigate(AppleRoute.Profile(accountType: data.accountType, userKey: user.key))
                }
            )
        case let .statusDetail(data):
            StatusDetailScreen(
                accountType: data.accountType,
                statusKey: data.statusKey
            )
        case let .statusMedia(data):
            StatusMediaScreen(accountType: data.accountType, statusKey: data.statusKey, index: data.index, dismiss: onBack)
        case let .vVO(data):
            switch onEnum(of: data) {
            case let .commentDetail(data): VVOCommentScreen(accountType: data.accountType, commentKey: data.statusKey)
            case let .replyToComment(data):
                ComposeScreen(onBack: onBack, accountType: data.accountType, status: ComposeStatusVVOComment(statusKey: data.replyTo, rootId: data.rootId))
            case let .statusDetail(data): VVOStatusDetailScreen(accountType: data.accountType, statusKey: data.statusKey)
            }
        }
    }
}

struct SwipeToDismissModifier: ViewModifier {
    var onDismiss: () -> Void
    @State private var offset: CGSize = .zero

    func body(content: Content) -> some View {
        content
            .offset(y: offset.height)
            .animation(.interactiveSpring(), value: offset)
            .simultaneousGesture(
                DragGesture()
                    .onChanged { gesture in
                        if gesture.translation.width < 50 {
                            offset = gesture.translation
                        }
                    }
                    .onEnded { _ in
                        if abs(offset.height) > 100 {
                            offset = .zero
                            onDismiss()
                        } else {
                            offset = .zero
                        }
                    }
            )
    }
}
