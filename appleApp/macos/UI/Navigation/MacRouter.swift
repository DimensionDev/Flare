import SwiftUI

struct MacRouter: View {
    let initialRoute: MacRoute
    @State private var backStack: [MacRoute] = []

    var body: some View {
        NavigationStack(path: $backStack) {
            initialRoute.view(
                onNavigate: navigate,
                goBack: {}
            )
            .navigationDestination(for: MacRoute.self) { route in
                route.view(
                    onNavigate: navigate,
                    goBack: goBack
                )
            }
        }
    }

    private func navigate(_ route: MacRoute) {
        if backStack.last != route {
            backStack.append(route)
        }
    }

    private func goBack() {
        if !backStack.isEmpty {
            backStack.removeLast()
        }
    }
}
