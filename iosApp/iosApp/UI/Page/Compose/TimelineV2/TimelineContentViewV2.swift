
import Combine
import Kingfisher
import shared
import SwiftUI

struct TimelineContentViewV2: View {
    let state: FlareTimelineState
    let presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    let onError: (FlareError) -> Void

    var body: some View {
        switch state {
        case .loading:
            TimelineLoadingViewV2()

        case let .loaded(items, hasMore, isRefreshing):
            TimelineItemsViewV2(
                items: items,
                hasMore: hasMore,
                isRefreshing: isRefreshing,
                presenter: presenter,
                scrollPositionID: $scrollPositionID,
                onError: onError
            )

        case let .error(error):
            TimelineErrorViewV2(error: error) {
                Task {
                    if let presenter,
                       let timelineState = presenter.models.value as? TimelineState
                    {
                        try? await timelineState.refresh()
                    }
                }
            }

        case .empty:
            TimelineEmptyViewV2()
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

private struct TimelineEmptyViewV2: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "tray")
                .font(.largeTitle)
                .foregroundColor(.secondary)

            Text("No Timeline Items")
                .font(.headline)

            Text("There are no items to display in this timeline.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding()
    }
}

private struct TimelineErrorViewV2: View {
    let error: FlareError
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.orange)

            Text("Timeline Error")
                .font(.headline)

            Text(error.localizedDescription)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button("Retry", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding()
    }
}
