import Combine
import shared
import SwiftUI

@MainActor
@Observable
class TimelineViewModel {
    private(set) var timelineState: FlareTimelineState = .loading
    private(set) var showErrorAlert = false
    private(set) var currentError: FlareError?

    private(set) var presenter: TimelinePresenter?
    private let stateConverter = PagingStateConverter()
    private var refreshDebounceTimer: Timer?
    private var cancellables = Set<AnyCancellable>()

    private var dataSourceTask: Task<Void, Never>?

    private(set) var isLoadingMore: Bool = false

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

    func setupDataSource(for tab: FLTabItem, using store: AppBarTabSettingStore) async {
        dataSourceTask?.cancel()

        FlareLog.debug("Timeline Setting up data source with cache for tab: \(tab.key)")

        guard let cachedPresenter = store.getOrCreatePresenter(for: tab) else {
            FlareLog.error("Timeline Failed to get cached presenter for tab: \(tab.key)")
            currentError = FlareError.data(.parsing)
            showErrorAlert = true
            return
        }

        if presenter === cachedPresenter {
            FlareLog.debug("Timeline Using existing presenter for tab: \(tab.key)")
        } else {
            FlareLog.debug("Timeline Setting new cached presenter for tab: \(tab.key)")
            presenter = cachedPresenter
        }

        dataSourceTask = Task {
            for await state in cachedPresenter.models {
                guard !Task.isCancelled else {
                    FlareLog.debug("[Timeline ViewModel] Task cancelled, stopping data processing")
                    break
                }

                // 🔥 添加日志：状态更新详情
                FlareLog.debug("[Timeline ViewModel] 收到KMP状态更新 - 类型: \(type(of: state.listState))")
                if let successState = state.listState as? PagingStateSuccess<UiTimeline> {
                    FlareLog.debug("[Timeline ViewModel] KMP状态详情 - isRefreshing: \(successState.isRefreshing), itemCount: \(successState.itemCount)")
                }
                FlareLog.debug("[Timeline ViewModel] 收到KMP状态更新: \(type(of: state.listState))")

                let flareState = await Task.detached(priority: .userInitiated) { [stateConverter] in
                    return stateConverter.convert(state.listState)
                }.value

                // 🔥 添加日志：转换结果
                FlareLog.debug("[Timeline ViewModel] 状态转换完成 - 转换前类型: \(type(of: state.listState)), 转换后类型: \(type(of: flareState))")
                FlareLog.debug("[Timeline ViewModel] 状态转换完成: \(type(of: flareState))")

                await MainActor.run {
                    let oldItemsCount = self.items.count
                    self.timelineState = flareState
                    let newItemsCount = self.items.count
                    // 🔥 添加日志：UI状态更新
                    FlareLog.debug("[Timeline ViewModel] UI状态已更新 - items数量变化: \(oldItemsCount) -> \(newItemsCount), hasMore: \(self.hasMore)")
                    FlareLog.debug("[Timeline ViewModel] UI状态已更新: items数量=\(self.items.count), hasMore=\(self.hasMore)")
                }
            }
        }
    }

    func handleRefresh() async {
        // 🔥 添加日志：refresh操作开始
        FlareLog.debug("[Timeline ViewModel] handleRefresh开始 - isLoadingMore: \(isLoadingMore)")
        FlareLog.debug("Timeline Handling refresh")

        // 🔥 新增：刷新前重置状态转换器
        stateConverter.reset()
        FlareLog.debug("[Timeline ViewModel] 刷新前已重置状态转换器")

        guard let presenter else {
            FlareLog.warning("[Timeline ViewModel] handleRefresh失败 - presenter为空")
            FlareLog.warning("Timeline No presenter available for refresh")
            return
        }

        do {
            let refreshResult = try await Task.detached(priority: .userInitiated) { [presenter] in
                let timelineState = presenter.models.value
                try await timelineState.refresh()
                return true
            }.value

            if refreshResult {
                FlareLog.debug("[Timeline ViewModel] handleRefresh完成")
                FlareLog.debug("Timeline Refresh completed")
            }
        } catch {
            FlareLog.error("[Timeline ViewModel] handleRefresh失败: \(error)")
            FlareLog.error("Timeline Refresh failed: \(error)")
            let flareError = await Task.detached(priority: .utility) {
                FlareError.from(error)
            }.value

            currentError = flareError
            showErrorAlert = true
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
        currentError = error
        showErrorAlert = true
    }

    func handleScrollOffsetChange(_ offsetY: CGFloat, showFloatingButton: Binding<Bool>) {
        let shouldShow = offsetY > 50

        if showFloatingButton.wrappedValue != shouldShow {
            showFloatingButton.wrappedValue = shouldShow
        }
    }

    func handleLoadMore() async {
        // 🔥 添加日志：loadMore操作状态检查
        FlareLog.debug("[Timeline ViewModel] handleLoadMore开始 - isLoadingMore: \(isLoadingMore), presenter存在: \(presenter != nil)")

        guard let presenter else {
            FlareLog.warning("[Timeline ViewModel] handleLoadMore失败 - presenter为空")
            FlareLog.warning("[Timeline ViewModel] presenter为空，无法加载更多")
            return
        }

        guard !isLoadingMore else {
            FlareLog.debug("[Timeline ViewModel] handleLoadMore跳过 - 正在加载中")
            FlareLog.debug("[Timeline ViewModel] 正在加载中，跳过重复调用")
            return
        }

        isLoadingMore = true
        // 🔥 添加日志：loadMore状态变更
        FlareLog.debug("[Timeline ViewModel] handleLoadMore状态更新 - isLoadingMore设为true")
        defer {
            isLoadingMore = false
            FlareLog.debug("[Timeline ViewModel] handleLoadMore状态恢复 - isLoadingMore设为false")
        }

        do {
            try await presenter.models.value.loadMore()
            FlareLog.debug("[Timeline ViewModel] handleLoadMore完成")
            FlareLog.debug("[Timeline ViewModel] LoadMore completed successfully")
        } catch {
            FlareLog.error("[Timeline ViewModel] handleLoadMore失败: \(error)")
            FlareLog.error("[Timeline ViewModel] LoadMore failed: \(error)")
        }
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
