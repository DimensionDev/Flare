import Foundation
import Network
@preconcurrency import Combine
import SwiftUI
import FlareAppleUI

@MainActor
final class NetworkMonitor: ObservableObject {
    @Published private(set) var kind: NetworkKind = .cellular

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "net.monitor.queue")

    init() {
        start()
    }

    deinit {
        monitor.cancel()
    }

    private func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self else { return }
                let nextKind = self.map(path)
                guard self.kind != nextKind else { return }
                self.kind = nextKind
            }
        }
        monitor.start(queue: queue)
    }

    private func map(_ path: NWPath) -> NetworkKind {
        if path.isExpensive || path.isConstrained {
            return .cellular
        }

        guard path.status == .satisfied else {
            return .cellular
        }

        if path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet) {
            return .wifi
        }

        return .cellular
    }
}

private struct NetworkStatusModifier: ViewModifier {
    @StateObject private var monitor = NetworkMonitor()

    func body(content: Content) -> some View {
        content
            .environment(\.networkKind, monitor.kind)
    }
}

extension View {
    func networkStatus() -> some View {
        modifier(NetworkStatusModifier())
    }
}
