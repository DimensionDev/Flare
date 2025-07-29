
import Combine
import Kingfisher
import shared
import SwiftUI

struct TimelineContentViewV2: View {
    let state: FlareTimelineState
    let presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    let onError: (FlareError) -> Void
    let viewModel: TimelineViewModel?

    var body: some View {
        switch state {
        case .loading:
            TimelineLoadingViewV2()

        case let .loaded(items, hasMore):
            TimelineItemsViewV2(
                items: items,
                hasMore: hasMore,
                presenter: presenter,
                scrollPositionID: $scrollPositionID,
                onError: onError,
                viewModel: viewModel
            )

        case let .error(error):
            TimelineErrorView(error: error) {
                Task {
                    if let presenter,
                       let timelineState = presenter.models.value as? TimelineState
                    {
                        try? await timelineState.refresh()
                    }
                }
            }

        case .empty:
            TimelineEmptyView()
        }
    }
}

private struct TimelineLoadingViewV2: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.2)
            Text("Loading Timeline...")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
    }
}
