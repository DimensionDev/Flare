import Foundation
import os.log
import shared
import SwiftUI

class ProfileTabSettingStore: ObservableObject, TabStateProvider {
    // - Published Properties
    @Published var availableTabs: [FLTabItem] = [] // 当前显示的所有标签
    @Published var selectedTabKey: String? // 当前选中的标签
    @Published var currentUser: UiUserV2?
    @Published var currentPresenter: TimelinePresenter?
    @Published var currentMediaPresenter: ProfileMediaPresenter?

    // - Private Properties
//    private var timelineStore: TimelineStore
    private var isInitializing = false
//    private var presenter = ActiveAccountPresenter()
    private var presenterCache: [String: TimelinePresenter] = [:] // 添加缓存
    private var mediaPresenterCache: [String: ProfileMediaPresenter] = [:] // 媒体presenter缓存

    // TabStateProvider 协议实现
    var onTabChange: ((Int) -> Void)?

    var tabCount: Int {
        availableTabs.count
    }

    var selectedIndex: Int {
        guard let selectedTabKey else { return 0 }
        return availableTabs.firstIndex { $0.key == selectedTabKey } ?? 0
    }

    // - Initialization
    init(userKey: MicroBlogKey?) { // timelineStore: TimelineStore,
//        self.timelineStore = timelineStore
        observeUser(userKey: userKey)
    }

    private func observeUser(userKey: MicroBlogKey?) {
        // 先检查UserManager中是否有用户
        if let user = UserManager.shared.getCurrentUser() {
            initializeWithUser(user, userKey: userKey)
            return
        }

        // 如果没有，则等待用户更新通知
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleUserUpdate),
            name: .userDidUpdate,
            object: nil
        )
    }

    @objc private func handleUserUpdate(_ notification: Notification) {
        if let user = notification.object as? UiUserV2,
           let userKey = user.key as? MicroBlogKey {
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
        // 根据平台类型获取对应的标签
        var tabs = FLTabSettings.defaultThree(user: user, userKey: userKey)

        // 添加 media tab 到倒数第二的位置
        let mediaTab = FLProfileMediaTabItem(
            metaData: FLTabMetaData(
                title: .localized(.profileMedia),
                icon: .mixed(.media, userKey: user.key)
            ),
            account: AccountTypeSpecific(accountKey: user.key),
            userKey: userKey
        )

        // 插入到倒数第二的位置
        if tabs.isEmpty {
            tabs.append(mediaTab)
        } else {
            tabs.insert(mediaTab, at: max(0, tabs.count - 1))
        }

        availableTabs = tabs

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
