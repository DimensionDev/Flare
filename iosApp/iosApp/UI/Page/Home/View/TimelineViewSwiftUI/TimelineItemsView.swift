import shared
import SwiftUI

struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
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
                    index: itemIndexMap[item.id] ?? 0
                )
                .padding(.vertical, 4)
                .onAppear {
                    if let currentIndex = itemIndexMap[item.id] {
                        let remainingItems = items.count - currentIndex - 1

                        // 🔥 添加日志：loadMore触发条件检查
                        FlareLog.debug("[Timeline ItemsView] item出现 - ID: \(item.id), 当前索引: \(currentIndex), 剩余items: \(remainingItems)")
                        FlareLog.debug("[Timeline ItemsView] loadMore条件检查 - remainingItems <= \(loadMoreThreshold): \(remainingItems <= loadMoreThreshold), hasMore: \(hasMore), isLoadingMore: \(!viewModel.isLoadingMore)")

                        if remainingItems <= loadMoreThreshold, hasMore, !viewModel.isLoadingMore {
                            FlareLog.debug("[Timeline ItemsView] 触发loadMore - 当前索引: \(currentIndex), 剩余items: \(remainingItems)")
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
            // 🔥 增强日志：详细的数据变化分析
            FlareLog.debug("[Timeline ItemsView] items数组变化详情:")
            FlareLog.debug("  - 数量变化: \(oldItems.count) -> \(newItems.count)")
            FlareLog.debug("[Timeline ItemsView] items数组变化: \(oldItems.count) -> \(newItems.count)")

            if newItems.count > oldItems.count {
                let newItemIds = newItems.suffix(newItems.count - oldItems.count).map(\.id)
                FlareLog.debug("  - 新增items数量: \(newItems.count - oldItems.count)")
                FlareLog.debug("  - 新增items的ID: \(newItemIds)")
                FlareLog.debug("[Timeline ItemsView] 检测到新items！新增了 \(newItems.count - oldItems.count) 个")
                FlareLog.debug("[Timeline ItemsView] 新增items的ID: \(newItemIds)")

                // 🔥 添加日志：检查新增items的位置
                if !oldItems.isEmpty, !newItems.isEmpty {
                    let oldFirstId = oldItems.first?.id
                    let newFirstId = newItems.first?.id
                    FlareLog.debug("  - 第一个item变化: \(oldFirstId ?? "nil") -> \(newFirstId ?? "nil")")

                    if oldFirstId == newFirstId {
                        FlareLog.debug("  - 新增位置：底部追加")
                    } else {
                        FlareLog.debug("  - 新增位置：顶部插入或全量替换")
                    }
                }
            } else if newItems.count < oldItems.count {
                FlareLog.debug("  - 减少items数量: \(oldItems.count - newItems.count)")
            } else {
                FlareLog.debug("  - items数量未变，可能是内容更新")
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
        // 🔥 添加日志：索引映射更新
        FlareLog.debug("[Timeline ItemsView] updateItemIndexMap开始 - items数量: \(items.count)")

        var newIndexMap: [String: Int] = [:]
        var duplicateCount = 0

        for (index, item) in items.enumerated() {
            if newIndexMap[item.id] != nil {
                duplicateCount += 1
                FlareLog.warning("[Timeline ItemsView] 发现重复item ID: \(item.id), 使用最新索引: \(index)")
                FlareLog.warning("Timeline Duplicate item ID found: \(item.id), using latest index: \(index)")
            }
            newIndexMap[item.id] = index
        }

        itemIndexMap = newIndexMap
        FlareLog.debug("[Timeline ItemsView] 索引映射更新完成 - items: \(items.count), 重复处理: \(duplicateCount)")
        FlareLog.debug("Timeline Updated item index map with \(items.count) items, \(duplicateCount) duplicates handled")
    }

    private func updateLastItemId(_ items: [TimelineItem]) {
        lastItemId = items.last?.id
        FlareLog.debug("Timeline Updated last item ID: \(lastItemId ?? "nil")")
    }
}
