import shared
import SwiftUI

struct HomeTabScreenSwiftUI: View {
    let accountType: AccountType
    @Binding var scrollToTopTrigger: Bool
    @Binding var showFloatingButton: Bool

    var onSwitchToMenuTab: (() -> Void)?

    @StateObject private var tabStore = AppBarTabSettingStore.shared
    @State private var selectedHomeAppBarTabKey: String = ""
    @State private var showAppbarSettings = false
    @State private var showLogin = false
    @State private var tabScrollTriggers: [String: Bool] = [:]
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        VStack(spacing: 0) {
            AppBarViewSwiftUI(
                selectedHomeAppBarTab: $selectedHomeAppBarTabKey,
                tabs: tabStore.availableAppBarTabsItems,
                user: tabStore.currentUser,
                accountType: accountType,
                onAvatarTap: {
                    onSwitchToMenuTab?()
                },
                onSettingsTap: {
                    showAppbarSettings = true
                },
                onScrollToTop: { tabKey in
                    FlareLog.debug("HomeTabScreenSwiftUI Triggering scroll to top for tab: \(tabKey)")
                    // 切换指定标签的滚动触发器
                    tabScrollTriggers[tabKey] = !(tabScrollTriggers[tabKey] ?? false)

                    // 同时触发旧的 scrollToTopTrigger 以支持浮动按钮
                    if tabKey == selectedHomeAppBarTabKey {
                        scrollToTopTrigger.toggle()
                    }
                }
            )
            .frame(height: 44)

            TabContentViewSwiftUI(
                tabStore: tabStore,
                selectedTab: $selectedHomeAppBarTabKey,
                tabScrollTriggers: $tabScrollTriggers,
                showFloatingButton: $showFloatingButton
            )
        }.toolbarVisibility(.hidden, for: .navigationBar) // 隐藏，避免滑动返回 appbar 高度增加
            .onChange(of: tabStore.availableAppBarTabsItems.count) { _, _ in
                // 标签列表变化时，确保选中状态正确
                let newItems = tabStore.availableAppBarTabsItems
                let currentTabExists = newItems.contains { $0.key == selectedHomeAppBarTabKey }

                // 如果当前选中的标签不存在，或者没有选中任何标签，则选择第一个
                if (!selectedHomeAppBarTabKey.isEmpty && !currentTabExists) || selectedHomeAppBarTabKey.isEmpty {
                    if let firstTab = newItems.first {
                        selectedHomeAppBarTabKey = firstTab.key
                        tabStore.updateSelectedTab(firstTab)
                    }
                }
            }
            .onChange(of: scrollToTopTrigger) { _, _ in
                // 当浮动按钮触发 scrollToTopTrigger 时，同步到当前标签的 tabScrollTriggers
                FlareLog.debug("HomeTabScreenSwiftUI FloatingButton triggered scroll to top for current tab: \(selectedHomeAppBarTabKey)")
                tabScrollTriggers[selectedHomeAppBarTabKey] = !(tabScrollTriggers[selectedHomeAppBarTabKey] ?? false)
            }
            .onChange(of: selectedHomeAppBarTabKey) { _, newValue in
                if let tab = tabStore.availableAppBarTabsItems.first(where: { $0.key == newValue }) {
                    tabStore.updateSelectedTab(tab)
                }
            }
            .sheet(isPresented: $showAppbarSettings) {
                HomeAppBarSettingsView()
            }
            .sheet(isPresented: $showLogin) {
                ServiceSelectScreen(toHome: {
                    showLogin = false
                })
            }
    }
}
