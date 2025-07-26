
import Foundation
import Generated
import shared
import SwiftUI

extension NSNotification.Name {
    static let timelineItemUpdated = NSNotification.Name("timelineItemUpdated")
}

struct TimelineActionsViewV2: View {
    let item: TimelineItem
    let onAction: (TimelineActionType, TimelineItem) -> Void

    @Environment(\.openURL) private var openURL
    @State private var errorMessage: String?
    @State private var showRetweetMenu = false

    @State private var itemId: String = ""
    @State private var refreshTrigger: Int = 0

    @State private var displayLikeCount: Int = 0
    @State private var displayIsLiked: Bool = false
    @State private var displayRetweetCount: Int = 0
    @State private var displayIsRetweeted: Bool = false
    @State private var displayBookmarkCount: Int = 0
    @State private var displayIsBookmarked: Bool = false

    var body: some View {
        VStack(spacing: 0) {
            if let errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .font(.caption)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 4)
            }

            HStack(spacing: 0) {
                ActionButtonV2(
                    iconImage: Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline),
                    count: item.replyCount,
                    isActive: false,
                    activeColor: .blue
                ) {
                    handleReplyAction()
                }
                .frame(maxWidth: .infinity)

                ActionButtonV2(
                    iconImage: Image(asset: Asset.Image.Status.Toolbar.repeat),
                    count: displayRetweetCount,
                    isActive: displayIsRetweeted,
                    activeColor: .green
                ) {
                    handleRetweetAction()
                }
                .frame(maxWidth: .infinity)
                .confirmationDialog("Retweet Options", isPresented: $showRetweetMenu) {
                    Button("Retweet") { performRetweetAction(isQuote: false) }
                    Button("Quote Tweet") { performRetweetAction(isQuote: true) }
                    Button("Cancel", role: .cancel) {}
                }

                ActionButtonV2(
                    iconImage: displayIsLiked ?
                        Image(asset: Asset.Image.Status.Toolbar.favorite) :
                        Image(asset: Asset.Image.Status.Toolbar.favoriteBorder),
                    count: displayLikeCount,
                    isActive: displayIsLiked,
                    activeColor: .red
                ) {
                    handleLikeAction()
                }
                .frame(maxWidth: .infinity)

                ActionButtonV2(
                    iconImage: displayIsBookmarked ?
                        Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled) :
                        Image(asset: Asset.Image.Status.Toolbar.bookmark),
                    count: displayBookmarkCount,
                    isActive: displayIsBookmarked,
                    activeColor: .orange
                ) {
                    handleBookmarkAction()
                }
                .frame(maxWidth: .infinity)

                ActionButtonV2(
                    iconImage: Image(systemName: "character.bubble"),
                    count: 0,
                    isActive: false,
                    activeColor: .blue
                ) {
                    handleTranslateAction()
                }
                .frame(maxWidth: .infinity)

                ShareButtonV2(
                    item: item,
                    view: TimelineStatusViewV2(
                        item: item,
                        index: 0,
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
        }
        .onChange(of: item.id) { newId in
            // 🔥 当item变化时，同步显示状态
            syncDisplayStateFromItem()
            itemId = newId
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
    }

    /// 处理点赞操作
    private func handleLikeAction() {
        let newLikeCount = displayIsLiked ? displayLikeCount - 1 : displayLikeCount + 1
        let newIsLiked = !displayIsLiked

        displayLikeCount = newLikeCount
        displayIsLiked = newIsLiked

        let updatedItem = item.withUpdatedLikeState(count: newLikeCount, isLiked: newIsLiked)

        onAction(.like, updatedItem)

        refreshTrigger += 1

        performKMPAction(actionType: .like)
    }

    /// 处理转发操作
    private func handleRetweetAction() {
        showRetweetMenu = true
    }

    /// 执行转发操作
    private func performRetweetAction(isQuote: Bool) {
        if isQuote {
            performKMPAction(actionType: .quote)
            return
        }

        let newRetweetCount = displayIsRetweeted ? displayRetweetCount - 1 : displayRetweetCount + 1
        let newIsRetweeted = !displayIsRetweeted

        displayRetweetCount = newRetweetCount
        displayIsRetweeted = newIsRetweeted

        var updatedItem = item
        updatedItem.retweetCount = newRetweetCount
        updatedItem.isRetweeted = newIsRetweeted

        onAction(.repost, updatedItem)

        refreshTrigger += 1

        performKMPAction(actionType: .repost)
    }

    private func handleReplyAction() {
        performKMPAction(actionType: .reply)
    }

    private func handleBookmarkAction() {
        let newBookmarkCount = displayIsBookmarked ? displayBookmarkCount - 1 : displayBookmarkCount + 1
        let newIsBookmarked = !displayIsBookmarked

        displayBookmarkCount = newBookmarkCount
        displayIsBookmarked = newIsBookmarked

        var updatedItem = item
        updatedItem.bookmarkCount = newBookmarkCount
        updatedItem.isBookmarked = newIsBookmarked

        onAction(.bookmark, updatedItem)

        refreshTrigger += 1

        performKMPAction(actionType: .bookmark)
    }

    private func handleTranslateAction() {
        onAction(.translate, item)
    }

    private func performKMPAction(actionType: TimelineActionType) {
        func findAndExecuteAction(in actions: [StatusAction], actionType: TimelineActionType) -> Bool {
            for (index, action) in actions.enumerated() {
                let enumResult = onEnum(of: action)

                if case let .item(actionItem) = enumResult,
                   let clickable = actionItem as? StatusActionItemClickable
                {
                    let shouldExecute = switch actionType {
                    case .like: actionItem is StatusActionItemLike
                    case .repost: actionItem is StatusActionItemRetweet
                    case .reply: actionItem is StatusActionItemReply
                    case .bookmark: actionItem is StatusActionItemBookmark
                    case .quote: actionItem is StatusActionItemQuote
                    default: false
                    }

                    if shouldExecute {
                        let openURLAction = OpenURLAction { url in
                            openURL(url)
                            return .handled
                        }

                        clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURLAction)))
                        return true
                    }
                } else if case let .group(group) = enumResult {
                    if findAndExecuteAction(in: group.actions, actionType: actionType) {
                        return true
                    }
                }
            }
            return false
        }

        let _ = findAndExecuteAction(in: item.actions, actionType: actionType)
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
                    Text("\(formatCount(Int64(count)))")
                        .foregroundColor(isActive ? activeColor : .primary)
                        .font(.caption)
                }
            }
            .padding(8)
        }
        .buttonStyle(BorderlessButtonStyle())
    }
}

enum TimelineActionType {
    case like
    case repost
    case reply
    case bookmark
    case quote
    case share
    case translate
}
