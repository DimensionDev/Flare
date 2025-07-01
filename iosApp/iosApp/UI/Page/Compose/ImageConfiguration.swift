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

        cache.diskStorage.config.sizeLimit = 100 * 1024 * 1024

        cache.diskStorage.config.expiration = .days(7)

        cache.memoryStorage.config.expiration = .seconds(300)

        FlareLog.info("ImageCache Memory limit: \(memoryLimit / 1024 / 1024)MB, Disk limit: 100MB")
    }

    private func calculateOptimalMemoryLimit() -> UInt {
        let totalMemory = ProcessInfo.processInfo.physicalMemory
        let deviceType = UIDevice.current.userInterfaceIdiom

        // 🟢 根据设备类型和总内存动态调整
        // Dynamically adjust based on device type and total memory
        let percentage = switch deviceType {
        case .pad: 0.15 // iPad: 15%
        case .phone: 0.10 // iPhone: 10%
        default: 0.08 // 其他设备: 8%
        }

        let calculatedLimit = UInt(Double(totalMemory) * percentage)

        // 🟢 设置合理的上下限
        // Set reasonable upper and lower bounds
        let minLimit: UInt = 50 * 1024 * 1024 // 最小50MB
        let maxLimit: UInt = 200 * 1024 * 1024 // 最大200MB

        return max(minLimit, min(maxLimit, calculatedLimit))
    }

    private func configureImageDownloader() {
        let downloader = ImageDownloader.default

         downloader.downloadTimeout = 15.0

         downloader.sessionConfiguration.httpMaximumConnectionsPerHost = 6

        FlareLog.debug("ImageDownloader Timeout: 15s, Max connections: 6")
    }

    private func setupMemoryPressureHandling() {
        // 🟢 监听内存警告
        // Listen for memory warnings
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
        FlareLog.warning("FlareImageConfiguration Memory pressure detected, clearing cache")

        ImageCache.default.clearMemoryCache()

        ImageCache.default.cleanExpiredDiskCache()
    }

    private func handleBackgroundCleanup() {
        FlareLog.info("FlareImageConfiguration App entered background, performing cleanup")

        ImageCache.default.cleanExpiredDiskCache()
    }
}

public enum FlareImageOptions {
    public static func avatar(size: CGSize) -> KingfisherOptionsInfo {
        [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(UIScreen.main.scale),
            .memoryCacheExpiration(.seconds(300)),
            .diskCacheExpiration(.days(7)),
            .transition(.fade(0.25)),
        ]
    }

    public static func banner(size: CGSize) -> KingfisherOptionsInfo {
        [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(UIScreen.main.scale),
            .memoryCacheExpiration(.seconds(180)),
            .diskCacheExpiration(.days(3)),
            .transition(.fade(0.25)),
        ]
    }

    public static func mediaPreview(size: CGSize) -> KingfisherOptionsInfo {
        [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(UIScreen.main.scale),
            .memoryCacheExpiration(.seconds(600)),
            .diskCacheExpiration(.days(14)),
            .transition(.fade(0.25)),
        ]
    }

    public static func fullScreen(size: CGSize) -> KingfisherOptionsInfo {
        [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(UIScreen.main.scale),
            .memoryCacheExpiration(.seconds(120)),
            .diskCacheExpiration(.days(30)),
            .transition(.fade(0.25)),
        ]
    }

    public static func serviceIcon(size: CGSize) -> KingfisherOptionsInfo {
        [
            .processor(DownsamplingImageProcessor(size: size)),
            .scaleFactor(UIScreen.main.scale),
            .memoryCacheExpiration(.never),
            .diskCacheExpiration(.days(30)),
            .transition(.fade(0.25)),
        ]
    }
}

public extension KFImage {
    func flareAvatar(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(300))
            .diskCacheExpiration(.days(7))
            .fade(duration: 0.25)
    }

    func flareBanner(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(180))
            .diskCacheExpiration(.days(3))
            .fade(duration: 0.25)
    }

    func flareMediaPreview(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(600))
            .diskCacheExpiration(.days(14))
            .fade(duration: 0.25)
    }

    func flareFullScreen(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.seconds(120))
            .diskCacheExpiration(.days(30))
            .fade(duration: 0.25)
    }

    func flareServiceIcon(size: CGSize) -> KFImage {
        setProcessor(DownsamplingImageProcessor(size: size))
            .scaleFactor(UIScreen.main.scale)
            .memoryCacheExpiration(.never)
            .diskCacheExpiration(.days(30))
            .fade(duration: 0.25)
    }
}
