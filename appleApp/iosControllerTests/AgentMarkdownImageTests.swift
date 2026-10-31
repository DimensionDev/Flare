import XCTest
import SwiftUI
import UIKit
import ImageIO
@preconcurrency import Network
import KotlinSharedUI
import FlareAppleCore
@testable import FlareAppleUI
@testable import Textual

@MainActor
final class AgentMarkdownImageTests: XCTestCase {
    private var presenter: KotlinPresenter<ProxySettingsState>!
    private var editor: KotlinPresenter<ProxyEditorState>!
    private var original: ProxyPolicy!
    private var profileID: String?

    override func setUp() async throws {
        presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        editor = KotlinPresenter(presenter: ProxyEditorPresenter())
        try await eventually { self.presenter.state.settings is UiStateSuccess<ProxySettings> }
        original = (presenter.state.settings as! UiStateSuccess<ProxySettings>).data.global
    }

    override func tearDown() async throws {
        try await select(original)
        if let profileID {
            presenter.state.remove(id: profileID)
            try await eventually { !(self.presenter.state.settings as! UiStateSuccess<ProxySettings>).data.profiles.contains { $0.id == profileID } }
        }
        presenter = nil
        editor = nil
    }

    func testAttachmentRetainsImageSizeAlternativeTextCopyDataAndCache() async throws {
        try await select(ProxyPolicy(mode: .direct, proxyId: nil))
        let server = try MarkdownImageServer()
        defer { server.stop() }
        try await eventually { server.isReady }
        let loader = ProxyMarkdownImageLoader()
        let environment = ColorEnvironmentValues(colorScheme: .light, colorSchemeContrast: .standard)
        let first = try await loader.attachment(for: server.url, text: "Fixture", environment: environment)
        let second = try await loader.attachment(for: server.url, text: "Fixture", environment: environment)
        XCTAssertEqual(first, second)
        XCTAssertEqual(server.requests, 1)
        XCTAssertEqual(first.description, "Fixture")
        let size = first.sizeThatFits(ProposedViewSize(width: 32, height: nil), in: TextEnvironmentValues())
        XCTAssertEqual(size, CGSize(width: 32, height: 16))
        let copy = try XCTUnwrap(UIImage(data: try XCTUnwrap(first.pngData())))
        XCTAssertEqual(copy.size, CGSize(width: 64, height: 32))
    }

    func testMarkdownUsesTheProxyAndRecoversWhenThePolicyChanges() async throws {
        let image = try MarkdownImageServer(socks: true)
        let proxy = try MarkdownImageServer(rejectsConnections: true)
        defer { image.stop(); proxy.stop() }
        try await eventually { image.isReady && proxy.isReady }
        let id = UUID().uuidString
        profileID = id
        editor.state.save(profile: ProxyProfile(id: id, name: "Markdown test", protocol: .socks5,
            host: "127.0.0.1", port: Int32(proxy.listener.port!.rawValue)))
        try await eventually { (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.profiles.contains { $0.id == id } == true }
        try await select(ProxyPolicy(mode: .proxy, proxyId: id))
        let url = "http://markdown-image.local/\(UUID().uuidString).png"
        let host = UIHostingController(rootView: AgentMarkdownText(markdown: "Before **![Fixture](\(url))** after"))
        let (window, previous) = try show(host)
        defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
        try await eventually { proxy.requests > 0 }
        XCTAssertEqual(image.requests, 0, "Markdown must not connect directly when the selected proxy fails")
        editor.state.save(profile: ProxyProfile(id: id, name: "Markdown test", protocol: .socks5,
            host: "127.0.0.1", port: Int32(image.listener.port!.rawValue)))
        try await eventually {
            (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.profiles.first { $0.id == id }?.port == Int32(image.listener.port!.rawValue)
        }
        try await eventually { image.requests == 1 }
        try await eventually { self.colors(in: window).contains("red") }
    }

    func testAnimatedImageKeepsItsFrames() async throws {
        try await select(ProxyPolicy(mode: .direct, proxyId: nil))
        let server = try MarkdownImageServer(animated: true)
        defer { server.stop() }
        try await eventually { server.isReady }
        let attachment = try await ProxyMarkdownImageLoader().attachment(for: server.url, text: "Animated fixture",
            environment: ColorEnvironmentValues(colorScheme: .light, colorSchemeContrast: .standard))
        let host = UIHostingController(rootView: attachment.body)
        let (window, previous) = try show(host)
        defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
        var observed = Set<String>()
        let deadline = Date().addingTimeInterval(3)
        while observed.count < 2 && Date() < deadline {
            observed.formUnion(colors(in: window))
            try await Task.sleep(for: .milliseconds(60))
        }
        XCTAssertEqual(observed, ["red", "blue"])
    }

    private func select(_ policy: ProxyPolicy) async throws {
        presenter.state.select(accountKey: nil, policy: policy)
        try await eventually { (self.presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.global == policy }
        XCTAssertNil(presenter.state.error)
    }

    private func show(_ host: UIViewController) throws -> (UIWindow, UIWindow?) {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.overrideUserInterfaceStyle = .light
        window.rootViewController = host
        window.makeKeyAndVisible()
        return (window, previous)
    }

    private func colors(in window: UIWindow) -> Set<String> {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(bounds: window.bounds, format: format).image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        guard let cg = image.cgImage else { return [] }
        var bytes = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        guard let context = CGContext(data: &bytes, width: cg.width, height: cg.height, bitsPerComponent: 8,
            bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return [] }
        context.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        var colors = Set<String>()
        for i in stride(from: 0, to: bytes.count, by: 16) where bytes[i + 1] < 50 {
            if bytes[i] > 180 && bytes[i + 2] < 50 { colors.insert("red") }
            if bytes[i + 2] > 180 && bytes[i] < 50 { colors.insert("blue") }
        }
        return colors
    }

    private func eventually(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async throws {
        let deadline = Date().addingTimeInterval(10)
        while !condition() && Date() < deadline { try await Task.sleep(for: .milliseconds(20)) }
        XCTAssertTrue(condition(), file: file, line: line)
        if !condition() { throw URLError(.timedOut) }
    }
}

@MainActor
private final class MarkdownImageServer {
    let listener: NWListener
    private(set) var isReady = false
    private(set) var requests = 0
    private var connections: [NWConnection] = []
    private let bytes: Data
    private let animated: Bool
    private let rejectsConnections: Bool
    private let socks: Bool
    private let path = UUID().uuidString
    var url: URL { URL(string: "http://127.0.0.1:\(listener.port!.rawValue)/\(path).\(animated ? "gif" : "png")")! }

    init(animated: Bool = false, rejectsConnections: Bool = false, socks: Bool = false) throws {
        self.animated = animated
        self.rejectsConnections = rejectsConnections
        self.socks = socks
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 32), format: format)
        let red = renderer.image { context in UIColor.red.setFill(); context.fill(CGRect(x: 0, y: 0, width: 64, height: 32)) }
        if animated {
            let blue = renderer.image { context in UIColor.blue.setFill(); context.fill(CGRect(x: 0, y: 0, width: 64, height: 32)) }
            let data = NSMutableData()
            let destination = CGImageDestinationCreateWithData(data, "com.compuserve.gif" as CFString, 2, nil)!
            CGImageDestinationSetProperties(destination, [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFLoopCount: 0]] as CFDictionary)
            for image in [red, blue] {
                CGImageDestinationAddImage(destination, image.cgImage!, [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFDelayTime: 0.15]] as CFDictionary)
            }
            XCTAssertTrue(CGImageDestinationFinalize(destination))
            bytes = data as Data
        } else {
            bytes = red.pngData()!
        }
        listener = try NWListener(using: .tcp, on: .any)
        listener.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in if case .ready = state { self?.isReady = true } }
        }
        listener.newConnectionHandler = { [weak self] connection in Task { @MainActor in self?.accept(connection) } }
        listener.start(queue: .main)
    }

    private func accept(_ connection: NWConnection) {
        connections.append(connection)
        connection.start(queue: .main)
        if rejectsConnections { requests += 1; connection.cancel(); return }
        if socks { negotiate(connection) } else { readHTTP(connection) }
    }

    private func negotiate(_ connection: NWConnection) {
        read(2, from: connection) { prefix in
            self.read(Int(prefix[1]), from: connection) { _ in
                connection.send(content: Data([5, 0]), completion: .contentProcessed { _ in })
                self.read(4, from: connection) { request in
                    if request[3] == 3 {
                        self.read(1, from: connection) { length in self.address(connection, length: Int(length[0])) }
                    } else { self.address(connection, length: request[3] == 1 ? 4 : 16) }
                }
            }
        }
    }

    private func address(_ connection: NWConnection, length: Int) {
        read(length + 2, from: connection) { _ in
            connection.send(content: Data([5, 0, 0, 1, 0, 0, 0, 0, 0, 0]), completion: .contentProcessed { _ in })
            self.readHTTP(connection)
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

    private func readHTTP(_ connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            Task { @MainActor in
                guard let self, data != nil else { connection.cancel(); return }
                self.requests += 1
                let header = "HTTP/1.1 200 OK\r\nContent-Type: image/\(self.animated ? "gif" : "png")\r\nContent-Length: \(self.bytes.count)\r\nConnection: close\r\n\r\n"
                connection.send(content: Data(header.utf8) + self.bytes, isComplete: true, completion: .contentProcessed { _ in connection.cancel() })
            }
        }
    }

    func stop() {
        listener.cancel()
        connections.forEach { $0.cancel() }
    }
}
