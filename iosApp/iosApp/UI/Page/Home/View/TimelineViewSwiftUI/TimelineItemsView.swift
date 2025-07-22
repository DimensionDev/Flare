import shared
import SwiftUI

struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let isRefreshing: Bool
    let viewModel: TimelineViewModel

    @State private var itemIndexMap: [String: Int] = [:]
    @State private var lastItemId: String?
    @Environment(\.appSettings) private var appSettings

    private let loadMoreThreshold = 3

    var body: some View {
        Group {
            ForEach(items) { item in
                TimelineStatusViewV2(
                    item: item,
                    index: itemIndexMap[item.id] ?? 0,
                )
                .padding(.vertical, 4)
                .onAppear {
                    if let currentIndex = itemIndexMap[item.id] {
                        let remainingItems = items.count - currentIndex - 1

                        if remainingItems <= loadMoreThreshold, hasMore, !isRefreshing, !viewModel.isLoadingMore {
                            FlareLog.debug("Timeline 提前触发load more，当前索引: \(currentIndex), 剩余items: \(remainingItems)")
                            handleLoadMore()
                        }
                    }
                }
            }

            if viewModel.isLoadingMore {
                ForEach(0 ..< 2, id: \.self) { index in
                    TimelineStatusViewV2(
                        item: createSampleTimelineItem(),
                        index: items.count + index
                    )
                    .redacted(reason: .placeholder)
                    .padding(.vertical, 4)
                }
            }
        }
        .onChange(of: items) { oldItems, newItems in
            FlareLog.debug("[Timeline ItemsView] items数组变化: \(oldItems.count) -> \(newItems.count)")
            if newItems.count > oldItems.count {
                FlareLog.debug("[Timeline ItemsView] 检测到新items！新增了 \(newItems.count - oldItems.count) 个")
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
    }

    private func handleLoadMore() {
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
