import Combine
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

struct Router: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.openWindow) private var openWindow
    let initialRoute: Route
    let externalNavigationRequest: MacMainWindowNavigationRequest?
    let forwardsContentRoutesToMainWindow: Bool
    @State private var backStack: [Route] = []
    @State private var sheet: Route?
    @State private var alertRoute: Route?
    @State private var statusMediaResolveRequest: MacStatusMediaResolveRequest?
    @State private var didHandleInitialActionRoute = false
    @State private var handledExternalNavigationRequestId: UUID?
    @StateObject private var deepLinkPresenter: KotlinPresenter<DeepLinkPresenterState>
    @StateObject private var deepLinkHandler: MacDeepLinkHandler

    init(
        initialRoute: Route,
        externalNavigationRequest: MacMainWindowNavigationRequest? = nil,
        forwardsContentRoutesToMainWindow: Bool = false
    ) {
        self.initialRoute = initialRoute
        self.externalNavigationRequest = externalNavigationRequest
        self.forwardsContentRoutesToMainWindow = forwardsContentRoutesToMainWindow
        let handler = MacDeepLinkHandler()
        _deepLinkHandler = .init(wrappedValue: handler)
        _deepLinkPresenter = .init(
            wrappedValue: .init(
                presenter: DeepLinkPresenter(
                    onRoute: { [weak handler] deeplinkRoute in
                        if let route = Route.fromDeepLinkRoute(deeplinkRoute: deeplinkRoute) {
                            handler?.onRoute?(route)
                        }
                    },
                    onLink: { [weak handler] link in
                        handler?.onLink?(link)
                    }
                )
            )
        )
    }

    var body: some View {
        NavigationStack(path: $backStack) {
            initialRoute.view(
                onNavigate: handle(route:),
                goBack: {}
            )
            .environment(\.openURL, OpenURLAction { url in
                deepLinkPresenter.state.handle(url: url.absoluteString)
                return .handled
            })
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: handle(route:),
                    goBack: goBack
                )
                .backport
                .navigationTransitionAutomatic()
                .environment(\.openURL, OpenURLAction { url in
                    deepLinkPresenter.state.handle(url: url.absoluteString)
                    return .handled
                })
            }
        }
        .environment(\.macCrossPostAction, { data in
            handle(
                route: .statusCrossPost(
                    data.accountType,
                    data.statusKey,
                    data.shareUrl
                )
            )
        })
        .sheet(item: $sheet) { route in
            NavigationStack {
                route.view(
                    onNavigate: handle(route:),
                    goBack: {
                        sheet = nil
                    }
                )
            }
//            .frame(minWidth: 380, minHeight: 420)
        }
        .alert(alertRoute?.alertTitle ?? "", isPresented: Binding(
            get: { alertRoute != nil },
            set: { isPresented in
                if !isPresented {
                    alertRoute = nil
                }
            }
        )) {
            alertRoute?.alertActions()
        } message: {
            alertRoute?.alertMessage()
        }
        .background {
            if let statusMediaResolveRequest {
                MacStatusMediaResolver(
                    request: statusMediaResolveRequest,
                    onResolved: { medias, index, preview, shareContext in
                        MacMediaWindowCoordinator.shared.open(
                            medias: medias,
                            initialIndex: index,
                            preview: preview,
                            shareContext: shareContext,
                            openWindow: openWindow
                        )
                    },
                    onFinished: {
                        self.statusMediaResolveRequest = nil
                    }
                )
                .id(statusMediaResolveRequest.id)
            }
        }
        .onOpenURL { url in
            deepLinkPresenter.state.handle(url: url.absoluteString)
        }
        .onAppear {
            deepLinkHandler.onRoute = { route in
                handle(route: route)
            }
            deepLinkHandler.onLink = { link in
                if let url = URL(string: link) {
                    openURL(url)
                }
            }
            handleInitialActionRouteIfNeeded()
            handleExternalNavigationRequestIfNeeded(externalNavigationRequest)
        }
        .onChange(of: externalNavigationRequest?.id) { _, _ in
            handleExternalNavigationRequestIfNeeded(externalNavigationRequest)
        }
    }

    private func navigate(_ route: Route) {
        if backStack.last != route {
            backStack.append(route)
        }
    }

    private func handle(route: Route) {
        switch route {
        case _ where forwardsContentRoutesToMainWindow && shouldForwardToMainWindow(route):
            MacMainWindowCoordinator.shared.open(route: route, openWindow: openWindow)
        case .composeNew:
            MacComposeWindowCoordinator.shared.openNew(openWindow: openWindow)
        case .composeCrossPost(let prefill):
            sheet = nil
            MacComposeWindowCoordinator.shared.openCrossPost(
                prefill: prefill,
                openWindow: openWindow
            )
        case .composeDraft(let groupId):
            MacComposeWindowCoordinator.shared.openDraft(
                groupId: groupId,
                openWindow: openWindow
            )
        case .composeQuote(let accountType, let statusKey):
            MacComposeWindowCoordinator.shared.openQuote(
                accountType: accountType,
                statusKey: statusKey,
                openWindow: openWindow
            )
        case .composeReply(let accountType, let statusKey):
            MacComposeWindowCoordinator.shared.openReply(
                accountType: accountType,
                statusKey: statusKey,
                openWindow: openWindow
            )
        case .composeVVOReplyComment(let accountType, let statusKey, let rootId):
            MacComposeWindowCoordinator.shared.openVVOReplyComment(
                accountType: accountType,
                statusKey: statusKey,
                rootId: rootId,
                openWindow: openWindow
            )
        case .mediaImage, .mediaRaw:
            MacMediaWindowCoordinator.shared.open(route: route, openWindow: openWindow)
        case .mediaStatusMedia(let accountType, let statusKey, let selectedIndex, let preview):
            statusMediaResolveRequest = MacStatusMediaResolveRequest(
                accountType: accountType,
                statusKey: statusKey,
                initialIndex: Int(selectedIndex),
                preview: preview
            )
        case .externalLink(let link):
            if let url = URL(string: link) {
                openURL(url)
            }
        case _ where route.isDirectMessageWindowRoute:
            MacDirectMessageWindowCoordinator.shared.open(route: route, openWindow: openWindow)
        case _ where route.isAgentWindowRoute:
            MacAgentWindowCoordinator.shared.open(route: route, openWindow: openWindow)
        case _ where route.alertTitle != nil:
            alertRoute = route
        case _ where route == initialRoute:
            backStack.removeAll()
            sheet = nil
        default:
            if isSheetRoute(route) {
                sheet = route
            } else {
                navigate(route)
                sheet = nil
            }
        }
    }

    private func shouldForwardToMainWindow(_ route: Route) -> Bool {
        switch route {
        case .statusDetail,
                .profileUser,
                .profileUserNameWithHost:
            true
        default:
            false
        }
    }

    private func handleInitialActionRouteIfNeeded() {
        guard !didHandleInitialActionRoute else { return }
        didHandleInitialActionRoute = true

        if initialRoute.alertTitle != nil {
            handle(route: initialRoute)
            return
        }

        switch initialRoute {
        case .composeNew,
                .composeCrossPost,
                .composeDraft,
                .composeQuote,
                .composeReply,
                .composeVVOReplyComment,
                .mediaImage,
                .mediaRaw,
                .mediaStatusMedia,
                .directMessages,
                .dmConversation,
                .userDirectMessages,
                .allDirectMessages,
                .externalLink:
            handle(route: initialRoute)
        default:
            break
        }
    }

    private func handleExternalNavigationRequestIfNeeded(_ request: MacMainWindowNavigationRequest?) {
        guard let request,
              handledExternalNavigationRequestId != request.id
        else {
            return
        }

        handledExternalNavigationRequestId = request.id
        handle(route: request.route)
    }

    private func goBack() {
        if !backStack.isEmpty {
            backStack.removeLast()
        }
    }

    private func isSheetRoute(_ route: Route) -> Bool {
        switch route {
        case .serviceSelect,
                .relogin,
                .deepLinkAccountPicker,
                .statusAddReaction,
                .statusShareSheet,
                .statusCrossPost,
                .statusBlueskyReport,
                .statusMisskeyReport,
                .editUserList:
            true
        default:
            false
        }
    }
}

final class MacDeepLinkHandler: ObservableObject {
    var onRoute: ((Route) -> Void)?
    var onLink: ((String) -> Void)?
}

public extension Backport where Content: View {
    @ViewBuilder
    func navigationTransitionAutomatic() -> some View {
        if #available(macOS 15.0, *) {
            content.navigationTransition(.automatic)
        } else {
            content
        }
    }
}
