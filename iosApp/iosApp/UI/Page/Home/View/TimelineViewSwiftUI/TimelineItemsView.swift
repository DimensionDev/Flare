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

    var body: some View {
        ForEach(items) { item in
            TimelineStatusViewV2(
                item: item,
                index: itemIndexMap[item.id] ?? 0,
            )
            .padding(.vertical, 4)
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
