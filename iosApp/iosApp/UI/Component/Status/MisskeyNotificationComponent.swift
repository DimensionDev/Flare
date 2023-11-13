import SwiftUI
import shared

struct MisskeyNotificationComponent: View {
    let data: UiStatus.MisskeyNotification
    var body: some View {
        VStack {
            switch data.type {
            case .follow:
                StatusRetweetHeaderComponent(iconSystemName: "person.badge.plus", nameMarkdown: data.user?.extra.nameMarkdown, text: "followed you")
            case .mention:
                StatusRetweetHeaderComponent(iconSystemName: "at", nameMarkdown: data.user?.extra.nameMarkdown, text: "mentioned you")
            case .reply:
                StatusRetweetHeaderComponent(iconSystemName: "arrowshape.turn.up.left", nameMarkdown: data.user?.extra.nameMarkdown, text: "replied")
            case .renote:
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: data.user?.extra.nameMarkdown, text: "renoted your note")
            case .quote:
                StatusRetweetHeaderComponent(iconSystemName: "arrowshape.turn.up.left", nameMarkdown: data.user?.extra.nameMarkdown, text: "quoted your note")
            case .reaction:
                StatusRetweetHeaderComponent(iconSystemName: "star", nameMarkdown: data.user?.extra.nameMarkdown, text: "react your note")
            case .pollended:
                StatusRetweetHeaderComponent(iconSystemName: "list.bullet", nameMarkdown: nil, text: "A poll you were participating in has ended")
            case .receivefollowrequest:
                StatusRetweetHeaderComponent(iconSystemName: "person.badge.plus", nameMarkdown: data.user?.extra.nameMarkdown, text: "request to follow you")
            case .followrequestaccepted:
                StatusRetweetHeaderComponent(iconSystemName: "person.badge.plus", nameMarkdown: data.user?.extra.nameMarkdown, text: "accepted your follow")
            case .achievementearned:
                StatusRetweetHeaderComponent(iconSystemName: "star", nameMarkdown: nil, text: "You have earn a new achievement")
            case .app:
                StatusRetweetHeaderComponent(iconSystemName: "app", nameMarkdown: data.user?.extra.nameMarkdown, text: "app notification")
            }
            if let note = data.note {
                MisskeyStatusComponent(misskey: note)
            }
            if let user = data.user {
                if [shared.Notification_.Type_.follow, shared.Notification_.Type_.followrequestaccepted, shared.Notification_.Type_.receivefollowrequest].contains(data.type) {
                    HStack {
                        UserComponent(user: user)
                        Spacer()
                    }
                }
            }
        }
    }
}

//#Preview {
//    MisskeyNotificationComponent()
//}
