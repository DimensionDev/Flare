import shared
import SwiftUI

struct HomeTabScreen: View {
    let accountType: AccountType
//    @StateObject private var timelineStore: AppBarTabSettingStore
    @StateObject private var tabStore: AppBarTabSettingStore
    @State private var isShowAppBar: Bool? = true
    @State private var showSettings = false
    @State private var showTabSettings = false
    @State private var showLogin = false
    @State private var selectedTab = 0

    init(accountType: AccountType) {
        self.accountType = accountType


        // 2. 初始化 TabSettingsStore timelineStore: timelineStore,
        let tabStore = AppBarTabSettingStore(accountType: accountType)
        _tabStore = StateObject(wrappedValue: tabStore)

        // 3. 游客模式特殊处理
        if accountType is AccountTypeGuest {
            // 设置默认的 Home Timeline
            tabStore.currentPresenter = HomeTimelinePresenter(accountType: accountType)

            // 只使用 Home 标签
            let homeTab = FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .material(.home)
                ), account: accountType
            )
            tabStore.availableAppBarTabsItems = [homeTab]
            tabStore.updateSelectedTab(homeTab)
        }
    }

    var body: some View {
        // 游客模式或者用户数据已加载时显示内容
        if accountType is AccountTypeGuest || tabStore.currentUser != nil {
            HomeNewViewControllerRepresentable(
                tabStore: tabStore,
                accountType: accountType,
                selectedTab: $selectedTab,
                isShowAppBar: $isShowAppBar
            )
            .onAppear {
                // 添加通知监听
                NotificationCenter.default.addObserver(
                    forName: NSNotification.Name("ShowSettings"),
                    object: nil,
                    queue: .main
                ) { _ in
                    showSettings = true
                }

                NotificationCenter.default.addObserver(
                    forName: NSNotification.Name("ShowTabSettings"),
                    object: nil,
                    queue: .main
                ) { _ in
                    showTabSettings = true
                }

                NotificationCenter.default.addObserver(
                    forName: NSNotification.Name("ShowLogin"),
                    object: nil,
                    queue: .main
                ) { _ in
                    showLogin = true
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsScreen()
            }
            .sheet(isPresented: $showTabSettings) {
                HomeAppBarSettingsView(store: tabStore)
            }
            .sheet(isPresented: $showLogin) {
                ServiceSelectScreen(toHome: {
                    showLogin = false
                })
            }
        } else {
            // 显示加载状态
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

// - HomeNewViewControllerRepresentable
struct HomeNewViewControllerRepresentable: UIViewControllerRepresentable {
    let tabStore: AppBarTabSettingStore
    let accountType: AccountType
    @Binding var selectedTab: Int
    @Binding var isShowAppBar: Bool?

    func makeUIViewController(context _: Context) -> HomeTabController {
        let controller = HomeTabController(
            tabStore: tabStore,
            accountType: accountType
        )
        return controller
    }

    func updateUIViewController(_ uiViewController: HomeTabController, context _: Context) {
        // 更新选中的标签页
        uiViewController.updateSelectedTab(selectedTab)
        // 更新 AppBar 的可见性
        uiViewController.updateAppBarVisibility(isShowAppBar)
    }
}
