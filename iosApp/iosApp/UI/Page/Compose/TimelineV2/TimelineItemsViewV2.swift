
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
        ForEach(items) { item in
//            let item = items[index]

            TimelineStatusViewV2(
                item: item,
                timelineViewModel: nil  // 这个文件暂时不支持乐观更新
//                presenter: presenter,
//                scrollPositionID: $scrollPositionID,
//                onError: onError
            )

//            if index < items.count - 1 {
//                Divider()
//                    .padding(.horizontal, 16)
//            }
        }

        if hasMore, let viewModel {
            TimelineLoadMoreView {
                try await viewModel.handleLoadMore()
            }
        }
    }
}
