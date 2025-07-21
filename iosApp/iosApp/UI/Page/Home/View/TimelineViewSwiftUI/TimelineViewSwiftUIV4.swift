import Combine
import shared
import SwiftUI

struct TimelineViewSwiftUIV4: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    let isCurrentTab: Bool
    @Environment(FlareTheme.self) private var theme
    @EnvironmentObject private var timelineState: TimelineExtState

    @State private var viewModel = TimelineViewModel()

    @State private var isInitialized: Bool = false

    init(tab: FLTabItem, store: AppBarTabSettingStore, isCurrentTab: Bool) {
        self.tab = tab
        self.store = store
        self.isCurrentTab = isCurrentTab
        FlareLog.debug("[TimelineV4] 视图初始化 for tab: \(tab.key)")
    }

    @State private var refreshDebounceTimer: Timer?

    var body: some View {
        ScrollViewReader { proxy in
            VStack {
                List {
                    EmptyView()
                        .id("timeline-top-v4")
                        .frame(height: 0)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())

                    switch viewModel.timelineState {
                    case .loading:
                        TimelineLoadingView()
                            .listRowBackground(theme.primaryBackgroundColor)
                            .listRowInsets(EdgeInsets())

                    case let .loaded(items, hasMore, isRefreshing):
                        TimelineItemsView(
                            items: items,
                            hasMore: hasMore,
                            isRefreshing: isRefreshing,
                            viewModel: viewModel
                        )
                        .listRowBackground(theme.primaryBackgroundColor)
                        .listRowInsets(EdgeInsets())

                    case let .error(error):
                        TimelineErrorView(error: error) {
                            Task {
                                await viewModel.handleRefresh()
                            }
                        }
                        .listRowInsets(EdgeInsets())

                    case .empty:
                        TimelineEmptyView()
                            .listRowBackground(theme.primaryBackgroundColor)
                            .listRowInsets(EdgeInsets())
                    }

                    EmptyView()
                        .id("timeline-bottom-v4")
                        .frame(height: 0)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                }
                .listStyle(.plain)
                .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
                    geometry
                } action: { _, newValue in
                    viewModel.handleScrollOffsetChange(newValue.contentOffset.y, showFloatingButton: $timelineState.showFloatingButton)
                }
                .refreshable {
                    await viewModel.handleRefresh()
                }
            }
            .onChange(of: timelineState.scrollToTopTrigger) { _, _ in
                guard isCurrentTab else { return }

                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo("timeline-top-v4", anchor: .center)
                }
            }
            .task(id: tab.key) {
                if !isInitialized {
                    isInitialized = true
                    FlareLog.debug("[TimelineV4] First time initialization for tab: \(tab.key)")
                    await viewModel.setupDataSource(for: tab, using: store)
                } else {
                    FlareLog.debug("[TimelineV4] Tab reappeared, skipping setupDataSource for tab: \(tab.key)")
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .timelineItemUpdated)) { _ in
                FlareLog.debug("TimelineV4 Received item update for tab: \(tab.key)")

                refreshDebounceTimer?.invalidate()
                refreshDebounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
                    guard isCurrentTab else { return }
                    Task { await viewModel.handleRefresh() }
                }
            }
        }
    }
}
