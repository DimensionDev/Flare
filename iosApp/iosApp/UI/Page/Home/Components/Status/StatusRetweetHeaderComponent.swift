import Awesome
import MarkdownUI
import shared
import SwiftUI

struct StatusRetweetHeaderComponent: View {
    let topMessage: UiTimeline.TopMessage
    var body: some View {
        let text = switch onEnum(of: topMessage.type) {
        case let .bluesky(data):
            switch onEnum(of: data) {
            case .follow: String(localized: "bluesky_notification_item_followed_you")
            case .like: String(localized: "bluesky_notification_item_favourited_your_status")
            case .mention: String(localized: "bluesky_notification_item_mentioned_you")
            case .quote: String(localized: "bluesky_notification_item_quoted_your_status")
            case .reply: String(localized: "bluesky_notification_item_replied_to_you")
            case .repost: String(localized: "bluesky_notification_item_reblogged_your_status")
            case .unKnown: String(localized: "bluesky_notification_item_unKnown")
            case .starterpackJoined: String(localized: "bluesky_notification_item_starterpack_joined")
            case .pinned: String(localized: "bluesky_notification_item_pin")
            }
        case let .mastodon(data):
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
            case .pinned: String(localized: "mastodon_item_pinned")
            }
        case let .misskey(data):
            switch onEnum(of: data) {
            case .achievementEarned: String(localized: "misskey_notification_achievement_earned")
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
            case .pinned: String(localized: "misskey_item_pinned")
            }
        case let .vVO(data):
            switch onEnum(of: data) {
            case let .custom(message): message.message
            case .like: String(localized: "vvo_notification_like")
            }
        case let .xQT(data):
            switch onEnum(of: data) {
            case let .custom(message): message.message
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
            case .pin: Awesome.Classic.Solid.thumbtack.image
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
