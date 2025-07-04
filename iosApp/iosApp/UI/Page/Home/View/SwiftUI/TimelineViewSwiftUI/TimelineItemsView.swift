import shared
import SwiftUI

 
struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let isRefreshing: Bool
    let presenter: TimelinePresenter?
    let onError: (FlareError) -> Void

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
            .onScrollVisibilityChange(threshold: 0.3) { isVisible in
                if isVisible {
                    FlareLog.debug("Timeline item \(item.id) became visible")
                }
            }
            .onAppear { 
                if item.id == lastItemId && hasMore {
                    FlareLog.debug("Timeline Last item appeared, triggering load more")
                    handleLoadMore()
                }
            }
        }
        .onChange(of: items) { _, newItems in
            updateItemIndexMap(newItems)
            updateLastItemId(newItems)
        }
        .onAppear {
            updateItemIndexMap(items)
            updateLastItemId(items)
        }

       
        if hasMore {
            TimelineLoadMoreView(isRefreshing: isRefreshing)
                .onAppear {
                    FlareLog.debug("Timeline Load more triggered")
                    handleLoadMore()
                }
        }
    }

    private func handleLoadMore() {
        FlareLog.debug("Timeline Handling load more")

        guard let presenter = presenter else {
            FlareLog.warning("Timeline No presenter available for load more")
            return
        }

        Task.detached(priority: .userInitiated) { [presenter, items] in
            let timelineState = presenter.models.value
            if let pagingState = timelineState.listState as? PagingStateSuccess<UiTimeline> {
                let currentItemCount = Int(pagingState.itemCount)
                let nextPageIndex = items.count

                if nextPageIndex < currentItemCount {
                    FlareLog.debug("Timeline Safe next page load: requesting index=\(nextPageIndex), total=\(currentItemCount)")
                    _ = pagingState.get(index: Int32(nextPageIndex))
                } else {
                    FlareLog.debug("Timeline Skipped next page load: index=\(nextPageIndex) >= total=\(currentItemCount)")
                }
            }
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

 

struct TimelineLoadMoreView: View {
    let isRefreshing: Bool

    var body: some View {
        HStack {
            if isRefreshing {
                ProgressView()
                    .scaleEffect(0.8)
                Text("Loading more...")
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else {
                Text("Pull to load more")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 50)
    }
}
