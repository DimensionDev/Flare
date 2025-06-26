
import Foundation
import Generated
import SwiftUI
import shared

 extension NSNotification.Name {
    static let timelineItemUpdated = NSNotification.Name("timelineItemUpdated")
}


struct TimelineActionsViewV2: View {
    let item: TimelineItem
    let onAction: (TimelineActionType, TimelineItem) -> Void

    @Environment(\.openURL) private var openURL
    @State private var errorMessage: String?
    @State private var showRetweetMenu = false

    // 🔥 添加状态追踪，确保UI响应item变化
    @State private var itemId: String = ""
    @State private var refreshTrigger: Int = 0

    // 🔥 使用@State来跟踪展示状态，确保UI能响应变化
    @State private var displayLikeCount: Int = 0
    @State private var displayIsLiked: Bool = false
    @State private var displayRetweetCount: Int = 0
    @State private var displayIsRetweeted: Bool = false
    @State private var displayBookmarkCount: Int = 0
    @State private var displayIsBookmarked: Bool = false

    var body: some View {
        // 🔍 UI渲染日志 - 使用更明显的标识
        let _ = print("🚨🚨🚨 [TimelineActionsView] RENDERING UI FOR ITEM: \(item.id) 🚨🚨🚨")
        let _ = print("🚨🚨🚨 [TimelineActionsView] UI STATE - Like: \(displayLikeCount) (liked: \(displayIsLiked)), Retweet: \(displayRetweetCount) (retweeted: \(displayIsRetweeted)) 🚨🚨🚨")
 
        return VStack(spacing: 0) {
            if let errorMessage = errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .font(.caption)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 4)
            }

             HStack(spacing: 0) {
                // 1. 回复
                ActionButtonV2(
                    iconImage: Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline),
                    count: item.replyCount,
                    isActive: false,
                    activeColor: .blue
                ) {
                    handleReplyAction()
                }
                .frame(maxWidth: .infinity)

                // 2. 转发
                ActionButtonV2(
                    iconImage: Image(asset: Asset.Image.Status.Toolbar.repeat),
                    count: displayRetweetCount,
                    isActive: displayIsRetweeted,
                    activeColor: .green
                ) {
                    handleRetweetAction()
                }
                .frame(maxWidth: .infinity)
                .confirmationDialog("转发选项", isPresented: $showRetweetMenu) {
                    Button("转发") { performRetweetAction(isQuote: false) }
                    Button("引用转发") { performRetweetAction(isQuote: true) }
                    Button("取消", role: .cancel) { }
                }

                // 3. 点赞
                ActionButtonV2(
                    iconImage: displayIsLiked ?
                        Image(asset: Asset.Image.Status.Toolbar.favorite) :
                        Image(asset: Asset.Image.Status.Toolbar.favoriteBorder),
                    count: displayLikeCount,
                    isActive: displayIsLiked,
                    activeColor: .red
                ) {
                    print("🔥🔥🔥 [TimelineActionsView] LIKE BUTTON CLICKED! Item: \(item.id)")
                    handleLikeAction()
                }
                .frame(maxWidth: .infinity)

                // 4. 书签
                ActionButtonV2(
                    iconImage: displayIsBookmarked ?
                        Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled) :
                        Image(asset: Asset.Image.Status.Toolbar.bookmark),
                    count: displayBookmarkCount,
                    isActive: displayIsBookmarked,
                    activeColor: .orange
                ) {
                    print("🔖🔖🔖 [TimelineActionsView] BOOKMARK BUTTON CLICKED! Item: \(item.id)")
                    handleBookmarkAction()
                }
                .frame(maxWidth: .infinity)

                // 5. 分享 - 使用ShareButtonV2
                ShareButtonV2(
                    item: item,
                    view: TimelineStatusViewV2(
                        item: item,
                        index: 0,
                        presenter: nil,
                        scrollPositionID: .constant(nil),
                        onError: { _ in }
                    )
                )
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 16)
        }
        .id("\(item.id)-\(displayLikeCount)-\(displayIsLiked)-\(displayRetweetCount)-\(displayIsRetweeted)-\(displayBookmarkCount)-\(displayIsBookmarked)-\(refreshTrigger)")
        .onAppear {
            // 🔥 初始化显示状态
            syncDisplayStateFromItem()
            itemId = item.id
            print("🖼️ [TimelineActionsView] onAppear for item: \(item.id)")
            print("🖼️ [TimelineActionsView] Initial display state - Like: \(displayLikeCount) (liked: \(displayIsLiked)), Retweet: \(displayRetweetCount) (retweeted: \(displayIsRetweeted)), Bookmark: \(displayBookmarkCount) (bookmarked: \(displayIsBookmarked))")
        }
        .onChange(of: item.id) { newId in
            // 🔥 当item变化时，同步显示状态
            syncDisplayStateFromItem()
            itemId = newId
            print("🔄 [TimelineActionsView] Item changed to: \(newId)")
            print("🔄 [TimelineActionsView] Updated display state - Like: \(displayLikeCount) (liked: \(displayIsLiked)), Retweet: \(displayRetweetCount) (retweeted: \(displayIsRetweeted)), Bookmark: \(displayBookmarkCount) (bookmarked: \(displayIsBookmarked))")
        }
    }

 
    /// 🔥 同步显示状态从item
    private func syncDisplayStateFromItem() {
        displayLikeCount = item.likeCount
        displayIsLiked = item.isLiked
        displayRetweetCount = item.retweetCount
        displayIsRetweeted = item.isRetweeted
        displayBookmarkCount = item.bookmarkCount
        displayIsBookmarked = item.isBookmarked
        print("🔄 [TimelineActionsView] Synced display state from item: Like \(displayLikeCount) (liked: \(displayIsLiked)), Retweet \(displayRetweetCount) (retweeted: \(displayIsRetweeted)), Bookmark \(displayBookmarkCount) (bookmarked: \(displayIsBookmarked))")
    }

 
    /// 处理点赞操作
    private func handleLikeAction() {
        print("🔥🔥🔥 [TimelineActionsView] LIKE BUTTON CLICKED! Item: \(item.id)")

        // 🎯 乐观更新：立即更新显示状态
        let newLikeCount = displayIsLiked ? displayLikeCount - 1 : displayLikeCount + 1
        let newIsLiked = !displayIsLiked

        print("🎯 [TimelineActionsView] Optimistic update - Like: \(displayLikeCount) → \(newLikeCount), Liked: \(displayIsLiked) → \(newIsLiked)")

        // 🔥 立即更新@State变量，触发UI重新渲染
        displayLikeCount = newLikeCount
        displayIsLiked = newIsLiked

        print("✨ [TimelineActionsView] UI state updated immediately - Like: \(displayLikeCount), Liked: \(displayIsLiked)")

        print("🔍 [TimelineActionsView] Current item state before update:")
        print("   - ID: \(item.id)")
        print("   - Like count: \(item.likeCount)")
        print("   - Is liked: \(item.isLiked)")

        // 创建更新后的TimelineItem
        let updatedItem = item.withUpdatedLikeState(count: newLikeCount, isLiked: newIsLiked)

        print("🔍 [TimelineActionsView] Updated item state:")
        print("   - ID: \(updatedItem.id)")
        print("   - Like count: \(updatedItem.likeCount)")
        print("   - Is liked: \(updatedItem.isLiked)")

        // 立即通知父组件更新UI
        print("🚀 [TimelineActionsView] Calling onAction with updated item")
        onAction(.like, updatedItem)
        print("✅ [TimelineActionsView] onAction call completed")

        // 🔥 强制UI刷新
        refreshTrigger += 1
        print("🔄 [TimelineActionsView] Triggered UI refresh: \(refreshTrigger)")

        // 同时调用KMP操作
        performKMPAction(actionType: .like)
    }

    /// 处理转发操作
    private func handleRetweetAction() {
        // 转发是Group类型，显示菜单
        showRetweetMenu = true
    }

    /// 执行转发操作
    private func performRetweetAction(isQuote: Bool) {
        print("🔄🔄🔄 [TimelineActionsView] RETWEET BUTTON CLICKED! Item: \(item.id), isQuote: \(isQuote)")

        // 🎯 乐观更新：立即更新显示状态
        let newRetweetCount = displayIsRetweeted ? displayRetweetCount - 1 : displayRetweetCount + 1
        let newIsRetweeted = !displayIsRetweeted

        print("🎯 [TimelineActionsView] Optimistic update - Retweet: \(displayRetweetCount) → \(newRetweetCount), Retweeted: \(displayIsRetweeted) → \(newIsRetweeted)")

        // 🔥 立即更新@State变量，触发UI重新渲染
        displayRetweetCount = newRetweetCount
        displayIsRetweeted = newIsRetweeted

        print("✨ [TimelineActionsView] UI state updated immediately - Retweet: \(displayRetweetCount), Retweeted: \(displayIsRetweeted)")

        // 创建更新后的TimelineItem
        var updatedItem = item
        updatedItem.retweetCount = newRetweetCount
        updatedItem.isRetweeted = newIsRetweeted

        print("🔍 [TimelineActionsView] Updated item state:")
        print("   - ID: \(updatedItem.id)")
        print("   - Retweet count: \(updatedItem.retweetCount)")
        print("   - Is retweeted: \(updatedItem.isRetweeted)")

        // 立即通知父组件更新UI
        print("🚀 [TimelineActionsView] Calling onAction with updated item")
        onAction(.repost, updatedItem)
        print("✅ [TimelineActionsView] onAction call completed")

        // 🔥 强制UI刷新
        refreshTrigger += 1
        print("🔄 [TimelineActionsView] Triggered UI refresh: \(refreshTrigger)")

        // 同时调用KMP操作
        performKMPAction(actionType: .repost)
    }

    /// 处理回复操作
    private func handleReplyAction() {
        print("🔥🔥🔥 [TimelineActionsView] REPLY BUTTON CLICKED! Item: \(item.id)")

        // 回复操作不需要乐观更新，直接调用KMP
        performKMPAction(actionType: .reply)
    }

     private func handleBookmarkAction() {
        print("🔖🔖🔖 [TimelineActionsView] BOOKMARK BUTTON CLICKED! Item: \(item.id)")

        // 🎯 乐观更新：立即更新显示状态
        let newBookmarkCount = displayIsBookmarked ? displayBookmarkCount - 1 : displayBookmarkCount + 1
        let newIsBookmarked = !displayIsBookmarked

        print("🎯 [TimelineActionsView] Optimistic update - Bookmark: \(displayBookmarkCount) → \(newBookmarkCount), Bookmarked: \(displayIsBookmarked) → \(newIsBookmarked)")

        // 🔥 立即更新@State变量，触发UI重新渲染
        displayBookmarkCount = newBookmarkCount
        displayIsBookmarked = newIsBookmarked

        print("✨ [TimelineActionsView] UI state updated immediately - Bookmark: \(displayBookmarkCount), Bookmarked: \(displayIsBookmarked)")

        // 创建更新后的TimelineItem
        var updatedItem = item
        updatedItem.bookmarkCount = newBookmarkCount
        updatedItem.isBookmarked = newIsBookmarked

        print("🔍 [TimelineActionsView] Updated item state:")
        print("   - ID: \(updatedItem.id)")
        print("   - Bookmark count: \(updatedItem.bookmarkCount)")
        print("   - Is bookmarked: \(updatedItem.isBookmarked)")

        // 立即通知父组件更新UI
        print("🚀 [TimelineActionsView] Calling onAction with updated item")
        onAction(.bookmark, updatedItem)
        print("✅ [TimelineActionsView] onAction call completed")

        // 🔥 强制UI刷新
        refreshTrigger += 1
        print("🔄 [TimelineActionsView] Triggered UI refresh: \(refreshTrigger)")

        // 同时调用KMP操作
        performKMPAction(actionType: .bookmark)
    }

    /// 执行KMP操作
    private func performKMPAction(actionType: TimelineActionType) {
        print("🔥 [TimelineActionsView] Starting KMP action: \(actionType) for item: \(item.id)")
        print("🔥 [TimelineActionsView] Current state - likeCount: \(item.likeCount), isLiked: \(item.isLiked)")

        // 找到对应的StatusAction并调用KMP
        for (index, action) in item.actions.enumerated() {
            if case .item(let actionItem) = onEnum(of: action),
               let clickable = actionItem as? StatusActionItemClickable {

                // 根据类型匹配
                let shouldExecute = switch actionType {
                case .like: actionItem is StatusActionItemLike
                case .repost: actionItem is StatusActionItemRetweet
                case .reply: actionItem is StatusActionItemReply
                case .bookmark: actionItem is StatusActionItemBookmark
                default: false
                }

                if shouldExecute {
                    print("🎯 [TimelineActionsView] Found matching action at index \(index): \(type(of: actionItem))")

                    // 记录当前StatusAction的状态
                    if let likeAction = actionItem as? StatusActionItemLike {
                        print("🔍 [TimelineActionsView] Like action state - count: \(likeAction.count), liked: \(likeAction.liked)")
                    }

                    let openURLAction = OpenURLAction { url in
                        openURL(url)
                        return .handled
                    }

                    print("🚀 [TimelineActionsView] Calling KMP onClicked() for \(type(of: actionItem))")
                    clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURLAction)))
                    print("✅ [TimelineActionsView] KMP onClicked() call completed")
                    break
                }
            }
        }
    }
}

 
private struct ActionButtonV2: View {
    let iconImage: Image
    let count: Int
    let isActive: Bool
    let activeColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                iconImage
                    .renderingMode(.template)
                    .foregroundColor(isActive ? activeColor : .primary)

                if count > 0 {
                    Text("\(count)")
                        .foregroundColor(isActive ? activeColor : .primary)
                        .font(.caption)
                }
            }
            .padding(8) // 增加点击区域
        }
        .buttonStyle(BorderlessButtonStyle())
    }
}

 


 

enum TimelineActionType {
    case like
    case repost
    case reply
    case bookmark
    case share
}
