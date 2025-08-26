import Combine
import Foundation
import shared
import SwiftUI

// 🔥 新的数据结构
struct ProfileTabViewModel {
    let tabKey: String
    let tabItem: FLTabItem
    let timelineViewModel: TimelineViewModel
    let timelinePresenter: TimelinePresenter // 🔥 所有Tab都有TimelinePresenter，不再可选
    let mediaPresenter: ProfileMediaPresenter? // 只有Media Tab才有

    var isMediaTab: Bool { mediaPresenter != nil }
}

class ProfilePresenterWrapper: ObservableObject {
    // 🔥 核心状态管理
    @Published var selectedTabKey: String? {
        didSet {
            // 监听selectedTabKey变化，自动切换Tab
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

    // 🔥 内部管理
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

    // 🔥 异步完整初始化
    @MainActor
    func setup() async {
        do {
            FlareLog.debug("🚀 [ProfilePresenterWrapper] 开始setup")

            // 1. 获取用户信息
            let user = getUserForInitialization()

            // 2. 根据登录状态筛选availableTabs
            availableTabs = createAvailableTabs(user: user, userKey: userKey)
            FlareLog.debug("📋 [ProfilePresenterWrapper] 创建了\(availableTabs.count)个Tab")

            // 3. 为每个Tab创建ProfileTabViewModel
            await createAllTabViewModels()

            // 4. 设置默认selectedTabKey（这会触发didSet，自动切换到第一个Tab）
            if let firstTab = availableTabs.first {
                selectedTabKey = firstTab.key
            }

            isInitialized = true
            FlareLog.debug("✅ [ProfilePresenterWrapper] 初始化完成")

        } catch {
            FlareLog.error("💥 [ProfilePresenterWrapper] 初始化失败: \(error)")
        }
    }

    // 🔥 Tab切换核心逻辑（私有方法，由selectedTabKey的didSet触发）
    @MainActor
    private func switchToTab(_ tabKey: String) async {
        guard let tabViewModel = tabViewModels[tabKey] else {
            FlareLog.error("⚠️ [ProfilePresenterWrapper] Tab未找到: \(tabKey)")
            return
        }

        FlareLog.debug("🔄 [ProfilePresenterWrapper] 切换到Tab: \(tabKey)")

        // 暂停当前的ViewModel
        currentTabViewModel?.timelineViewModel.pause()

        // 激活新的ViewModel
        tabViewModel.timelineViewModel.resume()

        // 更新状态
        currentTabViewModel = tabViewModel

        FlareLog.debug("✅ [ProfilePresenterWrapper] Tab切换完成: \(tabKey)")
    }

    // 🔥 创建所有TabViewModel
    @MainActor
    private func createAllTabViewModels() async {
        for tab in availableTabs {
            let timelineViewModel = TimelineViewModel()

            if tab is FLProfileMediaTabItem {
                // 🔥 Media Tab：从ProfileMediaPresenter获取内部的TimelinePresenter
                let mediaPresenter = createMediaPresenter(for: tab)
                let timelinePresenter = mediaPresenter.getMediaTimelinePresenter() // 🔥 使用新的方法获取内部实例
                await timelineViewModel.setupDataSource(presenter: timelinePresenter)

                let tabViewModel = ProfileTabViewModel(
                    tabKey: tab.key,
                    tabItem: tab,
                    timelineViewModel: timelineViewModel,
                    timelinePresenter: timelinePresenter, // 使用从ProfileMediaPresenter获取的实例
                    mediaPresenter: mediaPresenter
                )
                tabViewModels[tab.key] = tabViewModel

            } else {
                // Timeline Tab使用TimelinePresenter
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

    // 🔥 迁移的Tab配置逻辑（从ProfileTabSettingStore迁移）
    private func createAvailableTabs(user: UiUserV2, userKey: MicroBlogKey?) -> [FLTabItem] {
        let isGuestMode = user.key is AccountTypeGuest || UserManager.shared.getCurrentUser().0 == nil
        let isOwnProfile = userKey == nil

        // 创建media标签
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
            // 已登录用户显示所有标签
            var tabs = FLTabSettings.defaultThree(user: user, userKey: userKey)

            // 插入media tab到倒数第二的位置
            if tabs.isEmpty {
                tabs.append(mediaTab)
            } else {
                tabs.insert(mediaTab, at: max(0, tabs.count - 1))
            }

            // 过滤Like tab（只有自己的profile才显示）
            if !isOwnProfile {
                tabs = tabs.filter { !$0.key.contains("likes") }
            }

            return tabs
        }
    }

    // 🔥 Presenter创建方法
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

    // 🔥 获取用户信息（从ProfileTabSettingStore迁移）
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

    // 🔥 生命周期管理
    @MainActor
    func clearAllViewModels() {
        FlareLog.debug("🧹 [ProfilePresenterWrapper] 开始清理所有ViewModel")

        // 暂停所有ViewModel
        for tabViewModel in tabViewModels.values {
            tabViewModel.timelineViewModel.pause()
        }

        // 清理缓存
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

    // 🔥 刷新当前Tab
    @MainActor
    func refreshCurrentTab() async {
        if let currentViewModel = currentTabViewModel?.timelineViewModel {
            await currentViewModel.handleRefresh()
        }
    }

    // 🔥 TabStateProvider协议支持
    var tabCount: Int { availableTabs.count }
    var selectedIndex: Int {
        guard let selectedTabKey else { return 0 }
        return availableTabs.firstIndex { $0.key == selectedTabKey } ?? 0
    }
}
