import Foundation
import os
import shared
import SwiftUI

extension Notification.Name {
    static let accountChanged = Notification.Name("accountChanged")
    static let listPinStatusChanged = Notification.Name("listPinStatusChanged")
    static let listTitleDidUpdate = Notification.Name("listTitleDidUpdate")
}

class AppBarTabSettingStore: ObservableObject, TabStateProvider {
    
    static let shared = AppBarTabSettingStore(accountType: AccountTypeGuest())

    @Published var primaryHomeItems: [FLTabItem] = [] // 主要标签（不可更改状态）Appbar 第一个Home 标签
    @Published var secondaryItems: [FLTabItem] = [] // 所有次要标签
    @Published var availableAppBarTabsItems: [FLTabItem] = [] // UserDefaults 存储的已启用标签

    // 简化列表状态管理，只存储已pin的列表ID
    @Published var pinnedListIds: [String] = [] // 收藏的列表ID
    @Published var listTitles: [String: String] = [:] // 列表ID到标题的映射
    @Published var listIconUrls: [String: String] = [:] // 列表ID到头像URL的映射

    // 添加统一配置存储
    private var appBarItems: [AppBarItemConfig] = []

    // 添加同步锁，确保线程安全
    private let storageLock = NSLock()

    @Published var selectedAppBarTabKey: String = "" // 选中的 tab key
    @Published var currentPresenter: TimelinePresenter?
    @Published var currentUser: UiUserV2?

    private var presenter = ActiveAccountPresenter()
    private let settingsManager = FLTabSettingsManager()
    var accountType: AccountType // 改为变量，允许更新，并将访问级别更改为internal

    // 添加上次初始化的用户ID
    private var lastInitializedUserId: MicroBlogKey?

    // 引入服务对象
    private let configService = AppBarConfigService()
    private let notificationService = AppBarNotificationService()
    private let presenterService = AppBarPresenterService()

    // 添加列表管理器和Feed管理器
    private let listTabManager = ListTabManager()
    private let feedTabManager = FeedTabManager()

    // TabStateProvider 协议实现
    var onTabChange: ((Int) -> Void)?

    // 保留公开初始化方法，但标记为deprecated
    @available(*, deprecated, message: "请使用AppBarTabSettingStore.shared代替")
    init(accountType: AccountType) {
        self.accountType = accountType
        observeUser()
        observeListPinChanges()
        observeListTitleChanges()
    }

    // 添加初始化方法，供UserManager调用
    func initialize(with account: AccountType?, user: UiUserV2?) {
        // 检查用户ID是否变化
        let userId = user?.key

        // 如果用户ID相同，不重复初始化
        if userId == lastInitializedUserId, userId != nil {
            FlareLog.debug("用户ID未变化，跳过初始化: \(String(describing: userId))")
            return
        }

        // 记录新的用户ID
        lastInitializedUserId = userId

        FlareLog
            .debug(
                "初始化AppBarTabSettingStore: account=\(account != nil ? "有账号" : "无账号"), user=\(user != nil ? String(describing: user?.name) : "无用户")"
            )

        // 清理现有状态
        clearAllState()

        // 设置账号类型
        if let account {
            updateAccountType(account)
        } else {
            updateAccountType(AccountTypeGuest())
        }

        // 初始化用户相关设置
        if let user {
            initializeWithUser(user)
        }
    }

    var tabCount: Int {
        availableAppBarTabsItems.count
    }

    // 获取分段标题
    var segmentTitles: [String] {
        availableAppBarTabsItems.map { tab in
            switch tab.metaData.title {
            case let .text(title): title
            case let .localized(key): NSLocalizedString(key, comment: "")
            }
        }
    }

    // 获取选中索引
    var selectedIndex: Int {
        availableAppBarTabsItems.firstIndex { $0.key == selectedAppBarTabKey } ?? 0
    }

    // 获取或创建 Presenter
    func getOrCreatePresenter(for tab: FLTabItem) -> TimelinePresenter? {
        presenterService.getOrCreatePresenter(for: tab)
    }

    // 清除缓存
    func clearCache() {
        presenterService.clearCache()
    }

    // 更新选中标签
    func updateSelectedTab(_ tab: FLTabItem) {
        selectedAppBarTabKey = tab.key
        notifyTabChange()
    }

    // 清除所有状态
    func clearAllState() {
        FlareLog.debug("清除所有AppBar状态...")

        storageLock.lock()
        defer { storageLock.unlock() }

        presenterService.clearCache()
        currentPresenter = nil
        selectedAppBarTabKey = ""
        primaryHomeItems = []
        secondaryItems = []
        availableAppBarTabsItems = []
        pinnedListIds = []
        listTitles = [:]
        listIconUrls = [:]
        appBarItems = [] // 清空统一配置

        objectWillChange.send()
        FlareLog.debug("状态清除完成")
    }

    // 更新账户类型
    func updateAccountType(_ newAccountType: AccountType) {
        accountType = newAccountType

        // 如果是游客模式，设置默认的 Home Timeline
        if newAccountType is AccountTypeGuest {
            currentPresenter = HomeTimelinePresenter(accountType: newAccountType)

            // 只使用 Home 标签
            let homeTab = FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .material(.home)
                ), account: newAccountType
            )
            availableAppBarTabsItems = [homeTab]

            // 添加到统一配置
            let homeConfig = AppBarItemConfig(
                key: homeTab.key,
                type: .main,
                addedTime: Date(timeIntervalSince1970: 0),
                metadata: ["title": "home"]
            )
            appBarItems = [homeConfig]

            // 保存配置
            saveAppBarConfig()

            updateSelectedTab(homeTab)
        }
    }

    // 观察列表Pin状态变化
    private func observeListPinChanges() {
        notificationService.addListPinStatusChangedObserver(
            target: self,
            selector: #selector(handleListPinChange)
        )
    }

    @objc private func handleListPinChange(_ notification: Notification) {
        // 简化通知处理，只使用ID和标题，避免处理整个Kotlin对象
        if let listId = notification.userInfo?["listId"] as? String,
           let listTitle = notification.userInfo?["listTitle"] as? String,
           let isPinned = notification.userInfo?["isPinned"] as? Bool
        {
            // 检查是否为Feed类型
            let isBlueskyFeed = notification.userInfo?["itemType"] as? String == "feed"

            FlareLog.debug("收到\(isBlueskyFeed ? "Feed" : "List")Pin状态变更通知: ID=\(listId), 标题=\(listTitle), isPinned=\(isPinned)")

            // 记录列表标题，这对于任何情况都需要
            DispatchQueue.main.async {
                self.listTitles[listId] = listTitle

                if let iconUrl = notification.userInfo?["listIconUrl"] as? String {
                    self.listIconUrls[listId] = iconUrl
                    FlareLog.debug("设置图标URL: \(listId) -> \(iconUrl)")
                }

                // 根据类型生成不同前缀的tabId
                let tabId = isBlueskyFeed ? "feed_\(self.accountType)_\(listId)" : "list_\(self.accountType)_\(listId)"

                if isPinned {
                    // 如果是收藏操作，且标签不存在，则添加标签
                    if !self.availableAppBarTabsItems.contains(where: { $0.key == tabId }) {
                        // 直接调用toggleTab确保完整的标签添加流程被执行
                        self.toggleTab(tabId)
                        FlareLog.debug("通过toggleTab添加\(isBlueskyFeed ? "Feed" : "List")标签: \(tabId)")
                    } else {
                        // 标签已存在，仅更新pinnedListIds
                        if !self.pinnedListIds.contains(listId) {
                            self.pinnedListIds.append(listId)

                            // 更新并保存统一配置
                            self.updateConfigFromUiState()
                            self.saveAppBarConfig()

                            FlareLog.debug("标签已存在，仅更新pinnedListIds: \(listId)")
                        }
                    }
                } else {
                    // 如果是取消收藏操作，且标签存在，则移除标签
                    if self.availableAppBarTabsItems.contains(where: { $0.key == tabId }) {
                        // 直接调用toggleTab确保完整的标签移除流程被执行
                        self.toggleTab(tabId)
                        FlareLog.debug("通过toggleTab移除\(isBlueskyFeed ? "Feed" : "List")标签: \(tabId)")
                    } else {
                        // 标签已经不存在，仅更新pinnedListIds
                        self.pinnedListIds.removeAll { $0 == listId }

                        // 更新并保存统一配置
                        self.updateConfigFromUiState()
                        self.saveAppBarConfig()

                        FlareLog.debug("标签已不存在，仅更新pinnedListIds: \(listId)")
                    }
                }
            }
        }
    }

    deinit {
        notificationService.removeAllObservers(target: self)
    }

    private func observeUser() {
        // 先检查UserManager中是否有用户
        let result = UserManager.shared.getCurrentUser()

        if let user = result.0 {
            initializeWithUser(user)
            return
        }
    }

    private func initializeWithUser(_ user: UiUserV2) {
        FlareLog.debug("初始化用户: \(user.name)")

        currentUser = user

        // 准备基础标签
        primaryHomeItems = FLTabSettings.defaultPrimary(user: user)
        secondaryItems = FLTabSettings.defaultSecondary(user: user)

        // 加载统一配置
        loadAppBarConfig()

        // 如果没有配置，创建默认配置
        if appBarItems.isEmpty {
            appBarItems = configService.createDefaultAppBarConfig(for: user)
            saveAppBarConfig()
            FlareLog.debug("创建默认AppBar配置")
        }

        // 从配置更新UI状态
        updateUiStateFromConfig()
    }

    // 加载AppBar配置
    private func loadAppBarConfig() {
        guard let user = currentUser else { return }

        storageLock.lock()
        defer { storageLock.unlock() }

        // 使用配置服务加载配置
        appBarItems = configService.loadAppBarConfig(for: user)
    }

    // 保存AppBar配置
    private func saveAppBarConfig() {
        guard let user = currentUser else { return }

        storageLock.lock()
        defer { storageLock.unlock() }

        // 使用配置服务保存配置
        configService.saveAppBarConfig(appBarItems, for: user)
    }

    // 强制从存储重新加载以确保一致性
    private func reloadFromStorage() {
        guard let user = currentUser else { return }

        // 保存当前选中状态
        let selectedKey = selectedAppBarTabKey

        // 重新加载配置
        loadAppBarConfig()

        // 更新UI状态
        updateUiStateFromConfig()

        // 恢复选中状态
        if let index = availableAppBarTabsItems.firstIndex(where: { $0.key == selectedKey }) {
            selectedAppBarTabKey = selectedKey
        } else if !availableAppBarTabsItems.isEmpty {
            selectedAppBarTabKey = availableAppBarTabsItems[0].key
        }

        // 通知UI更新
        objectWillChange.send()
    }

    // 从配置更新UI状态
    private func updateUiStateFromConfig() {
        // 清空当前状态
        availableAppBarTabsItems = []
        pinnedListIds = []
        listTitles = [:]
        listIconUrls = [:]

        guard let user = currentUser else { return }

        // 使用配置服务转换配置为标签
        availableAppBarTabsItems = configService.convertConfigToTabs(
            appBarItems,
            for: user,
            accountType: accountType
        )

        // 提取固定列表和Feed信息
        for item in appBarItems {
            if item.type == .list || item.type == .feed {
                if let components = item.key.split(separator: "_").last {
                    let itemId = String(components)

                    // 记录信息
                    if let title = item.metadata["title"] {
                        listTitles[itemId] = title
                    }

                    if let iconUrl = item.metadata["iconUrl"] {
                        listIconUrls[itemId] = iconUrl
                        FlareLog.debug("加载图标URL: \(itemId) -> \(iconUrl)")
                    }

                    // 添加到固定ID列表
                    if !pinnedListIds.contains(itemId) {
                        pinnedListIds.append(itemId)
                    }
                }
            }
        }

        // 确保首页在第一位（安全检查）
        ensureHomePageFirst()

        // 初始化选中标签
        if selectedAppBarTabKey.isEmpty, !availableAppBarTabsItems.isEmpty {
            selectedAppBarTabKey = availableAppBarTabsItems[0].key
        }
    }

    // 确保首页在第一位，同时更新配置中的时间戳
    private func ensureHomePageFirst() {
        // 找到首页标签
        if let homeIndex = availableAppBarTabsItems.firstIndex(where: { tab in
            primaryHomeItems.contains(where: { $0.key == tab.key })
        }), homeIndex > 0 {
            // 移动UI项
            let homeItem = availableAppBarTabsItems.remove(at: homeIndex)
            availableAppBarTabsItems.insert(homeItem, at: 0)

            // 同时更新配置中的时间戳
            if let configIndex = appBarItems.firstIndex(where: { $0.key == homeItem.key }) {
                var updatedItem = appBarItems[configIndex]
                // 使用新结构替换旧结构
                let newItem = AppBarItemConfig(
                    key: updatedItem.key,
                    type: updatedItem.type,
                    addedTime: Date(timeIntervalSince1970: 0), // 极早时间
                    metadata: updatedItem.metadata
                )
                appBarItems[configIndex] = newItem

                // 记录但不立即保存，避免频繁IO
                FlareLog.debug("更新首页时间戳确保排序")
            }
        }
    }

    // 从UI状态更新统一配置
    private func updateConfigFromUiState() {
        guard let user = currentUser else { return }

        // 创建新配置
        var newItems: [AppBarItemConfig] = []

        // 遍历UI标签
        for (index, tab) in availableAppBarTabsItems.enumerated() {
            let type: AppBarItemType = if primaryHomeItems.contains(where: { $0.key == tab.key }) {
                .main
            } else if tab.key.starts(with: "list_") {
                .list
            } else if tab.key.starts(with: "feed_") {
                .feed
            } else {
                .secondary
            }

            // 尝试保留原有时间戳
            if let existingItem = appBarItems.first(where: { $0.key == tab.key }) {
                // 更新元数据但保留时间戳
                var newMetadata = existingItem.metadata

                // 更新标题
                switch tab.metaData.title {
                case let .text(text):
                    newMetadata["title"] = text
                case let .localized(key):
                    newMetadata["title"] = NSLocalizedString(key, comment: "")
                }

                // 如果是列表或Feed，确保图标URL也被保留
                if type == .list || type == .feed {
                    if let components = tab.key.split(separator: "_").last {
                        let id = String(components)
                        if let iconUrl = listIconUrls[id], !iconUrl.isEmpty {
                            newMetadata["iconUrl"] = iconUrl
                            FlareLog.debug("保留现有图标URL: \(id) -> \(iconUrl)")
                        }
                    }
                }

                // 创建新结构
                let updatedItem = AppBarItemConfig(
                    key: existingItem.key,
                    type: existingItem.type,
                    addedTime: existingItem.addedTime,
                    metadata: newMetadata
                )
                newItems.append(updatedItem)
            } else {
                // 添加新项目，使用适当的时间戳
                let newTime = if type == .main {
                    Date(timeIntervalSince1970: 0) // 首页使用最早时间
                } else {
                    // 使用当前时间加索引间隔
                    Date().addingTimeInterval(Double(index) * 60)
                }

                // 创建元数据，包括标题和图标URL
                var metadata: [String: String] = [:]

                // 添加标题
                switch tab.metaData.title {
                case let .text(text):
                    metadata["title"] = text
                case let .localized(key):
                    metadata["title"] = NSLocalizedString(key, comment: "")
                }

                // 如果是列表或Feed，添加图标URL
                if type == .list || type == .feed {
                    if let components = tab.key.split(separator: "_").last {
                        let id = String(components)
                        if let iconUrl = listIconUrls[id], !iconUrl.isEmpty {
                            metadata["iconUrl"] = iconUrl
                            FlareLog.debug("添加新图标URL: \(id) -> \(iconUrl)")
                        }
                    }
                }

                // 使用配置服务创建配置
                let newItem = configService.convertTabToConfig(tab, type: type, addedTime: newTime)
                newItems.append(newItem)
            }
        }

        // 更新配置
        appBarItems = newItems
    }

    // 保存已pin的列表到统一配置
    func savePinnedLists() {
        // 更新配置并保存
        updateConfigFromUiState()
        saveAppBarConfig()
    }

    // 保存标签结构
    func saveTabs() {
        // 更新配置并保存
        updateConfigFromUiState()
        saveAppBarConfig()
    }

    // 移动标签逻辑优化
    func moveTab(from source: IndexSet, to destination: Int) {
        guard let sourceIndex = source.first, sourceIndex < availableAppBarTabsItems.count else {
            return
        }

        // 记录源项和选中状态
        let movedTab = availableAppBarTabsItems[sourceIndex]
        let selectedKey = selectedAppBarTabKey

        // 执行UI移动
        availableAppBarTabsItems.move(fromOffsets: source, toOffset: destination)

        // 重新计算所有项目的时间，确保顺序
        for (index, tab) in availableAppBarTabsItems.enumerated() {
            if let configIndex = appBarItems.firstIndex(where: { $0.key == tab.key }) {
                var newTime

                    // 首页特殊处理
                    = if primaryHomeItems.contains(where: { $0.key == tab.key })
                {
                    Date(timeIntervalSince1970: 0) // 首页永远在最前
                } else {
                    // 其他项目用递增时间，使用大间隔确保顺序清晰
                    Date().addingTimeInterval(Double(index) * 3600) // 间隔1小时
                }

                // 使用新结构替换，而不是修改现有结构
                let updatedItem = AppBarItemConfig(
                    key: appBarItems[configIndex].key,
                    type: appBarItems[configIndex].type,
                    addedTime: newTime,
                    metadata: appBarItems[configIndex].metadata
                )
                appBarItems[configIndex] = updatedItem
            }
        }

        // 保存配置
        saveAppBarConfig()

        // 发送变更通知
        objectWillChange.send()
        notificationService.postTabsDidUpdateNotification()

        // 恢复选中状态
        selectedAppBarTabKey = selectedKey
        FlareLog.debug("移动标签完成：\(movedTab.key) 移动到位置 \(destination)")
    }

    // 专门用于移动列表标签的方法
    func moveListTab(from source: IndexSet, to destination: Int) {
        listTabManager.moveListTab(
            from: source,
            to: destination,
            availableAppBarTabsItems: availableAppBarTabsItems,
            moveTabHandler: moveTab
        )
    }

    // 专门用于移动Feed标签的方法
    func moveFeedTab(from source: IndexSet, to destination: Int) {
        feedTabManager.moveFeedTab(
            from: source,
            to: destination,
            availableAppBarTabsItems: availableAppBarTabsItems,
            moveTabHandler: moveTab
        )
    }

    // 观察列表标题变化
    private func observeListTitleChanges() {
        notificationService.addListTitleDidUpdateObserver(
            target: self,
            selector: #selector(handleListTitleChange)
        )
    }

    @objc private func handleListTitleChange(_ notification: Notification) {
        if let listId = notification.userInfo?["listId"] as? String,
           let newTitle = notification.userInfo?["newTitle"] as? String
        {
            // 检查是否为Feed类型
            let isBlueskyFeed = notification.userInfo?["itemType"] as? String == "feed"

            FlareLog.debug("收到\(isBlueskyFeed ? "Feed" : "列表")标题更新通知: ID=\(listId), 新标题=\(newTitle)")

            DispatchQueue.main.async {
                // 更新标题映射
                self.listTitles[listId] = newTitle

                // 查找并更新相应的标签标题
                let tabKey = isBlueskyFeed ?
                    "feed_\(self.accountType)_\(listId)" :
                    "list_\(self.accountType)_\(listId)"

                // 更新UI标签
                for (index, tab) in self.availableAppBarTabsItems.enumerated() {
                    if tab.key == tabKey {
                        // 创建新的标签项替换旧的
                        let updatedTab: FLTabItem = if isBlueskyFeed {
                            self.feedTabManager.createFeedTab(
                                feedId: listId,
                                title: newTitle,
                                accountType: self.accountType,
                                iconUrl: self.listIconUrls[listId]
                            )
                        } else {
                            self.listTabManager.createListTab(
                                listId: listId,
                                title: newTitle,
                                accountType: self.accountType,
                                iconUrl: self.listIconUrls[listId]
                            )
                        }

                        // 替换标签
                        self.availableAppBarTabsItems[index] = updatedTab

                        // 更新统一配置中的项目（只更新标题，不改变时间）
                        if let itemIndex = self.appBarItems.firstIndex(where: { $0.key == tabKey }) {
                            var updatedItem = self.appBarItems[itemIndex]
                            updatedItem.metadata["title"] = newTitle
                            self.appBarItems[itemIndex] = updatedItem
                        }

                        // 保存配置
                        self.saveAppBarConfig()

                        // 如果当前选中的是被更新的标签，刷新选中状态
                        if self.selectedAppBarTabKey == tabKey {
                            self.updateSelectedTab(updatedTab)
                        }

                        // 发送通知告知AppBar标签标题已更新
                        self.notificationService.postTabsDidUpdateNotification(
                            updatedTabKey: tabKey,
                            newTitle: newTitle
                        )

                        // 通知UI更新
                        self.objectWillChange.send()

                        break
                    }
                }
            }
        }
    }

    // 通知标签变更
    func notifyTabChange() {
        onTabChange?(selectedIndex)
    }

    // 切换标签显示状态
    func toggleTab(_ id: String) {
        guard let user = currentUser else { return }

        FlareLog.debug("切换标签状态: \(id)")

        let wasSelected = selectedAppBarTabKey == id
        let isBlueskyFeed = id.starts(with: "feed_")

        if availableAppBarTabsItems.contains(where: { $0.key == id }) {
            // 关闭标签：从 availableAppBarTabsItems 中移除
            availableAppBarTabsItems.removeAll { $0.key == id }

            // 同时从配置中移除
            appBarItems.removeAll { $0.key == id }

            // 如果关闭的是当前选中的标签，切换到第一个标签（首页）
            if wasSelected {
                if let firstTab = primaryHomeItems.first {
                    updateSelectedTab(firstTab)
                }
            }

            // 如果是列表标签或Feed标签，同步更新列表状态
            if id.starts(with: "list_") || id.starts(with: "feed_") {
                let components = id.split(separator: "_")
                if components.count >= 3 {
                    let listId = String(components.last!)

                    // 安全地更新本地状态
                    DispatchQueue.main.async { [self] in
                        // 从已pin列表中移除
                        pinnedListIds.removeAll { $0 == listId }

                        // 保存更新
                        updateConfigFromUiState()
                        saveAppBarConfig()

                        // 发送通知，让其他地方知道列表pin状态变化了
                        if let title = listTitles[listId] {
                            notificationService.postListPinStatusChangedNotification(
                                listId: listId,
                                listTitle: title,
                                isPinned: false,
                                isBlueskyFeed: isBlueskyFeed
                            )
                        }
                    }
                }
            } else {
                // second 保存更新
                updateConfigFromUiState()
                saveAppBarConfig()
            }
        } else {
            // 开启标签：添加到 availableAppBarTabsItems
            // 检查是否已存在相同的标签
            if !availableAppBarTabsItems.contains(where: { $0.key == id }) {
                if let item = secondaryItems.first(where: { $0.key == id }) {
                    availableAppBarTabsItems.append(item)

                    // 添加到配置
                    let newConfig = configService.convertTabToConfig(
                        item,
                        type: .secondary,
                        addedTime: Date()
                    )
                    appBarItems.append(newConfig)

                    // 保存更新
                    saveAppBarConfig()
                } else if id.starts(with: "list_") {
                    // 如果是列表标签
                    let components = id.split(separator: "_")
                    if components.count >= 3 {
                        let listId = String(components.last!)
                        if let title = listTitles[listId] {
                            // 创建列表标签
                            let listTab = listTabManager.createListTab(
                                listId: listId,
                                title: title,
                                accountType: accountType,
                                iconUrl: listIconUrls[listId]
                            )

                            // 安全地在主线程更新UI
                            DispatchQueue.main.async { [self] in
                                // 添加到UI
                                availableAppBarTabsItems.append(listTab)

                                // 创建列表配置
                                let newConfig = listTabManager.createListConfig(
                                    listTab: listTab,
                                    title: title,
                                    iconUrl: listIconUrls[listId]
                                )
                                appBarItems.append(newConfig)

                                // 添加到已pin列表
                                if !pinnedListIds.contains(listId) {
                                    pinnedListIds.append(listId)
                                }

                                // 保存更新
                                saveAppBarConfig()

                                // 发送通知，让其他地方知道列表pin状态变化了
                                notificationService.postListPinStatusChangedNotification(
                                    listId: listId,
                                    listTitle: title,
                                    isPinned: true,
                                    isBlueskyFeed: false,
                                    iconUrl: listIconUrls[listId]
                                )
                            }
                        }
                    }
                } else if id.starts(with: "feed_") {
                    // 如果是Feed标签
                    let components = id.split(separator: "_")
                    if components.count >= 3 {
                        let feedId = String(components.last!)
                        if let title = listTitles[feedId] {
                            // 创建Feed标签
                            let feedTab = feedTabManager.createFeedTab(
                                feedId: feedId,
                                title: title,
                                accountType: accountType,
                                iconUrl: listIconUrls[feedId]
                            )

                            // 安全地在主线程更新UI
                            DispatchQueue.main.async { [self] in
                                // 添加到UI
                                availableAppBarTabsItems.append(feedTab)

                                // 创建Feed配置
                                let newConfig = feedTabManager.createFeedConfig(
                                    feedTab: feedTab,
                                    title: title,
                                    iconUrl: listIconUrls[feedId]
                                )
                                appBarItems.append(newConfig)

                                // 添加到已pin列表
                                if !pinnedListIds.contains(feedId) {
                                    pinnedListIds.append(feedId)
                                }

                                // 保存更新
                                saveAppBarConfig()

                                // 发送通知，让其他地方知道Feed pin状态变化了
                                notificationService.postListPinStatusChangedNotification(
                                    listId: feedId,
                                    listTitle: title,
                                    isPinned: true,
                                    isBlueskyFeed: true,
                                    iconUrl: listIconUrls[feedId]
                                )
                            }
                        }
                    }
                }
            }
        }

        // 通知UI更新
        objectWillChange.send()

        // 添加发送TabsDidUpdate通知，确保HomeTabController更新UI
        notificationService.postTabsDidUpdateNotification()
        FlareLog.debug("发送TabsDidUpdate通知，标签状态已更改: \(id)")
    }
}
