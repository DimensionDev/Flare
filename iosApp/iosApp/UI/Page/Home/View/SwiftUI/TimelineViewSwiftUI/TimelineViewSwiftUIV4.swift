import Combine
import shared
import SwiftUI

struct TimelineViewSwiftUIV4: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    // @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool
    @Environment(FlareTheme.self) private var theme

    @State private var viewModel = TimelineViewModel()

    init(tab: FLTabItem, store: AppBarTabSettingStore, scrollToTopTrigger: Binding<Bool>, isCurrentTab: Bool, showFloatingButton: Binding<Bool>) {
        self.tab = tab
        self.store = store
        _scrollToTopTrigger = scrollToTopTrigger
        self.isCurrentTab = isCurrentTab
        _showFloatingButton = showFloatingButton
        FlareLog.debug("[TimelineV4] 视图初始化 for tab: \(tab.key)")
    }

    // @State private var cancellables = Set<AnyCancellable>()
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
                            presenter: viewModel.presenter,
                            onError: viewModel.handleError,
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
                        //   .listRowBackground(theme.primaryBackgroundColor)
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
                // .scrollPosition($scrollPosition) 实现不了
                // .background(theme.secondaryBackgroundColor)
                .listStyle(.plain)
                .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
                    // FlareLog.debug("TimelineV4 scroll offset: \(geometry.contentOffset)")
                    geometry
                } action: { _, newValue in
                    // FlareLog.debug("TimelineV4 scroll geometry changed: \(oldValue.contentOffset) -> \(newValue.contentOffset)")
                    viewModel.handleScrollOffsetChange(newValue.contentOffset.y, showFloatingButton: $showFloatingButton)
                }
                .refreshable {
                    await viewModel.handleRefresh()
                }
            }
            // .background(theme.secondaryBackgroundColor)
            .onChange(of: scrollToTopTrigger) { _, _ in
                let _ = FlareLog.debug("TimelineV4 ScrollToTop trigger for tab: \(tab.key)")
                guard isCurrentTab else { return }

                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo("timeline-top-v4", anchor: .center)
                }
            }
            .task(id: tab.key) {
                await viewModel.setupDataSource(for: tab, using: store)
            }
            .onReceive(NotificationCenter.default.publisher(for: .timelineItemUpdated)) { _ in
                FlareLog.debug("TimelineV4 Received item update for tab: \(tab.key)")

                refreshDebounceTimer?.invalidate()
                refreshDebounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
                    guard isCurrentTab else { return }
                    Task { await viewModel.handleRefresh() }
                }
            }
            // .onDisappear {
            //     cancellables.removeAll()
            // }
            // .alert("Error", isPresented: $showErrorAlert) {
            //     Button("OK") { }
            // } message: {
            //     Text(currentError?.localizedDescription ?? "Unknown error")
        }
    }
}
