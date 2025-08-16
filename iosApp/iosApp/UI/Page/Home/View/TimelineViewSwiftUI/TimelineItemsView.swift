import shared
import SwiftUI

struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let viewModel: TimelineViewModel

    @Environment(\.appSettings) private var appSettings

    var body: some View {
        ForEach(items) { item in
            TimelineStatusViewV2(
                item: item,
                timelineViewModel: viewModel
            ).id(item.id)
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
                .onAppear {
                    // viewModel.itemOnAppear(item: item)
                    FlareLog.debug("🔍 [TimelineItemsView] onAppear  for id: '\(item.id)', content: '\(item.content.raw)'")

//                    Task {
//                        if hasMore, !viewModel.isLoadingMore,
//                           items.count >= 7,
//                           item.id == items[items.count - 5].id ||
//                           item.id == items[items.count - 6].id
//                        {
//                            FlareLog.debug("[TimelineItemsView] 🚀 预加载触发 ")
//
//                            do {
//                                try await viewModel.handleLoadMore(isBottom: false)
//                                FlareLog.debug("[TimelineItemsView] ✅ 预加载成功 - 新总数: \(items.count)")
//                            } catch {
//                                FlareLog.error("[TimelineItemsView] ❌ 预加载失败: \(error)")
//                            }
//                        }
//                    }
                }
                .onDisappear {
                    FlareLog.debug("🔍 [TimelineItemsView] onDisappear  for id: '\(item.id)'")
                    //                  viewModel.itemDidDisappear(item: item)
                }
        }

        if hasMore {
            TimelineLoadMoreView {
                FlareLog.debug("[TimelineItemsView] LoadMoreView触发handleLoadMore")
                FlareLog.debug("🔍 [TimelineItemsView] onDisappear  for  items.last?.id: '\(items.last?.id)'")

                try await viewModel.handleLoadMore(scrollToId: items.last?.id ?? "")
            }
        }
    }
}
