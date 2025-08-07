import Awesome
import MarkdownUI
import shared
import SwiftUI

struct StatusRetweetHeaderComponentV2: View, Equatable {
    let topMessage: TopMessage
    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

    
    static func == (lhs: StatusRetweetHeaderComponentV2, rhs: StatusRetweetHeaderComponentV2) -> Bool {
        lhs.topMessage.statusKey == rhs.topMessage.statusKey
    }

    var body: some View {
        let text = getLocalizedText(for: topMessage.type)
        let nameMarkdown = topMessage.user?.name.markdown ?? ""

        Button(action: {
            handleTopMessageTap()
        }) {
            HStack(alignment: .center) {
                getIcon(for: topMessage.icon)
                    .frame(maxWidth: 14, maxHeight: 14, alignment: .center)

                Markdown {
                    nameMarkdown + (nameMarkdown.isEmpty ? "" : " ") + text
                }
                .frame(alignment: .center)
                .lineLimit(1)
                .markdownTheme(.flareMarkdownStyle(using: theme.captionTextStyle, fontScale: theme.fontSizeScale))
                .markdownTextStyle(\.text) {
                    FontSize(12)
                }
                .markdownInlineImageProvider(.emojiSmall)

                Spacer()
            }
            .opacity(0.6)
        }
        .buttonStyle(.plain)
    }

    private func handleTopMessageTap() {
        guard let user = topMessage.user else {
            return
        }

        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
        let userKey = user.createMicroBlogKey()

        router.navigate(to: .profile(
            accountType: accountType,
            userKey: userKey
        ))
    }

    
    @ViewBuilder
    private func getIcon(for iconType: TopMessageIcon) -> some View {
        switch iconType {
        case .retweet:
            Awesome.Classic.Solid.retweet.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .follow:
            Awesome.Classic.Solid.userPlus.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .favourite:
            Awesome.Classic.Solid.heart.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .mention:
            Awesome.Classic.Solid.at.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .poll:
            Awesome.Classic.Solid.squarePollHorizontal.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .edit:
            Awesome.Classic.Solid.pen.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .info:
            Awesome.Classic.Solid.circleInfo.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .reply:
            Awesome.Classic.Solid.reply.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .quote:
            Awesome.Classic.Solid.quoteLeft.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        case .pin:
            Awesome.Classic.Solid.thumbtack.image
                .foregroundColor(UIColor(theme.labelColor))
                .size(14)
        }
    }

    
    private func getLocalizedText(for messageType: TopMessageType) -> String {
        switch messageType {
        case let .bluesky(type):
            getBlueSkyText(for: type)
        case let .mastodon(type):
            getMastodonText(for: type)
        case let .misskey(type):
            getMisskeyText(for: type)
        case let .vVO(type):
            getVVOText(for: type)
        case let .xQT(type):
            getXQTText(for: type)
        }
    }

    /// BlueSky平台的本地化文本
    private func getBlueSkyText(for type: BlueSkyMessageType) -> String {
        switch type {
        case .follow:
            String(localized: "bluesky_notification_item_followed_you")
        case .like:
            String(localized: "bluesky_notification_item_favourited_your_status")
        case .mention:
            String(localized: "bluesky_notification_item_mentioned_you")
        case .quote:
            String(localized: "bluesky_notification_item_quoted_your_status")
        case .reply:
            String(localized: "bluesky_notification_item_replied_to_you")
        case .repost:
            String(localized: "bluesky_notification_item_reblogged_your_status")
        case .unKnown:
            String(localized: "bluesky_notification_item_unKnown")
        case .starterpackJoined:
            String(localized: "bluesky_notification_item_starterpack_joined")
        case .pinned:
            String(localized: "bluesky_notification_item_pin")
        }
    }

    /// Mastodon平台的本地化文本
    private func getMastodonText(for type: MastodonMessageType) -> String {
        switch type {
        case .favourite:
            String(localized: "mastodon_notification_item_favourited_your_status")
        case .follow:
            String(localized: "mastodon_notification_item_followed_you")
        case .followRequest:
            String(localized: "mastodon_notification_item_requested_follow")
        case .mention:
            String(localized: "mastodon_notification_item_mentioned_you")
        case .poll:
            String(localized: "mastodon_notification_item_poll_ended")
        case .reblogged:
            String(localized: "mastodon_notification_item_reblogged_your_status")
        case .status:
            String(localized: "mastodon_notification_item_posted_status")
        case .update:
            String(localized: "mastodon_notification_item_updated_status")
        case .unKnown:
            String(localized: "mastodon_notification_item_updated_status")
        case .pinned:
            String(localized: "mastodon_item_pinned")
        }
    }

    /// Misskey平台的本地化文本
    private func getMisskeyText(for type: MisskeyMessageType) -> String {
        switch type {
        case .achievementEarned:
            String(localized: "misskey_notification_achievement_earned")
        case .app:
            String(localized: "misskey_notification_app")
        case .follow:
            String(localized: "misskey_notification_follow")
        case .followRequestAccepted:
            String(localized: "misskey_notification_follow_request_accepted")
        case .mention:
            String(localized: "misskey_notification_mention")
        case .pollEnded:
            String(localized: "misskey_notification_poll_ended")
        case .quote:
            String(localized: "misskey_notification_quote")
        case .reaction:
            String(localized: "misskey_notification_reaction")
        case .receiveFollowRequest:
            String(localized: "misskey_notification_receive_follow_request")
        case .renote:
            String(localized: "misskey_notification_renote")
        case .reply:
            String(localized: "misskey_notification_reply")
        case .unKnown:
            String(localized: "misskey_notification_unknown")
        case .pinned:
            String(localized: "misskey_item_pinned")
        }
    }

    /// vVO平台的本地化文本
    private func getVVOText(for type: VVOMessageType) -> String {
        switch type {
        case let .custom(message):
            message
        case .like:
            String(localized: "vvo_notification_like")
        }
    }

    /// xQT平台的本地化文本
    private func getXQTText(for type: XQTMessageType) -> String {
        switch type {
        case let .custom(message):
            message
        case .mention:
            String(localized: "xqt_item_mention_status")
        case .retweet:
            String(localized: "xqt_item_reblogged_status")
        }
    }
}
