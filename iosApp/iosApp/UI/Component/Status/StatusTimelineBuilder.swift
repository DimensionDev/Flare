import SwiftUI
import shared

struct StatusTimelineComponent: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let data: PagingState<UiTimeline>
    var body: some View {
        switch onEnum(of: data) {
        case .empty: Text("timeline_load_empty", comment: "Timeline is empty")
        case .error(let error): Text("timeline_load_error", comment: "Timeline loading error")
        case .loading:
                        ForEach(0...10, id: \.self) { _ in
                            StatusPlaceHolder()
                                .if(horizontalSizeClass != .compact) { view in
                                    view.padding([.horizontal])
                                }
                        }
        case .success(let success):
            ForEach(0..<success.itemCount, id: \.self) { index in
                let data = success.peek(index: index)
                VStack {
                    if let status = data {
                        StatusItemView(
                            data: status
                        )
                    } else {
                        StatusPlaceHolder()
                    }
                }
                .onAppear {
                    success.get(index: index)
                }
                .if(horizontalSizeClass != .compact) { view in
                    view.padding([.horizontal])
                }
            }
        }
    }
}

struct StatusItemView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline
    var body: some View {
        if let topMessage = data.topMessage {
            let icon = switch topMessage.icon {
            case .retweet: "arrow.left.arrow.right"
            case .follow: "person.badge.plus"
            case .favourite: "star"
            case .mention: "at"
            case .poll: "list.bullet"
            case .edit: "pencil"
            case .info: "app"
            case .reply: "arrowshape.turn.up.left"
            }
            let text = switch onEnum(of: topMessage.type) {
            case .bluesky(let data):
                switch onEnum(of: data) {
                case .follow: String(localized: "bluesky_notification_follow")
                case .like: String(localized: "bluesky_notification_like")
                case .mention: String(localized: "bluesky_notification_mention")
                case .quote: String(localized: "bluesky_notification_quote")
                case .reply: String(localized: "bluesky_notification_reply")
                case .repost: String(localized: "bluesky_notification_repost")
                }
            case .mastodon(let data):
                switch onEnum(of: data) {
                case .favourite: String(localized: "mastodon_notification_favourite")
                case .follow: String(localized: "mastodon_notification_follow")
                case .followRequest: String(localized: "mastodon_notification_follow_request")
                case .mention: String(localized: "mastodon_notification_mention")
                case .poll: String(localized: "mastodon_notification_poll")
                case .reblogged: String(localized: "mastodon_notification_reblog")
                case .status: String(localized: "mastodon_notification_status")
                case .update: String(localized: "mastodon_notification_update")
                }
            case .misskey(let data):
                switch onEnum(of: data) {
                case .achievementEarned:  String(localized: "misskey_notification_achievement_earned")
                case .app: String(localized: "misskey_notification_app")
                case .follow: String(localized: "misskey_notification_follow")
                case .followRequestAccepted: String(localized: "misskey_notification_follow_request_accepted")
                case .mention: String(localized: "misskey_notification_mention")
                case .pollEnded: String(localized: "misskey_notification_poll_ended")
                case .quote: String(localized: "misskey_notification_quote")
                case .reaction: String(localized: "misskey_notification_reaction")
                case .receiveFollowRequest: String(localized: "misskey_notification_receive_follow_request")
                case .renote: String(localized: "misskey_notification_renote")
                case .reply: String(localized: "misskey_notification_reply")
                }
            case .vVO(let data):
                switch onEnum(of: data) {
                case .custom(let message): message.message
                case .like: String(localized: "vvo_notification_like")
                }
            case .xQT(let data):
                switch onEnum(of: data) {
                case .custom(let message): message.message
                case .mention: String(localized: "xqt_notification_mention")
                case .retweet: String(localized: "xqt_notification_retweet")
                }
            }
            StatusRetweetHeaderComponent(iconSystemName: icon, nameMarkdown: topMessage.user?.name.markdown, text: text)
                .onTapGesture {
                    topMessage.user?.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                }
        }
        if let content = data.content {
            switch onEnum(of: content) {
            case .status(let data): CommonStatusComponent(
                data: data,
                onMediaClick: { _, _ in

                }
            ).onTapGesture {
                data.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
            }
            case .user(let data):
                UserComponent(
                    user: data.value,
                    onUserClicked: {
                        data.value.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                    }
                )
            case .userList(let data): EmptyView()
            }
        }
    }
}

struct StatusPlaceHolder: View {
    var body: some View {
        StatusItemView(
            data: createSampleStatus(
                user: createSampleUser()
            )
        )
        .redacted(reason: .placeholder)
    }
}
