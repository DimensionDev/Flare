import shared
import SwiftUI

struct HomeTabScreenSwiftUI: View {
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

            TabContentViewSwiftUI(
                tabStore: tabStore,
                selectedTab: $selectedHomeAppBarTabKey,
            )
        }.toolbarVisibility(.hidden, for: .navigationBar)
            .onAppear {
                if selectedHomeAppBarTabKey.isEmpty, let firstTab = tabStore.availableAppBarTabsItems.first {
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
