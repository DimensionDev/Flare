import XCTest
import UIKit
import SwiftUI
import FlareAppleUI
import WebKit
@preconcurrency import Network
import CryptoKit
import KotlinSharedUI
@testable import FlareAppleCore
@testable import Flare

@MainActor
final class WebViewProxyTests: XCTestCase {
    private var presenter: KotlinPresenter<ProxySettingsState>!
    private var editor: KotlinPresenter<ProxyEditorState>!
    private var original: ProxyPolicy!
    private var profiles: [String] = []
    private var sessions: [WebViewProxy] = []
    private var servers: [WebServer] = []
    private var window: UIWindow!
    private var previousWindow: UIWindow?
    private var webViews: [WKWebView] = []

    override func setUp() async throws {
        presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        editor = KotlinPresenter(presenter: ProxyEditorPresenter())
        try await eventually { self.presenter.state.settings is UiStateSuccess<ProxySettings> }
        original = (presenter.state.settings as! UiStateSuccess<ProxySettings>).data.global
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        previousWindow = scene.windows.first(where: \.isKeyWindow)
        window = UIWindow(windowScene: scene)
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()
    }

    override func tearDown() async throws {
        sessions.forEach { $0.stop() }
        webViews.forEach { $0.stopLoading(); $0.removeFromSuperview() }
        webViews.removeAll()
        window.isHidden = true
        window.rootViewController = nil
        window = nil
        previousWindow?.makeKey()
        try await select(original)
        for id in profiles {
            presenter.state.remove(id: id)
            try await eventually { !(self.presenter.state.operation is UiStateLoading<KotlinUnit>) && (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.profiles.contains { $0.id == id } == false }
        }
        servers.forEach { $0.stop() }
        editor = nil
        presenter = nil
    }

    func testDirectSupportsPostSubresourcesAndWebSocketsAndStopsThemOnDisposal() async throws {
        try await select(ProxyPolicy(mode: .direct, proxyId: nil))
        let target = try await server(.http)
        let session = try await session()
        let route = try XCTUnwrap(session.route)
        let web = makeWebView(route)
        var request = URLRequest(url: target.url.appending(path: "post"))
        request.httpMethod = "POST"
        request.httpBody = Data("message=hello%20world".utf8)
        web.load(request)
        try await eventually { target.requests.contains { $0.contains("POST /post") && $0.hasSuffix("message=hello%20world") } }
        web.loadHTMLString("""
            <html><img src="/image"><script>
            fetch('/resource');
            var socket = new WebSocket('ws://\(target.url.host()!):\(target.url.port!)/ws');
            socket.onopen = () => document.title = 'open';
            </script></html>
            """, baseURL: target.url)
        try await eventually { target.requests.contains { $0.contains("GET /resource") } && target.webSockets == 1 }
        XCTAssertTrue(target.requests.contains { $0.contains("GET /image") })
        session.stop()
        try await eventually { target.closedSockets == 1 }
        XCTAssertFalse(route.isValid)
    }

    func testSocksUsesProxyDnsKeepsWebKitLoopbackExceptionAndFailsClosed() async throws {
        let target = try await server(.http)
        let proxy = try await server(.socks)
        try await selectProxy(proxy, protocol: .socks5)
        let session = try await session()
        let route = try XCTUnwrap(session.route)
        let web = makeWebView(route)
        let result = NavigationResult(route: route)
        web.navigationDelegate = result
        web.load(URLRequest(url: URL(string: "http://webview-proxy.local/dns")!))
        try await eventually { result.finished.contains("/dns") }
        XCTAssertEqual(proxy.requests.count, 1)
        XCTAssertTrue(proxy.addresses.contains("webview-proxy.local"))
        web.load(URLRequest(url: target.url.appending(path: "loopback")))
        do {
            try await eventually { result.finished.contains("/loopback") }
        } catch {
            XCTFail("Loopback navigation: direct=\(target.requests.count), proxy=\(proxy.requests.count), error=\(String(describing: result.error))")
            throw error
        }
        XCTAssertEqual(proxy.requests.count, 1)

        proxy.stop()
        web.load(URLRequest(url: URL(string: "http://webview-proxy.local/unavailable")!, timeoutInterval: 5))
        try await eventually { result.error != nil }
        XCTAssertTrue(route.failed)
        XCTAssertEqual(target.requests.count, 1)
    }

    func testHttpsProxyUsesTlsAndFailsClosed() async throws {
        let target = try await server(.http)
        let proxy = try await server(.tlsFailure)
        try await selectProxy(proxy, protocol: .https)
        let session = try await session()
        let route = try XCTUnwrap(session.route)
        let web = makeWebView(route)
        let result = NavigationResult(route: route)
        web.navigationDelegate = result
        web.load(URLRequest(url: URL(string: "http://webview-proxy.local/tls")!, timeoutInterval: 5))
        try await eventually { result.error != nil }
        XCTAssertEqual(proxy.firstByte, 22)
        XCTAssertEqual(target.accepted, 0)
        XCTAssertTrue(route.failed)
    }

    func testRouteChangeKeepsCookiesRetiresConnectionsAndIgnoresAccountChanges() async throws {
        let first = try await server(.socks)
        let second = try await server(.socks)
        try await selectProxy(first, protocol: .socks5)
        let session = try await session()
        let old = try XCTUnwrap(session.route)
        let web = makeWebView(old)
        await old.dataStore.httpCookieStore.setCookie(try XCTUnwrap(HTTPCookie(properties: [
            .domain: "webview-proxy.local", .path: "/", .name: "session", .value: "keep",
        ])))
        web.load(URLRequest(url: URL(string: "http://webview-proxy.local/pending")!))
        try await eventually { first.requests.count == 1 }
        let account = MicroBlogKey(id: UUID().uuidString, host: "webview-tests.example")
        presenter.state.select(accountKey: account, policy: ProxyPolicy(mode: .direct, proxyId: nil))
        try await eventually { (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.policy(accountKey: account).mode == .direct }
        XCTAssertTrue(session.route === old)
        presenter.state.select(accountKey: account, policy: ProxyPolicy(mode: .inherit, proxyId: nil))
        try await eventually { (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.policy(accountKey: account).mode == .inherit }

        try await selectProxy(second, protocol: .socks5)
        try await eventually { session.route != nil && session.route !== old && first.closedPending == 1 }
        let current = try XCTUnwrap(session.route)
        XCTAssertFalse(old.isValid)
        XCTAssertFalse(old.dataStore === current.dataStore)
        let cookies = await current.dataStore.httpCookieStore.allCookies()
        XCTAssertEqual(cookies.first { $0.name == "session" }?.value, "keep")
        let nextWeb = makeWebView(current)
        nextWeb.load(URLRequest(url: URL(string: "http://webview-proxy.local/next")!))
        try await eventually { second.requests.count == 1 }
        XCTAssertTrue(second.requests[0].contains("session=keep"))
        XCTAssertEqual(first.requests.count, 1)
    }

    func testLoginScreensLoadOnceAndKeepCookiesOnGlobalChange() async throws {
        let target = try await server(.http)
        for modern in [false, true] {
            try await select(ProxyPolicy(mode: .direct, proxyId: nil))
            let path = modern ? "/modern-login" : "/legacy-login"
            let url = target.url.appending(path: path).absoluteString
            if modern {
                if #available(iOS 26.0, *) {
                    window.rootViewController = UIHostingController(rootView: WebLoginScreen(onCookie: { _ in }, url: url))
                } else { continue }
            } else {
                window.rootViewController = UIHostingController(rootView: BackportWebLoginScreen(onCookie: { _ in }, url: url))
            }
            try await eventually { target.requests.contains { $0.hasPrefix("GET \(path) ") } }
            try await Task.sleep(for: .milliseconds(200))
            XCTAssertEqual(target.requests.filter { $0.hasPrefix("GET \(path) ") }.count, 1)
            try await select(ProxyPolicy(mode: .system, proxyId: nil))
            try await eventually { target.requests.filter { $0.hasPrefix("GET \(path) ") }.count == 2 }
            XCTAssertTrue(target.requests.last { $0.hasPrefix("GET \(path) ") }!.contains("webview=ready"))
            window.rootViewController = UIViewController()
        }
    }

    func testDirectTunnelForwardsPostAndClosesActiveConnections() async throws {
        let target = try await server(.http)
        let proxy = try WebViewDirectProxy()
        _ = try await proxy.start()
        defer { proxy.stop() }
        let client = NWConnection(host: "127.0.0.1", port: try XCTUnwrap(proxy.listener.port), using: .tcp)
        client.start(queue: .main)
        defer { client.cancel() }
        try await send(Data([5, 1, 0]), to: client)
        let greeting = try await receive(2, from: client)
        XCTAssertEqual(greeting, Data([5, 0]))
        let port = target.listener.port!.rawValue
        try await send(Data([5, 1, 0, 1, 127, 0, 0, 1, UInt8(port >> 8), UInt8(port & 255)]), to: client)
        let reply = try await receive(10, from: client)
        XCTAssertEqual(reply[1], 0)
        try await send(Data("POST /pending HTTP/1.1\r\nHost: localhost\r\nContent-Length: 7\r\n\r\nprobe=1".utf8), to: client)
        try await eventually { target.requests.contains { $0.hasPrefix("POST /pending") && $0.hasSuffix("probe=1") } }
        proxy.stop()
        try await eventually { target.closedPending == 1 }
    }

    private func send(_ bytes: Data, to connection: NWConnection) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: bytes, completion: .contentProcessed { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            })
        }
    }

    private func receive(_ count: Int, from connection: NWConnection) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            connection.receive(minimumIncompleteLength: count, maximumLength: count) { bytes, _, _, error in
                if let bytes, bytes.count == count { continuation.resume(returning: bytes) }
                else { continuation.resume(throwing: error ?? URLError(.badServerResponse)) }
            }
        }
    }

    func testBothLoginImplementationsUseThePreparedCookieStore() async throws {
        try await select(ProxyPolicy(mode: .system, proxyId: nil))
        let session = try await session()
        let route = try XCTUnwrap(session.route)
        let legacy = BackportWebLoginViewModel(onCookie: { _ in }, url: "https://example.com", route: route)
        XCTAssertTrue(legacy.configuration.websiteDataStore === route.dataStore)
        if #available(iOS 26.0, *) {
            let modern = WebLoginViewModel(onCookie: { _ in }, url: "https://example.com", route: route)
            XCTAssertTrue(modern.route.dataStore === route.dataStore)
        }
    }

    private func makeWebView(_ route: WebViewProxyRoute) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = route.dataStore
        let web = WKWebView(frame: .zero, configuration: configuration)
        route.bind(web)
        webViews.append(web)
        let container = window.rootViewController!.view!
        web.frame = container.bounds
        web.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        container.addSubview(web)
        return web
    }

    private func session() async throws -> WebViewProxy {
        let session = WebViewProxy()
        sessions.append(session)
        session.start()
        try await eventually { session.route != nil || session.failed }
        XCTAssertFalse(session.failed)
        return session
    }

    private func server(_ kind: WebServer.Kind) async throws -> WebServer {
        let server = try WebServer(kind)
        servers.append(server)
        try await eventually { (server.listener.port?.rawValue ?? 0) > 0 }
        return server
    }

    private func selectProxy(_ server: WebServer, protocol kind: ProxyProtocol) async throws {
        let id = UUID().uuidString
        let profile = ProxyProfile(id: id, name: "WebView test", protocol: kind, host: "127.0.0.1", port: Int32(server.listener.port!.rawValue))
        profiles.append(id)
        editor.state.save(profile: profile)
        try await eventually { (self.editor.state.saveState is UiStateSuccess<KotlinUnit> && (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.profiles.contains { $0.id == id } == true) || self.editor.state.error != nil }
        XCTAssertNil(editor.state.error)
        try await select(ProxyPolicy(mode: .proxy, proxyId: id))
    }

    private func select(_ policy: ProxyPolicy) async throws {
        if (presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.global == policy { return }
        presenter.state.select(accountKey: nil, policy: policy)
        try await eventually { (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.global == policy }
        XCTAssertNil(presenter.state.error)
    }

    private func eventually(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async throws {
        let deadline = Date().addingTimeInterval(20)
        while !condition(), Date() < deadline { try await Task.sleep(for: .milliseconds(10)) }
        XCTAssertTrue(condition(), file: file, line: line)
        if !condition() { throw URLError(.timedOut) }
    }
}

@MainActor
private final class NavigationResult: NSObject, WKNavigationDelegate {
    let route: WebViewProxyRoute
    var error: Error?
    var finished: [String] = []
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if let path = webView.url?.path { finished.append(path) }
    }
    init(route: WebViewProxyRoute) { self.route = route }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        self.error = error
        route.report(error)
    }
}

@MainActor
private final class WebServer {
    enum Kind { case http, socks, tlsFailure }
    let listener: NWListener
    var requests: [String] = []
    var addresses: [String] = []
    var accepted = 0
    var firstByte: UInt8?
    var closedPending = 0
    var webSockets = 0
    var closedSockets = 0
    private var connections: [NWConnection] = []
    var url: URL { URL(string: "http://127.0.0.1:\(listener.port!.rawValue)")! }

    init(_ kind: Kind) throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in
            Task { @MainActor in
                guard let self else { connection.cancel(); return }
                self.connections.append(connection)
                self.accepted += 1
                connection.start(queue: .main)
                switch kind {
                case .http: self.readHTTP(connection)
                case .tlsFailure:
                    self.read(1, from: connection) { bytes in self.firstByte = bytes.first; connection.cancel() }
                case .socks: self.negotiate(connection)
                }
            }
        }
        listener.start(queue: .main)
    }

    private func negotiate(_ connection: NWConnection) {
        read(2, from: connection) { prefix in
            self.read(Int(prefix[1]), from: connection) { _ in
                connection.send(content: Data([5, 0]), completion: .contentProcessed { _ in })
                self.read(4, from: connection) { request in
                    if request[3] == 3 {
                        self.read(1, from: connection) { length in self.address(connection, length: Int(length[0]), domain: true) }
                    } else { self.address(connection, length: request[3] == 1 ? 4 : 16, domain: false) }
                }
            }
        }
    }

    private func address(_ connection: NWConnection, length: Int, domain: Bool) {
        read(length, from: connection) { address in
            if domain { self.addresses.append(String(decoding: address, as: UTF8.self)) }
            self.read(2, from: connection) { _ in
                connection.send(content: Data([5, 0, 0, 1, 0, 0, 0, 0, 0, 0]), completion: .contentProcessed { _ in })
                self.readHTTP(connection)
            }
        }
    }

    private func read(_ count: Int, from connection: NWConnection, then: @escaping @MainActor @Sendable (Data) -> Void) {
        connection.receive(minimumIncompleteLength: count, maximumLength: count) { data, _, _, _ in
            Task { @MainActor in
                guard let data, data.count == count else { connection.cancel(); return }
                then(data)
            }
        }
    }

    private func readHTTP(_ connection: NWConnection, buffered: Data = Data()) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { bytes, _, _, error in
            Task { @MainActor in
                guard error == nil, let bytes else { connection.cancel(); return }
                let data = buffered + bytes
                let text = String(decoding: data, as: UTF8.self)
                guard let boundary = text.range(of: "\r\n\r\n") else { self.readHTTP(connection, buffered: data); return }
                let length = text[..<boundary.lowerBound].components(separatedBy: "\r\n")
                    .first { $0.lowercased().hasPrefix("content-length:") }
                    .flatMap { Int($0.split(separator: ":")[1].trimmingCharacters(in: .whitespaces)) } ?? 0
                guard text[boundary.upperBound...].utf8.count >= length else { self.readHTTP(connection, buffered: data); return }
                self.requests.append(text)
                if text.hasPrefix("GET /pending") || text.hasPrefix("POST /pending") {
                    self.observeClose(connection) { self.closedPending += 1 }
                } else if text.hasPrefix("GET /ws ") {
                    self.webSockets += 1
                    let key = text.components(separatedBy: "\r\n").first { $0.lowercased().hasPrefix("sec-websocket-key:") }!
                        .split(separator: ":", maxSplits: 1)[1].trimmingCharacters(in: .whitespaces)
                    let accept = Data(Insecure.SHA1.hash(data: Data((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").utf8))).base64EncodedString()
                    let response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: \(accept)\r\n\r\n"
                    connection.send(content: Data(response.utf8), completion: .contentProcessed { _ in })
                    self.observeClose(connection) { self.closedSockets += 1 }
                } else {
                    let body = "<html>proxied</html>"
                    let response = "HTTP/1.1 200 OK\r\nSet-Cookie: webview=ready; Path=/\r\nContent-Type: text/html\r\nCache-Control: no-store\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)"
                    connection.send(content: Data(response.utf8), isComplete: true, completion: .contentProcessed { _ in })
                }
            }
        }
    }

    private func observeClose(_ connection: NWConnection, completion: @escaping @MainActor @Sendable () -> Void) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { _, _, complete, error in
            Task { @MainActor in
                if complete || error != nil { completion(); connection.cancel() }
                else { self.observeClose(connection, completion: completion) }
            }
        }
    }

    func stop() {
        listener.cancel()
        connections.forEach { $0.cancel() }
    }
}
