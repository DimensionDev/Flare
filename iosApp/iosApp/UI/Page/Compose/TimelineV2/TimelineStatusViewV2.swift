
import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
import SwiftDate
import SwiftUI
import UIKit

// MARK: - Swift原生类型定义
//enum SwiftAccountType {
//    case specific(accountKey: String)
//    case active
//    case guest
//}
//
//struct SwiftMicroBlogKey {
//    let id: String
//    let host: String
//
//    init(id: String, host: String) {
//        self.id = id
//        self.host = host
//    }
//}

struct TimelineStatusViewV2: View {
    let item: TimelineItem
    let index: Int
    let presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    let onError: (FlareError) -> Void

    // 添加TimelineStatusView需要的状态和环境变量
    let isDetail: Bool = false
    let enableTranslation: Bool = true
    @State private var showMedia: Bool = false
    @State private var showShareMenu: Bool = false

    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    // 媒体点击回调 - 使用Swift Media类型
    private let onMediaClick: (Int, Media) -> Void = { _, _ in }

    // 创建临时的StatusViewModel来兼容现有组件
    private var viewModel: StatusViewModel? {
        // 暂时返回nil，后续需要从TimelineItem转换为StatusViewModel
        // 或者直接修改组件使用TimelineItem
        return nil
    }
    
    var body: some View {
        // 🔥 新增：Timeline级别敏感内容隐藏检查
        if shouldHideInTimeline {
            EmptyView()
        } else {
            timelineContent
        }
    }

    // MARK: - 敏感内容隐藏逻辑

    /// Timeline级别敏感内容隐藏判断 - 对应V1版本StatusItemView.shouldHideInTimeline
    private var shouldHideInTimeline: Bool {
        let sensitiveSettings = appSettings.appearanceSettings.sensitiveContentSettings

        // 第一步：检查是否开启timeline隐藏功能
        guard sensitiveSettings.hideInTimeline else {
//            FlareLog.debug("TimelineStatusViewV2 SensitiveContent Timeline隐藏未开启 - item.id: \(item.id)")
            return false
        }

        // 第二步：检查内容是否为敏感内容
        guard item.sensitive else {
//            FlareLog.debug("TimelineStatusViewV2 SensitiveContent 内容非敏感 - item.id: \(item.id)")
            return false
        }

        // 第三步：根据时间范围设置决定是否隐藏
        if let timeRange = sensitiveSettings.timeRange {
            // 有时间范围：只在时间范围内隐藏
            let shouldHide = timeRange.isCurrentTimeInRange()
//            FlareLog.debug("TimelineStatusViewV2 SensitiveContent 时间范围检查 - item.id: \(item.id), shouldHide: \(shouldHide)")
            return shouldHide
        } else {
            // 没有时间范围：总是隐藏敏感内容
//            FlareLog.debug("TimelineStatusViewV2 SensitiveContent 总是隐藏敏感内容 - item.id: \(item.id)")
            return true
        }
    }


    private var timelineContent: some View {
        // 添加详细日志
//        let _ = FlareLog.debug("TimelineStatusViewV2 渲染Timeline项目")
//        let _ = FlareLog.debug("TimelineStatusViewV2 item.id: \(item.id)")
//        let _ = FlareLog.debug("TimelineStatusViewV2 item.hasImages: \(item.hasImages)")
//        let _ = FlareLog.debug("TimelineStatusViewV2 item.images.count: \(item.images.count)")
//        let _ = FlareLog.debug("TimelineStatusViewV2 item.images: \(item.images)")

        // 使用TimelineStatusView的结构
        return VStack(alignment: .leading) {
            Spacer().frame(height: 2)

            // 🔥 新增：转发头部显示 - 条件显示topMessage
            if let topMessage = item.topMessage {
                StatusRetweetHeaderComponentV2(topMessage: topMessage)
                    .environment(router)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 4)
            }

            // 使用V2版本的StatusHeaderView - 直接使用TimelineItem
            StatusHeaderViewV2(
                item: item,
                isDetailView: isDetail
            )

            // 使用V2版本的StatusContentView - 直接使用TimelineItem，添加左右边距
            StatusContentViewV2(
                item: item,
                isDetailView: isDetail,
                enableTranslation: enableTranslation,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL,
                onMediaClick: { index, media in
                    // TODO: 需要适配Swift Media类型的回调
                    // onMediaClick(index, media)
                },
                onPodcastCardTap: { card in
                    handlePodcastCardTap(card: card)
                }
            )
            // 添加左右16点的边距

            // 使用V2版本的StatusActionsView
//            if let viewModel = viewModel {
//                StatusActionsViewV2(
//                    viewModel: viewModel,
//                    appSettings: appSettings,
//                    openURL: openURL,
//                    parentView: self
//                )
//            } else {
                // 暂时使用现有的V2 Actions (当viewModel为nil时)
                TimelineActionsViewV2(
                    item: item,
                    onAction: { actionType, updatedItem in
                        handleTimelineAction(actionType, item: updatedItem, at: index)
                    }
                )
//            }

            // Spacer().frame(height: 3)
        }
         .padding(.horizontal, 16)
        .frame(alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture {
            handleStatusTap()
        }
        .onAppear {
            // 保留原有的onAppear逻辑
            // 设置滚动位置ID
            if index == 0 {
                scrollPositionID = item.id
            }

            // TODO: 预加载逻辑需要重新实现，暂时移除shared依赖
            // 原逻辑依赖TimelineState和PagingStateSuccess，需要Swift原生实现
            Task {
                // 暂时使用简化的预加载逻辑
                if index > 0 && index % 10 == 0 {
                    FlareLog.debug("TimelineItemRowView Simplified preload trigger at index: \(index)")
                }
            }
        }
    }
    
    // MARK: - 从TimelineStatusView复制的方法

    private func handleStatusTap() {
        // 🔥 实现推文点击跳转到详情页面
        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()

        // 构造MicroBlogKey - 需要从item.id和platformType构造
        let statusKey = createMicroBlogKey(from: item)

        FlareLog.debug("TimelineStatusView Navigate to status detail: \(item.id)")
        router.navigate(to: .statusDetail(
            accountType: accountType,
            statusKey: statusKey
        ))
    }

    private func handlePodcastCardTap(card: Card) {
        // 🔥 实现播客卡片点击处理
        if let route = AppDeepLinkHelper().parse(url: card.url) as? AppleRoute.Podcast {
            FlareLog.debug("TimelineStatusViewV2 Podcast Card Tapped, navigating to: podcastSheet(accountType: \(route.accountType), podcastId: \(route.id))")
            router.navigate(to: .podcastSheet(accountType: route.accountType, podcastId: route.id))
        } else {
            let parsedRoute = AppDeepLinkHelper().parse(url: card.url)
            FlareLog.error("TimelineStatusViewV2 Error: Could not parse Podcast URL from card: \(card.url). Parsed type: \(type(of: parsedRoute)) Optional value: \(String(describing: parsedRoute))")
            // 降级处理：使用系统URL打开
            if let url = URL(string: card.url) {
                openURL(url)
            }
        }
    }

    // MARK: - 辅助方法

    /// 从TimelineItem创建MicroBlogKey
    private func createMicroBlogKey(from item: TimelineItem) -> MicroBlogKey {
        // 从platformType推断host
        let host = extractHostFromPlatformType(item.platformType)
        return MicroBlogKey(id: item.id, host: host)
    }

    /// 从platformType提取host信息
    private func extractHostFromPlatformType(_ platformType: String) -> String {
        // 根据platformType推断默认host
        switch platformType.lowercased() {
        case "mastodon":
            return "mastodon.social" // 默认Mastodon实例
        case "bluesky":
            return "bsky.app"
        case "misskey":
            return "misskey.io"
        case "xqt", "twitter":
            return "x.com"
        case "vvo":
            return "weibo.com"
        default:
            return "unknown.host"
        }
    }

    private func handleTimelineAction(_ actionType: TimelineActionType, item: TimelineItem, at index: Int) {
        FlareLog.debug("TimelineView_v2 Handling action \(actionType) for item: \(item.id) at index: \(index)")
        FlareLog.debug("TimelineView_v2 Received updated item state:")
        FlareLog.debug("   - ID: \(item.id)")
        FlareLog.debug("   - Like count: \(item.likeCount)")
        FlareLog.debug("   - Is liked: \(item.isLiked)")
        FlareLog.debug("   - Retweet count: \(item.retweetCount)")
        FlareLog.debug("   - Is retweeted: \(item.isRetweeted)")
        FlareLog.debug("   - Bookmark count: \(item.bookmarkCount)")
        FlareLog.debug("   - Is bookmarked: \(item.isBookmarked)")

        Task {
            FlareLog.debug("TimelineView_v2 Updating local state for index: \(index)")
            FlareLog.debug("TimelineView_v2 Local state update logged for index: \(index)")
        }
    }
}

