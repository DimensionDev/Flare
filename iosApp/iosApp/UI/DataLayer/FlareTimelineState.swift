import Foundation
import shared

struct RichText: Equatable, Hashable {
    let raw: String // 对应 UiRichText.raw
    let markdown: String // 对应 UiRichText.markdown
    let isRTL: Bool // 对应 UiRichText.isRTL

    init(raw: String, markdown: String = "", isRTL: Bool = false) {
        self.raw = raw
        self.markdown = markdown.isEmpty ? raw : markdown
        self.isRTL = isRTL
    }
}

/// 用户信息结构 - 对应 shared.UiUserV2
struct User: Equatable, Hashable {
    let key: String // 对应 UiUserV2.key (MicroBlogKey转换为String)
    let name: RichText // 对应 UiUserV2.name
    let handle: String // 对应 UiUserV2.handle
    let avatar: String // 对应 UiUserV2.avatar
    let banner: String? // 对应 UiUserV2.banner
    let description: RichText? // 对应 UiUserV2.description

    var handleWithoutFirstAt: String {
        handle.hasPrefix("@") ? String(handle.dropFirst()) : handle
    }
}

/// 媒体类型枚举 - 对应 shared.UiMedia的子类型
enum TimelineMediaType: String, CaseIterable, Equatable, Hashable {
    case image // 对应 UiMediaImage
    case video // 对应 UiMediaVideo
    case gif // 对应 UiMediaGif
    case audio // 对应 UiMediaAudio
}

/// 媒体信息结构 - 对应 shared.UiMedia
struct Media: Equatable, Hashable {
    let url: String // 对应 UiMedia.url
    let previewUrl: String? // 对应 UiMedia.previewUrl
    let type: TimelineMediaType // 对应 UiMedia类型
    let altText: String? // 对应 UiMedia.altText
    let width: Int? // 对应 UiMedia.width
    let height: Int? // 对应 UiMedia.height
}

/// 卡片信息结构 - 对应 shared.UiCard
struct Card: Equatable, Hashable {
    let url: String // 对应 UiCard.url
    let title: String? // 对应 UiCard.title
    let description: String? // 对应 UiCard.description
    let media: Media? // 对应 UiCard.media
}

/// 转发头部消息结构 - 对应 shared.UiTimeline.TopMessage
struct TopMessage: Equatable, Hashable {
    let user: User? // 对应 TopMessage.user
    let icon: TopMessageIcon // 对应 TopMessage.icon
    let type: TopMessageType // 对应 TopMessage.type
    let statusKey: String // 对应 TopMessage.statusKey.id
}

/// 转发头部图标类型 - 对应 shared.UiTimeline.TopMessage.Icon
enum TopMessageIcon: String, CaseIterable, Equatable, Hashable {
    case retweet = "Retweet"
    case follow = "Follow"
    case favourite = "Favourite"
    case mention = "Mention"
    case poll = "Poll"
    case edit = "Edit"
    case info = "Info"
    case reply = "Reply"
    case quote = "Quote"
    case pin = "Pin"
}

/// 转发头部消息类型 - 对应 shared.UiTimeline.TopMessage.MessageType
enum TopMessageType: Equatable, Hashable {
    case bluesky(BlueSkyMessageType)
    case mastodon(MastodonMessageType)
    case misskey(MisskeyMessageType)
    case vVO(VVOMessageType)
    case xQT(XQTMessageType)
}

enum BlueSkyMessageType: String, CaseIterable, Equatable, Hashable {
    case follow, like, mention, quote, reply, repost, unKnown, starterpackJoined, pinned
}

enum MastodonMessageType: String, CaseIterable, Equatable, Hashable {
    case favourite, follow, followRequest, mention, poll, reblogged, status, update, unKnown, pinned
}

enum MisskeyMessageType: String, CaseIterable, Equatable, Hashable {
    case achievementEarned, app, follow, followRequestAccepted, mention, pollEnded, quote, reaction, receiveFollowRequest, renote, reply, unKnown, pinned
}

enum VVOMessageType: Equatable, Hashable {
    case custom(String)
    case like
}

enum XQTMessageType: Equatable, Hashable {
    case custom(String)
    case mention
    case retweet
}

/// 回复内容结构 - 对应 shared.UiTimelineItemContentStatusAboveTextContent
enum AboveTextContent: Equatable, Hashable {
    case replyTo(handle: String) // 对应 UiTimelineItemContentStatusAboveTextContentReplyTo
}

/// 底部内容结构 - 对应 shared.UiTimelineItemContentStatusBottomContent
enum BottomContent: Equatable, Hashable {
    case reaction(emojiReactions: [EmojiReaction]) // 对应 UiTimelineItemContentStatusBottomContentReaction
}

/// 表情反应结构 - 对应 shared.UiTimelineItemContentStatusBottomContentReactionEmojiReaction
struct EmojiReaction: Equatable, Hashable {
    let name: String // 对应 EmojiReaction.name
    let url: String // 对应 EmojiReaction.url
    let count: Int // 对应 EmojiReaction.count
    let me: Bool // 对应 EmojiReaction.me
    let isUnicode: Bool // 对应 EmojiReaction.isUnicode
    let isImageReaction: Bool // 对应 EmojiReaction.isImageReaction
}

/// 顶部结束内容结构 - 对应 shared.UiTimelineItemContentStatusTopEndContent
enum TopEndContent: Equatable, Hashable {
    case visibility(type: VisibilityType) // 对应 UiTimelineItemContentStatusTopEndContentVisibility
}

/// 可见性类型枚举 - 对应 shared.UiTimelineItemContentStatusTopEndContentVisibilityType
enum VisibilityType: String, CaseIterable, Equatable, Hashable {
    case publicType = "public" // 对应 UiTimelineItemContentStatusTopEndContentVisibilityType.public_
    case home // 对应 UiTimelineItemContentStatusTopEndContentVisibilityType.home
    case followers // 对应 UiTimelineItemContentStatusTopEndContentVisibilityType.followers
    case specified // 对应 UiTimelineItemContentStatusTopEndContentVisibilityType.specified
}

/// 投票结构 - 对应 shared.UiPoll
struct Poll: Equatable, Hashable {
    let options: [PollOption] // 对应 UiPoll.options
    let expiresAt: Date? // 对应 UiPoll.expiresAt
    let expired: Bool // 对应 UiPoll.expired
    let multiple: Bool // 对应 UiPoll.multiple
    let votesCount: Int // 对应 UiPoll.votesCount
    let votersCount: Int? // 对应 UiPoll.votersCount
}

/// 投票选项结构 - 对应 shared.UiPollOption
struct PollOption: Equatable, Hashable {
    let title: String // 对应 UiPollOption.title
    let votesCount: Int // 对应 UiPollOption.votesCount
    let voted: Bool // 对应 UiPollOption.voted
}

struct TimelineItem: Identifiable, Equatable, Hashable {
    // - 核心标识字段

    let id: String // 对应 UiTimelineItemContentStatus.statusKey.id

    // - 内容字段 (扩展为复杂结构)

    let content: RichText // 对应 UiTimelineItemContentStatus.content (UiRichText)
    let user: User? // 对应 UiTimelineItemContentStatus.user (UiUserV2)
    let timestamp: Date // 对应 UiTimelineItemContentStatus.createdAt
    let images: [Media] // 对应 UiTimelineItemContentStatus.images ([UiMedia])

    // - 新增字段 (来自StatusViewModel需求)

    let url: String // 对应 UiTimelineItemContentStatus.url
    let platformType: String // 对应 UiTimelineItemContentStatus.platformType.name
    let aboveTextContent: AboveTextContent? // 对应 UiTimelineItemContentStatus.aboveTextContent
    let contentWarning: RichText? // 对应 UiTimelineItemContentStatus.contentWarning
    let card: Card? // 对应 UiTimelineItemContentStatus.card
    let quote: [TimelineItem] // 对应 UiTimelineItemContentStatus.quote (递归结构)
    let bottomContent: BottomContent? // 对应 UiTimelineItemContentStatus.bottomContent
    let topEndContent: TopEndContent? // 对应 UiTimelineItemContentStatus.topEndContent
    let poll: Poll? // 对应 UiTimelineItemContentStatus.poll

    // - 转发头部信息 (新增)

    let topMessage: TopMessage? // 对应 UiTimeline.topMessage
    let sensitive: Bool // 对应 UiTimelineItemContentStatus.sensitive

    // - 兼容性字段 (保持向后兼容)

    let visibility: String // 从topEndContent.visibility转换而来
    let language: String? // 暂时保留，未来可能从其他地方获取

    // - 操作相关字段

    let actions: [StatusAction] // 对应 UiTimelineItemContentStatus.actions

    // - UI状态字段 (可变)

    var likeCount: Int
    var isLiked: Bool
    var retweetCount: Int
    var isRetweeted: Bool
    var replyCount: Int
    var bookmarkCount: Int
    var isBookmarked: Bool

    init(
        id: String,
        content: RichText,
        user: User?,
        timestamp: Date,
        images: [Media] = [],
        url: String = "",
        platformType: String = "",
        aboveTextContent: AboveTextContent? = nil,
        contentWarning: RichText? = nil,
        card: Card? = nil,
        quote: [TimelineItem] = [],
        bottomContent: BottomContent? = nil,
        topEndContent: TopEndContent? = nil,
        poll: Poll? = nil,
        topMessage: TopMessage? = nil,
        sensitive: Bool = false,
        visibility: String = "public",
        language: String? = nil,
        actions: [StatusAction] = []
    ) {
        self.id = id
        self.content = content
        self.user = user
        self.timestamp = timestamp
        self.images = images
        self.url = url
        self.platformType = platformType
        self.aboveTextContent = aboveTextContent
        self.contentWarning = contentWarning
        self.card = card
        self.quote = quote
        self.bottomContent = bottomContent
        self.topEndContent = topEndContent
        self.poll = poll
        self.topMessage = topMessage
        self.sensitive = sensitive
        self.visibility = visibility
        self.language = language
        self.actions = actions

        var likeCount = 0
        var isLiked = false
        var retweetCount = 0
        var isRetweeted = false
        var replyCount = 0
        var bookmarkCount = 0
        var isBookmarked = false

//        FlareLog.debug("TimelineItem Extracting state for item: \(id)")
//        FlareLog.debug("TimelineItem Processing \(actions.count) actions")

        for (index, action) in actions.enumerated() {
            if case let .item(item) = onEnum(of: action) {
                if let likeAction = item as? StatusActionItemLike {
                    likeCount = Int(likeAction.count)
                    isLiked = likeAction.liked
//                    FlareLog.debug("TimelineItem Action[\(index)] Like - count: \(likeCount), liked: \(isLiked)")
                } else if let retweetAction = item as? StatusActionItemRetweet {
                    retweetCount = Int(retweetAction.count)
                    isRetweeted = retweetAction.retweeted
//                    FlareLog.debug("TimelineItem Action[\(index)] Retweet - count: \(retweetCount), retweeted: \(isRetweeted)")
                } else if let replyAction = item as? StatusActionItemReply {
                    replyCount = Int(replyAction.count)
//                    FlareLog.debug("TimelineItem Action[\(index)] Reply - count: \(replyCount)")
                } else {
//                    FlareLog.debug("TimelineItem Action[\(index)] Other - type: \(type(of: item))")
                }
            } else if case let .group(group) = onEnum(of: action) {
               // FlareLog.debug("TimelineItem Action[\(index)] Group - displayItem: \(type(of: group.displayItem))")

                // 遍历Group中的所有SubActions来提取数据
                for (subIndex, subAction) in group.actions.enumerated() {
                    switch onEnum(of: subAction) {
                    case let .item(subItem):
                        if let retweetItem = subItem as? StatusActionItemRetweet {
                            retweetCount = Int(retweetItem.count)
                            isRetweeted = retweetItem.retweeted
//                            FlareLog.debug("TimelineItem SubAction[\(subIndex)] Retweet - count: \(retweetCount), retweeted: \(isRetweeted)")
                        } else if let bookmarkItem = subItem as? StatusActionItemBookmark {
                            bookmarkCount = Int(bookmarkItem.count)
                            isBookmarked = bookmarkItem.bookmarked
//                            FlareLog.debug("TimelineItem SubAction[\(subIndex)] Bookmark - count: \(bookmarkCount), bookmarked: \(isBookmarked)")
                        } else if let quoteItem = subItem as? StatusActionItemQuote {
                            // Quote暂时不处理，但记录日志
//                            FlareLog.debug("TimelineItem SubAction[\(subIndex)] Quote - count: \(quoteItem.count)")
                        }
                    case .group:
                        break // FlareLog.debug("TimelineItem SubAction[\(subIndex)] Nested Group")
                    case .asyncActionItem:
                        break // FlareLog.debug("TimelineItem SubAction[\(subIndex)] Async Action")
                    }
                }
            }
        }

        self.likeCount = likeCount
        self.isLiked = isLiked
        self.retweetCount = retweetCount
        self.isRetweeted = isRetweeted
        self.replyCount = replyCount
        self.bookmarkCount = bookmarkCount
        self.isBookmarked = isBookmarked

//        FlareLog.debug("TimelineItem Final state for \(id):")
//        FlareLog.debug("   Like: \(likeCount) (liked: \(isLiked))")
//        FlareLog.debug("   Retweet: \(retweetCount) (retweeted: \(isRetweeted))")
//        FlareLog.debug("   Reply: \(replyCount)")
//        FlareLog.debug("   Bookmark: \(bookmarkCount) (bookmarked: \(isBookmarked))")
    }

    mutating func updateLikeState(liked: Bool) {
        isLiked = liked
        likeCount += liked ? 1 : -1
        likeCount = max(0, likeCount) // 确保不为负数
    }

    mutating func updateRetweetState(retweeted: Bool) {
        isRetweeted = retweeted
        retweetCount += retweeted ? 1 : -1
        retweetCount = max(0, retweetCount) // 确保不为负数
    }

    mutating func updateBookmarkState(bookmarked: Bool) {
        isBookmarked = bookmarked
        bookmarkCount += bookmarked ? 1 : -1
        bookmarkCount = max(0, bookmarkCount) // 确保不为负数
    }

    mutating func updateReplyCount(_ count: Int) {
        replyCount = max(0, count)
    }

    func withUpdatedLikeState(count: Int, isLiked: Bool) -> TimelineItem {
        var newItem = self
        newItem.likeCount = max(0, count)
        newItem.isLiked = isLiked
        return newItem
    }

    func withUpdatedRetweetState(count: Int, isRetweeted: Bool) -> TimelineItem {
        var newItem = self
        newItem.retweetCount = max(0, count)
        newItem.isRetweeted = isRetweeted
        return newItem
    }

    func withUpdatedBookmarkState(count: Int, isBookmarked: Bool) -> TimelineItem {
        var newItem = self
        newItem.bookmarkCount = max(0, count)
        newItem.isBookmarked = isBookmarked
        return newItem
    }

    static func from(_ uiTimeline: UiTimeline) -> TimelineItem {
        // 处理不同类型的content
        if let statusContent = uiTimeline.content as? UiTimelineItemContentStatus {
            let status = statusContent

//            FlareLog.debug("TimelineItem Creating item \(status.statusKey.id) with \(status.actions.count) actions")

            // 🔥 新增：处理topMessage转换
            let topMessage = uiTimeline.topMessage?.toSwift()
            if let topMessage {
               // FlareLog.debug("TimelineItem Found topMessage: \(topMessage.type)")
            }

            return TimelineItem(
                id: status.statusKey.id,
                content: status.content.toSwift(), // UiRichText -> RichText
                user: status.user?.toSwift(), // UiUserV2? -> User?
                timestamp: status.createdAt as Date,
                images: status.images.map { $0.toSwift() }, // [UiMedia] -> [Media]
                url: status.url, // String
                platformType: status.platformType.name, // PlatformType.name -> String
                aboveTextContent: status.aboveTextContent?.toSwift(), // AboveTextContent?
                contentWarning: status.contentWarning?.toSwift(), // UiRichText? -> RichText?
                card: status.card?.toSwift(), // UiCard? -> Card?
                quote: [], // 暂时为空，递归转换较复杂，后续完善
                bottomContent: status.bottomContent?.toSwift(), // BottomContent?
                topEndContent: status.topEndContent?.toSwift(), // TopEndContent?
                poll: status.poll?.toSwift(), // UiPoll? -> Poll?
                topMessage: topMessage, // 🔥 新增：TopMessage转换
                sensitive: status.sensitive, // Bool
                visibility: status.topEndContent?.extractVisibility() ?? "public", // 从topEndContent提取
                language: nil, // TODO: 从实际状态获取
                actions: status.actions // 🔥 保留完整的KMP StatusAction数组
            )
        } else {
            return TimelineItem(
                id: uiTimeline.itemKey,
                content: RichText(raw: "Unsupported content type"),
                user: nil,
                timestamp: Date()
            )
        }
    }

    // - 计算属性 (替代StatusViewModel的has*系列)

    /// 是否有媒体内容 - 对应 StatusViewModel.hasImages
    var hasMedia: Bool {
        !images.isEmpty
    }

    /// 是否有用户信息 - 对应 StatusViewModel.hasUser
    var hasUser: Bool {
        user != nil
    }

    /// 是否有回复内容 - 对应 StatusViewModel.hasAboveTextContent
    var hasAboveTextContent: Bool {
        aboveTextContent != nil
    }

    /// 是否有内容警告 - 对应 StatusViewModel.hasContentWarning
    var hasContentWarning: Bool {
        contentWarning != nil && !contentWarning!.raw.isEmpty
    }

    /// 是否有内容 - 对应 StatusViewModel.hasContent
    var hasContent: Bool {
        !content.raw.isEmpty
    }

    /// 是否有图片 - 对应 StatusViewModel.hasImages
    var hasImages: Bool {
        !images.isEmpty
    }

    /// 是否有卡片 - 对应 StatusViewModel.hasCard
    var hasCard: Bool {
        card != nil
    }

    /// 是否有引用 - 对应 StatusViewModel.hasQuote
    var hasQuote: Bool {
        !quote.isEmpty
    }

    /// 是否有底部内容 - 对应 StatusViewModel.hasBottomContent
    var hasBottomContent: Bool {
        bottomContent != nil
    }

    /// 是否有操作 - 对应 StatusViewModel.hasActions
    var hasActions: Bool {
        !actions.isEmpty
    }

    /// 是否有转发头部信息 - 新增
    var hasTopMessage: Bool {
        topMessage != nil
    }

    /// 是否为播客卡片 - 对应 StatusViewModel.isPodcastCard
    var isPodcastCard: Bool {
        guard let card,
              let url = URL(string: card.url) else { return false }
        return url.scheme == "flare" && url.host?.lowercased() == "podcast"
    }

    /// 是否应该显示链接预览 - 对应 StatusViewModel.shouldShowLinkPreview
    var shouldShowLinkPreview: Bool {
        guard let card else { return false }
        return !isPodcastCard && card.media != nil
    }

    /// 格式化时间戳 - 对应 StatusViewModel.getFormattedDate()
    var formattedTimestamp: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: timestamp, relativeTo: Date())
    }

    /// 内容预览 - 便利方法
    func contentPreview(maxLength: Int = 100) -> String {
        if content.raw.count <= maxLength {
            return content.raw
        }
        return String(content.raw.prefix(maxLength)) + "..."
    }

    /// 处理操作 - 对应 StatusViewModel.getProcessedActions()
    func getProcessedActions() -> (mainActions: [StatusAction], moreActions: [StatusActionItem]) {
        ActionProcessor.processActions(actions)
    }

    /// 格式化日期 - 对应 StatusViewModel.getFormattedDate()
    func getFormattedDate() -> String {
        formattedTimestamp
    }

    // - 兼容性属性 (保持向后兼容)

    /// 作者名称 - 兼容性属性
    var author: String {
        user?.name.raw ?? "Unknown"
    }

    /// 作者头像 - 兼容性属性
    var authorAvatar: String {
        user?.avatar ?? ""
    }

    /// 媒体URL列表 - 兼容性属性
    var mediaUrls: [String] {
        images.map(\.url)
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: TimelineItem, rhs: TimelineItem) -> Bool {
        lhs.id == rhs.id
    }
}

enum FlareTimelineState: Equatable {
    case loading

    case loaded(items: [TimelineItem], hasMore: Bool)

    case error(FlareError)

    case empty

    var items: [TimelineItem] {
        if case let .loaded(items, _) = self {
            return items
        }
        return []
    }

    var isLoading: Bool {
        switch self {
        case .loading:
            true
        default:
            false
        }
    }

    var hasMore: Bool {
        if case let .loaded(_, hasMore) = self {
            return hasMore
        }
        return false
    }

    var itemCount: Int {
        items.count
    }

    var isEmpty: Bool {
        switch self {
        case .empty:
            true
        case let .loaded(items, _):
            items.isEmpty
        default:
            false
        }
    }

    var isError: Bool {
        if case .error = self {
            return true
        }
        return false
    }

    var error: FlareError? {
        if case let .error(error) = self {
            return error
        }
        return nil
    }

    func toLoading(preserveItems: Bool = false) -> FlareTimelineState {
        if preserveItems, case let .loaded(items, hasMore) = self {
            return .loaded(items: items, hasMore: hasMore)
        }
        return .loading
    }

    /// 添加新项目（用于分页加载）
    func appendingItems(_ newItems: [TimelineItem], hasMore: Bool) -> FlareTimelineState {
        let currentItems = items
        let updatedItems = currentItems + newItems

        if updatedItems.isEmpty {
            return .empty
        }

        return .loaded(items: updatedItems, hasMore: hasMore)
    }

    func replacingItems(_ newItems: [TimelineItem], hasMore: Bool) -> FlareTimelineState {
        if newItems.isEmpty {
            return .empty
        }
        return .loaded(items: newItems, hasMore: hasMore)
    }

    func updatingItem(_ item: TimelineItem, at index: Int) -> FlareTimelineState {
        guard case .loaded(var items, let hasMore) = self,
              index >= 0, index < items.count
        else {
            return self
        }

        items[index] = item
        return .loaded(items: items, hasMore: hasMore)
    }

    func removingItem(at index: Int) -> FlareTimelineState {
        guard case .loaded(var items, let hasMore) = self,
              index >= 0, index < items.count
        else {
            return self
        }

        items.remove(at: index)

        if items.isEmpty {
            return .empty
        }

        return .loaded(items: items, hasMore: hasMore)
    }

    func insertingItem(_ item: TimelineItem, at index: Int) -> FlareTimelineState {
        guard case .loaded(var items, let hasMore) = self else {
            return .loaded(items: [item], hasMore: false)
        }

        let insertIndex = max(0, min(index, items.count))
        items.insert(item, at: insertIndex)

        return .loaded(items: items, hasMore: hasMore)
    }

    func toError(_ error: FlareError) -> FlareTimelineState {
        .error(error)
    }

    func stoppingRefresh() -> FlareTimelineState {
        // 🔥 简化：移除isRefreshing后，此方法不再需要修改状态
        self
    }
}

extension FlareTimelineState {
    static func == (lhs: FlareTimelineState, rhs: FlareTimelineState) -> Bool {
        switch (lhs, rhs) {
        case (.loading, .loading):
            true
        case (.empty, .empty):
            true
        case let (.error(lhsError), .error(rhsError)):
            lhsError == rhsError
        case let (.loaded(lhsItems, lhsHasMore),
                  .loaded(rhsItems, rhsHasMore)):
            lhsItems == rhsItems && lhsHasMore == rhsHasMore
        default:
            false
        }
    }
}

extension FlareTimelineState: CustomStringConvertible {
    var description: String {
        switch self {
        case .loading:
            "FlareTimelineState.loading"
        case .empty:
            "FlareTimelineState.empty"
        case let .error(error):
            "FlareTimelineState.error(\(error.localizedDescription))"
        case let .loaded(items, hasMore):
            "FlareTimelineState.loaded(items: \(items.count), hasMore: \(hasMore))"
        }
    }
}

/// UiRichText -> RichText 转换
extension UiRichText {
    func toSwift() -> RichText {
        RichText(
            raw: raw,
            markdown: markdown,
            isRTL: isRTL
        )
    }
}

/// UiUserV2 -> User 转换
extension UiUserV2 {
    func toSwift() -> User {
        User(
            key: key.id, // MicroBlogKey.id
            name: name.toSwift(),
            handle: handle,
            avatar: avatar,
            banner: nil, // UiUserV2可能没有banner字段，暂时设为nil
            description: nil // UiUserV2可能没有description字段，暂时设为nil
        )
    }
}

/// UiMedia -> Media 转换
extension UiMedia {
    func toSwift() -> Media {
        // 根据具体的UiMedia子类型进行转换
        if let image = self as? UiMediaImage {
            Media(
                url: image.url,
                previewUrl: cleanPreviewUrl(image.url, for: .image),
                type: .image,
                altText: image.description_,
                width: Int(image.width),
                height: Int(image.height)
            )
        } else if let video = self as? UiMediaVideo {
            Media(
                url: video.url,
                previewUrl: cleanPreviewUrl(video.thumbnailUrl, for: .video), // ✅ 修复：使用thumbnailUrl作为previewUrl并清理
                type: .video,
                altText: video.description_,
                width: Int(video.width),
                height: Int(video.height)
            )
        } else if let gif = self as? UiMediaGif {
            Media(
                url: gif.url,
                previewUrl: cleanPreviewUrl(gif.previewUrl, for: .gif),
                type: .gif,
                altText: gif.description_,
                width: Int(gif.width),
                height: Int(gif.height)
            )
        } else if let audio = self as? UiMediaAudio {
            Media(
                url: audio.url,
                previewUrl: audio.previewUrl, // 音频不处理 previewUrl
                type: .audio,
                altText: audio.description_,
                width: nil,
                height: nil
            )
        } else {
            // 默认处理
            Media(
                url: url,
                previewUrl: nil,
                type: .image,
                altText: nil,
                width: nil,
                height: nil
            )
        }
    }

    private func cleanPreviewUrl(_ url: String?, for type: TimelineMediaType) -> String? {
        guard let url else { return nil }

        switch type {
        case .image, .video, .gif:
            if url.hasSuffix("?name=orig") {
                return String(url.dropLast("?name=orig".count))
            }
            return url
        case .audio:
            return url
        }
    }
}

/// UiCard -> Card 转换
extension UiCard {
    func toSwift() -> Card {
        Card(
            url: url,
            title: title,
            description: description_,
            media: media?.toSwift()
        )
    }
}

/// UiTimelineItemContentStatusAboveTextContent -> AboveTextContent 转换
extension UiTimelineItemContentStatusAboveTextContent {
    func toSwift() -> AboveTextContent? {
        // 使用onEnum来处理sealed class
        switch onEnum(of: self) {
        case let .replyTo(replyTo):
            .replyTo(handle: replyTo.handle)
        }
    }
}

/// UiTimelineItemContentStatusBottomContent -> BottomContent 转换
extension UiTimelineItemContentStatusBottomContent {
    func toSwift() -> BottomContent? {
        // 使用onEnum来处理sealed class
        switch onEnum(of: self) {
        case let .reaction(reaction):
            let emojiReactions = reaction.emojiReactions.map { emoji in
                EmojiReaction(
                    name: emoji.name,
                    url: emoji.url,
                    count: Int(emoji.count),
                    me: emoji.me,
                    isUnicode: emoji.isUnicode,
                    isImageReaction: emoji.isImageReaction
                )
            }
            return .reaction(emojiReactions: emojiReactions)
        }
    }
}

/// UiTimelineItemContentStatusTopEndContent -> TopEndContent 转换
extension UiTimelineItemContentStatusTopEndContent {
    func toSwift() -> TopEndContent? {
        // 暂时返回默认值，后续完善
        .visibility(type: .publicType)
    }

    func extractVisibility() -> String {
        // 暂时返回默认值，后续完善
        "public"
    }
}

/// UiPoll -> Poll 转换
extension UiPoll {
    func toSwift() -> Poll {
        Poll(
            options: [], // 暂时为空，后续完善
            expiresAt: nil,
            expired: false,
            multiple: false,
            votesCount: 0,
            votersCount: nil
        )
    }
}

extension FlareTimelineState {
    func needsUIUpdate(from other: FlareTimelineState) -> Bool {
        // 如果状态类型不同，需要更新
        switch (other, self) {
        case (.loading, .loading), (.empty, .empty), (.error, .error), (.loaded, .loaded):
            break
        default:
            return true
        }

        // 如果是loaded状态，检查具体变化
        if case let .loaded(oldItems, oldHasMore) = other,
           case let .loaded(newItems, newHasMore) = self
        {
            // 检查项目数量变化
            if oldItems.count != newItems.count {
                return true
            }

            // 检查hasMore状态变化
            if oldHasMore != newHasMore {
                return true
            }

            // 检查项目内容变化（仅检查ID，避免深度比较）
            for (index, oldItem) in oldItems.enumerated() {
                if index < newItems.count, oldItem.id != newItems[index].id {
                    return true
                }
            }

            return false
        }

        return self != other
    }

    func changesSummary(from other: FlareTimelineState) -> String {
        switch (other, self) {
        case let (.loading, .loaded(items, _)):
            return "Loaded \(items.count) items"
        case let (.loaded(oldItems, _), .loaded(newItems, _)):
            let itemDiff = newItems.count - oldItems.count
            if itemDiff > 0 {
                return "Added \(itemDiff) items"
            } else if itemDiff < 0 {
                return "Removed \(-itemDiff) items"
            } else {
                return "Updated items"
            }
        case let (_, .error(error)):
            return "Error: \(error.localizedDescription)"
        case (_, .empty):
            return "No items"
        case (_, .loading):
            return "Loading..."
        default:
            return "State changed"
        }
    }
}

/// UiTimeline.TopMessage -> TopMessage 转换
extension UiTimeline.TopMessage {
    func toSwift() -> TopMessage {
        TopMessage(
            user: user?.toSwift(),
            icon: convertIcon(),
            type: convertMessageType(),
            statusKey: statusKey.id
        )
    }

    /// 转换图标类型
    private func convertIcon() -> TopMessageIcon {
        // 使用字符串比较来避免类型桥接问题
        let iconString = String(describing: icon)
        switch iconString {
        case "Retweet": return .retweet
        case "Follow": return .follow
        case "Favourite": return .favourite
        case "Mention": return .mention
        case "Poll": return .poll
        case "Edit": return .edit
        case "Info": return .info
        case "Reply": return .reply
        case "Quote": return .quote
        case "Pin": return .pin
        default: return .info
        }
    }

    /// 转换消息类型
    private func convertMessageType() -> TopMessageType {
        // 使用字符串比较来避免复杂的类型桥接
        let typeString = String(describing: type)

        // 简化处理：根据字符串内容判断平台类型
        if typeString.contains("Mastodon") {
            if typeString.contains("Reblogged") {
                return .mastodon(.reblogged)
            } else if typeString.contains("Favourite") {
                return .mastodon(.favourite)
            } else if typeString.contains("Follow") {
                return .mastodon(.follow)
            } else if typeString.contains("Mention") {
                return .mastodon(.mention)
            } else {
                return .mastodon(.unKnown)
            }
        } else if typeString.contains("Bluesky") {
            if typeString.contains("Like") {
                return .bluesky(.like)
            } else if typeString.contains("Repost") {
                return .bluesky(.repost)
            } else if typeString.contains("Follow") {
                return .bluesky(.follow)
            } else {
                return .bluesky(.unKnown)
            }
        } else if typeString.contains("Misskey") {
            return .misskey(.unKnown)
        } else if typeString.contains("VVO") {
            return .vVO(.like)
        } else if typeString.contains("XQT") {
            return .xQT(.retweet)
        } else {
            // 默认返回Mastodon类型
            return .mastodon(.unKnown)
        }
    }
}
