import shared
import SwiftUI

struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let isRefreshing: Bool
    let presenter: TimelinePresenter?
    let onError: (FlareError) -> Void
    let viewModel: TimelineViewModel

    @State private var itemIndexMap: [String: Int] = [:]
    @State private var lastItemId: String?

    var body: some View {
        ForEach(items) { item in
            TimelineStatusViewV2(
                item: item,
                index: itemIndexMap[item.id] ?? 0,
                presenter: presenter,
                scrollPositionID: .constant(nil),
                onError: onError
            )
            .padding(.vertical, 4)
//            .onScrollVisibilityChange(threshold: 0.3) { isVisible in
            ////                if isVisible {
            ////                    FlareLog.debug("Timeline item \(item.id) became visible")
            ////                }
//            }
            .onAppear {
                if item.id == lastItemId, hasMore, !isRefreshing, !viewModel.isLoadingMore {
                    FlareLog.debug("Timeline Last item appeared, triggering load more")
                    handleLoadMore()
                }
            }
        }
        .onChange(of: items) { oldItems, newItems in
            FlareLog.debug("[Timeline ItemsView] items数组变化: \(oldItems.count) -> \(newItems.count)")
            if newItems.count > oldItems.count {
                FlareLog.debug("[Timeline ItemsView] 🎉 检测到新items！新增了 \(newItems.count - oldItems.count) 个")
                let newItemIds = newItems.suffix(newItems.count - oldItems.count).map(\.id)
                FlareLog.debug("[Timeline ItemsView] 新增items的ID: \(newItemIds)")
            }
            updateItemIndexMap(newItems)
            updateLastItemId(newItems)
        }
        .onAppear {
            updateItemIndexMap(items)
            updateLastItemId(items)
        }

//        if hasMore {
//            TimelineLoadMoreView(isRefreshing: isRefreshing)
//                .onAppear {
//                    // 🔥 只在非刷新状态下触发，避免重复
//                    if hasMore, !isRefreshing, !viewModel.isLoadingMore {
//                        FlareLog.debug("Timeline Load more triggered")
//                        handleLoadMore()
//                    }
//                }
//        }
    }

    private func handleLoadMore() {
        FlareLog.debug("[Timeline LoadMore UI] UI层触发load more，委托给ViewModel")
        // UI层只负责触发，业务逻辑委托给ViewModel
        Task {
            await viewModel.handleLoadMore()
        }
    }

    private func updateItemIndexMap(_ items: [TimelineItem]) {
        var newIndexMap: [String: Int] = [:]
        var duplicateCount = 0

        for (index, item) in items.enumerated() {
            if newIndexMap[item.id] != nil {
                duplicateCount += 1
                FlareLog.warning("Timeline Duplicate item ID found: \(item.id), using latest index: \(index)")
            }
            newIndexMap[item.id] = index
        }

        itemIndexMap = newIndexMap
        FlareLog.debug("Timeline Updated item index map with \(items.count) items, \(duplicateCount) duplicates handled")
    }

    private func updateLastItemId(_ items: [TimelineItem]) {
        lastItemId = items.last?.id
        FlareLog.debug("Timeline Updated last item ID: \(lastItemId ?? "nil")")
    }
}
