import Foundation
import Kingfisher
import SensitiveContentAnalysis
import UIKit

class SensitiveContentAnalyzer {
    static let shared = SensitiveContentAnalyzer()

    private let analyzer = SCSensitivityAnalyzer()
    private let cache = SensitiveContentCache.shared

    private init() {}

    func analyzeImage(url: String) async -> Bool {
        // 1. cache
        if let cachedResult = cache.get(for: url) {
            return cachedResult
        }

        // 2.   Kingfisher cache
//        if let cachedImage = await getCachedImage(url: url) {
//            do {
//                let response = try await analyzer.analyzeImage(cachedImage as! CGImage)
//                let isSensitive = response.isSensitive
//
//
//                cache.set(url: url, isSensitive: isSensitive)
//                return isSensitive
//            } catch {
//                print("Kingfisher cache fail: \(error)")
//             }
//        }

        // 3. analyze NetworkImage
        do {
            guard let imageURL = URL(string: url) else {
                return false
            }

            let response = try await analyzer.analyzeImage(at: imageURL)
            let isSensitive = response.isSensitive

            cache.set(url: url, isSensitive: isSensitive)

            return isSensitive
        } catch {
            print("analyze NetworkImage check faile: \(error)")
            return false
        }
    }

    private func getCachedImage(url: String) async -> UIImage? {
        await withCheckedContinuation { continuation in
            guard let imageURL = URL(string: url) else {
                continuation.resume(returning: nil)
                return
            }

            // check  Kingfisher mem cache
            let cache = ImageCache.default
            let key = imageURL.cacheKey

            if let image = cache.retrieveImageInMemoryCache(forKey: key) {
                continuation.resume(returning: image)
                return
            }

            // check Kingfisher disk cache
            cache.retrieveImage(forKey: key, options: nil) { result in
                switch result {
                case let .success(value):
                    continuation.resume(returning: value.image)
                case .failure:
                    continuation.resume(returning: nil)
                }
            }
        }
    }
}
