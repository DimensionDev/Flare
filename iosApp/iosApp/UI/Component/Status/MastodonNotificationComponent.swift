import SwiftUI
import shared

struct MastodonNotificationComponent: View {
    let data: UiStatus.MastodonNotification
    var body: some View {
        VStack {
            switch data.type {
            case .favourite:
                StatusRetweetHeaderComponent(iconSystemName: "star", nameMarkdown: data.user.extra.nameMarkdown, text: "favourited your status")
            case .follow:
                StatusRetweetHeaderComponent(iconSystemName: "person.badge.plus", nameMarkdown: data.user.extra.nameMarkdown, text: "followed you")
            case .followrequest:
                StatusRetweetHeaderComponent(iconSystemName: "person.badge.plus", nameMarkdown: data.user.extra.nameMarkdown, text: "request to follow you")
            case .mention:
                StatusRetweetHeaderComponent(iconSystemName: "at", nameMarkdown: data.user.extra.nameMarkdown, text: "mentioned you")
            case .poll:
                StatusRetweetHeaderComponent(iconSystemName: "list.bullet", nameMarkdown: nil, text: "A poll you were participating in has ended")
            case .reblog:
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: data.user.extra.nameMarkdown, text: "reposted your status")
            case .status:
                StatusRetweetHeaderComponent(iconSystemName: "plus.bubble", nameMarkdown: data.user.extra.nameMarkdown, text: "boosted a status")
            case .update:
                StatusRetweetHeaderComponent(iconSystemName: "pencil", nameMarkdown: data.user.extra.nameMarkdown, text: "updated a status")
            }
            if let status = data.status {
                MastodonStatusComponent(mastodon: status)
            }
            if [NotificationTypes.follow, NotificationTypes.followrequest].contains(data.type) {
                HStack {
                    UserComponent(user: data.user)
                    Spacer()
                }
            }
        }
    }
}

//#Preview {
//    MastodonNotificationComponent()
//}
