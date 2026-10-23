import AVFoundation
import XCTest
@preconcurrency import Network
import KotlinSharedUI
import FlareAppleCore
@testable import FlareAppleUI

@MainActor
final class ProxyPlaybackLifecycleTests: XCTestCase {
    func testRelayFailureReachesPlaybackErrorState() async throws {
        let presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        addTeardownBlock { @MainActor in try await self.select(.system, presenter: presenter) }
        try await select(.direct, presenter: presenter)
        let server = try PlaybackHTTPServer(statusCode: 503)
        defer { server.stop() }
        try await eventually { (server.listener.port?.rawValue ?? 0) > 0 }
        let session = VideoPlaybackSession()
        defer {
            session.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        session.play(media: media(server.url), playing: true)
        try await eventually {
            if case .error = session.state { return true }
            return false
        }
        guard case .error(let error) = session.state else { return XCTFail("Expected playback error") }
        XCTAssertEqual(error.localizedDescription, "Target returned HTTP 503")
    }

    func testChangingRouteRetiresTheNativeItemWhileItsSurfaceIsDetached() async throws {
        let presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        addTeardownBlock { @MainActor in try await self.select(.system, presenter: presenter) }
        try await select(.direct, presenter: presenter)
        let server = try PlaybackHTTPServer()
        defer { server.stop() }
        try await eventually { (server.listener.port?.rawValue ?? 0) > 0 }
        let session = VideoPlaybackSession()
        defer {
            session.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        session.play(media: media(server.url), playing: false)
        try await eventually { session.player?.currentItem?.status == .readyToPlay }
        let player = try XCTUnwrap(session.player)
        session.detach()
        XCTAssertFalse(player.items().isEmpty)
        try await select(.system, presenter: presenter)
        try await eventually(timeout: 2) { player.items().isEmpty }
    }

    func testRelaySurvivesHandoffAndIsReleasedWithItsNativeItems() async throws {
        let presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        addTeardownBlock { @MainActor in try await self.select(.system, presenter: presenter) }
        try await select(.direct, presenter: presenter)
        let server = try PlaybackHTTPServer()
        defer { server.stop() }
        try await eventually { (server.listener.port?.rawValue ?? 0) > 0 }
        let inline = VideoPlaybackSession()
        let detail = VideoPlaybackSession()
        defer {
            inline.detach()
            detail.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        inline.play(media: media(server.url), playing: false)
        try await eventually { inline.player?.currentItem?.status == .readyToPlay }
        let player = try XCTUnwrap(inline.player)
        let item = try XCTUnwrap(player.currentItem)
        let relay = try XCTUnwrap((item.asset as? AVURLAsset)?.url)
        XCTAssertNotEqual(relay.absoluteString, server.url)
        inline.detach()
        let retained = try await readRange(relay)
        XCTAssertEqual(retained.1.statusCode, 206)
        XCTAssertEqual(retained.0.count, 4)

        VideoPlaybackSession.setPosition(for: server.url, seconds: 1.25)
        detail.play(media: media(server.url), playing: false)
        try await eventually { detail.hasRestoredPosition }
        XCTAssertTrue(detail.player === player)
        XCTAssertTrue(player.currentItem === item)
        XCTAssertEqual(detail.position, 1.25, accuracy: 0.1)

        detail.play(media: media(server.url + "?alternate=1"), playing: false)
        try await eventually { player.currentItem !== item && player.currentItem?.status == .readyToPlay }
        let replaced = try await readRange(relay)
        XCTAssertEqual(replaced.1.statusCode, 502)
        let replacement = try XCTUnwrap((player.currentItem?.asset as? AVURLAsset)?.url)
        detail.detach()
        VideoPlaybackSession.releaseIdleBuffer()
        XCTAssertTrue(player.items().isEmpty)
        let expired = try await readRange(replacement)
        XCTAssertEqual(expired.1.statusCode, 502)
    }

    func testOnlyTheAffectedAccountReloadsAndPreservesPositionAndPlaybackIntent() async throws {
        let presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
        let account = MicroBlogKey(id: UUID().uuidString, host: "playback-tests.example")
        addTeardownBlock { @MainActor in
            try await self.select(.inherit, account: account, presenter: presenter)
            try await self.select(.system, presenter: presenter)
        }
        try await select(.direct, presenter: presenter)
        try await select(.direct, account: account, presenter: presenter)
        let server = try PlaybackHTTPServer()
        defer { server.stop() }
        try await eventually { (server.listener.port?.rawValue ?? 0) > 0 }
        let session = VideoPlaybackSession()
        defer {
            session.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        session.play(media: media(server.url, proxy: ProxyRef.Account(accountKey: account)), position: 1.25, playing: false)
        try await eventually { session.hasRestoredPosition }
        let player = try XCTUnwrap(session.player)
        let item = try XCTUnwrap(player.currentItem)
        try await select(.system, presenter: presenter)
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertTrue(player.currentItem === item, "An unrelated global change must preserve the account's native queue")
        XCTAssertEqual(player.rate, 0)

        try await select(.inherit, account: account, presenter: presenter)
        try await eventually { player.currentItem !== item && session.hasRestoredPosition }
        XCTAssertTrue(session.player === player)
        XCTAssertEqual(session.position, 1.25, accuracy: 0.1)
        XCTAssertEqual(player.rate, 0, "A proxy reload must preserve an explicit pause")
        let systemItem = try XCTUnwrap(player.currentItem)
        session.setPlaying(true, rate: 1.5)
        try await select(.direct, account: account, presenter: presenter)
        try await eventually { player.currentItem !== systemItem && session.hasRestoredPosition && player.rate == 1.5 }
        XCTAssertGreaterThanOrEqual(session.position, 1.2)
    }

    private func readRange(_ url: URL) async throws -> (Data, HTTPURLResponse) {
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData)
        request.setValue("bytes=44-47", forHTTPHeaderField: "Range")
        let (data, response) = try await URLSession.shared.data(for: request)
        return (data, try XCTUnwrap(response as? HTTPURLResponse))
    }

    private func media(_ url: String, proxy: ProxyRef = ProxyRef.Global.shared) -> UiMediaVideo {
        UiMediaVideo(url: url, thumbnailUrl: "", description: nil, height: 0, width: 0,
                     customHeaders: nil, proxy: proxy, downloadUrl: nil)
    }

    private func select(_ mode: ProxyMode, account: MicroBlogKey? = nil,
                        presenter: KotlinPresenter<ProxySettingsState>) async throws {
        try await eventually { presenter.state.settings != nil && !presenter.state.busy }
        if presenter.state.settings?.policy(accountKey: account).mode == mode { return }
        presenter.state.select(accountKey: account, policy: ProxyPolicy(mode: mode, proxyId: nil))
        try await eventually { presenter.state.settings?.policy(accountKey: account).mode == mode && !presenter.state.busy }
        XCTAssertNil(presenter.state.error)
    }

    private func eventually(timeout: TimeInterval = 10, _ condition: () -> Bool,
                            file: StaticString = #filePath, line: UInt = #line) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline { try await Task.sleep(for: .milliseconds(10)) }
        XCTAssertTrue(condition(), file: file, line: line)
        if !condition() { throw URLError(.timedOut) }
    }
}

@MainActor
private final class PlaybackHTTPServer {
    let listener: NWListener
    private var connections: [NWConnection] = []
    private let bytes: Data
    private let statusCode: Int
    var url: String { "http://127.0.0.1:\(listener.port!.rawValue)/\(id).wav" }
    private let id = UUID().uuidString

    init(statusCode: Int = 200) throws {
        self.statusCode = statusCode
        var data = Data("RIFF".utf8)
        func append(_ value: UInt32) {
            var value = value.littleEndian
            withUnsafeBytes(of: &value) { data.append(contentsOf: $0) }
        }
        let size = 8000 * 2 * 5
        append(UInt32(36 + size))
        data.append(Data("WAVEfmt ".utf8))
        append(16)
        data.append(contentsOf: [1, 0, 1, 0])
        append(8000)
        append(16000)
        data.append(contentsOf: [2, 0, 16, 0])
        data.append(Data("data".utf8))
        append(UInt32(size))
        data.append(Data(repeating: 0, count: size))
        bytes = data
        listener = try NWListener(using: .tcp, on: .any)
        listener.newConnectionHandler = { [weak self] connection in
            Task { @MainActor in
                guard let self else { connection.cancel(); return }
                self.connections.append(connection)
                connection.start(queue: .main)
                self.read(connection)
            }
        }
        listener.start(queue: .main)
    }

    private func read(_ connection: NWConnection, prefix: Data = Data()) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] data, _, complete, error in
            Task { @MainActor in
                guard let self, let data, error == nil else { connection.cancel(); return }
                let received = prefix + data
                let text = String(decoding: received, as: UTF8.self)
                guard text.contains("\r\n\r\n") else {
                    if complete { connection.cancel() } else { self.read(connection, prefix: received) }
                    return
                }
                if self.statusCode != 200 {
                    connection.send(content: Data("HTTP/1.1 \(self.statusCode) Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".utf8),
                                    completion: .contentProcessed { _ in connection.cancel() })
                    return
                }
                let range = text.components(separatedBy: "\r\n").first { $0.lowercased().hasPrefix("range: bytes=") }?
                    .split(separator: "=").last?.split(separator: "-", omittingEmptySubsequences: false)
                let start = range.flatMap { Int($0[0]) } ?? 0
                let end = min(range.flatMap { $0.count > 1 ? Int($0[1]) : nil } ?? self.bytes.count - 1, self.bytes.count - 1)
                guard start >= 0, start <= end else { connection.cancel(); return }
                let body = self.bytes[start...end]
                let status = range == nil ? "200 OK" : "206 Partial Content"
                let contentRange = range == nil ? "" : "Content-Range: bytes \(start)-\(end)/\(self.bytes.count)\r\n"
                let headers = "HTTP/1.1 \(status)\r\nContent-Type: audio/wav\r\nContent-Length: \(body.count)\r\nAccept-Ranges: bytes\r\nCache-Control: no-store\r\n\(contentRange)Connection: close\r\n\r\n"
                connection.send(content: Data(headers.utf8) + (text.hasPrefix("HEAD ") ? Data() : body),
                                completion: .contentProcessed { _ in connection.cancel() })
            }
        }
    }

    func stop() {
        listener.cancel()
        connections.forEach { $0.cancel() }
    }
}
