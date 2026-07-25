import Combine
import FlareUIRuntime

/// Observation glue used by the SwiftUI renderer.
@MainActor
final class FlareTreeModel: ObservableObject {
    @Published private(set) var nodes: [FlareUINode]

    private let host: any FlareUITreeHost

    init(makeHost: @MainActor () -> any FlareUITreeHost) {
        let host = makeHost()
        self.host = host
        nodes = host.snapshot()
        host.setOnTreeChanged { [weak self] nodes in
            self?.nodes = nodes
        }
    }

    isolated deinit {
        host.setOnTreeChanged(nil)
        host.dispose()
    }
}
