import Combine
import shared
import SwiftUI

struct TimelineViewSwiftUIV4: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    let isCurrentTab: Bool
    @Environment(FlareTheme.self) private var theme
    @EnvironmentObject private var timelineState: TimelineExtState

    @State private var timeLineViewModel = TimelineViewModel()
    @State private var isInitialized: Bool = false

    init(tab: FLTabItem, store: AppBarTabSettingStore, isCurrentTab: Bool) {
        self.tab = tab
        self.store = store
        self.isCurrentTab = isCurrentTab
        FlareLog.debug("🔍 [TimelineV4] 视图初始化 for tab: '\(tab.key)', received isCurrentTab: \(isCurrentTab)")
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

                    switch timeLineViewModel.timelineState {
                    case .loading:
                        ForEach(0 ..< 5, id: \.self) { _ in
                            TimelineStatusViewV2(
                                item: createSampleTimelineItem(),
                                timelineViewModel: timeLineViewModel
                            ).padding(.horizontal, 16)
                                .redacted(reason: .placeholder)
                                .listRowBackground(theme.primaryBackgroundColor)
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                        }

                    case let .loaded(items, hasMore):
                        TimelineItemsView(
                            items: items,
                            hasMore: hasMore,
                            viewModel: timeLineViewModel
                        )
                        .listRowBackground(theme.primaryBackgroundColor)
                        .listRowInsets(EdgeInsets())

                    case let .error(error):
                        TimelineErrorView(error: error) {
                            Task {
                                await timeLineViewModel.handleRefresh()
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
                    timeLineViewModel.handleScrollOffsetChange(
                        newValue.contentOffset.y,
                        showFloatingButton: $timelineState.showFloatingButton,
                        timelineState: timelineState,
                        isHomeTab: isCurrentTab
                    )
                }
                .refreshable {
                    // 🔥 添加日志：下拉刷新触发
                    FlareLog.debug("[TimelineV4] 下拉刷新触发")
                    await timeLineViewModel.handleRefresh()
                    FlareLog.debug("[TimelineV4] 下拉刷新完成")
                }
            }
            .onChange(of: timelineState.scrollToTopTrigger) { _, _ in
                guard isCurrentTab else { return }

                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo("timeline-top-v4", anchor: .center)
                }
            }
            .onChange(
                of: timeLineViewModel.timelineState.itemCount)
            { _, newValue in
                FlareLog.debug("🔍 [TimelineViewSwiftUIV4]  timeLineViewModel.scrollToId: '\(timeLineViewModel.scrollToId)'")
                FlareLog.debug("🔍 [TimelineViewSwiftUIV4] timeLineViewModel.timelineState.itemCount   newValue: '\(newValue)'")

                if timeLineViewModel.scrollToId == "" {
                    return
                }

//                    let currentVisibleIds = timeLineViewModel.getCurrentVisibleItemIds()
//
//                    if currentVisibleIds
//                        .contains(timeLineViewModel.scrollToId ) {
//                        FlareLog.debug("🔍 [TimelineViewSwiftUIV4] timeLineViewModel.clearScrollTarget  ")
//
//                         timeLineViewModel.clearScrollTarget()
//                    } else {
                withAnimation(.easeInOut(duration: 0.3)) {
                    FlareLog.debug("🔍 [TimelineViewSwiftUIV4] proxy.scrollTo   timeLineViewModel.scrollToId: '\(timeLineViewModel.scrollToId)'")

                    proxy.scrollTo(timeLineViewModel.scrollToId, anchor: .top)
                }

                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    FlareLog.debug("🔍 [TimelineViewSwiftUIV4] proxy.scrollTo timeLineViewModel.clearScrollTarget  timeLineViewModel.scrollToId: '\(timeLineViewModel.scrollToId)'")

                    timeLineViewModel.clearScrollTarget()
                }
//                    }
            }
            .task(id: tab.key) {
                let timestamp = Date().timeIntervalSince1970
                FlareLog.debug("📱 [TimelineV4] .task(id: \(tab.key)) triggered - isCurrentTab: \(isCurrentTab), timestamp: \(timestamp)")

                if !isInitialized {
                    isInitialized = true
                    FlareLog.debug("🚀 [TimelineV4] First time initialization for tab: \(tab.key)")
                    await timeLineViewModel.setupDataSource(for: tab, using: store)
                    FlareLog.debug("✅ [TimelineV4] setupDataSource completed for tab: \(tab.key)")
                } else {
                    FlareLog.debug("⏭️ [TimelineV4] Tab reappeared, skipping setupDataSource for tab: \(tab.key)")
                }
            }
            .onAppear {
                let timestamp = Date().timeIntervalSince1970
                FlareLog.debug("👁️ [TimelineV4] onAppear - tab: \(tab.key), isCurrentTab: \(isCurrentTab), timestamp: \(timestamp)")

                timeLineViewModel.resume()
            }
            .onDisappear {
                let timestamp = Date().timeIntervalSince1970
                FlareLog.debug("👋 [TimelineV4] onDisappear - tab: \(tab.key), isCurrentTab: \(isCurrentTab), timestamp: \(timestamp)")

                timeLineViewModel.pause()
            }
            .onReceive(NotificationCenter.default.publisher(for: .timelineItemUpdated)) { _ in
                let timestamp = Date().timeIntervalSince1970
                FlareLog.debug("📬 [TimelineV4] Received timelineItemUpdated notification - tab: \(tab.key), isCurrentTab: \(isCurrentTab), timestamp: \(timestamp)")

                refreshDebounceTimer?.invalidate()
                FlareLog.debug("⏰ [TimelineV4] Setting refresh debounce timer - tab: \(tab.key), isCurrentTab: \(isCurrentTab)")

                refreshDebounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
                    let timerTimestamp = Date().timeIntervalSince1970
                    FlareLog.debug("⏱️ [TimelineV4] Debounce timer fired - tab: \(tab.key), isCurrentTab: \(isCurrentTab), timestamp: \(timerTimestamp)")

                    guard isCurrentTab else {
                        FlareLog.debug("⏸️ [TimelineV4] Skipping refresh - not current tab: \(tab.key)")
                        return
                    }

                    FlareLog.debug("🔄 [TimelineV4] Starting handleRefresh - tab: \(tab.key)")
                    Task {
                        await timeLineViewModel.handleRefresh()
                    }
                }
            }
        }
    }
}
