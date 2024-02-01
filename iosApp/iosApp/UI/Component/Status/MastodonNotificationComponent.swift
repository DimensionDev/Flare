import SwiftUI
import shared

struct MastodonNotificationComponent: View {
    let data: UiStatus.MastodonNotification
    let event: MastodonStatusEvent
    var body: some View {
        VStack {
            switch data.type {
            case .favourite:
                StatusRetweetHeaderComponent(
                    iconSystemName: "star",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_favourite")
                )
            case .follow:
                StatusRetweetHeaderComponent(
                    iconSystemName: "person.badge.plus",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_follow")
                )
            case .followRequest:
                StatusRetweetHeaderComponent(
                    iconSystemName: "person.badge.plus",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_follow_request")
                )
            case .mention:
                StatusRetweetHeaderComponent(
                    iconSystemName: "at",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_mention")
                )
            case .poll:
                StatusRetweetHeaderComponent(
                    iconSystemName: "list.bullet",
                    nameMarkdown: nil,
                    text: String(localized: "mastodon_notification_poll")
                )
            case .reblog:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_reblog")
                )
            case .status:
                StatusRetweetHeaderComponent(
                    iconSystemName: "plus.bubble",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_status")
                )
            case .update:
                StatusRetweetHeaderComponent(
                    iconSystemName: "pencil",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_notification_update")
                )
            }
            if let status = data.status {
                MastodonStatusComponent(mastodon: status, event: event)
            }
            if [NotificationTypes.follow, NotificationTypes.followRequest].contains(data.type) {
                HStack {
                    UserComponent(user: data.user)
                    Spacer()
                }
            }
        }
    }
}

// #Preview {
//    MastodonNotificationComponent()
// }
