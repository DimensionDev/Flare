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
                    text: "followed you"
                )
            case .mention:
                StatusRetweetHeaderComponent(
                    iconSystemName: "at",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: "mentioned you"
                )
            case .reply:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrowshape.turn.up.left",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: "replied"
                )
            case .repost:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: "repost your post"
                )
            case .quote:
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrowshape.turn.up.left",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: "quoted your post"
                )
            case .like:
                StatusRetweetHeaderComponent(
                    iconSystemName: "star",
                    nameMarkdown: data.user.extra.nameMarkdown,
                    text: "like your note"
                )
            }
            HStack {
                UserComponent(user: data.user)
                Spacer()
            }
        }
    }
}
