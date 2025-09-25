import SwiftUI
import KotlinSharedUI

struct Router<Root: View>: View {
    @ViewBuilder let root: (@escaping (Route) -> Void) -> Root
    @State private var backStack: [Route] = []
    @State private var sheet: Route? = nil
    var body: some View {
        NavigationStack(path: $backStack) {
            root({ route in
                navigate(route: route)
            })
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    clearToHome: { backStack.removeAll() }
                )
            }
        }
        .sheet(item: $sheet) { route in
            NavigationStack {
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    clearToHome: { backStack.removeAll() }
                )
            }
        }
        .environment(\.openURL, OpenURLAction { url in
            if let newRoute = Route.fromDeepLink(url: url.absoluteString) {
                navigate(route: newRoute)
                return .handled
            } else {
                return .systemAction
            }
        })
    }

    func navigate(route: Route) {
        if isSheetRoute(route: route) {
            sheet = route
        } else if backStack.last != route {
            backStack.append(route)
            sheet = nil
        }
    }
    
    func isSheetRoute(route: Route) -> Bool {
        switch route {
        case .composeNew, .composeQuote, .composeReply, .composeVVOReplyComment:
            return true
        default:
            return false
        }
    }
}
