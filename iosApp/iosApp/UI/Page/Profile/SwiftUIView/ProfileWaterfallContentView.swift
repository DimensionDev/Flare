
import shared
import SwiftUI

struct ProfileWaterfallContentView: View {
    let timelineViewModel: TimelineViewModel
    let selectedTabKey: String?
  

    @EnvironmentObject private var timelineState: TimelineExtState
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Group {
            switch timelineViewModel.timelineState {
            case .loading:
                TimelineWaterfallLoadingView()
                    .listRowBackground(theme.primaryBackgroundColor)

            case let .loaded(items, hasMore):
                WaterfallItemsView(
                    items: items,
                    displayType: .mediaWaterfall,
                    hasMore: hasMore,
                    onError: timelineViewModel.handleError,
                    scrolledID: .constant(nil),
                    isCurrentTab: true,
                    viewModel: timelineViewModel
                )
                .listRowBackground(theme.primaryBackgroundColor)
                .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
                    geometry
                } action: { _, newValue in
                    
                    FlareLog.debug("📜 [ProfileWaterfallContentView] Media滚动检测 - offsetY: \(newValue.contentOffset.y)")
                    timelineViewModel.handleScrollOffsetChange(
                        newValue.contentOffset.y,
                        showFloatingButton: $timelineState.showFloatingButton,
                        timelineState: timelineState,
                        isHomeTab: true
                    )
                }

            case let .error(error):
                TimelineErrorView(message: error.localizedDescription) {
                    Task {
                        await timelineViewModel.handleRefresh()
                    }
                }

            case .empty:
                VStack(spacing: 16) {
                    Image(systemName: "photo")
                        .font(.system(size: 48))
                        .foregroundColor(theme.labelColor.opacity(0.6))

                    Text("No media content")
                        .font(.headline)
                        .foregroundColor(theme.labelColor)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
            }
        }
    }
}
