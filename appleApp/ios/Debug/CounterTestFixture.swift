#if DEBUG
import SwiftUI

/// Hosts the production route for navigation and overflow tests.
struct CounterTestFixture: View {
    private enum Destination: Hashable { case counter, detail }
    @State private var path: [Destination] = []

    var body: some View {
        NavigationStack(path: $path) {
            Button("Compose", systemImage: "square.and.pencil") {
                path.append(.counter)
            }
            .accessibilityIdentifier("counter-compose")
            .navigationTitle("Home")
            .navigationDestination(for: Destination.self) { route in
                switch route {
                case .counter:
                    Group {
                        if ProcessInfo.processInfo.arguments.contains("--counter-overflow") {
                            CounterScreen(initialValue: .max)
                        } else {
                            Route.counter.view(onNavigate: { _ in }, goBack: { path.removeLast() })
                        }
                    }
                        .toolbar {
                            NavigationLink("Next", value: Destination.detail)
                                .accessibilityIdentifier("counter-detail")
                        }
                case .detail:
                    Text("Child page").navigationTitle("Detail")
                }
            }
        }
    }
}
#endif
