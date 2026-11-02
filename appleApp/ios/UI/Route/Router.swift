import SwiftUI
@preconcurrency import KotlinSharedUI
import LazyPager
import Combine
import FlareAppleCore
import FlareAppleUI

struct Router<Root: View>: View {
    @Environment(\.openURL) private var openURL
    @ViewBuilder let root: (@escaping (Route) -> Void) -> Root
    @State private var navigation = RouterNavigationState<Route>()
    @State private var alertRoute: Route? = nil
    @State private var deepLinkPresenter: KotlinPresenter<DeepLinkPresenterState>
    @State private var deepLinkHandler: DeepLinkHandler
    private let pluginOAuthCallbacks = PluginOAuthCallbackFacadeV1()
    
    init(@ViewBuilder root: @escaping (@escaping (Route) -> Void) -> Root) {
        self.root = root
        let handler = DeepLinkHandler()
        self._deepLinkPresenter = .init(wrappedValue: .init(presenter: DeepLinkPresenter(onRoute: { [weak handler] deeplinkRoute in
            if let route = Route.fromDeepLinkRoute(deeplinkRoute: deeplinkRoute){
                handler?.onRoute?(route)
            }
        }, onLink: { [weak handler] link in
            handler?.onLink?(link)
        })))
        self.deepLinkHandler = handler
    }
    
    var body: some View {
        NavigationStack(path: $navigation.backStack) {
            root({ route in
                navigate(route: route)
            })
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    goBack: navigation.goBack
                )
            }
        }
        .environment(\.timelineMediaActionHandler, IOSTimelineMediaActions.handler)
        .sheet(item: $navigation.sheet) { route in
            if #available(iOS 18.0, *) {
                NavigationStack {
                    route.view(
                        onNavigate: { route in navigate(route: route) },
                        goBack: navigation.dismissSheet
                    )
                }
            } else {
                NavigationStack {
                    route.view(
                        onNavigate: { route in navigate(route: route) },
                        goBack: navigation.dismissSheet
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
        .fullScreenCover(item: $navigation.cover) { route in
            NavigationStack {
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    goBack: navigation.dismissCover
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
            if pluginOAuthCallbacks.canHandle(url: targetURL.absoluteString) {
                Task { try? await pluginOAuthCallbacks.handle(url: targetURL.absoluteString) }
            } else {
                deepLinkPresenter.state.handle(url: targetURL.absoluteString)
            }
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
            navigation.sheet = route
        } else if isFullScreenCover(route: route) {
            navigation.cover = route
        } else if navigation.backStack.last != route {
            navigation.backStack.append(route)
            navigation.sheet = nil
            navigation.cover = nil
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

final class DeepLinkHandler {
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
