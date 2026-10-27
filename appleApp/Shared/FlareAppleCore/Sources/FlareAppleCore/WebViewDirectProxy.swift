import Foundation
@preconcurrency import Network

// WKWebsiteDataStore has no direct override. A loopback SOCKS tunnel bypasses system proxies without rewriting web requests.
@MainActor
final class WebViewDirectProxy {
    let listener: NWListener
    private var ready: CheckedContinuation<NWEndpoint.Port, Error>?
    private var tunnels: [UUID: Tunnel] = [:]
    private var stopped = false

    init() throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
    }

    func start() async throws -> Network.ProxyConfiguration {
        let port = try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                ready = continuation
                listener.stateUpdateHandler = { [weak self] state in
                    Task { @MainActor in
                        guard let self, !self.stopped else { return }
                        switch state {
                        case .ready:
                            if let port = self.listener.port { self.ready?.resume(returning: port); self.ready = nil }
                        case .failed(let error): self.stop(error: error)
                        default: break
                        }
                    }
                }
                listener.newConnectionHandler = { [weak self] connection in
                    Task { @MainActor in
                        guard let self, !self.stopped, self.tunnels.count < 64 else { connection.cancel(); return }
                        let id = UUID()
                        let tunnel = Tunnel(connection: connection) { [weak self] in self?.tunnels.removeValue(forKey: id) }
                        self.tunnels[id] = tunnel
                        tunnel.start()
                    }
                }
                if stopped { continuation.resume(throwing: CancellationError()); ready = nil }
                else { listener.start(queue: .main) }
            }
        } onCancel: {
            Task { @MainActor in self.stop() }
        }
        var configuration = Network.ProxyConfiguration(socksv5Proxy: .hostPort(host: "127.0.0.1", port: port))
        configuration.allowFailover = false
        return configuration
    }

    func stop(error: Error = CancellationError()) {
        guard !stopped else { return }
        stopped = true
        ready?.resume(throwing: error)
        ready = nil
        listener.cancel()
        let active = Array(tunnels.values)
        tunnels.removeAll()
        active.forEach { $0.close() }
    }

    @MainActor
    private final class Tunnel {
        let client: NWConnection
        var remote: NWConnection?
        let onClose: () -> Void
        var closed = false
        var finishedReads = 0
        var deadline: Task<Void, Never>?

        init(connection: NWConnection, onClose: @escaping () -> Void) {
            client = connection
            self.onClose = onClose
        }

        func start() {
            client.start(queue: .main)
            deadline = Task { [weak self] in
                do { try await Task.sleep(for: .seconds(30)); self?.close() } catch {}
            }
            read(2) { [self] bytes in
                guard bytes[0] == 5, bytes[1] > 0 else { close(); return }
                read(Int(bytes[1])) { [self] methods in
                    guard methods.contains(0) else { close(); return }
                    send(Data([5, 0]), to: client) { [self] in request() }
                }
            }
        }

        func request() {
            read(4) { [self] bytes in
                guard bytes[0] == 5, bytes[1] == 1, bytes[2] == 0 else { close(); return }
                switch bytes[3] {
                case 1:
                    read(4) { [self] address in
                        guard let ip = IPv4Address(address) else { close(); return }
                        connect(host: .ipv4(ip))
                    }
                case 4:
                    read(16) { [self] address in
                        guard let ip = IPv6Address(address) else { close(); return }
                        connect(host: .ipv6(ip))
                    }
                case 3:
                    read(1) { [self] length in
                        guard length[0] > 0 else { close(); return }
                        read(Int(length[0])) { [self] address in
                            guard let host = String(data: address, encoding: .utf8), !host.contains("\0") else { close(); return }
                            connect(host: NWEndpoint.Host(host))
                        }
                    }
                default: close()
                }
            }
        }

        func connect(host: NWEndpoint.Host) {
            read(2) { [self] bytes in
                guard let port = NWEndpoint.Port(rawValue: UInt16(bytes[0]) << 8 | UInt16(bytes[1])), port.rawValue != 0 else {
                    close(); return
                }
                let parameters = NWParameters.tcp
                parameters.preferNoProxies = true
                let upstream = NWConnection(host: host, port: port, using: parameters)
                remote = upstream
                upstream.stateUpdateHandler = { [weak self] state in
                    Task { @MainActor in
                        guard let self, !self.closed else { return }
                        switch state {
                        case .ready:
                            self.deadline?.cancel()
                            self.send(Data([5, 0, 0, 1, 0, 0, 0, 0, 0, 0]), to: self.client) {
                                self.copy(from: self.client, to: upstream)
                                self.copy(from: upstream, to: self.client)
                            }
                        case .failed, .cancelled: self.close()
                        default: break
                        }
                    }
                }
                upstream.start(queue: .main)
            }
        }

        func read(_ count: Int, then: @escaping @MainActor @Sendable (Data) -> Void) {
            client.receive(minimumIncompleteLength: count, maximumLength: count) { [weak self] bytes, _, _, error in
                Task { @MainActor in
                    guard let self, !self.closed else { return }
                    guard error == nil, let bytes, bytes.count == count else { self.close(); return }
                    then(bytes)
                }
            }
        }

        func send(_ bytes: Data, to connection: NWConnection, then: @escaping @MainActor @Sendable () -> Void) {
            connection.send(content: bytes, completion: .contentProcessed { [weak self] error in
                Task { @MainActor in
                    guard let self, !self.closed else { return }
                    if error != nil { self.close() } else { then() }
                }
            })
        }

        func copy(from source: NWConnection, to destination: NWConnection) {
            source.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] bytes, _, complete, error in
                Task { @MainActor in
                    guard let self, !self.closed else { return }
                    guard error == nil else { self.close(); return }
                    destination.send(content: bytes, isComplete: complete, completion: .contentProcessed { [weak self] error in
                        Task { @MainActor in
                            guard let self, !self.closed else { return }
                            if error != nil { self.close() }
                            else if complete {
                                self.finishedReads += 1
                                if self.finishedReads == 2 { self.close() }
                            } else { self.copy(from: source, to: destination) }
                        }
                    })
                }
            }
        }

        func close() {
            guard !closed else { return }
            closed = true
            deadline?.cancel()
            deadline = nil
            client.cancel()
            remote?.cancel()
            onClose()
        }
    }
}
