import Combine
@preconcurrency import FlareUIDemoKit

/// Observation glue used by the SwiftUI renderer.
@MainActor
final class FlareTreeModel: ObservableObject {
    @Published private(set) var nodes: [FlareUiNodeSnapshot]

    private let host: FlareUiTreeHost

    init(host: FlareUiTreeHost) {
        self.host = host
        nodes = host.snapshot()
        host.setOnTreeChanged { [weak self] nodes in
            self?.nodes = nodes
        }
    }

    deinit {
        host.setOnTreeChanged(listener: nil)
        host.dispose()
    }
}
