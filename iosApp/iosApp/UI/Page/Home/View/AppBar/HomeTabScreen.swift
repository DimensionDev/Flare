import shared
import SwiftUI

struct HomeTabScreen: View {
    let accountType: AccountType
    @ObservedObject private var tabStore = AppBarTabSettingStore.shared
    @State private var isShowAppBar: Bool? = true
    @State private var showSettings = false
    @State private var showTabSettings = false
    @State private var showLogin = false
    @State private var selectedTab = 0

    init(accountType: AccountType) {
        self.accountType = accountType
        
        // 不再需要创建新的AppBarTabSettingStore实例
        // 游客模式的处理已经在AppBarTabSettingStore.shared中完成
    }

    var body: some View {
        // 游客模式或者用户数据已加载时显示内容
        if accountType is AccountTypeGuest || tabStore.currentUser != nil {
            HomeNewViewControllerRepresentable(
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
                HomeAppBarSettingsView()
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
    let accountType: AccountType
    @Binding var selectedTab: Int
    @Binding var isShowAppBar: Bool?

    func makeUIViewController(context _: Context) -> HomeTabController {
        let controller = HomeTabController(
            accountType: accountType
        )
        return controller
    }

    func updateUIViewController(_ uiViewController: HomeTabController, context _: Context) {
        // 更新选中的标签页
        uiViewController.updateSelectedTab(selectedTab)
        // 更新 AppBar 的可见性
        if let isShowAppBar = isShowAppBar {
            uiViewController.updateAppBarVisibility(isShowAppBar)
        }
    }
}
