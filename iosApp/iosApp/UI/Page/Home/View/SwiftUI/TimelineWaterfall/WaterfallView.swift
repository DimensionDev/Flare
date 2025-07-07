import SwiftUI
import WaterfallGrid
import shared


struct WaterfallView: View {

    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool
    let displayType: TimelineDisplayType


    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router


    @State private var viewModel = TimelineViewModel()
    @State private var scrolledID: String?



    var body: some View {
        switch viewModel.timelineState {
        case .loading:
            TimelineLoadingView()
                .task(id: tab.key) {
                    await viewModel.setupDataSource(for: tab, using: store)
                }

        case let .loaded(items, hasMore, isRefreshing):
            WaterfallItemsView(
                items: items,
                displayType: displayType,
                hasMore: hasMore,
                isRefreshing: isRefreshing,
                presenter: viewModel.presenter,
                onError: viewModel.handleError,
                scrolledID: $scrolledID,
                scrollToTopTrigger: $scrollToTopTrigger,
                isCurrentTab: isCurrentTab,
                showFloatingButton: $showFloatingButton
            )

        case let .error(error):
            TimelineErrorView(error: error) {
                Task {
                    await viewModel.handleRefresh()
                }
            }
            .task(id: tab.key) {
                await viewModel.setupDataSource(for: tab, using: store)
            }

        case .empty:
            TimelineEmptyView()
                .task(id: tab.key) {
                    await viewModel.setupDataSource(for: tab, using: store)
                }
        }
    }
}


struct WaterfallItemsView: View {
    let items: [TimelineItem]
    let displayType: TimelineDisplayType
    let hasMore: Bool
    let isRefreshing: Bool
    let presenter: TimelinePresenter?
    let onError: (FlareError) -> Void
    @Binding var scrolledID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool
    @State private var viewModel = TimelineViewModel()

    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

     private var waterfallItems: [WaterfallItem] {
        FlareLog.debug("WaterfallItemsView Converting \(items.count) timeline items to waterfall items")

        let result: [WaterfallItem]
        switch displayType {
        case .mediaWaterfall:
            result = createMediaWaterfallItems(from: items)
        case .mediaCardWaterfall:
            result = createCardWaterfallItems(from: items)
        case .timeline:
            result = []
        }

        FlareLog.debug("WaterfallItemsView Converted to \(result.count) waterfall items")
        return result
    }

     private var allWaterfallMedias: [Media] {
        return waterfallItems.map { $0.displayMedia }
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
                            case .showMediaPreview(let media, _, _):
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
                    TimelineLoadMoreView(isRefreshing: isRefreshing)
                        .onAppear {
                            FlareLog.debug("WaterfallItemsView LoadMore onAppear triggered")
                            handleLoadMore()
                        }
                        .padding(.top, 16)
                }
            }
        }
        .scrollPosition(id: $scrolledID)
        .background(theme.secondaryBackgroundColor)
        .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in

            geometry
        } action: { _, newValue in
           viewModel.handleScrollOffsetChange(newValue.contentOffset.y, showFloatingButton: $showFloatingButton)
            // handleScrollOffsetChange(newValue.contentOffset.y)
        }
        .refreshable {

        }
        .onChange(of: scrollToTopTrigger) { _, _ in
            guard isCurrentTab else { return }

            if let firstItem = waterfallItems.first {
                withAnimation(.easeInOut(duration: 0.5)) {
                    scrolledID = firstItem.id
                }
            }
        }
    }
 
    private func createMediaWaterfallItems(from items: [TimelineItem]) -> [WaterfallItem] {
        var waterfallItems: [WaterfallItem] = []

        for item in items {
            guard !item.images.isEmpty else { continue }

            for (index, media) in item.images.enumerated() {
                let waterfallItem = WaterfallItem(
                    id: "\(item.id)_\(index)",
                    sourceTimelineItem: item,
                    displayMedia: media,
                    mediaIndex: index,
                    displayType: .mediaWaterfall
                )
                waterfallItems.append(waterfallItem)
            }
        }

        return waterfallItems
    }


    private func createCardWaterfallItems(from items: [TimelineItem]) -> [WaterfallItem] {
        return items.compactMap { item in
            guard let firstMedia = item.images.first else { return nil }

            return WaterfallItem(
                id: item.id,
                sourceTimelineItem: item,
                displayMedia: firstMedia,
                mediaIndex: 0,
                displayType: .mediaCardWaterfall
            )
        }
    }

    /// 处理点击行为
    private func handleClickAction(_ action: ClickAction, allWaterfallMedias: [Media], waterfallItems: [WaterfallItem]) {
        switch action {
        case .showMediaPreview(let media, let allMedias, let index):

            PhotoBrowserManagerV2.shared.showPhotoBrowser(
                media: media,
                images: allMedias,
                initialIndex: index
            )

        case .showWaterfallMediaPreview(let media, let allWaterfallMedias, let index):

            PhotoBrowserManagerV2.shared.showPhotoBrowser(
                media: media,
                images: allWaterfallMedias,
                initialIndex: index
            )

        case .showTimelineDetail(let timelineItem):

            let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
            let statusKey = timelineItem.createMicroBlogKey(from: timelineItem)

            FlareLog.debug("WaterfallItemsView Navigate to timeline detail: \(timelineItem.id)")
            router.navigate(to: .statusDetail(
                accountType: accountType,
                statusKey: statusKey
            ))
        }
    }

 
    private func handleLoadMore() {
        guard let presenter else { return }

        Task.detached(priority: .userInitiated) { [presenter, items] in
            let timelineState = presenter.models.value
            FlareLog.debug("WaterfallItemsView Current timeline state type: \(type(of: timelineState.listState))")

            if let pagingState = timelineState.listState as? PagingStateSuccess<UiTimeline> {
                let currentItemCount = Int(pagingState.itemCount)
                let nextPageIndex = items.count

                FlareLog.debug("WaterfallItemsView Load more check: timeline_items=\(items.count), total=\(currentItemCount)")
                FlareLog.debug("WaterfallItemsView AppendState: \(pagingState.appendState)")

                if nextPageIndex < currentItemCount {
                    FlareLog.debug("WaterfallItemsView Safe next page load: requesting index=\(nextPageIndex), total=\(currentItemCount)")


                    _ = pagingState.get(index: Int32(nextPageIndex))
                    FlareLog.debug("WaterfallItemsView Load more request sent")
                } else {
                    FlareLog.debug("WaterfallItemsView Skipped next page load: index=\(nextPageIndex) >= total=\(currentItemCount)")
                }
            } else {
                FlareLog.warning("WaterfallItemsView Timeline state is not PagingStateSuccess: \(type(of: timelineState.listState))")
            }
        }
    }
}


 
