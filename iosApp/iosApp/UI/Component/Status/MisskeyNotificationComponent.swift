import SwiftUI
import shared

struct MisskeyNotificationComponent: View {
    @Environment(\.openURL) private var openURL
    let data: UiStatus.MisskeyNotification
    let event: MisskeyStatusEvent
    var body: some View {
        VStack {
            switch data.type {
            case .follow:
                StatusRetweetHeaderComponent(
                    iconSystemName: "person.badge.plus",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_follow")
                )
            case .mention:
                StatusRetweetHeaderComponent(
                    iconSystemName: "at",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_mention")
                )
            case .reply:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrowshape.turn.up.left",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_reply")
                )
            case .renote:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_renote")
                )
            case .quote:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrowshape.turn.up.left",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_quote")
                )
            case .reaction:
                StatusRetweetHeaderComponent(
                    iconSystemName: "star",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_reaction")
                )
            case .pollEnded:
                StatusRetweetHeaderComponent(
                    iconSystemName: "list.bullet",
                    nameMarkdown: nil,
                    text: String(localized: "misskey_notification_poll_ended")
                )
            case .receiveFollowRequest:
                StatusRetweetHeaderComponent(
                    iconSystemName: "person.badge.plus",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_receive_follow_request")
                )
            case .followRequestAccepted:
                StatusRetweetHeaderComponent(
                    iconSystemName: "person.badge.plus",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_follow_request_accepted")
                )
            case .achievementEarned:
                StatusRetweetHeaderComponent(
                    iconSystemName: "star",
                    nameMarkdown: nil,
                    text: String(localized: "misskey_notification_achievement_earned")
                )
            case .app:
                StatusRetweetHeaderComponent(
                    iconSystemName: "app",
                    nameMarkdown: data.user?.extra.nameMarkdown,
                    text: String(localized: "misskey_notification_app")
                )
            }
            if let note = data.note {
                MisskeyStatusComponent(misskey: note, event: event)
            }
            if let user = data.user {
                if [shared.NotificationType.follow,
                    shared.NotificationType.followRequestAccepted,
                    shared.NotificationType.receiveFollowRequest].contains(data.type) {
                    HStack {
                        UserComponent(
                            user: user,
                            onUserClicked: {
                                openURL(URL(string: AppDeepLink.Profile.shared.invoke(accountKey: data.accountKey, userKey: user.userKey))!)
                            }
                        )
                        Spacer()
                    }
                }
            }
        }
    }
}

// #Preview {
//    MisskeyNotificationComponent()
// }
