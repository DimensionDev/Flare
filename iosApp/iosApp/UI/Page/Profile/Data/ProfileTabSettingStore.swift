import Foundation
import shared
import SwiftUI

class ProfileTabSettingStore: ObservableObject {
    // MARK: - Published Properties
    @Published var availableTabs: [FLTabItem] = [] // 当前显示的所有标签
    @Published var selectedTabKey: String? // 当前选中的标签
    @Published var currentUser: UiUserV2?
    
    // MARK: - Private Properties
    private var timelineStore: TimelineStore
    private var isInitializing = false
    
    // MARK: - Initialization
    init(timelineStore: TimelineStore) {
        self.timelineStore = timelineStore
    }
    
    // MARK: - Public Methods
    func initializeWithUser(_ user: UiUserV2, accountKey: MicroBlogKey) {
        if isInitializing || self.currentUser?.key == user.key {
            return
        }
        
        isInitializing = true
        self.currentUser = user
        
        // 更新可用标签
        updateTabs(user: user, accountKey: accountKey)
        
        // 如果没有选中的标签，选中第一个
        if selectedTabKey == nil {
            if let firstItem = availableTabs.first {
                selectTab(firstItem.key)
            }
        }
        
        isInitializing = false
    }
    
    func selectTab(_ key: String) {
        selectedTabKey = key
        if let selectedItem = availableTabs.first(where: { $0.key == key }) {
            timelineStore.updateCurrentPresenter(for: selectedItem)
        }
    }
    
    // MARK: - Private Methods
    private func updateTabs(user: UiUserV2, accountKey: MicroBlogKey) {
        // 根据平台类型获取对应的标签
        availableTabs = FLTabSettings.defaultThree(user: user, accountKey: accountKey)
        
        // 如果没有选中的标签，选中第一个
        if selectedTabKey == nil, let firstTab = availableTabs.first {
            selectTab(firstTab.key)
        }
    }
} 