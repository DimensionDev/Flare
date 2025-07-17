import Foundation
import os.log
import shared
import SwiftUI

class ProfileTabSettingStore: ObservableObject, TabStateProvider {
 
    @Published var availableTabs: [FLTabItem] = [] // 当前显示的所有标签
    @Published var selectedTabKey: String? // 当前选中的标签
    @Published var currentUser: UiUserV2?
    @Published var currentPresenter: TimelinePresenter?
    @Published var currentMediaPresenter: ProfileMediaPresenter?
 
    private var isInitializing = false
 
    private var presenterCache: [String: TimelinePresenter] = [:] 
    private var mediaPresenterCache: [String: ProfileMediaPresenter] = [:] // 媒体presenter缓存

  
    var onTabChange: ((Int) -> Void)?

    var tabCount: Int {
        availableTabs.count
    }

    var selectedIndex: Int {
        guard let selectedTabKey else { return 0 }
        return availableTabs.firstIndex { $0.key == selectedTabKey } ?? 0
    }

  
    init(userKey: MicroBlogKey?) {   
        observeUser(userKey: userKey)
    }

    private func observeUser(userKey: MicroBlogKey?) {
        // 先检查UserManager中是否有用户
        let result = UserManager.shared.getCurrentUser()

        if let user = result.0 {
            initializeWithUser(user, userKey: userKey)
            return
        } else if let userKey {
            // 如果是未登录状态但查看他人资料，创建临时游客用户
            os_log("[📔][ProfileTabSettingStore]未登录状态查看用户：userKey=%{public}@", log: .default, type: .debug, userKey.description)
            initializeWithUser(createSampleUser(), userKey: userKey)
            return
        } 
    }

    @objc private func handleUserUpdate(_ notification: Notification) {
        if let user = notification.object as? UiUserV2,
           let userKey = user.key as? MicroBlogKey
        {
            initializeWithUser(user, userKey: userKey)
        }
    }

    // - Public Methods
    func initializeWithUser(_ user: UiUserV2, userKey: MicroBlogKey?) {
        if isInitializing || currentUser?.key == user.key {
            return
        }

        isInitializing = true
        currentUser = user

        // 更新可用标签
        updateTabs(user: user, userKey: userKey)

        // 如果没有选中的标签，选中第一个
        if let firstItem = availableTabs.first {
            selectTab(firstItem.key)
        }

        isInitializing = false
    }

    func selectTab(_ key: String) {
        selectedTabKey = key
        if let selectedItem = availableTabs.first(where: { $0.key == key }) {
            updateCurrentPresenter(for: selectedItem)
        }
        notifyTabChange()
    }

    func updateCurrentPresenter(for tab: FLTabItem) {
        selectedTabKey = tab.key
        if tab is FLProfileMediaTabItem {
            if let mediaTab = tab as? FLProfileMediaTabItem {
                // 使用 userKey 作为缓存键
                let cacheKey = "\(mediaTab.userKey?.description ?? "self")"
                if let cachedPresenter = mediaPresenterCache[cacheKey] {
                    currentMediaPresenter = cachedPresenter
                } else {
                    let newPresenter = ProfileMediaPresenter(accountType: mediaTab.account, userKey: mediaTab.userKey)
                    mediaPresenterCache[cacheKey] = newPresenter
                    currentMediaPresenter = newPresenter
                }
            }
        } else if let presenter = getOrCreatePresenter(for: tab) {
            // 直接设置 presenter，不使用 withAnimation
            currentPresenter = presenter

            // 确保 presenter 已经设置完成
            DispatchQueue.main.async {
                os_log("[📔][ProfileTabSettingStore]更新当前 presenter: tab=%{public}@, presenter=%{public}@", log: .default, type: .debug, tab.key, String(describing: self.currentPresenter))
            }
        }
    }

    func getOrCreatePresenter(for tab: FLTabItem) -> TimelinePresenter? {
        if let timelineItem = tab as? FLTimelineTabItem {
            let key = tab.key
            if let cachedPresenter = presenterCache[key] {
                return cachedPresenter
            } else {
                let presenter = timelineItem.createPresenter()
                presenterCache[key] = presenter
                return presenter
            }
        }
        return nil
    }

    func clearCache() {
        presenterCache.removeAll()
        mediaPresenterCache.removeAll()
        currentMediaPresenter = nil
    }

    // - Private Methods
    private func updateTabs(user: UiUserV2, userKey: MicroBlogKey?) {
        // 检查是否是未登录模式
        let isGuestMode = user.key is AccountTypeGuest || UserManager.shared.getCurrentUser().0 == nil

        // 创建media标签
        let mediaTab = FLProfileMediaTabItem(
            metaData: FLTabMetaData(
                title: .localized(.profileMedia),
                icon: .mixed(.media, userKey: user.key)
            ),
            account: AccountTypeSpecific(accountKey: user.key),
            userKey: userKey
        )

        // 如果是未登录用户查看别人的资料，只显示media标签
        if isGuestMode, userKey != nil {
            availableTabs = [mediaTab]
        } else {
            // 已登录用户显示所有标签
            var tabs = FLTabSettings.defaultThree(user: user, userKey: userKey)

            // 插入到倒数第二的位置
            if tabs.isEmpty {
                tabs.append(mediaTab)
            } else {
                tabs.insert(mediaTab, at: max(0, tabs.count - 1))
            }

            availableTabs = tabs
        }

        // 如果没有选中的标签，选中第一个
        if selectedTabKey == nil, let firstTab = availableTabs.first {
            selectTab(firstTab.key)
        }
    }

    func notifyTabChange() {
        onTabChange?(selectedIndex)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
