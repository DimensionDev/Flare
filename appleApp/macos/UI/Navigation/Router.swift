import Combine
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

struct Router: View {
    @Environment(\.openURL) private var openURL
    let initialRoute: Route
    let isActive: Bool
    @State private var backStack: [Route] = []
    @State private var sheet: Route?
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
        NavigationStack(path: $backStack.animation()) {
            initialRoute.view(
                onNavigate: handle(route:),
                goBack: {}
            )
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: handle(route:),
                    goBack: goBack
                )
                .background(Color(nsColor: .windowBackgroundColor))
            }
            .backport
            .navigationTransitionAutomatic()
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
        }
    }

    private func navigate(_ route: Route) {
        if backStack.last != route {
            backStack.append(route)
        }
    }

    private func handle(route: Route) {
        switch route {
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
