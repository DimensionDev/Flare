import shared
import SwiftUI

struct HomeTabScreenSwiftUI: View {
    let accountType: AccountType
    @StateObject private var tabStore = AppBarTabSettingStore.shared
    @State private var selectedTabKey: String = ""
    @State private var showSettings = false
    @State private var showTabSettings = false
    @State private var showLogin = false
    
    var body: some View {
        VStack(spacing: 0) {
            // 顶部AppBar
            AppBarViewSwiftUI(
                selectedTab: $selectedTabKey,
                tabs: tabStore.availableAppBarTabsItems,
                user: tabStore.currentUser,
                onAvatarTap: {
                    NotificationCenter.default.post(name: NSNotification.Name("flShowNewMenu"), object: nil)
                },
                onSettingsTap: {
                    showTabSettings = true
                }
            ) 
            .frame(height: 44)
            
            // 内容区域
            TabContentViewSwiftUI(
                tabStore: tabStore,
                selectedTab: $selectedTabKey
            )
        }.toolbarVisibility(.hidden, for: .navigationBar)//隐藏，避免滑动返回 appbar 高度增加
        .onAppear {
            if let firstTab = tabStore.availableAppBarTabsItems.first {
                selectedTabKey = firstTab.key
                tabStore.updateSelectedTab(firstTab)
            }
        }
        .onChange(of: selectedTabKey) { _, newValue in
            if let tab = tabStore.availableAppBarTabsItems.first(where: { $0.key == newValue }) {
                tabStore.updateSelectedTab(tab)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsScreen()
        }
        .sheet(isPresented: $showTabSettings) {
            HomeAppBarSettingsView()
        }
        .sheet(isPresented: $showLogin) {
            ServiceSelectScreen(toHome: {
                showLogin = false
            })
        }
    }
} 
