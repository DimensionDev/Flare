import shared
import SwiftUI

struct TimelineItemsView: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let viewModel: TimelineViewModel

   
    @Environment(\.appSettings) private var appSettings

    var body: some View {
             #if DEBUG
        let _ = Self._printChanges()  
        let _ = print("🔍 [TimelineItemsView]   view changed")
        #endif
        Group {
            ForEach(items) { item in
                TimelineStatusViewV2(
                    item: item
                )
                .padding(.vertical, 4)
                .onAppear {
                    // FlareLog.debug("[TimelineItemsView] item出现: \(item.id), index: \(index)")
                    viewModel.itemDidAppear(item: item)
                }
                .onDisappear {
                    // FlareLog.debug("[TimelineItemsView] item消失: \(item.id), index: \(index)")
                    viewModel.itemDidDisappear(item: item)
                }
                // .id(item.id)
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
        }
        .onAppear {
            FlareLog.debug("[Timeline ItemsView] TimelineItemsView appeared with \(items.count) items")
        }
    }


}
