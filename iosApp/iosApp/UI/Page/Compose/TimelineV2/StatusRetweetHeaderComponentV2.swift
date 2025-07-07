import Awesome
import MarkdownUI
import shared
import SwiftUI

struct StatusRetweetHeaderComponentV2: View {
    let topMessage: TopMessage
    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

    var body: some View {
        let text = getLocalizedText(for: topMessage.type)
        let nameMarkdown = topMessage.user?.name.markdown ?? ""

        Button(action: {
            handleTopMessageTap()
        }) {
            HStack(alignment: .center) {
                // 图标显示
                getIcon(for: topMessage.icon)
                    .foregroundColor(theme.labelColor)
                    .font(.system(size: 14))
                    .frame(maxWidth: 14, maxHeight: 14, alignment: .center)

                // 文本显示：用户名 + 操作描述
                Markdown {
                    nameMarkdown + (nameMarkdown.isEmpty ? "" : " ") + text
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
        .buttonStyle(.plain)
    }

    /// 处理转发头部点击事件
    private func handleTopMessageTap() {
        // 🔥 实现转发头部点击跳转到执行操作的用户页面
        guard let user = topMessage.user else {
            FlareLog.warning("StatusRetweetHeaderV2 No user in topMessage")
            return
        }

        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
        let userKey = user.createMicroBlogKey(from: user)

        FlareLog.debug("StatusRetweetHeaderV2 Navigate to profile: \(user.key)")
        router.navigate(to: .profile(
            accountType: accountType,
            userKey: userKey
        ))
    }

    /// 根据图标类型获取对应的图标
    @ViewBuilder
    private func getIcon(for iconType: TopMessageIcon) -> some View {
        switch iconType {
        case .retweet:
            Awesome.Classic.Solid.retweet.image
        case .follow:
            Awesome.Classic.Solid.userPlus.image
        case .favourite:
            Awesome.Classic.Solid.heart.image
        case .mention:
            Awesome.Classic.Solid.at.image
        case .poll:
            Awesome.Classic.Solid.squarePollHorizontal.image
        case .edit:
            Awesome.Classic.Solid.pen.image
        case .info:
            Awesome.Classic.Solid.circleInfo.image
        case .reply:
            Awesome.Classic.Solid.reply.image
        case .quote:
            Awesome.Classic.Solid.quoteLeft.image
        case .pin:
            Awesome.Classic.Solid.thumbtack.image
        }
    }

    /// 根据消息类型获取本地化文本
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
