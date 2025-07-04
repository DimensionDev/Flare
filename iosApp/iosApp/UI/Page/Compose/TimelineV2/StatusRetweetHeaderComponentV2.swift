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
    
    // MARK: - 私有方法

    /// 处理转发头部点击事件
    private func handleTopMessageTap() {
        // 🔥 实现转发头部点击跳转到执行操作的用户页面
        guard let user = topMessage.user else {
            FlareLog.warning("StatusRetweetHeaderV2 No user in topMessage")
            return
        }

        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
        let userKey = createMicroBlogKey(from: user)

        FlareLog.debug("StatusRetweetHeaderV2 Navigate to profile: \(user.key)")
        router.navigate(to: .profile(
            accountType: accountType,
            userKey: userKey
        ))
    }

    /// 从User创建MicroBlogKey
    private func createMicroBlogKey(from user: User) -> MicroBlogKey {
        // User.key已经是String格式的ID，需要推断host
        let host = extractHostFromHandle(user.handle)
        return MicroBlogKey(id: user.key, host: host)
    }

    /// 从用户handle提取host信息
    private func extractHostFromHandle(_ handle: String) -> String {
        // handle格式通常是 @username@host 或 @username
        if handle.contains("@") {
            let components = handle.components(separatedBy: "@")
            if components.count >= 3 {
                // @username@host 格式
                return components[2]
            } else if components.count == 2 {
                // @username 格式，需要根据其他信息推断
                return "mastodon.social" // 默认值
            }
        }
        return "unknown.host"
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
            return getBlueSkyText(for: type)
        case let .mastodon(type):
            return getMastodonText(for: type)
        case let .misskey(type):
            return getMisskeyText(for: type)
        case let .vVO(type):
            return getVVOText(for: type)
        case let .xQT(type):
            return getXQTText(for: type)
        }
    }
    
    /// BlueSky平台的本地化文本
    private func getBlueSkyText(for type: BlueSkyMessageType) -> String {
        switch type {
        case .follow:
            return String(localized: "bluesky_notification_item_followed_you")
        case .like:
            return String(localized: "bluesky_notification_item_favourited_your_status")
        case .mention:
            return String(localized: "bluesky_notification_item_mentioned_you")
        case .quote:
            return String(localized: "bluesky_notification_item_quoted_your_status")
        case .reply:
            return String(localized: "bluesky_notification_item_replied_to_you")
        case .repost:
            return String(localized: "bluesky_notification_item_reblogged_your_status")
        case .unKnown:
            return String(localized: "bluesky_notification_item_unKnown")
        case .starterpackJoined:
            return String(localized: "bluesky_notification_item_starterpack_joined")
        case .pinned:
            return String(localized: "bluesky_notification_item_pin")
        }
    }
    
    /// Mastodon平台的本地化文本
    private func getMastodonText(for type: MastodonMessageType) -> String {
        switch type {
        case .favourite:
            return String(localized: "mastodon_notification_item_favourited_your_status")
        case .follow:
            return String(localized: "mastodon_notification_item_followed_you")
        case .followRequest:
            return String(localized: "mastodon_notification_item_requested_follow")
        case .mention:
            return String(localized: "mastodon_notification_item_mentioned_you")
        case .poll:
            return String(localized: "mastodon_notification_item_poll_ended")
        case .reblogged:
            return String(localized: "mastodon_notification_item_reblogged_your_status")
        case .status:
            return String(localized: "mastodon_notification_item_posted_status")
        case .update:
            return String(localized: "mastodon_notification_item_updated_status")
        case .unKnown:
            return String(localized: "mastodon_notification_item_updated_status")
        case .pinned:
            return String(localized: "mastodon_item_pinned")
        }
    }
    
    /// Misskey平台的本地化文本
    private func getMisskeyText(for type: MisskeyMessageType) -> String {
        switch type {
        case .achievementEarned:
            return String(localized: "misskey_notification_achievement_earned")
        case .app:
            return String(localized: "misskey_notification_app")
        case .follow:
            return String(localized: "misskey_notification_follow")
        case .followRequestAccepted:
            return String(localized: "misskey_notification_follow_request_accepted")
        case .mention:
            return String(localized: "misskey_notification_mention")
        case .pollEnded:
            return String(localized: "misskey_notification_poll_ended")
        case .quote:
            return String(localized: "misskey_notification_quote")
        case .reaction:
            return String(localized: "misskey_notification_reaction")
        case .receiveFollowRequest:
            return String(localized: "misskey_notification_receive_follow_request")
        case .renote:
            return String(localized: "misskey_notification_renote")
        case .reply:
            return String(localized: "misskey_notification_reply")
        case .unKnown:
            return String(localized: "misskey_notification_unknown")
        case .pinned:
            return String(localized: "misskey_item_pinned")
        }
    }
    
    /// vVO平台的本地化文本
    private func getVVOText(for type: VVOMessageType) -> String {
        switch type {
        case let .custom(message):
            return message
        case .like:
            return String(localized: "vvo_notification_like")
        }
    }
    
    /// xQT平台的本地化文本
    private func getXQTText(for type: XQTMessageType) -> String {
        switch type {
        case let .custom(message):
            return message
        case .mention:
            return String(localized: "xqt_item_mention_status")
        case .retweet:
            return String(localized: "xqt_item_reblogged_status")
        }
    }
}
 
