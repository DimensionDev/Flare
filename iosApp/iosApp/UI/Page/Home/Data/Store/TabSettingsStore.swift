import Foundation
import shared
import SwiftUI

class TabSettingsStore: ObservableObject, TabStateProvider {
    @Published var primaryItems: [FLTabItem] = [] // 主要标签（不可更改状态）
    @Published var secondaryItems: [FLTabItem] = [] // 所有次要标签
    @Published var storeItems: [FLTabItem] = [] // UserDefaults 存储的已启用标签
    @Published var availableTabs: [FLTabItem] = []
    @Published var selectedTabKey: String = ""
    @Published var currentPresenter: TimelinePresenter?
    @Published var currentUser: UiUserV2?

    private var presenter = ActiveAccountPresenter()
    private var isInitializing = false
    private var timelineStore: TimelineStore
    private let settingsManager = FLTabSettingsManager()
    private let accountType: AccountType

    // 缓存 presenter 避免重复创建
    private var presenterCache: [String: TimelinePresenter] = [:]

    // TabStateProvider 协议实现
    var onTabChange: ((Int) -> Void)?
    
    var tabCount: Int {
        availableTabs.count
    }

    // 获取分段标题
    var segmentTitles: [String] {
        availableTabs.map { tab in
            switch tab.metaData.title {
            case let .text(title): title
            case let .localized(key): NSLocalizedString(key, comment: "")
            }
        }
    }

    // 获取选中索引
    var selectedIndex: Int {
        availableTabs.firstIndex { $0.key == selectedTabKey } ?? 0
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
        currentPresenter = nil
    }

    // 更新选中标签
    func updateSelectedTab(_ tab: FLTabItem) {
        selectedTabKey = tab.key
        if let presenter = getOrCreatePresenter(for: tab) {
            currentPresenter = presenter
        }
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

    init(timelineStore: TimelineStore, accountType: AccountType) {
        self.timelineStore = timelineStore
        self.accountType = accountType
        observeAccountChanges()
        observeUser()
    }

    private func observeUser() {
        Task { @MainActor in
            for await state in presenter.models {
                if case let .success(data) = onEnum(of: state.user) {
                    // 直接初始化，不需要额外的 MainActor.run
                    self.initializeWithUser(data.data)
                }
            }
        }
    }

    private func initializeWithUser(_ user: UiUserV2) {
        if isInitializing || currentUser?.key == user.key {
            return
        }

        isInitializing = true
        currentUser = user

        // 同步加载所有数据
        let primary = FLTabSettings.defaultPrimary(user: user)
        let secondary = FLTabSettings.defaultSecondary(user: user)
        primaryItems = primary
        secondaryItems = secondary

        // 从 UserDefaults 加载存储的标签
        storeItems = settingsManager.getEnabledItems(for: user) ?? secondary

        // 立即更新可用标签
        if let homeItem = primary.first {
            let enabledItems = storeItems.isEmpty ? secondary : storeItems
            availableTabs = [homeItem] + enabledItems
        }

        // 选择第一个标签
        if selectedTabKey.isEmpty {
            if let firstItem = availableTabs.first {
                updateSelectedTab(firstItem)
            }
        }

        isInitializing = false
    }

    func saveTabs() {
        guard let user = currentUser else { return }
        settingsManager.saveEnabledItems(storeItems, for: user)
        updateAvailableTabs()
    }

    private func updateAvailableTabs() {
        if let homeItem = primaryItems.first {
            let enabledItems = storeItems.isEmpty ? secondaryItems : storeItems
            availableTabs = [homeItem] + enabledItems
        }
    }

    func toggleTab(_ id: String) {
        guard let user = currentUser else { return }

        let wasSelected = selectedTabKey == id

        if storeItems.contains(where: { $0.key == id }) {
            // 关闭标签：从 storeItems 中移除
            storeItems.removeAll { $0.key == id }

            // 如果关闭的是当前选中的标签，切换到第一个标签（首页）
            if wasSelected {
                if let firstTab = primaryItems.first {
                    updateSelectedTab(firstTab)
                }
            }
        } else {
            // 开启标签：添加到 storeItems
            if let item = secondaryItems.first(where: { $0.key == id }) {
                storeItems.append(item)
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
        storeItems.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }

    func notifyTabChange() {
        onTabChange?(selectedIndex)
    }
}
