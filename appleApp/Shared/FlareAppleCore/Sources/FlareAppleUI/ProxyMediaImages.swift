import Combine
import CryptoKit
import FlareAppleCore
import Foundation
import Kingfisher
@preconcurrency import KotlinSharedUI
import Observation

public extension UiMediaImage {
    convenience init(url: String, proxy: ProxyRef, headers: [String: String]? = nil) {
        self.init(url: url, previewUrl: url, description: nil, height: 0, width: 0,
                  sensitive: false, customHeaders: headers, proxy: proxy)
    }
}

public struct PreparedMediaImage {
    public let source: Source
    public let options: KingfisherOptionsInfo
    public let redirectHandler: AnyRedirectHandler
    public let downloader: ImageDownloader
    public let key: String
}

@MainActor
@Observable
public final class ProxyMediaImages {
    public static let shared = ProxyMediaImages()
    public private(set) var revision: UInt64 = 0
    @ObservationIgnored private var observation: AnyCancellable?
    @ObservationIgnored private var downloaders: [String: (ProxyRef, String, ImageDownloader)] = [:]

    private init() {
        observation = ProxyNetwork.shared.changes.toPublisher()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.revision &+= 1
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    for (key, value) in self.downloaders {
                        let current = try? await ProxyNetwork.shared.snapshot(ref: value.0)
                        if current?.key != value.1 {
                            value.2.cancelAll()
                            self.downloaders.removeValue(forKey: key)
                        }
                    }
                }
            }
    }

    public func prepare(_ media: any UiMedia) async throws -> PreparedMediaImage {
        guard let url = URL(string: media.url) else { throw URLError(.badURL) }
        if !media.isRemote {
            let fileURL = url.isFileURL ? url : URL(fileURLWithPath: media.url)
            return PreparedMediaImage(source: .provider(LocalFileImageDataProvider(fileURL: fileURL)),
                                      options: [], redirectHandler: AnyRedirectHandler { _, _, request, completion in completion(request) }, downloader: .default, key: media.cacheKey)
        }
        let route = try await ProxyNetwork.shared.snapshot(ref: media.proxy)
        let headers = media.customHeaders ?? [:]
        let headerData = headers.sorted { $0.key < $1.key }.map { "\($0.key):\($0.value)" }.joined(separator: "\n")
        let headerKey = SHA256.hash(data: Data(headerData.utf8)).map { String(format: "%02x", $0) }.joined()
        let key = route.key + "|" + headerKey
        let downloader: ImageDownloader
        if let existing = downloaders[key] {
            downloader = existing.2
        } else {
            downloader = ImageDownloader(name: key)
            let configuration = URLSessionConfiguration.ephemeral
            configuration.urlCredentialStorage = nil
            configuration.urlCache = nil
            configuration.httpCookieStorage = nil
            ProxySessionConfigurator().configure(configuration: configuration, connection: route.connection)
            downloader.sessionConfiguration = configuration
            downloaders[key] = (media.proxy, route.key, downloader)
        }
        let redirect = AnyRedirectHandler { _, _, request, completion in
            completion(MediaTransfer.redirectedRequest(request, origin: url))
        }
        let modifier = AnyModifier { request in
            var request = request
            headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
            return request
        }
        return PreparedMediaImage(
            source: .network(ImageResource(downloadURL: url, cacheKey: media.cacheKey)),
            options: [.downloader(downloader), .requestModifier(modifier), .redirectHandler(redirect)],
            redirectHandler: redirect,
            downloader: downloader,
            key: route.key + "|" + media.cacheKey
        )
    }

    public func retrieve(_ media: any UiMedia, options: KingfisherOptionsInfo = []) async throws -> RetrieveImageResult {
        let prepared = try await prepare(media)
        return try await KingfisherManager.shared.retrieveImage(with: prepared.source, options: options + prepared.options)
    }
}

#if canImport(UIKit)
import UIKit
import ObjectiveC

@MainActor private var mediaLoadKey: UInt8 = 0

private final class MediaImageLoad {
    var task: Task<Void, Never>?
    var observation: AnyCancellable?
    deinit { task?.cancel() }
}

public extension UIImageView {
    func cancelMediaLoad() {
        objc_setAssociatedObject(self, &mediaLoadKey, nil, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        kf.cancelDownloadTask()
    }

    func setMedia(_ media: (any UiMedia)?, options: KingfisherOptionsInfo = [],
                  completion: (@MainActor @Sendable (Result<RetrieveImageResult, KingfisherError>) -> Void)? = nil) {
        cancelMediaLoad()
        if media == nil || !options.contains(where: { if case .keepCurrentImageWhileLoading = $0 { return true }; return false }) {
            image = nil
        }
        guard let media else { return }
        let load = MediaImageLoad()
        objc_setAssociatedObject(self, &mediaLoadKey, load, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        load.observation = ProxyNetwork.shared.changes.toPublisher()
            .receive(on: DispatchQueue.main)
            .sink { [weak self, weak load] _ in
                guard let self, let load else { return }
                load.task?.cancel()
                load.task = Task { @MainActor [weak self, weak load] in
                    guard let self, let load else { return }
                    do {
                        let prepared = try await ProxyMediaImages.shared.prepare(media)
                        guard !Task.isCancelled,
                              objc_getAssociatedObject(self, &mediaLoadKey) as? MediaImageLoad === load else { return }
                        self.kf.setImage(with: prepared.source, options: options + prepared.options, completionHandler: completion)
                    } catch {
                        guard !Task.isCancelled else { return }
                        completion?(.failure(.requestError(reason: .emptyRequest)))
                    }
                }
            }
    }
}
#endif
