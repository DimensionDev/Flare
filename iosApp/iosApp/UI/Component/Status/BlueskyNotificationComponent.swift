import SwiftUI
import shared

struct BlueskyNotificationComponent: View {
    let data: UiStatus.BlueskyNotification
    var body: some View {
        VStack {
            switch data.reason {
            case .follow:
                StatusRetweetHeaderComponent(
                    iconSystemName: "person.badge.plus",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "bluesky_notification_follow")
                )
            case .mention:
                StatusRetweetHeaderComponent(
                    iconSystemName: "at",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "bluesky_notification_mention")
                )
            case .reply:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrowshape.turn.up.left",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "bluesky_notification_reply")
                )
            case .repost:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "bluesky_notification_repost")
                )
            case .quote:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrowshape.turn.up.left",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "bluesky_notification_quote")
                )
            case .like:
                StatusRetweetHeaderComponent(
                    iconSystemName: "star",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "bluesky_notification_like")
                )
            }
            HStack {
                UserComponent(user: data.user)
                Spacer()
            }
        }
    }
}
