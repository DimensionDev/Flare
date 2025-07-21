
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
    let viewModel: TimelineViewModel?

    var body: some View {
        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
            TimelineStatusViewV2(
                item: item,
                index: index,
//                presenter: presenter,
//                scrollPositionID: $scrollPositionID,
//                onError: onError
            )

            if index < items.count - 1 {
                Divider()
                    .padding(.horizontal, 16)
            }
        }

        if hasMore {
            TimelineLoadMoreView(isRefreshing: isRefreshing)
                .onAppear {
                    if hasMore, !isRefreshing, !(viewModel?.isLoadingMore ?? false) {
                        FlareLog.debug("TimelineItemsViewV2 Load more triggered")
                        handleLoadMore()
                    }
                }
        }
    }

    private func handleLoadMore() {
        FlareLog.debug("[TimelineItemsViewV2 LoadMore] UI层触发load more，委托给ViewModel")
        Task {
            await viewModel?.handleLoadMore()
        }
    }
}
