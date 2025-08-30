import shared
import SwiftUI
import WaterfallGrid

struct WaterfallItemsView: View {
    let items: [TimelineItem]
    let displayType: TimelineDisplayType
    let hasMore: Bool
    let onError: (FlareError) -> Void
    @Binding var scrolledID: String?
    let isCurrentAppBarTabSelected: Bool
    let viewModel: TimelineViewModel
    @EnvironmentObject private var timelineState: TimelineExtState
    @Environment(FlareRouter.self) private var router

    @State private var scrollThreshold: CGFloat = 500
    @State private var hasInitialLoadCompleted = false

    @Environment(FlareTheme.self) private var theme

    private var waterfallItems: [WaterfallItem] {
        FlareLog.debug("WaterfallItemsView Converting \(items.count) timeline items to waterfall items")

        let result: [WaterfallItem] = switch displayType {
        case .mediaWaterfall:
            createMediaWaterfallItems(from: items)
        case .mediaCardWaterfall:
            createCardWaterfallItems(from: items)
        case .timeline:
            []
        }

        FlareLog.debug("WaterfallItemsView Converted to \(result.count) waterfall items")
        return result
    }

    private var allWaterfallMedias: [Media] {
        waterfallItems.map(\.displayMedia)
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                WaterfallGrid(waterfallItems, id: \.id) { item in
                    WaterfallItemView(
                        item: item,
                        onTap: { action in
                            let waterfallAction: ClickAction
                            switch action {
                            case let .showMediaPreview(media, _, _):
                                let waterfallIndex = allWaterfallMedias.firstIndex { $0.url == media.url } ?? 0
                                waterfallAction = .showWaterfallMediaPreview(
                                    media: media,
                                    allWaterfallMedias: allWaterfallMedias,
                                    index: waterfallIndex
                                )
                            default:
                                waterfallAction = action
                            }
                            handleClickAction(waterfallAction, allWaterfallMedias: allWaterfallMedias, waterfallItems: waterfallItems)
                        }
                    )
                }
                .gridStyle(
                    columnsInPortrait: 2,
                    columnsInLandscape: 3,
                    spacing: displayType == .mediaWaterfall ? 8 : 12,
                    animation: .easeInOut(duration: 0.3)
                )
                .scrollOptions(direction: .vertical)
                .padding(.horizontal, 8)
                if hasMore {
                    TimelineLoadMoreView {
                        try await viewModel.handleLoadMore(scrollToId: "")
                    }
                    .padding(.top, 16)
                }
            }
        }
        .onAppear {
            hasInitialLoadCompleted = false
            FlareLog.debug("Waterfall view appeared, reset trigger flags")

            if items.count > 0 {
                FlareLog.debug("Waterfall has \(items.count) items, setting up LoadMore protection")
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    hasInitialLoadCompleted = true
                    FlareLog.debug("Waterfall initial load completed, LoadMore protection enabled")
                }
            } else {
                hasInitialLoadCompleted = true
                FlareLog.debug("Waterfall no initial items, LoadMore protection enabled immediately")
            }
        }
        .scrollPosition(id: $scrolledID)
        .background(theme.secondaryBackgroundColor)
        .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
            geometry
        } action: { _, newValue in
            viewModel.handleScrollOffsetChange(newValue.contentOffset.y, showFloatingButton: $timelineState.showFloatingButton)

            let currentOffset = newValue.contentOffset.y

            let contentHeight = newValue.contentSize.height
            let scrollViewHeight = newValue.visibleRect.height

            let distanceFromBottom = contentHeight - (currentOffset + scrollViewHeight)

            if distanceFromBottom <= scrollThreshold,
               hasMore,
               !viewModel.isLoadingMore,
               hasInitialLoadCompleted
            {
                FlareLog.debug("Waterfall Near bottom, triggering load more (distance: \(distanceFromBottom))")
                Task {
                    await viewModel.handleLoadMore(scrollToId: "")
                }
            }
        }
        .refreshable {}
        .onChange(of: timelineState.scrollToTopTrigger) { _, _ in
            guard isCurrentAppBarTabSelected else { return }

            if let firstItem = waterfallItems.first {
                withAnimation(.easeInOut(duration: 0.5)) {
                    scrolledID = firstItem.id
                }
            }
        }
    }

    private func createMediaWaterfallItems(from items: [TimelineItem]) -> [WaterfallItem] {
        var waterfallItems: [WaterfallItem] = []
        var existingIds: Set<String> = []

        for item in items {
            guard !item.images.isEmpty else { continue }

            for (index, media) in item.images.enumerated() {
                let itemId = "\(item.id)_\(index)"

                if existingIds.contains(itemId) {
                    FlareLog.debug("WaterfallView: Skipping duplicate item with ID: \(itemId)")
                    continue
                }

                let waterfallItem = WaterfallItem(
                    id: itemId,
                    sourceTimelineItem: item,
                    displayMedia: media,
                    mediaIndex: index,
                    displayType: .mediaWaterfall
                )
                waterfallItems.append(waterfallItem)
                existingIds.insert(itemId)
            }
        }

        return waterfallItems
    }

    private func createCardWaterfallItems(from items: [TimelineItem]) -> [WaterfallItem] {
        var existingIds: Set<String> = []

        return items.compactMap { item in
            guard let firstMedia = item.images.first else { return nil }

            let itemId = item.id

            if existingIds.contains(itemId) {
                FlareLog.debug("WaterfallView: Skipping duplicate card item with ID: \(itemId)")
                return nil
            }

            existingIds.insert(itemId)

            return WaterfallItem(
                id: itemId,
                sourceTimelineItem: item,
                displayMedia: firstMedia,
                mediaIndex: 0,
                displayType: .mediaCardWaterfall
            )
        }
    }

    private func handleClickAction(_ action: ClickAction, allWaterfallMedias _: [Media], waterfallItems _: [WaterfallItem]) {
        switch action {
        case let .showMediaPreview(media, allMedias, index):
            PhotoBrowserManagerV2.shared.showPhotoBrowser(
                media: media,
                images: allMedias,
                initialIndex: index
            )

        case let .showWaterfallMediaPreview(media, allWaterfallMedias, index):
            PhotoBrowserManagerV2.shared.showPhotoBrowser(
                media: media,
                images: allWaterfallMedias,
                initialIndex: index
            )

        case let .showTimelineDetail(timelineItem):
            let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
            let statusKey = timelineItem.createMicroBlogKey()

            router.navigate(to: .statusDetailV2(
                accountType: accountType,
                statusKey: statusKey,
                preloadItem: timelineItem
            ))
        }
    }
}
