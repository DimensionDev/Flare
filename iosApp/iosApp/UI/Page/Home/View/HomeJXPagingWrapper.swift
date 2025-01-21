import SwiftUI
import shared

struct HomeJXPagingWrapper: UIViewControllerRepresentable {
    let router: Router
    let accountType: AccountType
    @Binding var showSettings: Bool
    @Binding var showLogin: Bool
    @Binding var selectedHomeTab: Int
    @ObservedObject var timelineStore: TimelineStore
    @ObservedObject var tabSettingsStore: TabSettingsStore
    
    func makeUIViewController(context: Context) -> HomeJXPagingViewController {
        let controller = HomeJXPagingViewController()
        controller.configure(
            router: router,
            accountType: accountType,
            showSettings: $showSettings,
            showLogin: $showLogin,
            selectedHomeTab: $selectedHomeTab,
            timelineStore: timelineStore,
            tabSettingsStore: tabSettingsStore
        )
        return controller
    }
    
    func updateUIViewController(_ uiViewController: HomeJXPagingViewController, context: Context) {
        uiViewController.updateContent()
    }
} 