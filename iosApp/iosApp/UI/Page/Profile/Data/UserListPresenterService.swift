import Foundation
import shared
import SwiftUI

class UserListPresenterService {
    static let shared = UserListPresenterService()

    private let cacheLock = NSLock()
    
    private var fansPresenterCache: [String: FansPresenter] = [:]
    private var followingPresenterCache: [String: FollowingPresenter] = [:]
    private var profilePresenterCache: [String: ProfilePresenter] = [:]
    
    private init() {}
    
    private func getCacheKey(accountType: AccountType, userKey: MicroBlogKey) -> String {
        let accountKey = (accountType as? AccountTypeSpecific)?.accountKey.description ?? String(describing: accountType)
        let userKeyStr = userKey.description
        return "\(accountKey)_\(userKeyStr)"
    }
    
    func getOrCreateFansPresenter(accountType: AccountType, userKey: MicroBlogKey) -> FansPresenter {
        let key = getCacheKey(accountType: accountType, userKey: userKey)
        
        cacheLock.lock()
        defer { cacheLock.unlock() }
        
        if let cached = fansPresenterCache[key] {
            FlareLog.debug("🔄 [UserListPresenterService] 使用缓存 FansPresenter: \(key)")
            return cached
        }

        let presenter = FansPresenter(accountType: accountType, userKey: userKey)
        fansPresenterCache[key] = presenter
        FlareLog.debug("✨ [UserListPresenterService] 创建新 FansPresenter: \(key)")
        return presenter
    }
    
    func getOrCreateFollowingPresenter(accountType: AccountType, userKey: MicroBlogKey) -> FollowingPresenter {
        let key = getCacheKey(accountType: accountType, userKey: userKey)
        
        cacheLock.lock()
        defer { cacheLock.unlock() }
        
        if let cached = followingPresenterCache[key] {
            FlareLog.debug("🔄 [UserListPresenterService] 使用缓存 FollowingPresenter: \(key)")
            return cached
        }

        let presenter = FollowingPresenter(accountType: accountType, userKey: userKey)
        followingPresenterCache[key] = presenter
        FlareLog.debug("✨ [UserListPresenterService] 创建新 FollowingPresenter: \(key)")
        return presenter
    }
    
    func getOrCreateProfilePresenter(accountType: AccountType, userKey: MicroBlogKey) -> ProfilePresenter {
        let key = getCacheKey(accountType: accountType, userKey: userKey)
        
        cacheLock.lock()
        defer { cacheLock.unlock() }
        
        if let cached = profilePresenterCache[key] {
            FlareLog.debug("🔄 [UserListPresenterService] 使用缓存 ProfilePresenter: \(key)")
            return cached
        }

        let presenter = ProfilePresenter(accountType: accountType, userKey: userKey)
        profilePresenterCache[key] = presenter
        FlareLog.debug("✨ [UserListPresenterService] 创建新 ProfilePresenter: \(key)")
        return presenter
    }
    
    func clearCache() {
        cacheLock.lock()
        defer { cacheLock.unlock() }
        
        let fansCount = fansPresenterCache.count
        let followingCount = followingPresenterCache.count
        let profileCount = profilePresenterCache.count
        
        fansPresenterCache.removeAll()
        followingPresenterCache.removeAll()
        profilePresenterCache.removeAll()
        
        FlareLog.debug("🧹 [UserListPresenterService] 清除所有UserList缓存 - Fans: \(fansCount), Following: \(followingCount), Profile: \(profileCount)")
    }
    
    func clearCache(for accountType: AccountType, userKey: MicroBlogKey) {
        let key = getCacheKey(accountType: accountType, userKey: userKey)
        
        cacheLock.lock()
        defer { cacheLock.unlock() }
        
        fansPresenterCache.removeValue(forKey: key)
        followingPresenterCache.removeValue(forKey: key)
        profilePresenterCache.removeValue(forKey: key)
        
        FlareLog.debug("🧹 [UserListPresenterService] 清除特定UserList缓存: \(key)")
    }
    
    func getCacheInfo() -> String {
        cacheLock.lock()
        defer { cacheLock.unlock() }
        
        return "UserListPresenterService缓存状态 - Fans: \(fansPresenterCache.count), Following: \(followingPresenterCache.count), Profile: \(profilePresenterCache.count)"
    }
}
