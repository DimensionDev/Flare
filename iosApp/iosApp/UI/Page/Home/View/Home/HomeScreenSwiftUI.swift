import shared
import SwiftUI

struct HomeScreenSwiftUI: View {
    let accountType: AccountType

    var onSwitchToMenuTab: (() -> Void)?

    @StateObject private var tabStore = AppBarTabSettingStore.shared
    @EnvironmentObject private var timelineState: TimelineExtState
    @State private var selectedHomeAppBarTabKey: String = ""
    @State private var showAppbarSettings = false
    @State private var showLogin = false
    @Environment(\.appSettings) private var appSettings

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
                    if tabKey == selectedHomeAppBarTabKey {
                        timelineState.scrollToTopTrigger.toggle()
                    }
                }
            )
            .frame(height: 44)

            HomeTabViewContentViewSwiftUI(
                tabStore: tabStore,
                selectedTab: $selectedHomeAppBarTabKey
            )
        }.toolbarVisibility(.hidden, for: .navigationBar)
            .onAppear {
                FlareLog.debug("🏠 [HomeScreen] onAppear - selectedHomeAppBarTabKey: '\(selectedHomeAppBarTabKey)'")
                if selectedHomeAppBarTabKey.isEmpty, let firstTab = tabStore.availableAppBarTabsItems.first {
                    FlareLog.debug("🏠 [HomeScreen] Setting initial tab: '\(firstTab.key)'")
                    selectedHomeAppBarTabKey = firstTab.key
                    tabStore.updateSelectedTab(firstTab)
                }
            }
            .onChange(of: tabStore.availableAppBarTabsItems.count) { _, _ in
                let newItems = tabStore.availableAppBarTabsItems
                let currentTabExists = newItems.contains { $0.key == selectedHomeAppBarTabKey }

                if (!selectedHomeAppBarTabKey.isEmpty && !currentTabExists) || selectedHomeAppBarTabKey.isEmpty {
                    if let firstTab = newItems.first {
                        selectedHomeAppBarTabKey = firstTab.key
                        tabStore.updateSelectedTab(firstTab)
                    }
                }
            }
            .onChange(of: selectedHomeAppBarTabKey) { oldValue, newValue in
                FlareLog.debug("🔄 [HomeScreen] selectedHomeAppBarTabKey changed: '\(oldValue)' → '\(newValue)'")
                if let tab = tabStore.availableAppBarTabsItems.first(where: { $0.key == newValue }) {
                    FlareLog.debug("🔄 [HomeScreen] Updating tabStore with tab: '\(tab.key)'")
                    tabStore.updateSelectedTab(tab)
                } else {
                    FlareLog.warning("⚠️ [HomeScreen] No tab found for key: '\(newValue)'")
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
