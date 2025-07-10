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
        FlareLog.debug("Timeline Setting up data source with cache for tab: \(tab.key)")

        guard let cachedPresenter = store.getOrCreatePresenter(for: tab) else {
            FlareLog.error("Timeline Failed to get cached presenter for tab: \(tab.key)")
            currentError = FlareError.data(.parsing)
            showErrorAlert = true
            return
        }

        if presenter === cachedPresenter {
            FlareLog.debug("Timeline Using existing presenter for tab: \(tab.key)")
            return
        }

        FlareLog.debug("Timeline Setting new cached presenter for tab: \(tab.key)")
        presenter = cachedPresenter

        Task {
            for await state in cachedPresenter.models {
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

    /// 处理加载更多的业务逻辑
    func handleLoadMore() async {
        FlareLog.debug("[Timeline ViewModel] 开始处理load more请求")

        guard let presenter else {
            FlareLog.warning("[Timeline ViewModel] 错误: presenter为空，无法加载更多")
            return
        }

        let currentItemsCount = items.count
        // 获取下一个需要请求的KMP index
        let nextKmpIndex = stateConverter.getNextKmpIndex()

        FlareLog.debug("[Timeline ViewModel] 当前Swift显示数量: \(currentItemsCount), 下一个KMP index: \(nextKmpIndex)")

        _ = await Task.detached(priority: .userInitiated) { [presenter] in
            FlareLog.debug("[Timeline ViewModel] 开始异步任务，获取KMP状态")
            let timelineState = presenter.models.value
            FlareLog.debug("[Timeline ViewModel] 获取到TimelineState，listState类型: \(type(of: timelineState.listState))")

            if let pagingState = timelineState.listState as? PagingStateSuccess<UiTimeline> {
                let kmpTotalCount = Int(pagingState.itemCount)
                let appendState = pagingState.appendState
                let isRefreshing = pagingState.isRefreshing

                FlareLog.debug("[Timeline ViewModel] KMP状态: totalCount=\(kmpTotalCount), nextIndex=\(nextKmpIndex), appendState=\(appendState), isRefreshing=\(isRefreshing)")

                // 添加KMP数据一致性检查
                FlareLog.debug("[Timeline ViewModel] === KMP数据一致性检查开始 ===")
                var actualAvailableCount = 0
                var firstUnavailableIndex: Int?

                for i in 0 ..< kmpTotalCount {
                    if pagingState.peek(index: Int32(i)) != nil {
                        actualAvailableCount += 1
                    } else {
                        if firstUnavailableIndex == nil {
                            firstUnavailableIndex = i
                        }
                    }
                }

                FlareLog.debug("[Timeline ViewModel] KMP数据一致性: 报告\(kmpTotalCount)个，实际可用\(actualAvailableCount)个")
                if let firstUnavailable = firstUnavailableIndex {
                    FlareLog.debug("[Timeline ViewModel] 第一个不可用数据位置: index \(firstUnavailable)")
                }

                // 特别检查目标index的数据可用性
                FlareLog.debug("[Timeline ViewModel] === 目标index数据检查 ===")
                let targetIndex = nextKmpIndex
                let peekResult = pagingState.peek(index: Int32(targetIndex))
                FlareLog.debug("[Timeline ViewModel] peek(index: \(targetIndex)) 结果: \(peekResult != nil ? "有数据" : "null")")

                // 检查目标index前后的数据可用性
                if targetIndex > 0 {
                    let prevResult = pagingState.peek(index: Int32(targetIndex - 1))
                    FlareLog.debug("[Timeline ViewModel] peek(index: \(targetIndex - 1)) 结果: \(prevResult != nil ? "有数据" : "null")")
                }

                if targetIndex + 1 < kmpTotalCount {
                    let nextResult = pagingState.peek(index: Int32(targetIndex + 1))
                    FlareLog.debug("[Timeline ViewModel] peek(index: \(targetIndex + 1)) 结果: \(nextResult != nil ? "有数据" : "null")")
                }

                // 检查是否需要加载更多
                if nextKmpIndex < kmpTotalCount {
                    FlareLog.debug("[Timeline ViewModel] 条件满足，调用pagingState.get(index: \(nextKmpIndex))")
                    let result = pagingState.get(index: Int32(nextKmpIndex))
                    FlareLog.debug("[Timeline ViewModel] pagingState.get()调用完成，返回值: \(result != nil ? "有数据" : "null")")

                    if result == nil {
                        FlareLog.error("[Timeline ViewModel] ❌ KMP数据获取失败！")
                        FlareLog.error("[Timeline ViewModel] 失败详情: index=\(nextKmpIndex), totalCount=\(kmpTotalCount), actualAvailable=\(actualAvailableCount)")

                        // 尝试探测下一个可用数据
                        FlareLog.debug("[Timeline ViewModel] === 探测下一个可用数据 ===")
                        for i in (nextKmpIndex + 1) ..< min(nextKmpIndex + 10, kmpTotalCount) {
                            if let testResult = pagingState.peek(index: Int32(i)) {
                                FlareLog.debug("[Timeline ViewModel] 发现可用数据在index: \(i)")
                                break
                            }
                        }
                    } else {
                        FlareLog.debug("[Timeline ViewModel] ✅ KMP数据获取成功")
                    }
                } else {
                    FlareLog.debug("[Timeline ViewModel] 已到达KMP数据末尾: nextIndex(\(nextKmpIndex)) >= totalCount(\(kmpTotalCount))")
                }
            } else {
                FlareLog.warning("[Timeline ViewModel] 错误: listState不是PagingStateSuccess类型")
            }
        }.value

        FlareLog.debug("[Timeline ViewModel] handleLoadMore方法执行完成")
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
