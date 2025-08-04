import Combine
import shared
import SwiftUI

@MainActor
@Observable
class TimelineViewModel {
   
    private(set) var isPaused: Bool = true 

    private(set) var timelineState: FlareTimelineState = .loading
    private(set) var showErrorAlert = false
    private(set) var currentError: FlareError?

    private(set) var presenter: PresenterBase<TimelineState>?
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

    func updateItemOptimistically(itemId: String, actionType: ActionType) {
        return
            FlareLog.debug("🚀 [TimelineViewModel] 开始更新: itemId=\(itemId), actionType=\(actionType)")

        guard case let .loaded(items, hasMore) = timelineState else {
            FlareLog.warning("⚠️ [TimelineViewModel] 更新失败: timelineState不是loaded状态")
            return
        }

        guard let index = items.firstIndex(where: { $0.id == itemId }) else {
            FlareLog.warning("⚠️ [TimelineViewModel] 更新失败: 未找到item \(itemId)")
            return
        }

        var updatedItems = items
        var item = updatedItems[index]

        // 记录更新前的状态
        let beforeState = getItemState(item: item, actionType: actionType)

        // 执行更新
        switch actionType {
        case .like:
            item.isLiked.toggle()
            item.likeCount += item.isLiked ? 1 : -1

        case .retweet:
            item.isRetweeted.toggle()
            item.retweetCount += item.isRetweeted ? 1 : -1

        case .bookmark:
            item.isBookmarked.toggle()
            item.bookmarkCount += item.isBookmarked ? 1 : -1
        }

        // 更新数组
        updatedItems[index] = item

        // 更新timelineState
        timelineState = .loaded(items: updatedItems, hasMore: hasMore)

        // 记录更新后的状态
        let afterState = getItemState(item: item, actionType: actionType)

        FlareLog.debug("✅ [TimelineViewModel] 更新完成: \(actionType) for \(itemId)")
        FlareLog.debug("📊 [TimelineViewModel] 状态变化: \(beforeState) → \(afterState)")
    }

    private func getItemState(item: TimelineItem, actionType: ActionType) -> String {
        switch actionType {
        case .like:
            "isLiked=\(item.isLiked), likeCount=\(item.likeCount)"
        case .retweet:
            "isRetweeted=\(item.isRetweeted), retweetCount=\(item.retweetCount)"
        case .bookmark:
            "isBookmarked=\(item.isBookmarked), bookmarkCount=\(item.bookmarkCount)"
        }
    }

   
    func pause() {
        guard !isPaused else {
            FlareLog.debug("⏸️ [Timeline ViewModel] Already paused, skipping")
            return
        }

        isPaused = true
        dataSourceTask?.cancel()
        FlareLog.debug("⏸️ [Timeline ViewModel] Paused Swift layer data processing")
    }

  
    func resume() {
        guard isPaused else {
            FlareLog.debug("▶️ [Timeline ViewModel] Already active, skipping resume")
            return
        }

        if presenter == nil {
            FlareLog.debug("⚠️ [Timeline ViewModel] No presenter yet, will resume after setup")
            isPaused = false 
            return
        }

        isPaused = false
        FlareLog.debug("▶️ [Timeline ViewModel] Resuming Swift layer data processing")
        restartDataSourceMonitoring()
    }

   
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

    func setupDataSource(presenter: PresenterBase<TimelineState>) async {
        let timestamp = Date().timeIntervalSince1970
        let hadPreviousTask = dataSourceTask != nil

        FlareLog.debug("🔧 [Timeline ViewModel] setupDataSource (generic) started - hadPreviousTask: \(hadPreviousTask), timestamp: \(timestamp)")

        dataSourceTask?.cancel()
        if hadPreviousTask {
            FlareLog.debug("❌ [Timeline ViewModel] Previous dataSourceTask cancelled")
        }

        if self.presenter === presenter {
            FlareLog.debug("♻️ [Timeline ViewModel] Using existing presenter")
        } else {
            FlareLog.debug("🆕 [Timeline ViewModel] Setting new presenter")
            self.presenter = presenter
        }

   
        if !isPaused {
            FlareLog.debug("▶️ [Timeline ViewModel] Starting data monitoring immediately (not paused)")
            restartDataSourceMonitoring()
        } else {
            FlareLog.debug("⏸️ [Timeline ViewModel] Data source setup completed, but monitoring paused")
        }
    }

 
    func setupDataSource(for tab: FLTabItem, using store: AppBarTabSettingStore) async {
        let timestamp = Date().timeIntervalSince1970
        FlareLog.debug("🔧 [Timeline ViewModel] setupDataSource (tab) started - tab: \(tab.key), timestamp: \(timestamp)")

        FlareLog.debug("� [Timeline ViewModel] Getting cached presenter for tab: \(tab.key)")

        guard let cachedPresenter = store.getOrCreatePresenter(for: tab) else {
            FlareLog.error("💥 [Timeline ViewModel] Failed to get cached presenter for tab: \(tab.key)")
            currentError = FlareError.data(.parsing)
            showErrorAlert = true
            return
        }

        
        await setupDataSource(presenter: cachedPresenter)
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

    func getCurrentVisibleItemIds() -> [String] {
        visibleItems.map(\.id)
    }

    func itemDidAppear(item: TimelineItem) {
        if !visibleItems.contains(where: { $0.id == item.id }) {
            visibleItems.insert(item, at: 0)
        }

        if visibleItems.count > 50 {
            visibleItems = Array(visibleItems.prefix(50))
        }

        // FlareLog.debug("[TimelineViewModel] item出现: \(item.id), 当前可见items: \(visibleItems.count)")
    }

    func itemDidDisappear(item: TimelineItem) {
        visibleItems.removeAll { $0.id == item.id }
        // FlareLog.debug("[TimelineViewModel] item消失: \(item.id), 当前可见items: \(visibleItems.count)")
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

enum ActionType: String, CaseIterable {
    case like
    case retweet
    case bookmark
}

extension TimelineItem {
    func isActive(for actionType: ActionType) -> Bool {
        switch actionType {
        case .like: isLiked
        case .retweet: isRetweeted
        case .bookmark: isBookmarked
        }
    }

    func count(for actionType: ActionType) -> Int {
        switch actionType {
        case .like: likeCount
        case .retweet: retweetCount
        case .bookmark: bookmarkCount
        }
    }
}
