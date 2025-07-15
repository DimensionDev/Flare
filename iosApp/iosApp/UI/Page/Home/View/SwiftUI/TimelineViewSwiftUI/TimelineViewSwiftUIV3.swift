import Combine
import shared
import SwiftUI

struct TimelineViewSwiftUIV3: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool
    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router


    @State private var viewModel = TimelineViewModel()

    @State private var scrolledID: String?

    var body: some View {
        VStack {
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    switch viewModel.timelineState {
                    case .loading:
                        TimelineLoadingView()

                    case let .loaded(items, hasMore, isRefreshing):
                        TimelineItemsView(
                            items: items,
                            hasMore: hasMore,
                            isRefreshing: isRefreshing,
                            presenter: viewModel.presenter,
                            onError: viewModel.handleError,
                            viewModel: viewModel
                        )

                    case let .error(error):
                        TimelineErrorView(error: error) {
                            Task {
                                await viewModel.handleRefresh()
                            }
                        }

                    case .empty:
                        TimelineEmptyView()
                    }
                }
            }
            .scrollPosition(id: $scrolledID)
//            .background(theme.secondaryBackgroundColor)
            .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
                // FlareLog.debug("TimelineV3 scroll offset: \(geometry.contentOffset)")
                geometry
            } action: { _, newValue in
                viewModel.handleScrollOffsetChange(newValue.contentOffset.y, showFloatingButton: $showFloatingButton)
            }
            .refreshable {
                await viewModel.handleRefresh()
            }
            .task(id: tab.key) {
                await viewModel.setupDataSource(for: tab, using: store)
            }
        }
//        .background(theme.secondaryBackgroundColor)
        .onChange(of: scrollToTopTrigger) {
            let _ = FlareLog.debug("TimelineV3 ScrollToTop trigger for tab: \(tab.key)")
            guard isCurrentTab else {
                FlareLog.debug("TimelineV3 ScrollToTop skipped: not current tab")
                return
            }
            guard let firstID = viewModel.getFirstItemID() else {
                FlareLog.debug("TimelineV3 ScrollToTop skipped: no items")
                return
            }

            FlareLog.debug("TimelineV3 ScrollToTop executing: scrolling to firstID=\(firstID)")
            withAnimation(.spring) {
                scrolledID = firstID
            }
        }
    }
}
