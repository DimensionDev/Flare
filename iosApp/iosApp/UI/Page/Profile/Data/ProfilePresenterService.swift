import Foundation
import os
import shared
import SwiftUI

class ProfilePresenterService {
    static let shared = ProfilePresenterService()

    private let logger = Logger(subsystem: "com.flare.app", category: "ProfilePresenterService")
    private let cacheLock = NSLock()

    private var presenterCache: [String: ProfilePresenterWrapper] = [:]
    private var mediaPresenterCache: [String: ProfileMediaPresenterWrapper] = [:]
    // ❌ 已删除：tabStoreCache，因为ProfileTabSettingStore已被移除

    private init() {}

    private func getCacheKey(accountType: AccountType, userKey: MicroBlogKey?) -> String {
        let accountKey = (accountType as? AccountTypeSpecific)?.accountKey.description ?? String(describing: accountType)
        let userKeyStr = userKey?.description ?? "self"
        return "\(accountKey)_\(userKeyStr)"
    }

    func getOrCreatePresenter(accountType: AccountType, userKey: MicroBlogKey?) -> ProfilePresenterWrapper {
        let key = getCacheKey(accountType: accountType, userKey: userKey)

        cacheLock.lock()
        defer { cacheLock.unlock() }

        if let cached = presenterCache[key] {
            os_log("[📔][ProfilePresenterService] 🔄 使用缓存 ProfilePresenterWrapper: %{public}@", log: .default, type: .debug, key)
            return cached
        }

        let presenter = ProfilePresenterWrapper(accountType: accountType, userKey: userKey)
        presenterCache[key] = presenter
        os_log("[📔][ProfilePresenterService] ✨ 创建新 ProfilePresenterWrapper: %{public}@", log: .default, type: .debug, key)
        return presenter
    }

    func getOrCreateMediaPresenter(accountType: AccountType, userKey: MicroBlogKey?) -> ProfileMediaPresenterWrapper {
        let key = getCacheKey(accountType: accountType, userKey: userKey)

        cacheLock.lock()
        defer { cacheLock.unlock() }

        if let cached = mediaPresenterCache[key] {
            os_log("[📔][ProfilePresenterService] 🔄 使用缓存 ProfileMediaPresenterWrapper: %{public}@", log: .default, type: .debug, key)
            return cached
        }

        let presenter = ProfileMediaPresenterWrapper(accountType: accountType, userKey: userKey)
        mediaPresenterCache[key] = presenter
        os_log("[📔][ProfilePresenterService] ✨ 创建新 ProfileMediaPresenterWrapper: %{public}@", log: .default, type: .debug, key)
        return presenter
    }

    // ❌ 已删除：getOrCreateTabStore方法，因为ProfileTabSettingStore已被移除

    // ❌ 已删除：setupTimelineViewModel方法，因为ProfileTabSettingStore已被移除

    func clearCache() {
        cacheLock.lock()
        defer { cacheLock.unlock() }

        let presenterCount = presenterCache.count
        let mediaPresenterCount = mediaPresenterCache.count

        presenterCache.removeAll()
        mediaPresenterCache.removeAll()

        logger.debug("🧹 清除所有Profile缓存 - Presenter: \(presenterCount), MediaPresenter: \(mediaPresenterCount)")
    }

    func clearCache(for accountType: AccountType, userKey: MicroBlogKey?) {
        let key = getCacheKey(accountType: accountType, userKey: userKey)

        cacheLock.lock()
        defer { cacheLock.unlock() }

        presenterCache.removeValue(forKey: key)
        mediaPresenterCache.removeValue(forKey: key)

        logger.debug("🧹 清除特定Profile缓存: \(key)")
    }

    func getCacheInfo() -> String {
        cacheLock.lock()
        defer { cacheLock.unlock() }

        return "ProfilePresenterService缓存状态 - Presenter: \(presenterCache.count), MediaPresenter: \(mediaPresenterCache.count)"
    }
}
