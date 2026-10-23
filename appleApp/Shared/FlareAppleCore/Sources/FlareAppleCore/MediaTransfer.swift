import Foundation
@preconcurrency import KotlinSharedUI

public enum MediaTransfer {
    public static func download(_ media: any UiMedia) async throws -> (URL, URLResponse) {
        try await withSession(media) { session, request in
            let result = try await session.download(for: request)
            guard let response = result.1 as? HTTPURLResponse, (200..<300).contains(response.statusCode) else {
                try? FileManager.default.removeItem(at: result.0)
                throw URLError(.badServerResponse)
            }
            return result
        }
    }

    public static func data(_ media: any UiMedia) async throws -> Data {
        let (file, _) = try await download(media)
        defer { try? FileManager.default.removeItem(at: file) }
        return try Data(contentsOf: file)
    }

    public static func withSession<T: Sendable>(_ media: any UiMedia, operation: @escaping @MainActor (URLSession, URLRequest) async throws -> T) async throws -> T {
        guard let url = URL(string: media.urlForDownload), ["https", "http"].contains(url.scheme?.lowercased() ?? "") else {
            throw URLError(.unsupportedURL)
        }
        let snapshot = try await ProxyNetwork.shared.snapshot(ref: media.proxy)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.urlCredentialStorage = nil
        configuration.httpCookieStorage = nil
        ProxySessionConfigurator().configure(configuration: configuration, connection: snapshot.connection)
        let session = URLSession(configuration: configuration, delegate: MediaRedirectDelegate(origin: url), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url)
        media.customHeaders?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        return try await withRouteCancellation(register: { cancel in
            let unsubscribe = snapshot.onInvalidated(cancel: cancel)
            return { unsubscribe() }
        }) {
            try await operation(session, request)
        }
    }

    static func withRouteCancellation<T: Sendable>(
        register: (@escaping () -> Void) -> () -> Void,
        operation: @escaping @MainActor () async throws -> T
    ) async throws -> T {
        let transfer = Task {
            try Task.checkCancellation()
            return try await operation()
        }
        // Cancel the task first; invalidating a session before task creation raises an NSException.
        let unsubscribe = register { transfer.cancel() }
        defer { unsubscribe() }
        return try await withTaskCancellationHandler {
            try await transfer.value
        } onCancel: {
            transfer.cancel()
        }
    }

    nonisolated public static func redirectedRequest(_ request: URLRequest, origin: URL) -> URLRequest {
        guard let target = request.url else { return request }
        let port: (URL) -> Int = { $0.port ?? ($0.scheme == "https" ? 443 : 80) }
        if target.scheme == origin.scheme, target.host == origin.host, port(target) == port(origin) { return request }
        var request = request
        request.setValue(nil, forHTTPHeaderField: "Authorization")
        request.setValue(nil, forHTTPHeaderField: "Cookie")
        return request
    }
}

private final class MediaRedirectDelegate: NSObject, URLSessionTaskDelegate {
    let origin: URL
    init(origin: URL) { self.origin = origin }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(MediaTransfer.redirectedRequest(request, origin: origin))
    }
}
