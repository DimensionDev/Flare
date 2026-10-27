import Combine
import Foundation
@preconcurrency import KotlinSharedUI
import Observation
import WebKit

@MainActor
@Observable
public final class WebViewProxyRoute {
    public private(set) var failed = false
    public let dataStore: WKWebsiteDataStore
    private let snapshot: ProxySnapshot
    public var key: String { snapshot.key }
    public var isValid: Bool { snapshot.isValid && !closed }
    private var directProxy: WebViewDirectProxy?
    private var unsubscribe: (() -> Void)?
    private var stopLoading: [() -> Void] = []
    private var closed = false

    init(snapshot: ProxySnapshot) {
        self.snapshot = snapshot
        // Separate stores prevent WebKit from reusing connections established under the previous route.
        dataStore = .nonPersistent()
    }

    func prepare(cookies: [HTTPCookie]) async throws {
        let connection = snapshot.connection
        if connection.mode == .direct {
            let proxy = try WebViewDirectProxy()
            directProxy = proxy
            dataStore.proxyConfigurations = [try await proxy.start()]
        } else if connection.mode == .proxy {
            // shortcut: WebKit bypasses loopback targets; revisit if a public bypass override becomes available.
            dataStore.proxyConfigurations = [ProxySessionConfigurator.proxyConfiguration(connection)]
        } else if connection.mode != .system {
            throw URLError(.unsupportedURL)
        }
        for cookie in cookies {
            try Task.checkCancellation()
            await dataStore.httpCookieStore.setCookie(cookie)
        }
        try Task.checkCancellation()
        let cancel = snapshot.onInvalidated { @Sendable [weak self] in
            Task { @MainActor in self?.close() }
        }
        unsubscribe = { cancel() }
        guard isValid else { throw CancellationError() }
    }

    public func report(_ error: Error) {
        guard isValid, (error as NSError).code != NSURLErrorCancelled else { return }
        failed = true
        close()
    }

    public func bind(_ webView: WKWebView) {
        onStop { [weak webView] in
            webView?.stopLoading()
            webView?.loadHTMLString("", baseURL: nil)
        }
    }

    public func onStop(_ stop: @escaping () -> Void) {
        if isValid { stopLoading.append(stop) } else { stop() }
    }

    func close() {
        guard !closed else { return }
        closed = true
        unsubscribe?()
        unsubscribe = nil
        stopLoading.forEach { $0() }
        stopLoading.removeAll()
        directProxy?.stop()
        directProxy = nil
    }
}

@MainActor
@Observable
public final class WebViewProxy {
    public private(set) var route: WebViewProxyRoute?
    public private(set) var failed = false
    @ObservationIgnored private var observation: AnyCancellable?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var previousStore: WKWebsiteDataStore?

    public init() {}

    public func start() {
        guard observation == nil else { return }
        observation = ProxyNetwork.shared.changes.toPublisher()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.reload() }
    }

    public func reload() {
        task?.cancel()
        failed = false
        task = Task { [weak self] in
            guard let self else { return }
            var prepared: WebViewProxyRoute?
            do {
                let snapshot = try await ProxyNetwork.shared.snapshot(ref: ProxyRef.Global.shared)
                try Task.checkCancellation()
                if route?.key == snapshot.key, route?.isValid == true, route?.failed == false { return }
                if let route { previousStore = route.dataStore; route.close() }
                route = nil
                let cookies = await previousStore?.httpCookieStore.allCookies() ?? []
                try Task.checkCancellation()
                let next = WebViewProxyRoute(snapshot: snapshot)
                prepared = next
                try await next.prepare(cookies: cookies)
                guard next.isValid else { throw CancellationError() }
                route = next
                previousStore = nil
            } catch {
                prepared?.close()
                if !Task.isCancelled, !(error is CancellationError) {
                    if let route { previousStore = route.dataStore; route.close() }
                    route = nil
                    failed = true
                }
            }
        }
    }

    public func stop() {
        observation?.cancel()
        observation = nil
        task?.cancel()
        task = nil
        if let route { previousStore = route.dataStore; route.close() }
        route = nil
    }
}
