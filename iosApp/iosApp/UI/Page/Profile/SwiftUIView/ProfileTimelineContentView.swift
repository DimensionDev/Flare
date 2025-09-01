
import shared
import SwiftUI

struct ProfileTimelineContentView: View {
    let timelineViewModel: TimelineViewModel
    let isCurrentTab: Bool

    @EnvironmentObject private var timelineState: TimelineExtState
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Group {
            switch timelineViewModel.timelineState {
            case .loading:
                TimelineLoadingView().padding(.horizontal, 16)

            case let .loaded(items, hasMore):
                TimelineItemsView(
                    items: items,
                    hasMore: hasMore,
                    viewModel: timelineViewModel
                )
                .listRowBackground(theme.primaryBackgroundColor)
                .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
                    geometry
                } action: { _, newValue in
                    FlareLog.debug("📜 [ProfileTimelineContentView] Timeline滚动检测 - offsetY: \(newValue.contentOffset.y)")
                    timelineViewModel.handleScrollOffsetChange(
                        newValue.contentOffset.y,
                        showFloatingButton: $timelineState.showFloatingButton,
                        timelineState: timelineState,
                        isHomeTab: isCurrentTab
                    )
                }

            case let .error(error):
                TimelineErrorView(message: error.localizedDescription) {
                    Task {
                        await timelineViewModel.handleRefresh()
                    }
                }.listRowBackground(theme.primaryBackgroundColor)

            case .empty:
                VStack(spacing: 16) {
                    Image(systemName: "tray")
                        .font(.system(size: 48))
                        .foregroundColor(theme.labelColor.opacity(0.6))

                    Text("No content")
                        .font(.headline)
                        .foregroundColor(theme.labelColor)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
                .listRowBackground(theme.primaryBackgroundColor)
            }
        }
    }
}
