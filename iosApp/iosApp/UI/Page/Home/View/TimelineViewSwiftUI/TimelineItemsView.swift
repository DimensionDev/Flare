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
            ).padding(.horizontal, 16)
            .padding(.vertical, 4)
            .onAppear {
                //   viewModel.itemDidAppear(item: item)

                Task {
                    if hasMore,  !viewModel.isLoadingMore,
                       items.count >= 7,

                       item.id == items[items.count - 5].id ||
                       item.id == items[items.count - 6].id
                    {
                        FlareLog.debug("[TimelineItemsView] 🚀 预加载触发 ")

                        do {
                            try await viewModel.handleLoadMore()
                            FlareLog.debug("[TimelineItemsView] ✅ 预加载成功 - 新总数: \(items.count)")
                        } catch {
                            FlareLog.error("[TimelineItemsView] ❌ 预加载失败: \(error)")
                        }
                    }
                }
            }
//                .onDisappear {
//                     viewModel.itemDidDisappear(item: item)
//                }
        }

        if hasMore {
            TimelineLoadMoreView {
                FlareLog.debug("[TimelineItemsView] LoadMoreView触发handleLoadMore")
                try await viewModel.handleLoadMore()
            }
        }
    }
}
