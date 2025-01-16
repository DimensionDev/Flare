import Foundation
import shared
import SwiftUI

class TabSettingsStore: ObservableObject {
    @Published var primaryItems: [FLTabItem] = [] // 主要标签（不可更改状态）
    @Published var secondaryItems: [FLTabItem] = [] // 所有次要标签
    @Published var storeItems: [FLTabItem] = [] // UserDefaults 存储的已启用标签
    @Published var availableTabs: [FLTabItem] = [] // 实际显示在 AppBar 上的标签
    @Published var currentUser: UiUserV2?

    private var presenter = ActiveAccountPresenter()
    private var isInitializing = false
    private var timelineStore: TimelineStore
    private let settingsManager = FLTabSettingsManager()

    init(timelineStore: TimelineStore) {
        self.timelineStore = timelineStore
        observeUser()
    }

    private func observeUser() {
        Task { @MainActor in
            for await state in presenter.models {
                if case let .success(data) = onEnum(of: state.user) {
                    initializeWithUser(data.data)
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

        // 1. 加载默认配置
        primaryItems = FLTabSettings.defaultPrimary(user: user)
        secondaryItems = FLTabSettings.defaultSecondary(user: user)

        // 2. 加载存储的配置
        loadStoredItems(user)

        // 3. 更新可用标签
        updateAvailableTabs()

        // 4. 如果没有选中的标签，选中第一个
        if timelineStore.selectedTabKey == nil {
            if let firstItem = availableTabs.first {
                timelineStore.updateCurrentPresenter(for: firstItem)
            }
        }

        isInitializing = false
    }

    private func loadStoredItems(_ user: UiUserV2) {
        // 从 UserDefaults 加载存储的标签
        storeItems = settingsManager.getEnabledItems(for: user) ?? []
    }

    private func updateAvailableTabs() {
        if let homeItem = primaryItems.first {
            // 如果 storeItems 为空，使用所有 secondaryItems
            let enabledItems = storeItems.isEmpty ? [] : storeItems
            availableTabs = [homeItem] + enabledItems
        }
    }

    func saveTabs() {
        guard let user = currentUser else { return }
        settingsManager.saveEnabledItems(storeItems, for: user)
        updateAvailableTabs()
    }

    func toggleTab(_ id: String) {
        guard let user = currentUser else { return }

        if storeItems.contains(where: { $0.key == id }) {
            // 关闭标签：从 storeItems 中移除
            storeItems.removeAll { $0.key == id }
        } else {
            // 开启标签：添加到 storeItems
            if let item = secondaryItems.first(where: { $0.key == id }) {
                storeItems.append(item)
            }
        }

        saveTabs()
    }

    func moveTab(from source: IndexSet, to destination: Int) {
        storeItems.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }
}
