import shared
import SwiftUI
import WaterfallGrid

struct WaterfallView: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    let isCurrentTab: Bool
    let displayType: TimelineDisplayType

    @Environment(FlareTheme.self) private var theme

    @State private var viewModel = TimelineViewModel()
    @State private var scrolledID: String?

    var body: some View {
        switch viewModel.timelineState {
        case .loading:
            TimelineLoadingView()
                .task(id: tab.key) {
                    await viewModel.setupDataSource(for: tab, using: store)
                }

        case let .loaded(items, hasMore):
            WaterfallItemsView(
                items: items,
                displayType: displayType,
                hasMore: hasMore,
                presenter: viewModel.presenter,
                onError: viewModel.handleError,
                scrolledID: $scrolledID,
                isCurrentTab: isCurrentTab,
                viewModel: viewModel
            ).task(id: tab.key) {
                await viewModel.setupDataSource(for: tab, using: store)
            }

        case let .error(error):
            TimelineErrorView(error: error) {
                Task { @MainActor in
                    await viewModel.handleRefresh()
                }
            }
            .task(id: tab.key) {
                await viewModel.setupDataSource(for: tab, using: store)
            }

        case .empty:
            TimelineEmptyView()
                .task(id: tab.key) {
                    await viewModel.setupDataSource(for: tab, using: store)
                }
        }
    }
}
