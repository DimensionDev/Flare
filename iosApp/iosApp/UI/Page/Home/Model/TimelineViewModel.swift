import Combine
import shared
import SwiftUI

@MainActor
@Observable
class TimelineViewModel {
    // 只保留暂停状态管理
    private(set) var isPaused: Bool = true // 初始状态为暂停

    private(set) var timelineState: FlareTimelineState = .loading
    private(set) var showErrorAlert = false
    private(set) var currentError: FlareError?

    private(set) var presenter: TimelinePresenter?
    private let stateConverter = PagingStateConverter()
    private var refreshDebounceTimer: Timer?
    private var cancellables = Set<AnyCancellable>()

    private var dataSourceTask: Task<Void, Never>?

    private(set) var isLoadingMore: Bool = false

    var scrollToId: String?

    @ObservationIgnored
    private var visibleItems: [TimelineItem] = []

    var items: [TimelineItem] {
        if case let .loaded(items, _) = timelineState {
            return items
        }
        return []
    }

    var hasMore: Bool {
        if case let .loaded(_, hasMore) = timelineState {
            return hasMore
        }
        return false
    }

    // 🔥 移除：isRefreshing属性不再需要
    // var isRefreshing: Bool {
    //     return false
    // }

    /// 暂停数据流处理
    func pause() {
        guard !isPaused else {
            FlareLog.debug("⏸️ [Timeline ViewModel] Already paused, skipping")
            return
        }

        isPaused = true
        dataSourceTask?.cancel()
        FlareLog.debug("⏸️ [Timeline ViewModel] Paused Swift layer data processing")
    }

    /// 恢复数据流处理
    func resume() {
        guard isPaused else {
            FlareLog.debug("▶️ [Timeline ViewModel] Already active, skipping resume")
            return
        }

        if presenter == nil {
            FlareLog.debug("⚠️ [Timeline ViewModel] No presenter yet, will resume after setup")
            isPaused = false // 设置意图，但不启动监听
            return
        }

        isPaused = false
        FlareLog.debug("▶️ [Timeline ViewModel] Resuming Swift layer data processing")
        restartDataSourceMonitoring()
    }

    /// 重新启动数据源监听
    private func restartDataSourceMonitoring() {
        guard let presenter else {
            FlareLog.warning("⚠️ [Timeline ViewModel] No presenter available for restart")
            return
        }

        dataSourceTask = Task {
            FlareLog.debug("🔄 [Timeline ViewModel] Restarting data source monitoring")
            for await state in presenter.models {
                guard !isPaused, !Task.isCancelled else {
                    FlareLog.debug("🛑 [Timeline ViewModel] Data monitoring stopped - isPaused: \(isPaused), isCancelled: \(Task.isCancelled)")
                    break
                }

                FlareLog.debug("📦 [Timeline ViewModel] Received KMP state update - type: \(type(of: state.listState))")

                if let successState = state.listState as? PagingStateSuccess<UiTimeline> {
                    FlareLog.debug("📊 [Timeline ViewModel] KMP state details - isRefreshing: \(successState.isRefreshing), itemCount: \(successState.itemCount)")
                }

                FlareLog.debug("🔄 [Timeline ViewModel] Starting state conversion")

                let flareState = await Task.detached(priority: .userInitiated) { [stateConverter] in
                    return stateConverter.convert(state.listState)
                }.value

                FlareLog.debug("✨ [Timeline ViewModel] State conversion completed - beforeType: \(type(of: state.listState)), afterType: \(type(of: flareState))")

                await MainActor.run {
                    let oldItemsCount = self.items.count
                    let oldState = type(of: self.timelineState)
                    let oldHasMore = self.hasMore

                    self.timelineState = flareState

                    let newItemsCount = self.items.count
                    let newState = type(of: self.timelineState)
                    let newHasMore = self.hasMore

                    FlareLog.debug("🎨 [Timeline ViewModel] UI state updated - stateChange: \(oldState) → \(newState), itemsChange: \(oldItemsCount) → \(newItemsCount), hasMoreChange: \(oldHasMore) → \(newHasMore)")

                    if newItemsCount != oldItemsCount {
                        FlareLog.debug("📊 [Timeline ViewModel] Items数量变化详情 - 新增: \(newItemsCount - oldItemsCount)")
                    }
                }
            }

            FlareLog.debug("🏁 [Timeline ViewModel] Data monitoring loop ended")
        }
    }

    func setupDataSource(for tab: FLTabItem, using store: AppBarTabSettingStore) async {
        let timestamp = Date().timeIntervalSince1970
        let hadPreviousTask = dataSourceTask != nil

        FlareLog.debug("🔧 [Timeline ViewModel] setupDataSource started - tab: \(tab.key), hadPreviousTask: \(hadPreviousTask), timestamp: \(timestamp)")

        dataSourceTask?.cancel()
        if hadPreviousTask {
            FlareLog.debug("❌ [Timeline ViewModel] Previous dataSourceTask cancelled - tab: \(tab.key)")
        }

        FlareLog.debug("🏪 [Timeline ViewModel] Getting cached presenter for tab: \(tab.key)")

        guard let cachedPresenter = store.getOrCreatePresenter(for: tab) else {
            FlareLog.error("💥 [Timeline ViewModel] Failed to get cached presenter for tab: \(tab.key)")
            currentError = FlareError.data(.parsing)
            showErrorAlert = true
            return
        }

        if presenter === cachedPresenter {
            FlareLog.debug("♻️ [Timeline ViewModel] Using existing presenter")
        } else {
            FlareLog.debug("🆕 [Timeline ViewModel] Setting new cached presenter")
            presenter = cachedPresenter
        }

        // 如果当前未暂停，启动数据监听
        if !isPaused {
            FlareLog.debug("▶️ [Timeline ViewModel] Starting data monitoring immediately (not paused)")
            restartDataSourceMonitoring()
        } else {
            FlareLog.debug("⏸️ [Timeline ViewModel] Data source setup completed, but monitoring paused")
        }
    }

    func handleRefresh() async {
        let timestamp = Date().timeIntervalSince1970
        FlareLog.debug("🔄 [Timeline ViewModel] handleRefresh started - isLoadingMore: \(isLoadingMore), presenter: \(presenter != nil), timestamp: \(timestamp)")

        stateConverter.reset()
        FlareLog.debug("🔄 [Timeline ViewModel] State converter reset completed")

        guard let presenter else {
            FlareLog.warning("⚠️ [Timeline ViewModel] handleRefresh failed - no presenter available, timestamp: \(timestamp)")
            return
        }

        do {
            FlareLog.debug("🚀 [Timeline ViewModel] Starting refresh operation")

            let refreshResult = try await Task.detached(priority: .userInitiated) { [presenter] in
                let timelineState = presenter.models.value
                try await timelineState.refresh()
                return true
            }.value

            if refreshResult {
                let completionTimestamp = Date().timeIntervalSince1970
                FlareLog.debug("✅ [Timeline ViewModel] handleRefresh completed successfully - timestamp: \(completionTimestamp)")
            }
        } catch {
            let errorTimestamp = Date().timeIntervalSince1970
            FlareLog.error("💥 [Timeline ViewModel] handleRefresh failed - error: \(error), timestamp: \(errorTimestamp)")

            let flareError = await Task.detached(priority: .utility) {
                FlareError.from(error)
            }.value

            currentError = flareError
            showErrorAlert = true
            FlareLog.debug("🚨 [Timeline ViewModel] Error state set - showErrorAlert: true")
        }
    }

    func getFirstItemID() -> String? {
        guard let firstID = items.first?.id else {
            FlareLog.debug("Timeline ScrollToTop skipped: no items")
            return nil
        }

        FlareLog.debug("Timeline ScrollToTop available: firstID=\(firstID)")
        return firstID
    }

    func handleError(_ error: FlareError) {
        FlareLog.error("[TimelineViewModel] 处理错误: \(error)")
        currentError = error
        showErrorAlert = true
        FlareLog.debug("[TimelineViewModel] 错误状态已设置 - showErrorAlert: true")
    }

    func handleScrollOffsetChange(_ offsetY: CGFloat, showFloatingButton: Binding<Bool>) {
        let shouldShow = offsetY > 50

        // FlareLog.debug("[TimelineViewModel] 滚动偏移变化: offsetY=\(offsetY), shouldShow=\(shouldShow), current=\(showFloatingButton.wrappedValue)")

        if showFloatingButton.wrappedValue != shouldShow {
            showFloatingButton.wrappedValue = shouldShow
            FlareLog.debug("[TimelineViewModel] 浮动按钮状态更新: \(showFloatingButton.wrappedValue)")
        }
    }

    func handleLoadMore() async {
        let timestamp = Date().timeIntervalSince1970
        FlareLog.debug("📄 [Timeline ViewModel] handleLoadMore started - isLoadingMore: \(isLoadingMore), hasPresenter: \(presenter != nil), timestamp: \(timestamp)")

        guard let presenter else {
            FlareLog.warning("⚠️ [Timeline ViewModel] handleLoadMore failed - no presenter available, timestamp: \(timestamp)")
            return
        }

        guard !isLoadingMore else {
            FlareLog.debug("⏸️ [Timeline ViewModel] handleLoadMore skipped - already loading, timestamp: \(timestamp)")
            return
        }

        isLoadingMore = true
        FlareLog.debug("🔄 [Timeline ViewModel] isLoadingMore set to true, timestamp: \(timestamp)")

        let topVisibleItem = visibleItems.first
        FlareLog.debug("🎯 [Timeline ViewModel] 保存顶部可见item: \(topVisibleItem?.id ?? "nil")")

        defer {
            isLoadingMore = false
            let deferTimestamp = Date().timeIntervalSince1970
            FlareLog.debug("✅ [Timeline ViewModel] isLoadingMore reset to false, timestamp: \(deferTimestamp)")
        }

        do {
            FlareLog.debug("🚀 [Timeline ViewModel] Starting loadMore operation")
            try await presenter.models.value.loadMore()

            let completionTimestamp = Date().timeIntervalSince1970
            FlareLog.debug("✅ [Timeline ViewModel] handleLoadMore completed successfully, timestamp: \(completionTimestamp)")

            if let topItem = topVisibleItem,
               visibleItems.contains(where: { $0.id == topItem.id })
            {
                FlareLog.debug("🎯 [Timeline ViewModel] 恢复滚动位置到: \(topItem.id)")

                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    self.scrollTo(itemId: topItem.id)
                }
            }
        } catch {
            let errorTimestamp = Date().timeIntervalSince1970
            FlareLog.error("💥 [Timeline ViewModel] handleLoadMore failed - error: \(error), timestamp: \(errorTimestamp)")
        }
    }

    func clearScrollTarget() {
        FlareLog.debug("[TimelineViewModel] 清除滚动目标")
        scrollToId = nil
    }

    func scrollTo(itemId: String) {
        FlareLog.debug("[TimelineViewModel] 设置滚动目标: \(itemId)")
        scrollToId = itemId
    }

    func itemDidAppear(item: TimelineItem) {
        if !visibleItems.contains(where: { $0.id == item.id }) {
            visibleItems.insert(item, at: 0)
        }

        if visibleItems.count > 50 {
            visibleItems = Array(visibleItems.prefix(50))
        }

        FlareLog.debug("[TimelineViewModel] item出现: \(item.id), 当前可见items: \(visibleItems.count)")
    }

    func itemDidDisappear(item: TimelineItem) {
        visibleItems.removeAll { $0.id == item.id }
        FlareLog.debug("[TimelineViewModel] item消失: \(item.id), 当前可见items: \(visibleItems.count)")
    }
}

struct TimelineLoadingView: View {
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

func createSampleTimelineItem() -> TimelineItem {
    let sampleUser = User(
        key: "sample@mastodon.social",
        name: RichText(raw: "Sample User Name"),
        handle: "@sampleuser",
        avatar: "https://example.com/avatar.jpg",
        banner: "https://example.com/banner.jpg",
        description: RichText(raw: "Sample user description")
    )

    return TimelineItem(
        id: "sample-timeline-item-\(UUID().uuidString)",
        content: RichText(raw: "This is a sample timeline content for skeleton loading. It shows how the timeline item will look when loaded."),
        user: sampleUser,
        timestamp: Date(),
        images: [],
        url: "https://example.com/status",
        platformType: "Mastodon",
        aboveTextContent: nil,
        contentWarning: nil,
        card: nil,
        quote: [],
        bottomContent: nil,
        topEndContent: nil,
        poll: nil,
        topMessage: nil,
        sensitive: false,
        visibility: "public",
        language: "en",
        actions: []
    )
}

struct TimelineEmptyView: View {
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
