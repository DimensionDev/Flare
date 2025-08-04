
import Foundation
import Generated
import shared
import SwiftUI

extension NSNotification.Name {
    static let timelineItemUpdated = NSNotification.Name("timelineItemUpdated")
}

struct TimelineActionsViewV2: View {
    let item: TimelineItem
    let timelineViewModel: TimelineViewModel?

    let onAction: (TimelineActionType, TimelineItem) -> Void
    let onShare: (ShareType) -> Void

    @Environment(FlareRouter.self) private var router

    @State private var showRetweetMenu = false

    var body: some View {
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
                count: item.retweetCount,
                isActive: item.isRetweeted,
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
                iconImage: item.isLiked ?
                    Image(asset: Asset.Image.Status.Toolbar.favorite) :
                    Image(asset: Asset.Image.Status.Toolbar.favoriteBorder),
                count: item.likeCount,
                isActive: item.isLiked,
                activeColor: .red
            ) {
                handleLikeAction()
            }
            .frame(maxWidth: .infinity)

            ActionButtonV2(
                iconImage: item.isBookmarked ?
                    Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled) :
                    Image(asset: Asset.Image.Status.Toolbar.bookmark),
                count: item.bookmarkCount,
                isActive: item.isBookmarked,
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
            // .frame(maxWidth: .infinity)

            ShareButtonV3(
                item: item,
                onShare: onShare
            )
        }
        .padding(.vertical, 8)
        .id(item.id)
    }

    private func handleLikeAction() {
        FlareLog.debug("🔥 [TimelineActionsViewV2] handleLikeAction called for item: \(item.id)")

        timelineViewModel?.updateItemOptimistically(itemId: item.id, actionType: .like)

        performKMPAction(actionType: .like)
    }

    private func handleRetweetAction() {
        showRetweetMenu = true
    }

    private func performRetweetAction(isQuote: Bool) {
        if isQuote {
            FlareLog.debug("🔥 [TimelineActionsViewV2] performRetweetAction (quote) called for item: \(item.id)")
            performKMPAction(actionType: .quote)
            return
        }

        FlareLog.debug("🔥 [TimelineActionsViewV2] performRetweetAction (repost) called for item: \(item.id)")

        timelineViewModel?.updateItemOptimistically(itemId: item.id, actionType: .retweet)

        performKMPAction(actionType: .repost)
    }

    private func handleReplyAction() {
        performKMPAction(actionType: .reply)
    }

    private func handleBookmarkAction() {
        FlareLog.debug("🔥 [TimelineActionsViewV2] handleBookmarkAction called for item: \(item.id)")

        timelineViewModel?.updateItemOptimistically(itemId: item.id, actionType: .bookmark)

        performKMPAction(actionType: .bookmark)
    }

    private func handleTranslateAction() {
        onAction(.translate, item)
    }

    private func performKMPAction(actionType: TimelineActionType) {
        FlareLog.debug("🔥 [TimelineActionsViewV2] performKMPAction called - actionType: \(actionType), item: \(item.id)")
        func findAndExecuteAction(in actions: [StatusAction], actionType: TimelineActionType) -> Bool {
            for (_, action) in actions.enumerated() {
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
                            router.handleDeepLink(url)
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

                Group {
                    if count > 0 {
                        Text("\(formatCount(Int64(count)))")
                            .foregroundColor(isActive ? activeColor : .primary)
                            .font(.caption)
                    }
                }
            }
            .padding(8)
        }
        .buttonStyle(BorderlessButtonStyle())
//        .onAppear {
//            FlareLog.debug("🎨 [ActionButtonV2] Button appeared - isActive: \(isActive), count: \(count), color: \(isActive ? "active" : "primary")")
//        }
//        .onChange(of: isActive) { oldValue, newValue in
//            FlareLog.debug("🎨 [ActionButtonV2] isActive changed: \(oldValue) → \(newValue), count: \(count)")
//        }
//        .onChange(of: count) { oldValue, newValue in
//            FlareLog.debug("🎨 [ActionButtonV2] count changed: \(oldValue) → \(newValue), isActive: \(isActive)")
//        }
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
