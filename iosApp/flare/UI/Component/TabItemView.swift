import SwiftUI
import KotlinSharedUI

struct TabItemView : View {
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
        case .allListTabItem(_):
            EmptyView()
        case .feedsTabItem(_):
            EmptyView()
        case .directMessageTabItem(_):
            EmptyView()
        case .discoverTabItem(_):
            EmptyView()
        case .antennasListTabItem(_):
            EmptyView()
        case .notificationTabItem(_):
            NotificationScreen(accountType: tabItem.account)
        case .profileTabItem(_):
            EmptyView()
        case .rssTabItem(_):
            EmptyView()
        case .settingsTabItem(_):
            EmptyView()
        }
    }
}
