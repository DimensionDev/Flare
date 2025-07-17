import Foundation
import Kingfisher
import UIKit

public final class FlareImageConfiguration {
    public static let shared = FlareImageConfiguration()

    private init() {}

    public func configure() {
        configureImageCache()
        configureImageDownloader()
        setupMemoryPressureHandling()

        FlareLog.info("FlareImageConfiguration Image configuration applied successfully")
    }

    private func configureImageCache() {
        let cache = ImageCache.default

        let memoryLimit = calculateOptimalMemoryLimit()
        cache.memoryStorage.config.totalCostLimit = Int(memoryLimit)

        cache.memoryStorage.config.countLimit = UIDevice.current.userInterfaceIdiom == .pad ? 200 : 100

        cache.diskStorage.config.sizeLimit = 100 * 1024 * 1024 * 10

        cache.diskStorage.config.expiration = .days(7)

        cache.memoryStorage.config.expiration = .seconds(300)
//       cache.memoryStorage.config.keepWhenEnteringBackground = true

        FlareLog.info("ImageCache Memory limit: \(memoryLimit / 1024 / 1024)MB, Disk limit: 100MB, KeepInBackground: true")
    }

    private func calculateOptimalMemoryLimit() -> UInt {
        let totalMemory = ProcessInfo.processInfo.physicalMemory
        let deviceType = UIDevice.current.userInterfaceIdiom

        let percentage = switch deviceType {
        case .pad: 0.20
        case .phone: 0.15
        default: 0.12
        }

        let calculatedLimit = UInt(Double(totalMemory) * percentage)

        let minLimit: UInt = 50 * 1024 * 1024
        let maxLimit: UInt = 300 * 1024 * 1024

        return max(minLimit, min(maxLimit, calculatedLimit))
    }

    private func configureImageDownloader() {
        let downloader = ImageDownloader.default

        downloader.downloadTimeout = 15.0

        downloader.sessionConfiguration.httpMaximumConnectionsPerHost = 6

        FlareLog.debug("ImageDownloader Timeout: 15s, Max connections: 6")
    }

    private func setupMemoryPressureHandling() {
        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleMemoryPressure()
        }

        // 🟢 监听应用进入后台
        // Listen for app entering background
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleBackgroundCleanup()
        }
    }

    private func handleMemoryPressure() {
        FlareLog.warning("FlareImageConfiguration Memory pressure detected, performing aggressive cleanup")

        ImageCache.default.clearMemoryCache()
        ImageCache.default.cleanExpiredDiskCache()

        // Temporarily reduce memory limit
        let currentLimit = ImageCache.default.memoryStorage.config.totalCostLimit
        ImageCache.default.memoryStorage.config.totalCostLimit = currentLimit / 2

        FlareLog.info("FlareImageConfiguration Temporarily reduced memory limit to \(currentLimit / 2 / 1024 / 1024)MB")

        DispatchQueue.main.asyncAfter(deadline: .now() + 300) {
            ImageCache.default.memoryStorage.config.totalCostLimit = currentLimit
            FlareLog.info("FlareImageConfiguration Restored memory limit to \(currentLimit / 1024 / 1024)MB")
        }
    }

    private func handleBackgroundCleanup() {
        FlareLog.info("FlareImageConfiguration App entered background, performing cleanup")

        ImageCache.default.cleanExpiredDiskCache()
    }
}

public enum FlareImageOptions {
    public static func banner(size: CGSize) -> KingfisherOptionsInfo {
        [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(UIScreen.main.scale),
            .memoryCacheExpiration(.seconds(180)),
            .diskCacheExpiration(.days(3)),
        ]
    }

    public static func timelineAvatar(size: CGSize) -> KingfisherOptionsInfo {
        let scale = UIScreen.main.scale
        return [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(scale),
            .downloadPriority(0.7),
            .backgroundDecode,
            .alsoPrefetchToMemory,
            .memoryCacheExpiration(.seconds(600)),
            .diskCacheExpiration(.days(7)),
            .transition(.fade(0.2)),
        ]
    }
}

public extension KFImage {
    func flareMediaPreview(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(300))
            .diskCacheExpiration(.days(7))
    }

    func flareFullScreen(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(180))
            .diskCacheExpiration(.days(14))
    }

    func flareServiceIcon(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(3600))
            .diskCacheExpiration(.days(30))
    }

    @MainActor
    func flareTimelineMedia(size: CGSize, priority: Float = 0.5) -> KFImage {
        let scale = UIScreen.main.scale
        let processor = DownsamplingImageProcessor(size: size)

        var image = setProcessor(processor)
            .scaleFactor(scale)
            .cancelOnDisappear(true)
            .reducePriorityOnDisappear(true)
            .downloadPriority(priority)
            .memoryCacheExpiration(.seconds(240))
            .diskCacheExpiration(.days(5))
            .fade(duration: 0.25)

        #if !targetEnvironment(simulator)
            image = image.backgroundDecode(true)
        #endif

        return image
    }

    @MainActor
    func flareTimelineAvatar(size: CGSize) -> KFImage {
        let scale = UIScreen.main.scale

        let processor = DownsamplingImageProcessor(size: size)

        var image = setProcessor(processor)
            .scaleFactor(scale)
            .cancelOnDisappear(true)
            .downloadPriority(0.7)
            .memoryCacheExpiration(.seconds(600))
            .diskCacheExpiration(.days(7))
            .fade(duration: 0.2)

        #if !targetEnvironment(simulator)
            image = image.backgroundDecode(true)
        #endif

        return image
    }
}
