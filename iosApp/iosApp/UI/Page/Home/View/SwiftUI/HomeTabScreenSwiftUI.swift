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
                }
            )
            .frame(height: 44)

            TabContentViewSwiftUI(
                tabStore: tabStore,
                selectedTab: $selectedHomeAppBarTabKey,
                scrollToTopTrigger: $scrollToTopTrigger,
                showFloatingButton: $showFloatingButton
            )
        }.toolbarVisibility(.hidden, for: .navigationBar) // 隐藏，避免滑动返回 appbar 高度增加
            .onAppear {
                if let firstTab = tabStore.availableAppBarTabsItems.first {
                    selectedHomeAppBarTabKey = firstTab.key
                    tabStore.updateSelectedTab(firstTab)
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
