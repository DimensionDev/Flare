import SwiftUI
import Combine
import KotlinSharedUI
import LazyPager
import FlareAppleCore
import FlareAppleUI

struct Router<Root: View>: View {
    @Environment(\.openURL) private var openURL
    @ViewBuilder let root: (@escaping (Route) -> Void) -> Root
    @State private var ownedBackStack: [Route] = []
    private let externalBackStack: Binding<[Route]>?
    private let navigator: RouterNavigator?
    @State private var sheet: Route? = nil
    @State private var cover: Route? = nil
    @State private var alertRoute: Route? = nil
    @StateObject private var deepLinkPresenter: KotlinPresenter<DeepLinkPresenterState>
    @StateObject private var deepLinkHandler = DeepLinkHandler()
    
    init(
        backStack: Binding<[Route]>? = nil,
        navigator: RouterNavigator? = nil,
        @ViewBuilder root: @escaping (@escaping (Route) -> Void) -> Root
    ) {
        self.root = root
        self.externalBackStack = backStack
        self.navigator = navigator
        let handler = DeepLinkHandler()
        self._deepLinkHandler = .init(wrappedValue: handler)
        self._deepLinkPresenter = .init(wrappedValue: .init(presenter: DeepLinkPresenter(onRoute: { [weak handler] deeplinkRoute in
            if let route = Route.fromDeepLinkRoute(deeplinkRoute: deeplinkRoute){
                handler?.onRoute?(route)
            }
        }, onLink: { [weak handler] link in
            handler?.onLink?(link)
        })))
    }

    private var backStack: Binding<[Route]> {
        externalBackStack ?? $ownedBackStack
    }

    private var navigationRequests: AnyPublisher<Route, Never> {
        navigator?.requests.eraseToAnyPublisher() ?? Empty().eraseToAnyPublisher()
    }
    
    var body: some View {
        NavigationStack(path: backStack) {
            root({ route in
                navigate(route: route)
            })
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    goBack: pop
                )
            }
        }
        .environment(\.timelineMediaActionHandler, IOSTimelineMediaActions.handler)
        .sheet(item: $sheet) { route in
            if #available(iOS 18.0, *) {
                NavigationStack {
                    route.view(
                        onNavigate: { route in navigate(route: route) },
                        goBack: pop
                    )
                }
            } else {
                NavigationStack {
                    route.view(
                        onNavigate: { route in navigate(route: route) },
                        goBack: pop
                    )
                    .navigationDestination(for: Route.self) { destination in
                        destination.view(
                            onNavigate: { route in navigate(route: route) },
                            goBack: {}
                        )
                    }
                }
            }
        }
        .fullScreenCover(item: $cover) { route in
            NavigationStack {
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    goBack: pop
                )
            }
            .background(ClearFullScreenBackground())
            .colorScheme(.dark)
        }
        .alert(alertRoute?.alertTitle ?? "", isPresented: Binding(get: { alertRoute != nil }, set: { if !$0 { alertRoute = nil } })) {
            alertRoute?.alertActions()
        } message: {
            alertRoute?.alertMessage()
        }
        .environment(\.openURL, OpenURLAction { url in
            deepLinkPresenter.state.handle(url: url.absoluteString)
            return .handled
        })
        .onOpenURL { url in
            let targetURL = url.openInFlareTargetURL ?? url
            deepLinkPresenter.state.handle(url: targetURL.absoluteString)
        }
        .onReceive(navigationRequests) { route in
            navigate(route: route)
        }
        .onAppear {
            deepLinkHandler.onRoute = { route in
                navigate(route: route)
            }
            deepLinkHandler.onLink = { link in
                if let url = URL(string: link) {
                    openURL(url)
                }
            }
        }
    }

    func navigate(route: Route) {
        if route.alertTitle != nil {
            alertRoute = route
        } else if isSheetRoute(route: route) {
            sheet = route
        } else if isFullScreenCover(route: route) {
            cover = route
        } else if backStack.wrappedValue.last != route {
            backStack.wrappedValue.append(route)
            sheet = nil
            cover = nil
        }
    }

    private func pop() {
        if !backStack.wrappedValue.isEmpty {
            backStack.wrappedValue.removeLast()
        }
    }
    
    func isSheetRoute(route: Route) -> Bool {
        switch route {
        case .deepLinkAccountPicker,
                .composeNew,
                .composeCrossPost,
                .composeDraft,
                .composeQuote,
                .composeReply,
                .composeVVOReplyComment,
                .relogin,
                .tabSettings,
                .statusBlueskyReport,
                .statusMisskeyReport,
                .editUserList,
                .statusShareSheet,
                .secondaryMenu,
                .statusInsight,
                .profileInsight,
                .statusAddReaction:
            return true
        default:
            return false
        }
    }
    
    func isFullScreenCover(route: Route) -> Bool {
        switch route {
        case .mediaStatusMedia, .mediaImage, .mediaRaw:
            return true
        default:
            return false
        }
    }
}

@MainActor
final class RouterNavigator: ObservableObject {
    fileprivate let requests = PassthroughSubject<Route, Never>()

    func navigate(_ route: Route) {
        requests.send(route)
    }
}

class DeepLinkHandler : ObservableObject {
    var onRoute: ((Route) -> Void)?
    var onLink: ((String) -> Void)?
}

private extension URL {
    var openInFlareTargetURL: URL? {
        guard scheme?.lowercased() == "flare",
              host?.lowercased() == "open",
              let targetValue = URLComponents(
                  url: self,
                  resolvingAgainstBaseURL: false
              )?.queryItems?.first(where: { $0.name == "url" })?.value,
              let targetURL = URL(string: targetValue),
              let targetScheme = targetURL.scheme?.lowercased(),
              targetScheme == "https" || targetScheme == "http"
        else {
            return nil
        }
        return targetURL
    }
}
