import Combine
import Foundation
import shared
import SwiftUI

 
struct ProfileTabViewModel {
    let tabKey: String
    let tabItem: FLTabItem
    let timelineViewModel: TimelineViewModel
    let timelinePresenter: TimelinePresenter
    let mediaPresenter: ProfileMediaPresenter?

    var isMediaTab: Bool { mediaPresenter != nil }
}

class ProfilePresenterWrapper: ObservableObject {
    
    @Published var selectedTabKey: String? {
        didSet {
            
            if let tabKey = selectedTabKey {
                Task { @MainActor in
                    await switchToTab(tabKey)
                }
            }
        }
    }

    @Published var availableTabs: [FLTabItem] = []
    @Published private(set) var currentTabViewModel: ProfileTabViewModel?
    @Published private(set) var isInitialized: Bool = false

    
    private var tabViewModels: [String: ProfileTabViewModel] = [:]
    let profilePresenter: ProfilePresenter

    private let accountType: AccountType
    private let userKey: MicroBlogKey?

    init(accountType: AccountType, userKey: MicroBlogKey?) {
        self.accountType = accountType
        self.userKey = userKey
        profilePresenter = ProfilePresenter(accountType: accountType, userKey: userKey)

        FlareLog.debug("🏗️ [ProfilePresenterWrapper] 初始化开始")
    }

     
    @MainActor
    func setup() async {
        do {
            FlareLog.debug("🚀 [ProfilePresenterWrapper] 开始setup")

            
            let user = getUserForInitialization()

         
            availableTabs = createAvailableTabs(user: user, userKey: userKey)
            FlareLog.debug("📋 [ProfilePresenterWrapper] 创建了\(availableTabs.count)个Tab")

           
            await createAllTabViewModels()

          
            if let firstTab = availableTabs.first {
                selectedTabKey = firstTab.key
            }

            isInitialized = true
            FlareLog.debug("✅ [ProfilePresenterWrapper] 初始化完成")

        } catch {
            FlareLog.error("💥 [ProfilePresenterWrapper] 初始化失败: \(error)")
        }
    }

   
    @MainActor
    private func switchToTab(_ tabKey: String) async {
        guard let tabViewModel = tabViewModels[tabKey] else {
            FlareLog.error("⚠️ [ProfilePresenterWrapper] Tab未找到: \(tabKey)")
            return
        }

        FlareLog.debug("🔄 [ProfilePresenterWrapper] 切换到Tab: \(tabKey)")

       
        currentTabViewModel?.timelineViewModel.pause()

     
        tabViewModel.timelineViewModel.resume()

        
        currentTabViewModel = tabViewModel

        FlareLog.debug("✅ [ProfilePresenterWrapper] Tab切换完成: \(tabKey)")
    }

    
    @MainActor
    private func createAllTabViewModels() async {
        for tab in availableTabs {
            let timelineViewModel = TimelineViewModel()

            if tab is FLProfileMediaTabItem {
              
                let mediaPresenter = createMediaPresenter(for: tab)
                let timelinePresenter = mediaPresenter.getMediaTimelinePresenter()
                await timelineViewModel.setupDataSource(presenter: timelinePresenter)

                let tabViewModel = ProfileTabViewModel(
                    tabKey: tab.key,
                    tabItem: tab,
                    timelineViewModel: timelineViewModel,
                    timelinePresenter: timelinePresenter,
                    mediaPresenter: mediaPresenter
                )
                tabViewModels[tab.key] = tabViewModel

            } else {
                
                let timelinePresenter = createTimelinePresenter(for: tab)
                await timelineViewModel.setupDataSource(presenter: timelinePresenter)

                let tabViewModel = ProfileTabViewModel(
                    tabKey: tab.key,
                    tabItem: tab,
                    timelineViewModel: timelineViewModel,
                    timelinePresenter: timelinePresenter,
                    mediaPresenter: nil
                )
                tabViewModels[tab.key] = tabViewModel
            }

            FlareLog.debug("✅ [ProfilePresenterWrapper] 创建TabViewModel: \(tab.key)")
        }
    }

     
    private func createAvailableTabs(user: UiUserV2, userKey: MicroBlogKey?) -> [FLTabItem] {
        let isGuestMode = user.key is AccountTypeGuest || UserManager.shared.getCurrentUser().0 == nil
        let isOwnProfile = userKey == nil

        
        let mediaTab = FLProfileMediaTabItem(
            metaData: FLTabMetaData(
                title: .localized(.profileMedia),
                icon: .mixed(.media, userKey: user.key)
            ),
            account: AccountTypeSpecific(accountKey: user.key),
            userKey: userKey
        )

        if isGuestMode, userKey != nil {
            // 访客模式只显示media标签
            return [mediaTab]
        } else {
           
            var tabs = FLTabSettings.defaultThree(user: user, userKey: userKey)

            
            if tabs.isEmpty {
                tabs.append(mediaTab)
            } else {
                tabs.insert(mediaTab, at: max(0, tabs.count - 1))
            }

          
            if !isOwnProfile {
                tabs = tabs.filter { !$0.key.contains("likes") }
            }

            return tabs
        }
    }

  
    private func createTimelinePresenter(for tab: FLTabItem) -> TimelinePresenter {
        guard let timelineItem = tab as? FLTimelineTabItem else {
            fatalError("Invalid timeline tab")
        }
        return timelineItem.createPresenter()
    }

    private func createMediaPresenter(for tab: FLTabItem) -> ProfileMediaPresenter {
        guard let mediaTab = tab as? FLProfileMediaTabItem else {
            fatalError("Invalid media tab")
        }
        return ProfileMediaPresenter(accountType: mediaTab.account, userKey: mediaTab.userKey)
    }

 
    private func getUserForInitialization() -> UiUserV2 {
        let result = UserManager.shared.getCurrentUser()
        if let user = result.0 {
            return user
        } else if userKey != nil {
            // 使用shared模块的createSampleUser函数
            return createSampleUser()
        } else {
            fatalError("无法获取用户信息")
        }
    }

    
    @MainActor
    func clearAllViewModels() {
        FlareLog.debug("🧹 [ProfilePresenterWrapper] 开始清理所有ViewModel")

        
        for tabViewModel in tabViewModels.values {
            tabViewModel.timelineViewModel.pause()
        }

       
        tabViewModels.removeAll()
        currentTabViewModel = nil
        selectedTabKey = nil

        FlareLog.debug("✅ [ProfilePresenterWrapper] 清理完成")
    }

    @MainActor
    func resumeCurrentViewModel() {
        currentTabViewModel?.timelineViewModel.resume()
        FlareLog.debug("▶️ [ProfilePresenterWrapper] 恢复当前ViewModel")
    }

  
    @MainActor
    func refreshCurrentTab() async {
        if let currentViewModel = currentTabViewModel?.timelineViewModel {
            await currentViewModel.handleRefresh()
        }
    }

   
    var tabCount: Int { availableTabs.count }
    var selectedIndex: Int {
        guard let selectedTabKey else { return 0 }
        return availableTabs.firstIndex { $0.key == selectedTabKey } ?? 0
    }
}
