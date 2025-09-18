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
        case .allListTabItem:
            EmptyView()
        case .feedsTabItem:
            EmptyView()
        case .directMessageTabItem:
            EmptyView()
        case .discoverTabItem:
            EmptyView()
        case .antennasListTabItem:
            EmptyView()
        case .notificationTabItem:
            NotificationScreen(accountType: self.account)
        case .profileTabItem:
            EmptyView()
        case .rssTabItem:
            EmptyView()
        case .settingsTabItem:
            SettingsScreen()
        }
    }
}
