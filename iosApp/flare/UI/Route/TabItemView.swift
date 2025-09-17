import SwiftUI
import KotlinSharedUI

struct TabItemView: View {
    let tabItem: TabItem
    let onNavigate: (Route) -> Void

    var body: some View {
        switch onEnum(of: tabItem) {
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
            NotificationScreen(accountType: tabItem.account)
        case .profileTabItem:
            EmptyView()
        case .rssTabItem:
            EmptyView()
        case .settingsTabItem:
            SettingsScreen()
        }
    }
}
