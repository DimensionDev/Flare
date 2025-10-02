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
                HomeTimelineScreen(accountType: homeTimelineTabItem.account, toServiceSelect: { onNavigate(.serviceSelect) }, toCompose: { onNavigate(.composeNew(homeTimelineTabItem.account) ) }, toTabSetting: { onNavigate(.tabSettings) } )
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
        case .profileTabItem(let profileTabItem):
            ProfileScreen(accountType: profileTabItem.account, userKey: nil, onFollowingClick: { key in onNavigate(.userFollowing(profileTabItem.account, key)) }, onFansClick: { key in onNavigate(.userFans(profileTabItem.account, key)) })
        case .rssTabItem(let rssTabItem):
            RssScreen()
        case .directMessageTabItem(_):
            Text("Not implemented yet")
        case .antennasListTabItem(let antennasListTabItem):
            AntennasListScreen(accountType: antennasListTabItem.account)
        }
    }
}
