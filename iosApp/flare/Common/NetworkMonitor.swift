import Foundation
import Network
@preconcurrency import Combine
import SwiftUI

enum NetworkKind: Equatable {
    case wifi
    case cellular

    var description: String {
        switch self {
        case .wifi: return "Wi-Fi"
        case .cellular: return "Cellular"
        }
    }
}

@MainActor
final class NetworkMonitor: ObservableObject {
    @Published private(set) var kind: NetworkKind = .cellular
    @Published private(set) var isExpensive: Bool = false
    @Published private(set) var isConstrained: Bool = false

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "net.monitor.queue")

    init() { start() }

    deinit {
        monitor.cancel()
    }

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self else { return }
                self.kind = self.map(path)
                self.isExpensive = path.isExpensive
                if #available(iOS 13.0, *) {
                    self.isConstrained = path.isConstrained
                } else {
                    self.isConstrained = false
                }
            }
        }
        monitor.start(queue: queue)
    }

    private func map(_ path: NWPath) -> NetworkKind {
        if isExpensive || isConstrained { return .cellular }
        guard path.status == .satisfied else { return .cellular }
        if path.usesInterfaceType(.wifi)    { return .wifi }
        if path.usesInterfaceType(.wiredEthernet) { return .wifi }
        if path.usesInterfaceType(.cellular){ return .cellular }
        return .cellular
    }
}

private struct NetworkKindKey: EnvironmentKey {
    static let defaultValue: NetworkKind = .cellular
}

extension EnvironmentValues {
    var networkKind: NetworkKind {
        get { self[NetworkKindKey.self] }
        set { self[NetworkKindKey.self] = newValue }
    }
}

struct NetworkStatusModifier: ViewModifier {
    @StateObject private var monitor = NetworkMonitor()

    func body(content: Content) -> some View {
        content
            .environment(\.networkKind, monitor.kind)
    }
}

extension View {
    func networkStatus() -> some View {
        self.modifier(NetworkStatusModifier())
    }
}
