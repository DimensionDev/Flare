import Foundation
import shared
import SwiftUI

class ProfileTabSettingStore: ObservableObject {
    // MARK: - Published Properties
    @Published var availableTabs: [FLTabItem] = [] // 当前显示的所有标签
    @Published var selectedTabKey: String? // 当前选中的标签
    @Published var currentUser: UiUserV2?
    @Published var currentPresenter: TimelinePresenter?
    
    // MARK: - Private Properties
    private var timelineStore: TimelineStore
    private var isInitializing = false
    private var presenter = ActiveAccountPresenter()
    private var presenterCache: [String: TimelinePresenter] = [:]  // 添加缓存
    
    // MARK: - Initialization
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
    
    // MARK: - Public Methods
    func initializeWithUser(_ user: UiUserV2) {
        if isInitializing || self.currentUser?.key == user.key {
            return
        }
        
        isInitializing = true
        self.currentUser = user
        
        // 更新可用标签
        updateTabs(user: user)
        
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
            if let presenter = getOrCreatePresenter(for: selectedItem) {
                currentPresenter = nil  // 先设置为 nil 触发 UI 更新
                DispatchQueue.main.async {
                    self.currentPresenter = presenter
                }
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
    }
    
    // MARK: - Private Methods
    private func updateTabs(user: UiUserV2) {
        // 根据平台类型获取对应的标签
        var tabs = FLTabSettings.defaultThree(user: user)
        
//       // 添加 media tab 到倒数第二的位置
//       let mediaTab = FLProfileMediaTabItem(
//           metaData: FLTabMetaData(
//               title: .localized(.profileMedia),
//               icon: .mixed(.media, userKey: user.key)
//           ),
//           account: AccountTypeSpecific(accountKey: user.key),
//           userKey: user.key
//       )
//       
//       // 插入到倒数第二的位置
//       if tabs.isEmpty {
//           tabs.append(mediaTab)
//       } else {
//           tabs.insert(mediaTab, at: max(0, tabs.count - 1))
//       }
//        
        availableTabs = tabs
        
        // 如果没有选中的标签，选中第一个
        if selectedTabKey == nil, let firstTab = availableTabs.first {
            selectTab(firstTab.key)
        }
    }
} 
