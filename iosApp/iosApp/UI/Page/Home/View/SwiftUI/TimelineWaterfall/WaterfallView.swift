import shared
import SwiftUI
import WaterfallGrid

struct WaterfallView: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    let isCurrentTab: Bool
    let displayType: TimelineDisplayType
    @EnvironmentObject private var timelineState: TimelineExtState

    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

    @State private var viewModel = TimelineViewModel()
    @State private var scrolledID: String?

    var body: some View {
        switch viewModel.timelineState {
        case .loading:
            TimelineLoadingView()
                .task(id: tab.key) {
                    await viewModel.setupDataSource(for: tab, using: store)
                }

        case let .loaded(items, hasMore, isRefreshing):
            WaterfallItemsView(
                items: items,
                displayType: displayType,
                hasMore: hasMore,
                isRefreshing: isRefreshing,
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
