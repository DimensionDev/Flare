import XCTest
import UIKit
import SwiftUI
@preconcurrency import Network
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI
@testable import Flare

@MainActor
final class ProxyImageLifecycleTests: XCTestCase {
    func testInlineEmojiRetriesAfterRouteChangeWithoutReconfiguringText() async throws {
        let presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        addTeardownBlock { @MainActor in try await self.select(.system, presenter: presenter) }
        try await select(.direct, presenter: presenter)
        let server = try PendingImageServer()
        defer {
            server.stop()
        }
        try await eventually { (server.listener.port?.rawValue ?? 0) > 0 }
        let url = server.url
        let text = UiRichText(renderRuns: [RenderContent.Text(
            runs: [RenderRun.Image(url: url, alt: "emoji", proxy: ProxyRef.Global.shared)], block: RenderBlockStyle()
        )], isRtl: false, raw: ":emoji:", innerText: ":emoji:", imageUrls: [url])
        let view = RichTextUIView(frame: CGRect(x: 0, y: 0, width: 300, height: 80))
        view.configure(text: text, lineLimit: nil, isTextSelectionEnabled: false, onOpenURL: nil, contentKey: 7)
        try await eventually { server.requests.count == 1 }
        let renderer = try XCTUnwrap(textView(in: view))

        try await select(.system, presenter: presenter)
        try await eventually { server.requests.count == 2 && server.requests[0].cancelled }
        server.complete()
        try await eventually {
            let image = (renderer.attributedText.attribute(.attachment, at: 0, effectiveRange: nil) as? NSTextAttachment)?.image?.cgImage
            return image?.width == Int(server.imageSize.width) && image?.height == Int(server.imageSize.height)
        }
        XCTAssertTrue(textView(in: view) === renderer, "Retry must not rebuild unchanged text")
        try await select(.direct, presenter: presenter)
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertEqual(server.requests.count, 2, "A loaded emoji must not be downloaded again")
    }

    func testNetworkImageKeepsUnchangedAccountTransferWhileRestartingChangedRoute() async throws {
        let presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        addTeardownBlock { @MainActor in try await self.select(.system, presenter: presenter) }
        let account = MicroBlogKey(id: UUID().uuidString, host: "image-tests.example")
        try await select(.direct, presenter: presenter)
        try await select(.direct, account: account, presenter: presenter)
        let direct = try PendingImageServer()
        let inherited = try PendingImageServer()
        defer {
            direct.stop()
            inherited.stop()
        }
        try await eventually { (direct.listener.port?.rawValue ?? 0) > 0 && (inherited.listener.port?.rawValue ?? 0) > 0 }
        var directLoaded = false
        var inheritedLoaded = false
        let host = UIHostingController(rootView: HStack {
            NetworkImage(data: UiMediaImage(url: direct.url, proxy: ProxyRef.Account(accountKey: account)),
                         onImageSize: { _ in directLoaded = true }).frame(width: 100, height: 100)
            NetworkImage(data: UiMediaImage(url: inherited.url, proxy: ProxyRef.Global.shared),
                         onImageSize: { _ in inheritedLoaded = true }).frame(width: 100, height: 100)
        })
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousWindow?.makeKey()
        }
        try await eventually { direct.requests.count == 1 && inherited.requests.count == 1 }

        try await select(.system, presenter: presenter)
        try await eventually { inherited.requests.count == 2 && inherited.requests[0].cancelled }
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertEqual(direct.requests.count, 1, "An unrelated revision must not restart the account image")
        XCTAssertFalse(direct.requests[0].cancelled, "The original account transfer must stay active")
        direct.complete()
        inherited.complete()
        try await eventually { directLoaded && inheritedLoaded }
    }

    private func select(_ mode: ProxyMode, account: MicroBlogKey? = nil,
                        presenter: KotlinPresenter<ProxySettingsState>) async throws {
        try await eventually { presenter.state.settings is UiStateSuccess<ProxySettings> }
        if (presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.policy(accountKey: account).mode == mode { return }
        presenter.state.select(accountKey: account, policy: ProxyPolicy(mode: mode, proxyId: nil))
        try await eventually { (presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.policy(accountKey: account).mode == mode }
        XCTAssertNil(presenter.state.error)
    }

    private func textView(in view: UIView) -> UITextView? {
        if let text = view as? UITextView { return text }
        return view.subviews.lazy.compactMap { self.textView(in: $0) }.first
    }

    private func eventually(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async throws {
        let deadline = Date().addingTimeInterval(10)
        while !condition(), Date() < deadline { try await Task.sleep(for: .milliseconds(10)) }
        XCTAssertTrue(condition(), file: file, line: line)
        if !condition() { throw URLError(.timedOut) }
    }
}

@MainActor
private final class PendingImageServer {
    @MainActor
    final class Request {
        let connection: NWConnection
        var cancelled = false
        var completed = false
        init(_ connection: NWConnection) { self.connection = connection }
    }

    let listener: NWListener
    private(set) var requests: [Request] = []
    let imageSize = CGSize(width: 4, height: 2)
    private let bytes: Data
    private let path = UUID().uuidString
    var url: String { "http://127.0.0.1:\(listener.port!.rawValue)/\(path).png" }

    init() throws {
        let size = imageSize
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        bytes = UIGraphicsImageRenderer(size: size, format: format).pngData { context in
            UIColor.red.setFill()
            context.fill(CGRect(origin: .zero, size: size))
        }
        listener = try NWListener(using: .tcp, on: .any)
        listener.newConnectionHandler = { [weak self] connection in
            Task { @MainActor in self?.accept(connection) }
        }
        listener.start(queue: .main)
    }

    private func accept(_ connection: NWConnection) {
        connection.start(queue: .main)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            Task { @MainActor in
                guard let self, data != nil else { connection.cancel(); return }
                let request = Request(connection)
                self.requests.append(request)
                let headers = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: \(self.bytes.count)\r\nConnection: close\r\n\r\n"
                connection.send(content: Data(headers.utf8) + self.bytes.prefix(1), completion: .contentProcessed { _ in })
                self.observeClosure(request)
            }
        }
    }

    private func observeClosure(_ request: Request) {
        request.connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] _, _, complete, error in
            Task { @MainActor in
                if complete || error != nil {
                    if !request.completed { request.cancelled = true }
                } else {
                    self?.observeClosure(request)
                }
            }
        }
    }

    func complete() {
        for request in requests where !request.cancelled && !request.completed {
            request.completed = true
            request.connection.send(content: bytes.dropFirst(), isComplete: true, completion: .contentProcessed { _ in })
        }
    }

    func stop() {
        listener.cancel()
        requests.forEach { $0.connection.cancel() }
    }
}
