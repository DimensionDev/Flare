
import Combine
import Kingfisher
import shared
import SwiftUI

struct TimelineItemsViewV2: View {
    let items: [TimelineItem]
    let hasMore: Bool
    let presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    let onError: (FlareError) -> Void
    let viewModel: TimelineViewModel?

    var body: some View {
        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
            TimelineStatusViewV2(
                item: item,
                index: index
//                presenter: presenter,
//                scrollPositionID: $scrollPositionID,
//                onError: onError
            )

            if index < items.count - 1 {
                Divider()
                    .padding(.horizontal, 16)
            }
        }

        if hasMore, let viewModel = viewModel {
            TimelineLoadMoreView {
                try await viewModel.handleLoadMore()
            }
        }
    }
}
