import Foundation
import SwiftUI
import shared

class TabSettingsStore: ObservableObject {
    @Published var tabs: [FLTabItem]
    @Published var availableTabs: [FLTabItem] = []
    @Published var secondaryItems: [FLTabItem] = []
    
    private let user: UiUserV2
    
    init(user: UiUserV2) {
        self.user = user
        // 根据用户平台加载对应的标签项
        self.tabs = FLTabSettings.defaultPrimary(user: user)
        self.secondaryItems = FLTabSettings.defaultSecondary(user: user)
        updateAvailableTabs()
    }
    
    private func updateAvailableTabs() {
        let currentTabKeys = Set(tabs.map { $0.key })
        // 可用标签应该是当前平台的 secondary items 中未被使用的项
        availableTabs = secondaryItems.filter { !currentTabKeys.contains($0.key) }
    }
    
    func saveTabs() {
        let settings = FLTabSettings(
            items: tabs,
            secondaryItems: secondaryItems,
            homeTabs: [:]
        )
        // TODO: 保存设置到持久化存储
        updateAvailableTabs()
    }
    
    func moveTab(from source: IndexSet, to destination: Int) {
        tabs.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }
    
    func toggleTab(_ id: String) {
        if let index = tabs.firstIndex(where: { $0.key == id }) {
            let tab = tabs[index]
            let updatedMetaData = FLTabMetaData(
                title: tab.metaData.title,
                icon: tab.metaData.icon
            )
            tabs[index] = tab.update(metaData: updatedMetaData)
            saveTabs()
        }
    }
    
    func addTab(_ tab: FLTabItem) {
        tabs.append(tab)
        saveTabs()
    }
    
    func removeTab(_ id: String) {
        tabs.removeAll { $0.key == id }
        saveTabs()
    }
}
