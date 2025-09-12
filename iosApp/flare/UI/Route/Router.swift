import SwiftUI
import KotlinSharedUI

struct Router<Root: View>: View {
    @ViewBuilder let root: (@escaping (Route) -> Void) -> Root
    @State private var backStack: [Route] = []
    var body: some View {
        NavigationStack(path: $backStack) {
            root({ route in
                backStack.append(route)
            })
                .navigationDestination(for: Route.self) { route in
                    route.view(onNavigate: { route in backStack.append(route) })
                }
        }
        .environment(\.openURL, OpenURLAction { url in
            if let newRoute = Route.fromDeepLink(url: url.absoluteString) {
                backStack.append(newRoute)
                return .handled
            } else {
                return .systemAction
            }
        })
    }
}
