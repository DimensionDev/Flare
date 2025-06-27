import Foundation
import Kingfisher
import SensitiveContentAnalysis
import UIKit

// The SensitiveContentAnalysis entitlement is not available for Enterprise development or for people with free accounts.
// https://developer.apple.com/documentation/sensitivecontentanalysis/detecting-nudity-in-media-and-providing-intervention-options
// fuck
class SensitiveContentAnalyzer {
    static let shared = SensitiveContentAnalyzer()

    private let analyzer = SCSensitivityAnalyzer()
    private let cache = SensitiveContentCache.shared
    private let urlSession = URLSession.shared

    private init() {}

    func analyzeImage(url: String) async -> Bool {
        guard let imageURL = URL(string: url) else {
            return false
        }
        do {
            return try await analyzeImageDownLocal(url: imageURL)
        } catch {
            FlareLog.error("SensitiveContentAnalyzer analyzeImageDownLocal error: \(error)")
            return false
        }

        // 1. cache
        // if let cachedResult = cache.get(for: url) {
        //     return cachedResult
        // }

        // // 2.   Kingfisher cache
        // if let cachedImage = await getCachedImage(url: url) {
        //     do {
        //         let response = try await analyzer.analyzeImage(cachedImage as! CGImage)
        //         let isSensitive = response.isSensitive

        //         cache.set(url: url, isSensitive: isSensitive)
        //         return isSensitive
        //     } catch {
        //         FlareLog.debug("Kingfisher cache fail: \(error)")
        //         return false
        //     }
        // }
        // return false

//        var xsds = analyzer.analysisPolicy

        // 3. analyze NetworkImage
        // do {
        //     guard let imageURL = URL(string: url) else {
        //         return false
        //     }

        //     let response = try await analyzer.analyzeImage(at: imageURL)
        //     let isSensitive = response.isSensitive

        //     cache.set(url: url, isSensitive: isSensitive)

        //     return isSensitive
        // } catch {
        //     FlareLog.debug("analyze NetworkImage check faile: \(error)")
        //     return false
        // }
    }

    private func checkPolicy() -> Bool {
        if analyzer.analysisPolicy == .disabled {
            false
        } else {
            true
        }
    }

    private func getCachedImage(url: String) async -> UIImage? {
        await withCheckedContinuation { continuation in
            FlareLog.debug("SensitiveContentAnalyzer 开始检查缓存: \(url)")

            // check custom cache
            if let cachedResult = cache.get(for: url) {
                FlareLog.debug("SensitiveContentAnalyzer 自定义缓存命中")
                continuation.resume(returning: nil)
                return
            }

            let kingfisherCache = ImageCache.default
            // 使用与KFImage相同的缓存键
            let key = url // 改为使用absoluteString

            if let image = kingfisherCache.retrieveImageInMemoryCache(forKey: key) {
                FlareLog.debug("SensitiveContentAnalyzer 内存缓存命中: \(image)")
                continuation.resume(returning: image)
                return
            }

            FlareLog.debug("SensitiveContentAnalyzer 检查磁盘缓存...")
            kingfisherCache.retrieveImage(forKey: key, options: nil) { result in
                switch result {
                case let .success(value):
                    if let image = value.image {
                        FlareLog.debug("SensitiveContentAnalyzer 磁盘缓存成功: \(image)")
                        continuation.resume(returning: image)
                    } else {
                        FlareLog.debug("SensitiveContentAnalyzer 磁盘缓存损坏，清理缓存")
                        kingfisherCache.removeImage(forKey: key)
                        continuation.resume(returning: nil)
                    }
                case let .failure(error):
                    FlareLog.debug("SensitiveContentAnalyzer 磁盘缓存失败: \(error)")
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    private var cacheDirectory: URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("SensitiveContentCache", isDirectory: true)
    }

    func analyzeImageDownLocal(url: URL) async throws -> Bool {
        let cachedFile = cacheDirectory.appendingPathComponent(url.lastPathComponent)

        if !FileManager.default.fileExists(atPath: cachedFile.path) {
            try await downloadImage(from: url, to: cachedFile)
        }

        let response = try await analyzer.analyzeImage(at: cachedFile)
        return response.isSensitive
    }

    private func downloadImage(from url: URL, to localURL: URL) async throws {
        try FileManager.default.createDirectory(
            at: cacheDirectory,
            withIntermediateDirectories: true
        )

        let (tempURL, _) = try await urlSession.download(from: url)

        if FileManager.default.fileExists(atPath: localURL.path) {
            try FileManager.default.removeItem(at: localURL)
        }
        try FileManager.default.moveItem(at: tempURL, to: localURL)
    }

    func clearCache() throws {
        if FileManager.default.fileExists(atPath: cacheDirectory.path) {
            try FileManager.default.removeItem(at: cacheDirectory)
        }
    }
}
