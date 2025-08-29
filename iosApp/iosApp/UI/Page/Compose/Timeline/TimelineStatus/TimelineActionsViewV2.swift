
import Foundation
import Generated
import shared
import SwiftUI

extension NSNotification.Name {
    static let timelineItemUpdated = NSNotification.Name("timelineItemUpdated")
}

struct TimelineActionsViewV2: View, Equatable {
    let item: TimelineItem
    let timelineViewModel: TimelineViewModel?

    let onAction: (TimelineActionType, TimelineItem) -> Void
    let onShare: (MoreActionType) -> Void

    @Environment(FlareRouter.self) private var router

    @State private var showRetweetMenu = false

    static func == (lhs: TimelineActionsViewV2, rhs: TimelineActionsViewV2) -> Bool {
        lhs.item.id == rhs.item.id &&
            lhs.item.likeCount == rhs.item.likeCount &&
            lhs.item.isLiked == rhs.item.isLiked &&
            lhs.item.retweetCount == rhs.item.retweetCount &&
            lhs.item.isRetweeted == rhs.item.isRetweeted &&
            lhs.item.bookmarkCount == rhs.item.bookmarkCount &&
            lhs.item.isBookmarked == rhs.item.isBookmarked
    }


    private var isBlueskyPlatform: Bool {
        item.platformType.lowercased() == "bluesky"
    }

    private var isMisskeyPlatform: Bool {
        item.platformType.lowercased() == "misskey"
    }

    private var isVVOPlatform: Bool {
        item.platformType.lowercased() == "vvo"
    }

    var body: some View {
        HStack(spacing: 0) {
            ActionButtonV2(
                iconImage: Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline),
                count: item.replyCount,
                isActive: false,
                activeColor: .blue
            ) {
                FlareHapticManager.shared.buttonPress()
                handleReplyAction()
            }
            .frame(maxWidth: .infinity)

            // VVO平台不支持retweet功能
            if !isVVOPlatform {
                ActionButtonV2(
                    iconImage: Image(asset: Asset.Image.Status.Toolbar.repeat),
                    count: item.retweetCount,
                    isActive: item.isRetweeted,
                    activeColor: .green
                ) {
                    FlareHapticManager.shared.buttonPress()
                    handleRetweetAction()
                }
                .frame(maxWidth: .infinity)
                .confirmationDialog("Retweet Options", isPresented: $showRetweetMenu) {
                    Button(item.isRetweeted ? "retweet_remove" : "Retweet") {
                        FlareHapticManager.shared.buttonPress()
                        performRetweetAction(isQuote: false)
                    }
                    Button("Quote Tweet") {
                        FlareHapticManager.shared.buttonPress()
                        performRetweetAction(isQuote: true)
                    }
                    Button("Cancel", role: .cancel) {}
                }
            }

            //  Misskey平台使用表情反应系统，
            if !isMisskeyPlatform {
                ActionButtonV2(
                    iconImage: item.isLiked ?
                        Image(asset: Asset.Image.Status.Toolbar.favorite) :
                        Image(asset: Asset.Image.Status.Toolbar.favoriteBorder),
                    count: item.likeCount,
                    isActive: item.isLiked,
                    activeColor: .red
                ) {
                    FlareHapticManager.shared.buttonPress()
                    handleLikeAction()
                }
                .frame(maxWidth: .infinity)
            }

            // Bluesky和VVO平台不支持bookmark功能
            if !isBlueskyPlatform, !isVVOPlatform {
                ActionButtonV2(
                    iconImage: item.isBookmarked ?
                        Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled) :
                        Image(asset: Asset.Image.Status.Toolbar.bookmark),
                    count: item.bookmarkCount,
                    isActive: item.isBookmarked,
                    activeColor: .orange
                ) {
                    FlareHapticManager.shared.buttonPress()
                    handleBookmarkAction()
                }
                .frame(maxWidth: .infinity)
            }

            // ActionButtonV2(
            //     iconImage: Image(systemName: "character.bubble"),
            //     count: 0,
            //     isActive: false,
            //     activeColor: .blue
            // ) {
            //     handleTranslateAction()
            // }
            // .frame(maxWidth: .infinity)

            ShareButtonV3(
                item: item,
                onMoreAction: onShare
            )
        }
        .padding(.vertical, 8)
//        .id(item.id)
    }

    private func handleLikeAction() {
        FlareLog.debug("🔥 [TimelineActionsViewV2] handleLikeAction called for item: \(item.id)")
        FlareLog.debug("🔍 [TimelineActionsViewV2] handleLikeAction called for item: isLiked=\(item.isLiked), likeCount=\(item.likeCount)")

        
        let targetState = !item.isLiked
        timelineViewModel?.updateItemOptimisticallyWithState(itemId: item.id, actionType: .like, targetState: targetState)

        
        let success = performKMPAction(actionType: .like, targetState: targetState)

        
        if !success {
            FlareLog.debug("� [TimelineActionsViewV2] Like操作失败，回滚状态")
            timelineViewModel?.updateItemOptimisticallyWithState(itemId: item.id, actionType: .like, targetState: !targetState)
        }
    }

    private func handleRetweetAction() {
        showRetweetMenu = true
    }

    private func performRetweetAction(isQuote: Bool) {
        if isQuote {
            FlareLog.debug("🔥 [TimelineActionsViewV2] performRetweetAction (quote) called for item: \(item.id)")
            let _ = performKMPAction(actionType: .quote, targetState: true)
            return
        }

        FlareLog.debug("🔥 [TimelineActionsViewV2] performRetweetAction (repost) called for item: \(item.id)")

        
        let targetState = !item.isRetweeted
        timelineViewModel?.updateItemOptimisticallyWithState(itemId: item.id, actionType: .retweet, targetState: targetState)

        
        let success = performKMPAction(actionType: .repost, targetState: targetState)

        
        if !success {
            FlareLog.debug("🔄 [TimelineActionsViewV2] Retweet操作失败，回滚状态")
            timelineViewModel?.updateItemOptimisticallyWithState(itemId: item.id, actionType: .retweet, targetState: !targetState)
        }
    }

    private func handleReplyAction() {
        let _ = performKMPAction(actionType: .reply, targetState: true)
    }

    private func handleBookmarkAction() {
        FlareLog.debug("🔥 [TimelineActionsViewV2] handleBookmarkAction called for item: \(item.id)")

        
        let targetState = !item.isBookmarked
        timelineViewModel?.updateItemOptimisticallyWithState(itemId: item.id, actionType: .bookmark, targetState: targetState)

        
        let success = performKMPAction(actionType: .bookmark, targetState: targetState)

        
        if !success {
            FlareLog.debug("🔄 [TimelineActionsViewV2] Bookmark操作失败，回滚状态")
            timelineViewModel?.updateItemOptimisticallyWithState(itemId: item.id, actionType: .bookmark, targetState: !targetState)
        }
    }

    private func handleTranslateAction() {
        onAction(.translate, item)
    }

    private func performKMPAction(actionType: TimelineActionType, targetState: Bool) -> Bool {
        FlareLog.debug("🔥 [TimelineActionsViewV2] performKMPAction called - actionType: \(actionType), item: \(item.id)")
        func findAndExecuteAction(in actions: [StatusAction], actionType: TimelineActionType) -> Bool {
            for (_, action) in actions.enumerated() {
                let enumResult = onEnum(of: action)

                if case let .item(actionItem) = enumResult,
                   let likeAction = actionItem as? StatusActionItemLike
                {
                    if actionType == .like {
                        FlareLog.debug("🎯 [TimelineActionsViewV2] 执行Like操作: targetState=\(targetState)")

                        if let onClickedWithState = likeAction.onClickedWithState {
                            let result = onClickedWithState(KotlinBoolean(value: targetState))

                            if result.success {
                                FlareLog.debug("✅ [TimelineActionsViewV2] Like操作成功")
                                FlareHapticManager.shared.buttonPress()
                                return true
                            } else {
                                let errorMessage = result.errorMessage ?? "点赞操作失败，请重试"
                                FlareLog.error("❌ [TimelineActionsViewV2] Like操作失败: \(errorMessage)")
                                ErrorToastManager.shared.show(message: errorMessage)
                                FlareHapticManager.shared.buttonPress()
                                return false
                            }
                        } else {
                            FlareLog.debug("🔄 [TimelineActionsViewV2] 使用原有Like方法")
                            fallbackToOriginalLikeMethod(likeAction)
                            return true
                        }
                    }
                } else if case let .item(actionItem) = enumResult,
                          let bookmarkAction = actionItem as? StatusActionItemBookmark
                {
                    if actionType == .bookmark {
                        FlareLog.debug("🎯 [TimelineActionsViewV2] 执行Bookmark操作: targetState=\(targetState)")

                        if let onClickedWithState = bookmarkAction.onClickedWithState {
                            let result = onClickedWithState(KotlinBoolean(value: targetState))

                            if result.success {
                                FlareLog.debug("✅ [TimelineActionsViewV2] Bookmark操作成功")
                                FlareHapticManager.shared.buttonPress()
                                return true
                            } else {
                                let errorMessage = result.errorMessage ?? "书签操作失败，请重试"
                                FlareLog.error("❌ [TimelineActionsViewV2] Bookmark操作失败: \(errorMessage)")
                                ErrorToastManager.shared.show(message: errorMessage)
                                FlareHapticManager.shared.buttonPress()
                                return false
                            }
                        } else {
                            FlareLog.debug("🔄 [TimelineActionsViewV2] 使用原有Bookmark方法")
                            fallbackToOriginalBookmarkMethod(bookmarkAction)
                            return true
                        }
                    }
                } else if case let .item(actionItem) = enumResult,
                          let retweetAction = actionItem as? StatusActionItemRetweet
                {
                    if actionType == .repost {
                        FlareLog.debug("🎯 [TimelineActionsViewV2] 执行Retweet操作: targetState=\(targetState)")

                        if let onClickedWithState = retweetAction.onClickedWithState {
                            let result = onClickedWithState(KotlinBoolean(value: targetState))

                            if result.success {
                                FlareLog.debug("✅ [TimelineActionsViewV2] Retweet操作成功")
                                FlareHapticManager.shared.buttonPress()
                                return true
                            } else {
                                let errorMessage = result.errorMessage ?? "转发操作失败，请重试"
                                FlareLog.error("❌ [TimelineActionsViewV2] Retweet操作失败: \(errorMessage)")
                                ErrorToastManager.shared.show(message: errorMessage)
                                FlareHapticManager.shared.buttonPress()
                                return false
                            }
                        } else {
                            FlareLog.debug("🔄 [TimelineActionsViewV2] 使用原有Retweet方法")
                            fallbackToOriginalRetweetMethod(retweetAction)
                            return true
                        }
                    }
                } else if case let .item(actionItem) = enumResult,
                          let clickable = actionItem as? StatusActionItemClickable
                {
                    let shouldExecute = switch actionType {
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

        return findAndExecuteAction(in: item.actions, actionType: actionType)
    }



}

private struct ActionButtonV2: View {
    let iconImage: Image
    let count: Int
    let isActive: Bool
    let activeColor: Color
    let action: () -> Void

    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                iconImage
                    .renderingMode(.template)
                    .foregroundColor(isActive ? activeColor : theme.labelColor)

                Group {
                    if count > 0 {
                        Text("\(formatCount(Int64(count)))")
                            .foregroundColor(isActive ? activeColor : theme.labelColor)
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

extension TimelineActionsViewV2 {
    private func fallbackToOriginalLikeMethod(_ likeAction: StatusActionItemLike) {

        let clickable = likeAction as StatusActionItemClickable
        let openURLAction = OpenURLAction { url in
            router.handleDeepLink(url)
            return .handled
        }
        clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURLAction)))
    }

    private func fallbackToOriginalBookmarkMethod(_ bookmarkAction: StatusActionItemBookmark) {
        let clickable = bookmarkAction as StatusActionItemClickable
        let openURLAction = OpenURLAction { url in
            router.handleDeepLink(url)
            return .handled
        }
        clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURLAction)))
    }

    private func fallbackToOriginalRetweetMethod(_ retweetAction: StatusActionItemRetweet) {
        let clickable = retweetAction as StatusActionItemClickable
        let openURLAction = OpenURLAction { url in
            router.handleDeepLink(url)
            return .handled
        }
        clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURLAction)))
    }
}
