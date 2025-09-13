import SwiftUI
import KotlinSharedUI

struct Router<Root: View>: View {
    @ViewBuilder let root: (@escaping (Route) -> Void) -> Root
    @State private var backStack: [Route] = []
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
        if backStack.last != route {
            backStack.append(route)
        }
    }
}
