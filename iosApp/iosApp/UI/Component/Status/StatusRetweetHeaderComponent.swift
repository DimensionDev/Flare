import SwiftUI
import MarkdownUI
import shared
import Awesome

struct StatusRetweetHeaderComponent: View {
    let topMessage: UiTimeline.TopMessage
    var body: some View {
        let text = switch onEnum(of: topMessage.type) {
        case .bluesky(let data):
            switch onEnum(of: data) {
            case .follow: String(localized: "bluesky_notification_follow")
            case .like: String(localized: "bluesky_notification_like")
            case .mention: String(localized: "bluesky_notification_mention")
            case .quote: String(localized: "bluesky_notification_quote")
            case .reply: String(localized: "bluesky_notification_reply")
            case .repost: String(localized: "bluesky_notification_repost")
            case .unKnown: String(localized: "bluesky_notification_unKnown")
            case .starterpackJoined: String(localized: "bluesky_notification_starterpackJoined")
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
            case .unKnown: String(localized: "mastodon_notification_update")
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
            case .unKnown: String(localized: "misskey_notification_unknown")
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
        let nameMarkdown = topMessage.user?.name.markdown
        HStack(alignment: .center) {
            switch topMessage.icon {
            case .retweet: Awesome.Classic.Solid.retweet.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .follow: Awesome.Classic.Solid.userPlus.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .favourite: Awesome.Classic.Solid.heart.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .mention: Awesome.Classic.Solid.at.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .poll: Awesome.Classic.Solid.squarePollHorizontal.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .edit: Awesome.Classic.Solid.pen.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .info: Awesome.Classic.Solid.circleInfo.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .reply: Awesome.Classic.Solid.reply.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            case .quote: Awesome.Classic.Solid.quoteLeft.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
                    .size(14)
                    .frame(alignment: .center)
            }
            Markdown {
                (nameMarkdown ?? "") + (nameMarkdown == nil ? "" : " ") + text
            }
            .frame(alignment: .center)
            .lineLimit(1)
            .markdownTextStyle(\.text) {
                FontSize(12)
            }
            .markdownInlineImageProvider(.emojiSmall)
            Spacer()
        }
        .foregroundColor(.primary)
        .opacity(0.6)
    }
}
