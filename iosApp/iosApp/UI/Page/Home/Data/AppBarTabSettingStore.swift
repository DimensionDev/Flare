import Foundation
import os
import shared
import SwiftUI

extension Notification.Name {
    static let accountChanged = Notification.Name("accountChanged")
    static let listPinStatusChanged = Notification.Name("listPinStatusChanged")
    static let listTitleDidUpdate = Notification.Name("listTitleDidUpdate")
}

// 日志记录器
private let logger = Logger(subsystem: "com.flare.app", category: "AppBarTabSettingStore")

class AppBarTabSettingStore: ObservableObject, TabStateProvider {
    @Published var primaryHomeItems: [FLTabItem] = [] // 主要标签（不可更改状态）Appbar 第一个Home 标签
    @Published var secondaryItems: [FLTabItem] = [] // 所有次要标签
    @Published var availableAppBarTabsItems: [FLTabItem] = [] // UserDefaults 存储的已启用标签

    // 简化列表状态管理，只存储已pin的列表ID
    @Published var pinnedListIds: [String] = [] // 收藏的列表ID
    @Published var listTitles: [String: String] = [:] // 列表ID到标题的映射
    @Published var listIconUrls: [String: String] = [:] // 列表ID到头像URL的映射

    // 标记是否已经初始化过列表数据
    private var hasInitializedLists = false

    @Published var selectedAppBarTabKey: String = "" // 选中的 tab key
    @Published var currentPresenter: TimelinePresenter?
    @Published var currentUser: UiUserV2?

    private var presenter = ActiveAccountPresenter()
    private var isInitializing = false
    private let settingsManager = FLTabSettingsManager()
    var accountType: AccountType // 改为变量，允许更新，并将访问级别更改为internal

    // 缓存 presenter 避免重复创建
    private var presenterCache: [String: TimelinePresenter] = [:]

    // TabStateProvider 协议实现
    var onTabChange: ((Int) -> Void)?

    init(accountType: AccountType) {
        self.accountType = accountType
        observeAccountChanges()
        observeUser()
        observeListPinChanges()
        observeListTitleChanges()
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
        guard let timelineTab = tab as? FLTimelineTabItem else { return nil }

        if let cached = presenterCache[tab.key] {
            return cached
        }
        let presenter = timelineTab.createPresenter()
        presenterCache[tab.key] = presenter
        return presenter
    }

    // 清除缓存
    func clearCache() {
        presenterCache.removeAll()
//        currentPresenter = nil
        // 保留当前 presenter，清理其他缓存
        // 这个地方估计有问题 todo://
        //        let current = currentPresenter
        //
        //         if let key = currentKey {
        //            presenterCache[key] = current
        //        }
    }

    // 更新选中标签
    func updateSelectedTab(_ tab: FLTabItem) {
        selectedAppBarTabKey = tab.key
        notifyTabChange()
    }

    // 清除所有状态
    func clearAllState() {
        presenterCache.removeAll()
        currentPresenter = nil
        selectedAppBarTabKey = ""
        primaryHomeItems = []
        secondaryItems = []
        availableAppBarTabsItems = []
        pinnedListIds = []
        listTitles = [:]
        listIconUrls = [:]
        objectWillChange.send()
    }

    // 更新账户类型
    func updateAccountType(_ newAccountType: AccountType) {
        accountType = newAccountType
        clearAllState()

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
            updateSelectedTab(homeTab)
        }
    }

    // 监听账号变化
    func observeAccountChanges() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAccountChange),
            name: NSNotification.Name("AccountChanged"),
            object: nil
        )
    }

    @objc private func handleAccountChange() {
        // 获取新的账户类型
        if let newAccountType = UserManager.shared.getCurrentAccount() {
            updateAccountType(newAccountType)
        }

        // 获取新的用户信息
        if let user = UserManager.shared.getCurrentUser() {
            initializeWithUser(user)
        }
    }

    // 观察列表Pin状态变化
    private func observeListPinChanges() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleListPinChange),
            name: .listPinStatusChanged,
            object: nil
        )
    }

    @objc private func handleListPinChange(_ notification: Notification) {
        // 简化通知处理，只使用ID和标题，避免处理整个Kotlin对象
        if let listId = notification.userInfo?["listId"] as? String,
           let listTitle = notification.userInfo?["listTitle"] as? String,
           let isPinned = notification.userInfo?["isPinned"] as? Bool
        {
            logger.debug("收到列表Pin状态变更通知: 列表\(listId), 标题\(listTitle), isPinned=\(isPinned)")

            // 记录列表标题，这对于任何情况都需要
            DispatchQueue.main.async {
                self.listTitles[listId] = listTitle

                if let iconUrl = notification.userInfo?["iconUrl"] as? String {
                    self.listIconUrls[listId] = iconUrl
                }

                let tabId = "list_\(self.accountType)_\(listId)"

                if isPinned {
                    // 如果是收藏操作，且标签不存在，则添加标签
                    if !self.availableAppBarTabsItems.contains(where: { $0.key == tabId }) {
                        // 直接调用toggleTab确保完整的标签添加流程被执行
                        self.toggleTab(tabId)
                        logger.debug("通过toggleTab添加列表标签: \(tabId)")
                    } else {
                        // 标签已存在，仅更新pinnedListIds
                        if !self.pinnedListIds.contains(listId) {
                            self.pinnedListIds.append(listId)
                            self.savePinnedLists()
                            logger.debug("标签已存在，仅更新pinnedListIds: \(listId)")
                        }
                    }
                } else {
                    // 如果是取消收藏操作，且标签存在，则移除标签
                    if self.availableAppBarTabsItems.contains(where: { $0.key == tabId }) {
                        // 直接调用toggleTab确保完整的标签移除流程被执行
                        self.toggleTab(tabId)
                        logger.debug("通过toggleTab移除列表标签: \(tabId)")
                    } else {
                        // 标签已经不存在，仅更新pinnedListIds
                        self.pinnedListIds.removeAll { $0 == listId }
                        self.savePinnedLists()
                        logger.debug("标签已不存在，仅更新pinnedListIds: \(listId)")
                    }
                }
            }
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    private func observeUser() {
        // 先检查UserManager中是否有用户
        if let user = UserManager.shared.getCurrentUser() {
            initializeWithUser(user)
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
        if let user = notification.object as? UiUserV2 {
            initializeWithUser(user)
        }
    }

    private func initializeWithUser(_ user: UiUserV2) {
        if isInitializing || currentUser?.key == user.key {
            return
        }

        logger.debug("初始化用户: \(user.name)")

        isInitializing = true
        currentUser = user

        primaryHomeItems = FLTabSettings.defaultPrimary(user: user)
        secondaryItems = FLTabSettings.defaultSecondary(user: user)

        // 从 UserDefaults 加载存储的标签
        availableAppBarTabsItems = settingsManager.getEnabledItems(for: user) ?? secondaryItems

        if availableAppBarTabsItems.isEmpty {
            if let homeItem = primaryHomeItems.first {
                availableAppBarTabsItems = [homeItem] + secondaryItems
            }
        } else {
            // 确保首页标签存在并且是唯一的
            let hasHomeTab = availableAppBarTabsItems.contains { $0.key.contains("home_") }

            if let homeItem = primaryHomeItems.first {
                if !hasHomeTab {
                    // 如果没有首页标签，添加它
                    availableAppBarTabsItems = [homeItem] + availableAppBarTabsItems
                } else {
                    // 如果已有首页标签，确保没有重复
                    var uniqueTabs: [FLTabItem] = []
                    var seenKeys = Set<String>()

                    // 优先添加首页标签
                    uniqueTabs.append(homeItem)
                    seenKeys.insert(homeItem.key)

                    // 添加其他非重复标签
                    for tab in availableAppBarTabsItems {
                        if !seenKeys.contains(tab.key), tab.key != homeItem.key {
                            uniqueTabs.append(tab)
                            seenKeys.insert(tab.key)
                        }
                    }

                    availableAppBarTabsItems = uniqueTabs
                    logger.debug("初始化时移除了重复标签，最终标签数: \(uniqueTabs.count)")
                }
            }
        }

        // 选择第一个标签
        if selectedAppBarTabKey.isEmpty {
            if let firstItem = availableAppBarTabsItems.first {
                updateSelectedTab(firstItem)
            }
        }

        // 加载已pin的列表
        loadPinnedLists()

        isInitializing = false
    }

    // 从UserDefaults加载已pin的列表
    private func loadPinnedLists() {
        guard let user = currentUser else { return }

        logger.debug("从UserDefaults加载已pin的列表")

        // 1. 从UserDefaults加载已pin的列表ID
        let defaults = UserDefaults.standard
        let key = "pinnedLists_\(user.key)"

        if let savedData = defaults.data(forKey: key),
           let savedLists = try? JSONDecoder().decode([PinnedListInfo].self, from: savedData)
        {
            // 更新本地状态
            pinnedListIds = savedLists.map(\.id)

            // 更新标题映射和头像URL映射
            for list in savedLists {
                listTitles[list.id] = list.title
                if let listIconUrl = list.listIconUrl {
                    listIconUrls[list.id] = listIconUrl
                }
                // 确保对应的标签已添加
                addListTabIfNeeded(listId: list.id, title: list.title)
            }

            logger.debug("已加载\(savedLists.count)个已pin的列表")
        }
    }

    // 保存已pin的列表到UserDefaults
    func savePinnedLists() {
        guard let user = currentUser else { return }

        let listsToSave = pinnedListIds.compactMap { id -> PinnedListInfo? in
            if let title = listTitles[id] {
                return PinnedListInfo(id: id, title: title, listIconUrl: listIconUrls[id])
            }
            return nil
        }

        let defaults = UserDefaults.standard
        let key = "pinnedLists_\(user.key)"

        if let encodedData = try? JSONEncoder().encode(listsToSave) {
            defaults.set(encodedData, forKey: key)
        }
    }

    // 保存标签结构
    func saveTabs() {
        guard let user = currentUser else { return }

        // 添加去重逻辑，确保没有重复的标签，特别是首页标签
        var uniqueTabs: [FLTabItem] = []
        var seenKeys = Set<String>()

        // 优先添加首页标签（如果存在）
        if let homeItem = primaryHomeItems.first {
            uniqueTabs.append(homeItem)
            seenKeys.insert(homeItem.key)
        }

        // 添加其余标签，避免重复
        for tab in availableAppBarTabsItems {
            if !seenKeys.contains(tab.key) {
                uniqueTabs.append(tab)
                seenKeys.insert(tab.key)
            } else {
                logger.debug("发现重复标签，已跳过: \(tab.key)")
            }
        }

        // 更新标签列表并保存
        availableAppBarTabsItems = uniqueTabs
        settingsManager.saveEnabledItems(uniqueTabs, for: user)
        logger.debug("保存标签: 总数 \(uniqueTabs.count)")
    }

    // 简单的结构体用于存储pin的列表信息
    private struct PinnedListInfo: Codable {
        let id: String
        let title: String
        let listIconUrl: String?
    }

    // 修改toggleTab方法处理列表
    func toggleTab(_ id: String) {
        guard let user = currentUser else { return }

        logger.debug("切换标签状态: \(id)")

        let wasSelected = selectedAppBarTabKey == id

        if availableAppBarTabsItems.contains(where: { $0.key == id }) {
            // 关闭标签：从 storeItems 中移除
            availableAppBarTabsItems.removeAll { $0.key == id }

            // 如果关闭的是当前选中的标签，切换到第一个标签（首页）
            if wasSelected {
                if let firstTab = primaryHomeItems.first {
                    updateSelectedTab(firstTab)
                    logger.debug("切换到首页标签: \(firstTab.key)")
                }
            }

            // 如果是列表标签，同步更新列表状态
            if id.starts(with: "list_") {
                let components = id.split(separator: "_")
                if components.count >= 3 {
                    let listId = String(components.last!)
                    logger.debug("从标签移除列表: \(listId)")

                    // 安全地更新本地状态
                    DispatchQueue.main.async {
                        // 从已pin列表中移除
                        self.pinnedListIds.removeAll { $0 == listId }

                        // 保存更新后的pin状态
                        self.savePinnedLists()

                        // 发送通知，让其他地方知道列表pin状态变化了
                        if let title = self.listTitles[listId] {
                            NotificationCenter.default.post(
                                name: .listPinStatusChanged,
                                object: nil,
                                userInfo: [
                                    "listId": listId,
                                    "listTitle": title,
                                    "isPinned": false,
                                ]
                            )
                        }
                    }
                }
            }
        } else {
            // 开启标签：添加到 storeItems，但要确保不会添加重复标签
            // 检查是否已存在相同的标签
            if !availableAppBarTabsItems.contains(where: { $0.key == id }) {
                if let item = secondaryItems.first(where: { $0.key == id }) {
                    availableAppBarTabsItems.append(item)
                    logger.debug("添加次要标签: \(id)")
                } else if id.starts(with: "list_") {
                    // 如果是列表标签
                    let components = id.split(separator: "_")
                    if components.count >= 3 {
                        let listId = String(components.last!)
                        if let title = listTitles[listId] {
                            logger.debug("添加列表标签: \(title) (ID: \(listId))")

                            // 安全地在主线程更新UI
                            DispatchQueue.main.async {
                                self.addListTab(listId: listId, title: title)

                                // 添加到已pin列表
                                if !self.pinnedListIds.contains(listId) {
                                    self.pinnedListIds.append(listId)
                                }

                                // 保存更新后的pin状态
                                self.savePinnedLists()

                                // 发送通知，让其他地方知道列表pin状态变化了
                                NotificationCenter.default.post(
                                    name: .listPinStatusChanged,
                                    object: nil,
                                    userInfo: [
                                        "listId": listId,
                                        "listTitle": title,
                                        "isPinned": true,
                                    ]
                                )
                            }
                        }
                    }
                }
            } else {
                logger.debug("标签已存在，跳过添加: \(id)")
            }
        }

        // 保存并更新可用标签
        saveTabs()

        // 确保 UI 更新
        objectWillChange.send()

        // 发送通知
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: NSNotification.Name("TabsDidUpdate"), object: nil)
        }
    }

    // 只在标签不存在时添加列表标签
    private func addListTabIfNeeded(listId: String, title: String) {
        let tabKey = "list_\(accountType)_\(listId)"
        if !availableAppBarTabsItems.contains(where: { $0.key == tabKey }) {
            logger.debug("添加列表标签: \(title) (ID: \(listId))")
            addListTab(listId: listId, title: title)

            // 保存标签并触发UI更新
            saveTabs()
            objectWillChange.send()
        }
    }

    // 添加列表标签
    private func addListTab(listId: String, title: String) {
        let listTab = FLListTimelineTabItem(
            metaData: FLTabMetaData(
                title: .text(title),
                icon: .material(.list)
            ),
            account: accountType,
            listKey: listId
        )

        availableAppBarTabsItems.append(listTab)
    }

    // 移除列表标签
    private func removeListTab(listId: String) {
        let tabKey = "list_\(accountType)_\(listId)"
        let hadTab = availableAppBarTabsItems.contains(where: { $0.key == tabKey })
        availableAppBarTabsItems.removeAll { $0.key == tabKey }
        if hadTab {
            logger.debug("移除列表标签: \(listId)")
            saveTabs()
        }
    }

    func moveTab(from source: IndexSet, to destination: Int) {
        availableAppBarTabsItems.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }

    func notifyTabChange() {
        onTabChange?(selectedIndex)
    }

    // 添加观察列表标题变化的方法
    private func observeListTitleChanges() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleListTitleChange),
            name: .listTitleDidUpdate,
            object: nil
        )
    }

    @objc private func handleListTitleChange(_ notification: Notification) {
        if let listId = notification.userInfo?["listId"] as? String,
           let newTitle = notification.userInfo?["newTitle"] as? String
        {
            logger.debug("收到列表标题更新通知: 列表\(listId), 新标题\(newTitle)")

            DispatchQueue.main.async {
                // 更新标题映射
                self.listTitles[listId] = newTitle

                // 查找并更新相应的标签标题
                let tabKey = "list_\(self.accountType)_\(listId)"
                for (index, tab) in self.availableAppBarTabsItems.enumerated() {
                    if tab.key == tabKey {
                        // 创建新的标签项替换旧的
                        let updatedTab = FLListTimelineTabItem(
                            metaData: FLTabMetaData(
                                title: .text(newTitle),
                                icon: .material(.list)
                            ),
                            account: self.accountType,
                            listKey: listId
                        )

                        // 替换标签
                        self.availableAppBarTabsItems[index] = updatedTab
                        logger.debug("更新标签标题: \(tabKey) -> \(newTitle)")

                        // 保存标签设置
                        self.saveTabs()

                        // 如果当前选中的是被更新的标签，刷新选中状态
                        if self.selectedAppBarTabKey == tabKey {
                            self.updateSelectedTab(updatedTab)
                        }

                        // 发送专门的通知，告知AppBar标签标题已更新
                        // 添加延迟确保UI更新有足够时间
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            NotificationCenter.default.post(
                                name: NSNotification.Name("TabsDidUpdate"),
                                object: nil,
                                userInfo: [
                                    "updatedTabKey": tabKey,
                                    "newTitle": newTitle,
                                ]
                            )
                        }
                    }
                }

                // 保存列表信息
                self.savePinnedLists()

                // 通知UI更新
                self.objectWillChange.send()
            }
        }
    }
}
