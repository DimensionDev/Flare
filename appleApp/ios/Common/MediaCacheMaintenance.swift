import Foundation
import GSPlayer
import Kingfisher
import SwiftUI
import UIKit

@MainActor
enum MediaCacheMaintenance {
    private static let mebibyte: UInt = 1024 * 1024
    private static let kingfisherDiskLimit = 512 * mebibyte
    private static let videoCacheLimit = 1024 * mebibyte
    private static let videoCheckInterval: TimeInterval = 24 * 60 * 60
    private static let lastVideoCheckKey = "mediaCacheMaintenance.lastVideoCacheCheck"
    private static var isVideoCheckRunning = false

    static func configure() {
        let cache = KingfisherManager.shared.cache
        cache.diskStorage.config.sizeLimit = kingfisherDiskLimit
        cache.diskStorage.config.expiration = .days(14)
        cache.cleanExpiredDiskCache()
    }

    static func handleScenePhase(_ phase: ScenePhase) {
        guard phase == .background else { return }
        trimVideoCacheIfNeeded()
    }

    private static func trimVideoCacheIfNeeded(now: Date = .now) {
        guard !isVideoCheckRunning else { return }

        let defaults = UserDefaults.standard
        if let lastCheck = defaults.object(forKey: lastVideoCheckKey) as? Date,
           now >= lastCheck,
           now.timeIntervalSince(lastCheck) < videoCheckInterval {
            return
        }

        isVideoCheckRunning = true
        let backgroundTask = CacheCleanupBackgroundTask()
        let sizeLimit = videoCacheLimit
        DispatchQueue.global(qos: .utility).async {
            if VideoCacheManager.calculateCachedSize() >= sizeLimit {
                try? VideoCacheManager.cleanAllCache()
            }

            Task { @MainActor in
                UserDefaults.standard.set(now, forKey: lastVideoCheckKey)
                isVideoCheckRunning = false
                backgroundTask.end()
            }
        }
    }
}

@MainActor
private final class CacheCleanupBackgroundTask {
    private var identifier: UIBackgroundTaskIdentifier = .invalid

    init() {
        identifier = UIApplication.shared.beginBackgroundTask(
            withName: "MediaCacheMaintenance"
        ) { [weak self] in
            Task { @MainActor in
                self?.end()
            }
        }
    }

    func end() {
        guard identifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(identifier)
        identifier = .invalid
    }
}
