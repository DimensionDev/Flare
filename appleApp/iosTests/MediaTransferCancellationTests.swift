@testable import FlareAppleCore
import Foundation
import XCTest

final class MediaTransferCancellationTests: XCTestCase {
    @MainActor
    func testRouteInvalidationCancelsAnActiveURLSessionTransfer() async throws {
        try await checkActiveTransfer(cancelParent: false)
    }

    @MainActor
    func testParentCancellationAlsoStopsTheTransfer() async throws {
        try await checkActiveTransfer(cancelParent: true)
    }

    @MainActor
    func testRouteInvalidationCancelsStreamingExports() async throws {
        try await checkActiveTransfer(cancelParent: false, streaming: true)
    }

    @MainActor
    func testAlreadyInvalidatedRouteDoesNotStartTheOperation() async {
        let signal = InvalidationSignal()
        signal.invalidate()
        var started = false
        do {
            _ = try await MediaTransfer.withRouteCancellation(register: signal.register) {
                started = true
                return 1
            }
            XCTFail("An invalidated route must not start a transfer")
        } catch {
            XCTAssertTrue(error is CancellationError)
        }
        XCTAssertFalse(started)
        XCTAssertEqual(signal.unsubscribeCount, 1)
    }

    @MainActor
    func testCompletedTransferRemovesItsCancellationObserver() async throws {
        let signal = InvalidationSignal()
        let value = try await MediaTransfer.withRouteCancellation(register: signal.register) { 42 }
        XCTAssertEqual(value, 42)
        XCTAssertEqual(signal.unsubscribeCount, 1)
        XCTAssertNil(signal.cancel)
        signal.invalidate()
    }

    @MainActor
    private func checkActiveTransfer(cancelParent: Bool, streaming: Bool = false) async throws {
        let started = expectation(description: "URLSession received the first bytes")
        let stopped = expectation(description: "URLSession canceled the unfinished response")
        PendingMediaProtocol.callbacks.configure(
            start: { if !streaming { started.fulfill() } },
            stop: { stopped.fulfill() }
        )
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.protocolClasses = [PendingMediaProtocol.self]
        let session = URLSession(configuration: configuration)
        defer {
            session.invalidateAndCancel()
            PendingMediaProtocol.callbacks.configure(start: {}, stop: {})
        }
        let signal = InvalidationSignal()
        let transfer = Task {
            try await MediaTransfer.withRouteCancellation(register: signal.register) {
                let url = URL(string: "https://media.example.test/slow")!
                if streaming {
                    let (bytes, _) = try await session.bytes(from: url)
                    var result = Data()
                    for try await byte in bytes {
                        result.append(byte)
                        if result.count == 1 { started.fulfill() }
                    }
                    return result
                }
                return try await session.data(from: url).0
            }
        }
        await fulfillment(of: [started], timeout: 5)
        if cancelParent { transfer.cancel() } else { signal.invalidate() }
        do {
            _ = try await transfer.value
            XCTFail("The unfinished response must be canceled")
        } catch {
            XCTAssertTrue(error is CancellationError || (error as? URLError)?.code == .cancelled, "\(error)")
        }
        await fulfillment(of: [stopped], timeout: 5)
        XCTAssertEqual(signal.unsubscribeCount, 1)
        XCTAssertNil(signal.cancel)
    }
}

@MainActor
private final class InvalidationSignal {
    private var invalidated = false
    private(set) var cancel: (() -> Void)?
    private(set) var unsubscribeCount = 0

    func register(_ cancel: @escaping () -> Void) -> () -> Void {
        if invalidated { cancel() } else { self.cancel = cancel }
        return { [self] in
            self.cancel = nil
            unsubscribeCount += 1
        }
    }

    func invalidate() {
        invalidated = true
        cancel?()
    }
}

private final class PendingMediaProtocol: URLProtocol, @unchecked Sendable {
    static let callbacks = Callbacks()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "application/octet-stream", "Content-Length": "131072"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        // Fill AsyncBytes' buffer while leaving the second half of the response pending.
        client?.urlProtocol(self, didLoad: Data(repeating: 0x61, count: 64 * 1024))
        Self.callbacks.started()
    }

    override func stopLoading() { Self.callbacks.stopped() }

    final class Callbacks: @unchecked Sendable {
        private let lock = NSLock()
        private var start: @Sendable () -> Void = {}
        private var stop: @Sendable () -> Void = {}

        func configure(start: @escaping @Sendable () -> Void, stop: @escaping @Sendable () -> Void) {
            lock.withLock {
                self.start = start
                self.stop = stop
            }
        }

        func started() { lock.withLock { start }() }
        func stopped() { lock.withLock { stop }() }
    }
}
