import SwiftUI
import shared

class FlareRootState: ObservableObject {
    @Published var timelineStore: TimelineStore
    @Published var tabStore: TabSettingsStore
    @Published var appState: FLNewAppState
    let accountType: AccountType
    
    init(accountType: AccountType) {
        self.accountType = accountType
        
        // 保持原有初始化顺序
        timelineStore = TimelineStore(accountType: accountType)
        tabStore = TabSettingsStore(timelineStore: timelineStore, accountType: accountType)
        appState = FLNewAppState(tabStore: tabStore)
        
        // 游客模式特殊处理
        if accountType is AccountTypeGuest {
            // 设置默认的 Home Timeline
            timelineStore.currentPresenter = HomeTimelinePresenter(accountType: accountType)
            
            // 只使用 Home 标签
            let homeTab = FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .material(.home)
                ), account: accountType
            )
            tabStore.availableTabs = [homeTab]
            tabStore.updateSelectedTab(homeTab)
        }
    }
} 