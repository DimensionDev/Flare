import shared
import SwiftUI

struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let viewModel: TimelineViewModel

    @State private var itemIndexMap: [String: Int] = [:]
    @Environment(\.appSettings) private var appSettings

    var body: some View {
        Group {
            ForEach(items) { item in
                TimelineStatusViewV2(
                    item: item,
                    index: itemIndexMap[item.id] ?? 0
                )
                .padding(.vertical, 4)
                .onAppear {
                    //FlareLog.debug("[TimelineItemsView] item出现: \(item.id), index: \(index)")
                    viewModel.itemDidAppear(item: item)
                }
                .onDisappear {
                    //FlareLog.debug("[TimelineItemsView] item消失: \(item.id), index: \(index)")
                    viewModel.itemDidDisappear(item: item)
                }
                //.id(item.id)
            }


            if hasMore {
                TimelineLoadMoreView {
                    FlareLog.debug("[TimelineItemsView] LoadMoreView触发handleLoadMore")
                    try await viewModel.handleLoadMore()
                }
                .onAppear {
                    FlareLog.debug("[TimelineItemsView] 创建TimelineLoadMoreView - hasMore: \(hasMore), items数量: \(items.count)")
                }
            }
        }
        .onChange(of: items) { oldItems, newItems in
            FlareLog.debug("[Timeline ItemsView] items数组变化: \(oldItems.count) -> \(newItems.count)")
            updateItemIndexMap(newItems)
        }
        .onAppear {
            FlareLog.debug("[Timeline ItemsView] TimelineItemsView appeared with \(items.count) items")
            updateItemIndexMap(items)
        }
    }

    private func updateItemIndexMap(_ items: [TimelineItem]) {
        var newIndexMap: [String: Int] = [:]
        for (index, item) in items.enumerated() {
            newIndexMap[item.id] = index
        }
        itemIndexMap = newIndexMap
        FlareLog.debug("[Timeline ItemsView] 更新索引映射 - items: \(items.count)")
    }
}
