import SwiftUI
import KotlinSharedUI

struct Router<Root: View>: View {
    @ViewBuilder let root: () -> Root
    @State private var route: [Route] = []
    var body: some View {
        NavigationStack(path: $route) {
            root()
                .navigationDestination(for: Route.self) { route in
                    route.view()
                }
        }
        .environment(\.openURL, OpenURLAction { url in
            if let newRoute = Route.fromDeepLink(url: url.absoluteString) {
                route.append(newRoute)
                return .handled
            } else {
                return .systemAction
            }
        })
        .onOpenURL { url in
            if let newRoute = Route.fromDeepLink(url: url.absoluteString) {
                route.append(newRoute)
            }
        }
    }
}
