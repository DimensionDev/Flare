import Foundation
import shared
import SwiftUI

// 在文件顶部添加通知名称定义
extension Notification.Name {
    static let accountChanged = Notification.Name("accountChanged")
}

class AppBarTabSettingStore: ObservableObject, TabStateProvider {
    @Published var primaryHomeItems: [FLTabItem] = [] // 主要标签（不可更改状态）Appbar 第一个Home 标签
    @Published var secondaryItems: [FLTabItem] = [] // 所有次要标签
    @Published var availableAppBarTabsItems: [FLTabItem] = [] // UserDefaults 存储的已启用标签

    @Published var selectedAppBarTabKey: String = "" // 选中的 tab key
    @Published var currentPresenter: TimelinePresenter?
    @Published var currentUser: UiUserV2?

    private var presenter = ActiveAccountPresenter()
    private var isInitializing = false
    private let settingsManager = FLTabSettingsManager()
    private let accountType: AccountType

    // 缓存 presenter 避免重复创建
    private var presenterCache: [String: TimelinePresenter] = [:]

    // TabStateProvider 协议实现
    var onTabChange: ((Int) -> Void)?

    init(accountType: AccountType) {
        self.accountType = accountType
        observeAccountChanges()
        observeUser()
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
        clearCache()
        if let user = currentUser {
            initializeWithUser(user)
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
            if let homeItem = primaryHomeItems.first {
                if availableAppBarTabsItems.first?.key.contains("home_") == true {
                    availableAppBarTabsItems = availableAppBarTabsItems
                } else {
                    availableAppBarTabsItems = [homeItem] + availableAppBarTabsItems
                }
            }
        }

        // 选择第一个标签
        if selectedAppBarTabKey.isEmpty {
            if let firstItem = availableAppBarTabsItems.first {
                updateSelectedTab(firstItem)
            }
        }

        isInitializing = false
    }

    func saveTabs() {
        guard let user = currentUser else { return }
        settingsManager.saveEnabledItems(availableAppBarTabsItems, for: user)
    }

    func toggleTab(_ id: String) {
        guard let user = currentUser else { return }

        let wasSelected = selectedAppBarTabKey == id

        if availableAppBarTabsItems.contains(where: { $0.key == id }) {
            // 关闭标签：从 storeItems 中移除
            availableAppBarTabsItems.removeAll { $0.key == id }

            // 如果关闭的是当前选中的标签，切换到第一个标签（首页）
            if wasSelected {
                if let firstTab = primaryHomeItems.first {
                    updateSelectedTab(firstTab)
                }
            }
        } else {
            // 开启标签：添加到 storeItems
            if let item = secondaryItems.first(where: { $0.key == id }) {
                availableAppBarTabsItems.append(item)
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

    func moveTab(from source: IndexSet, to destination: Int) {
        availableAppBarTabsItems.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }

    func notifyTabChange() {
        onTabChange?(selectedIndex)
    }
}
