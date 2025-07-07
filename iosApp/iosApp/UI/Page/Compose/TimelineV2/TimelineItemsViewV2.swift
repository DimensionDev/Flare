
import Combine
import Kingfisher
import shared
import SwiftUI

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
            TimelineLoadMoreView(isRefreshing: isRefreshing)
                .onAppear {
                    FlareLog.debug("TimelineItemsViewV2 Reached end of list, triggering next page load")
                    Task {
                        if let presenter,
                           let timelineState = presenter.models.value as? TimelineState,
                           let pagingState = timelineState.listState as? PagingStateSuccess<UiTimeline>
                        {
                            let currentItemCount = Int(pagingState.itemCount)
                            let nextPageIndex = items.count

                            if nextPageIndex < currentItemCount {
                                FlareLog.debug("TimelineItemsViewV2 Safe next page load: requesting index=\(nextPageIndex), total=\(currentItemCount)")
                                _ = pagingState.get(index: Int32(nextPageIndex))
                            } else {
                                FlareLog.debug("TimelineItemsViewV2 Skipped next page load: index=\(nextPageIndex) >= total=\(currentItemCount)")
                            }
                        }
                    }
                }
        }
    }
}
