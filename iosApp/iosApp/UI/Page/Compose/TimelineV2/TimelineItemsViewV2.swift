
import Combine
import Kingfisher
import SwiftUI
import shared

 struct TimelineItemsViewV2: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let isRefreshing: Bool
    let presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    let onError: (FlareError) -> Void
    
    var body: some View {
        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
            TimelineStatusViewV2(
                item: item,
                index: index,
                presenter: presenter,
                scrollPositionID: $scrollPositionID,
                onError: onError
            )
            
            if index < items.count - 1 {
                Divider()
                    .padding(.horizontal, 16)
            }
        }
        
         if hasMore {
            TimelineLoadMoreViewV2(isRefreshing: isRefreshing)
                .onAppear {
                     print("🔄 [TimelineItemsView] Reached end of list, triggering next page load")
                    Task {
                         if let presenter,
                           let timelineState = presenter.models.value as? TimelineState,
                           let pagingState = timelineState.listState as? PagingStateSuccess<UiTimeline>
                        {
                            let currentItemCount = Int(pagingState.itemCount)
                            let nextPageIndex = items.count
                            
                             if nextPageIndex < currentItemCount {
                                print("🔄 [TimelineItemsView] Safe next page load: requesting index=\(nextPageIndex), total=\(currentItemCount)")
                                _ = pagingState.get(index: Int32(nextPageIndex))
                            } else {
                                print("🚫 [TimelineItemsView] Skipped next page load: index=\(nextPageIndex) >= total=\(currentItemCount)")
                            }
                        }
                    }
                }
        }
    }
}

private struct TimelineLoadMoreViewV2: View {
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
