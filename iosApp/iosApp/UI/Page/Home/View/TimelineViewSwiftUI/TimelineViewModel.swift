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
        if case let .loaded(items, _, _) = timelineState {
            return items
        }
        return []
    }

    var hasMore: Bool {
        if case let .loaded(_, hasMore, _) = timelineState {
            return hasMore
        }
        return false
    }

    var isRefreshing: Bool {
        if case let .loaded(_, _, isRefreshing) = timelineState {
            return isRefreshing
        }
        return false
    }

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

                FlareLog.debug("[Timeline ViewModel] 收到KMP状态更新: \(type(of: state.listState))")

                let flareState = await Task.detached(priority: .userInitiated) { [stateConverter] in
                    return stateConverter.convert(state.listState)
                }.value

                FlareLog.debug("[Timeline ViewModel] 状态转换完成: \(type(of: flareState))")

                await MainActor.run {
                    self.timelineState = flareState
                    FlareLog.debug("[Timeline ViewModel] UI状态已更新: items数量=\(self.items.count), hasMore=\(self.hasMore)")
                }
            }
        }
    }

    func handleRefresh() async {
        FlareLog.debug("Timeline Handling refresh")

        guard let presenter else {
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
                FlareLog.debug("Timeline Refresh completed")
            }
        } catch {
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
        guard let presenter else {
            FlareLog.warning("[Timeline ViewModel] presenter为空，无法加载更多")
            return
        }

        guard !isLoadingMore else {
            FlareLog.debug("[Timeline ViewModel] 正在加载中，跳过重复调用")
            return
        }

        isLoadingMore = true
        defer { isLoadingMore = false }

        do {
            try await presenter.models.value.loadMore()
            FlareLog.debug("[Timeline ViewModel] LoadMore completed successfully")
        } catch {
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
