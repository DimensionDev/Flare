import SwiftUI
import KotlinSharedUI

extension TabItem {
    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (Route) -> Void,
    ) -> some View {
        switch onEnum(of: self) {
        case .timelineTabItem(let timelineTabItem):
            switch onEnum(of: timelineTabItem) {
            case .HomeTimelineTabItem(let homeTimelineTabItem):
                HomeTimelineScreen(accountType: homeTimelineTabItem.account, toServiceSelect: { onNavigate(.serviceSelect) })
            default:
                TimelineScreen(tabItem: timelineTabItem)
            }
        case .notificationTabItem:
            NotificationScreen(accountType: self.account)
        case .settingsTabItem:
            SettingsScreen()
        case .discoverTabItem(let discoverTabItem):
            DiscoverScreen(accountType: discoverTabItem.account)
        case .allListTabItem(let allListTabItem):
            AllListScreen(accountType: allListTabItem.account)
        case .feedsTabItem(let feedsTabItem):
            AllFeedScreen(accountType: feedsTabItem.account)
        default:
            Text("Not done yet for \(self)")
        }
    }
}
