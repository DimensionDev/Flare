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
            case .follow: String(localized: "bluesky_notification_item_followed_you")
            case .like: String(localized: "bluesky_notification_item_favourited_your_status")
            case .mention: String(localized: "bluesky_notification_item_mentioned_you")
            case .quote: String(localized: "bluesky_notification_item_quoted_your_status")
            case .reply: String(localized: "bluesky_notification_item_replied_to_you")
            case .repost: String(localized: "bluesky_notification_item_reblogged_your_status")
            case .unKnown: String(localized: "bluesky_notification_item_unKnown")
            case .starterpackJoined: String(localized: "bluesky_notification_item_starterpack_joined")
            }
        case .mastodon(let data):
            switch onEnum(of: data) {
            case .favourite: String(localized: "mastodon_notification_item_favourited_your_status")
            case .follow: String(localized: "mastodon_notification_item_followed_you")
            case .followRequest: String(localized: "mastodon_notification_item_requested_follow")
            case .mention: String(localized: "mastodon_notification_item_mentioned_you")
            case .poll: String(localized: "mastodon_notification_item_poll_ended")
            case .reblogged: String(localized: "mastodon_notification_item_reblogged_your_status")
            case .status: String(localized: "mastodon_notification_item_posted_status")
            case .update: String(localized: "mastodon_notification_item_updated_status")
            case .unKnown: String(localized: "mastodon_notification_item_updated_status")
            }
        case .misskey(let data):
            switch onEnum(of: data) {
            case .achievementEarned:  String(localized: "misskey_notification_item_achievement_earned")
            case .app: String(localized: "misskey_notification_item_app")
            case .follow: String(localized: "misskey_notification_item_followed_you")
            case .followRequestAccepted: String(localized: "misskey_notification_item_follow_request_accepted")
            case .mention: String(localized: "misskey_notification_item_mentioned_you")
            case .pollEnded: String(localized: "misskey_notification_item_poll_ended")
            case .quote: String(localized: "misskey_notification_item_quoted_your_status")
            case .reaction: String(localized: "misskey_notification_item_reacted_to_your_status")
            case .receiveFollowRequest: String(localized: "misskey_notification_item_follow_request_accepted")
            case .renote: String(localized: "misskey_notification_item_reposted_your_status")
            case .reply: String(localized: "misskey_notification_item_replied_to_you")
            }
        case .vVO(let data):
            switch onEnum(of: data) {
            case .custom(let message): message.message
            case .like: String(localized: "vvo_notification_like")
            }
        case .xQT(let data):
            switch onEnum(of: data) {
            case .custom(let message): message.message
            case .mention: String(localized: "xqt_item_mention_status")
            case .retweet: String(localized: "xqt_item_reblogged_status")
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