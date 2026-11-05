#if DEBUG
import FlareRust
import SwiftUI

/// Hosts the production route; diagnostics are only visible in this test fixture.
struct CounterTestFixture: View {
    private enum Destination: Hashable { case counter, detail }
    @State private var path: [Destination] = []
    @State private var resources = counterResources()

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
        .safeAreaInset(edge: .bottom) {
            Text("\(resources.presenters),\(resources.subscriptions),\(resources.tasks)")
                .accessibilityIdentifier("counter-resources")
        }
        .task {
            while !Task.isCancelled {
                resources = counterResources()
                do { try await Task.sleep(for: .milliseconds(20)) } catch { break }
            }
        }
    }
}
#endif
