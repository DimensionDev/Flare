import Foundation
import shared
import SwiftUI

class TabSettingsStore: ObservableObject {
    @Published var primaryItems: [FLTabItem] = []
    @Published var secondaryItems: [FLTabItem] = []
    @Published var allTabs: [FLTabItem] = [] // 合并后的所有items
    @Published var availableTabs: [FLTabItem] = []
    @Published var currentUser: UiUserV2?

    private var presenter = ActiveAccountPresenter()
    private var isInitializing = false
    private var timelineStore: TimelineStore

    init(timelineStore: TimelineStore) {
        self.timelineStore = timelineStore
        // 开始观察用户信息
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
        // 如果正在初始化或已经初始化过相同用户，则不重复初始化
        if isInitializing || self.currentUser?.key == user.key {
            return
        }
        
        isInitializing = true
        self.currentUser = user
        // 加载primary和secondary items
        primaryItems = FLTabSettings.defaultPrimary(user: user)
        secondaryItems = FLTabSettings.defaultSecondary(user: user)
        // 合并items
        updateAllTabs()
        
        // 如果没有选中的标签，选中第一个
        if timelineStore.selectedTabKey == nil {
            if let firstItem = allTabs.first {
                timelineStore.updateCurrentPresenter(for: firstItem)
            }
        }
        
        isInitializing = false
    }

    private func updateAllTabs() {
        if let homeItem = primaryItems.first {
            allTabs = [homeItem] + secondaryItems
        } else {
            allTabs = secondaryItems
        }
    }

    private func updateAvailableTabs() {
        let currentTabKeys = Set(allTabs.map { $0.key })
        // 可用标签应该是当前平台的 secondary items 中未被使用的项
        availableTabs = secondaryItems.filter { !currentTabKeys.contains($0.key) }
    }

    func saveTabs() {
        let settings = FLTabSettings(
            items: allTabs,
            secondaryItems: secondaryItems,
            homeTabs: [:]
        )
        // TODO: 保存设置到持久化存储
        updateAllTabs()
    }

    func moveTab(from source: IndexSet, to destination: Int) {
        secondaryItems.move(fromOffsets: source, toOffset: destination)
        updateAllTabs()
        saveTabs()
    }

    func toggleTab(_ id: String) {
        if let index = secondaryItems.firstIndex(where: { $0.key == id }) {
            let tab = secondaryItems[index]
            let updatedMetaData = FLTabMetaData(
                title: tab.metaData.title,
                icon: tab.metaData.icon
            )
            secondaryItems[index] = tab.update(metaData: updatedMetaData)
            updateAllTabs()
            saveTabs()
        }
    }

    func addTab(_ tab: FLTabItem) {
        secondaryItems.append(tab)
        updateAllTabs()
        saveTabs()
    }

    func removeTab(_ id: String) {
        // 只允许删除 secondary items
        secondaryItems.removeAll { $0.key == id }
        updateAllTabs()
        saveTabs()
    }
}
