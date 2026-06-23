import Combine
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

struct Router: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.openWindow) private var openWindow
    let initialRoute: Route
    let isActive: Bool
    @State private var backStack: [Route] = []
    @State private var sheet: Route?
    @State private var statusMediaResolveRequest: MacStatusMediaResolveRequest?
    @State private var didHandleInitialActionRoute = false
    @StateObject private var deepLinkPresenter: KotlinPresenter<DeepLinkPresenterState>
    @StateObject private var deepLinkHandler: MacDeepLinkHandler

    init(
        initialRoute: Route,
        isActive: Bool = true
    ) {
        self.initialRoute = initialRoute
        self.isActive = isActive
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
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: handle(route:),
                    goBack: goBack
                )
                .navigationTransition(.automatic)
            }
        }
        .sheet(item: $sheet) { route in
            NavigationStack {
                route.view(
                    onNavigate: handle(route:),
                    goBack: {
                        sheet = nil
                    }
                )
            }
            .frame(minWidth: 380, minHeight: 420)
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
        .environment(\.openURL, OpenURLAction { url in
            deepLinkPresenter.state.handle(url: url.absoluteString)
            return .handled
        })
        .onOpenURL { url in
            if isActive {
                deepLinkPresenter.state.handle(url: url.absoluteString)
            }
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
        }
    }

    private func navigate(_ route: Route) {
        if backStack.last != route {
            backStack.append(route)
        }
    }

    private func handle(route: Route) {
        switch route {
        case .composeNew:
            MacComposeWindowCoordinator.shared.openNew(openWindow: openWindow)
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

    private func handleInitialActionRouteIfNeeded() {
        guard !didHandleInitialActionRoute else { return }
        didHandleInitialActionRoute = true

        switch initialRoute {
        case .composeNew,
                .composeDraft,
                .composeQuote,
                .composeReply,
                .composeVVOReplyComment,
                .mediaImage,
                .mediaRaw,
                .mediaStatusMedia,
                .externalLink:
            handle(route: initialRoute)
        default:
            break
        }
    }

    private func goBack() {
        if !backStack.isEmpty {
            backStack.removeLast()
        }
    }

    private func isSheetRoute(_ route: Route) -> Bool {
        switch route {
        case .serviceSelect, .deepLinkAccountPicker:
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
