import Foundation
import shared

struct TimelineItem: Identifiable, Equatable, Hashable {
    let id: String
    let content: String
    let author: String
    let authorAvatar: String
    let timestamp: Date
    let mediaUrls: [String]
    let visibility: String
    let language: String?

    let actions: [StatusAction]

    var likeCount: Int
    var isLiked: Bool
    var retweetCount: Int
    var isRetweeted: Bool
    var replyCount: Int
    var bookmarkCount: Int
    var isBookmarked: Bool

    init(
        id: String,
        content: String,
        author: String,
        authorAvatar: String = "",
        timestamp: Date,
        mediaUrls: [String] = [],
        visibility: String = "public",
        language: String? = nil,
        actions: [StatusAction] = []
    ) {
        self.id = id
        self.content = content
        self.author = author
        self.authorAvatar = authorAvatar
        self.timestamp = timestamp
        self.mediaUrls = mediaUrls
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

        print("🔍 [TimelineItem] Extracting state for item: \(id)")
        print("🔍 [TimelineItem] Processing \(actions.count) actions")

        for (index, action) in actions.enumerated() {
            if case let .item(item) = onEnum(of: action) {
                if let likeAction = item as? StatusActionItemLike {
                    likeCount = Int(likeAction.count)
                    isLiked = likeAction.liked
                    print("📊 [TimelineItem] Action[\(index)] Like - count: \(likeCount), liked: \(isLiked)")
                } else if let retweetAction = item as? StatusActionItemRetweet {
                    retweetCount = Int(retweetAction.count)
                    isRetweeted = retweetAction.retweeted
                    print("📊 [TimelineItem] Action[\(index)] Retweet - count: \(retweetCount), retweeted: \(isRetweeted)")
                } else if let replyAction = item as? StatusActionItemReply {
                    replyCount = Int(replyAction.count)
                    print("📊 [TimelineItem] Action[\(index)] Reply - count: \(replyCount)")
                } else {
                    print("📊 [TimelineItem] Action[\(index)] Other - type: \(type(of: item))")
                }
            } else if case let .group(group) = onEnum(of: action) {
                print("📊 [TimelineItem] Action[\(index)] Group - displayItem: \(type(of: group.displayItem))")

                // 遍历Group中的所有SubActions来提取数据
                for (subIndex, subAction) in group.actions.enumerated() {
                    switch onEnum(of: subAction) {
                    case let .item(subItem):
                        if let retweetItem = subItem as? StatusActionItemRetweet {
                            retweetCount = Int(retweetItem.count)
                            isRetweeted = retweetItem.retweeted
                            print("📊 [TimelineItem] SubAction[\(subIndex)] Retweet - count: \(retweetCount), retweeted: \(isRetweeted)")
                        } else if let bookmarkItem = subItem as? StatusActionItemBookmark {
                            bookmarkCount = Int(bookmarkItem.count)
                            isBookmarked = bookmarkItem.bookmarked
                            print("📊 [TimelineItem] SubAction[\(subIndex)] Bookmark - count: \(bookmarkCount), bookmarked: \(isBookmarked)")
                        } else if let quoteItem = subItem as? StatusActionItemQuote {
                            // Quote暂时不处理，但记录日志
                            print("📊 [TimelineItem] SubAction[\(subIndex)] Quote - count: \(quoteItem.count)")
                        }
                    case .group:
                        print("📊 [TimelineItem] SubAction[\(subIndex)] Nested Group")
                    case .asyncActionItem:
                        print("📊 [TimelineItem] SubAction[\(subIndex)] Async Action")
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

        print("✅ [TimelineItem] Final state for \(id):")
        print("   Like: \(likeCount) (liked: \(isLiked))")
        print("   Retweet: \(retweetCount) (retweeted: \(isRetweeted))")
        print("   Reply: \(replyCount)")
        print("   Bookmark: \(bookmarkCount) (bookmarked: \(isBookmarked))")
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

            print("📊 [TimelineItem] Creating item \(status.statusKey.id) with \(status.actions.count) actions")

            return TimelineItem(
                id: status.statusKey.id,
                content: status.content.raw,
                author: status.user?.name.raw ?? "Unknown",
                authorAvatar: status.user?.avatar ?? "",
                timestamp: status.createdAt as Date,
                mediaUrls: status.images.map(\.url),
                visibility: "public", // TODO: 从实际状态获取
                language: nil, // TODO: 从实际状态获取
                actions: status.actions // 🔥 保留完整的KMP StatusAction数组
            )
        } else {
            return TimelineItem(
                id: uiTimeline.itemKey,
                content: "Unsupported content type",
                author: "Unknown",
                timestamp: Date(),
                actions: []
            )
        }
    }

 
     var hasMedia: Bool {
        !mediaUrls.isEmpty
    }

     var formattedTimestamp: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: timestamp, relativeTo: Date())
    }

     func contentPreview(maxLength: Int = 100) -> String {
        if content.count <= maxLength {
            return content
        }
        return String(content.prefix(maxLength)) + "..."
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

     case loaded(items: [TimelineItem], hasMore: Bool, isRefreshing: Bool)

     case error(FlareError)

     case empty

 
     var items: [TimelineItem] {
        if case let .loaded(items, _, _) = self {
            return items
        }
        return []
    }

     var isLoading: Bool {
        switch self {
        case .loading:
            true
        case let .loaded(_, _, isRefreshing):
            isRefreshing
        default:
            false
        }
    }

     var hasMore: Bool {
        if case let .loaded(_, hasMore, _) = self {
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
        case let .loaded(items, _, _):
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
        if preserveItems, case let .loaded(items, hasMore, _) = self {
            return .loaded(items: items, hasMore: hasMore, isRefreshing: true)
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

        return .loaded(items: updatedItems, hasMore: hasMore, isRefreshing: false)
    }

   
    func replacingItems(_ newItems: [TimelineItem], hasMore: Bool) -> FlareTimelineState {
        if newItems.isEmpty {
            return .empty
        }
        return .loaded(items: newItems, hasMore: hasMore, isRefreshing: false)
    }

 
    func updatingItem(_ item: TimelineItem, at index: Int) -> FlareTimelineState {
        guard case .loaded(var items, let hasMore, let isRefreshing) = self,
              index >= 0, index < items.count
        else {
            return self
        }

        items[index] = item
        return .loaded(items: items, hasMore: hasMore, isRefreshing: isRefreshing)
    }

 
    func removingItem(at index: Int) -> FlareTimelineState {
        guard case .loaded(var items, let hasMore, let isRefreshing) = self,
              index >= 0, index < items.count
        else {
            return self
        }

        items.remove(at: index)

        if items.isEmpty {
            return .empty
        }

        return .loaded(items: items, hasMore: hasMore, isRefreshing: isRefreshing)
    }

 
    func insertingItem(_ item: TimelineItem, at index: Int) -> FlareTimelineState {
        guard case .loaded(var items, let hasMore, let isRefreshing) = self else {
            return .loaded(items: [item], hasMore: hasMore, isRefreshing: false)
        }

        let insertIndex = max(0, min(index, items.count))
        items.insert(item, at: insertIndex)

        return .loaded(items: items, hasMore: hasMore, isRefreshing: isRefreshing)
    }

  
    func toError(_ error: FlareError) -> FlareTimelineState {
        .error(error)
    }

 
    func stoppingRefresh() -> FlareTimelineState {
        if case let .loaded(items, hasMore, _) = self {
            return .loaded(items: items, hasMore: hasMore, isRefreshing: false)
        }
        return self
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
        case let (.loaded(lhsItems, lhsHasMore, lhsRefreshing),
                  .loaded(rhsItems, rhsHasMore, rhsRefreshing)):
            lhsItems == rhsItems && lhsHasMore == rhsHasMore && lhsRefreshing == rhsRefreshing
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
        case let .loaded(items, hasMore, isRefreshing):
            "FlareTimelineState.loaded(items: \(items.count), hasMore: \(hasMore), isRefreshing: \(isRefreshing))"
        }
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
        if case let .loaded(oldItems, oldHasMore, oldRefreshing) = other,
           case let .loaded(newItems, newHasMore, newRefreshing) = self
        {
            // 检查项目数量变化
            if oldItems.count != newItems.count {
                return true
            }

            // 检查hasMore或isRefreshing状态变化
            if oldHasMore != newHasMore || oldRefreshing != newRefreshing {
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
        case let (.loading, .loaded(items, _, _)):
            return "Loaded \(items.count) items"
        case let (.loaded(oldItems, _, _), .loaded(newItems, _, _)):
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
