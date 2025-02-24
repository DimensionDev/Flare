import Foundation
import MJRefresh
import os
import shared

// Timeline数据管理类
class TimelineDataManager {
    private weak var tableView: UITableView?
    private weak var presenter: TimelinePresenter?

    init(tableView: UITableView, presenter: TimelinePresenter?) {
        self.tableView = tableView
        self.presenter = presenter
    }

    // 设置刷新控件
    func setupRefreshControl(loadingState: TimelineLoadingState) {
        // 下拉刷新
        tableView?.mj_header = MJRefreshNormalHeader(refreshingBlock: { [weak self] in
            Task {
                if let timelineState = self?.presenter?.models.value as? shared.TimelineState {
                    try? await timelineState.refresh()
                }
                await MainActor.run {
                    loadingState.clearLoadingRows()
                    self?.tableView?.mj_header?.endRefreshing()
                }
            }
        })

        // 上拉加载更多
        tableView?.mj_footer = MJRefreshAutoNormalFooter(refreshingBlock: { [weak self] in
            Task {
                if let timelineState = self?.presenter?.models.value as? shared.TimelineState,
                   case let .success(data) = onEnum(of: timelineState.listState),
                   let lastVisibleRow = self?.tableView?.indexPathsForVisibleRows?.last?.row
                {
                    // 触发加载下一页
                    os_log("[📔][TimelineDataManager] 上拉加载触发get: lastVisibleRow = %{public}d", log: .default, type: .debug, lastVisibleRow)
                    _ = data.get(index: Int32(lastVisibleRow))

                    // 等待直到加载状态改变或超时
                    let startTime = Date()
                    while Date().timeIntervalSince(startTime) < 1.0 {
                        if case .loading = onEnum(of: data.appendState) {
                            try? await Task.sleep(nanoseconds: UInt64(0.1 * Double(NSEC_PER_SEC)))
                            continue
                        }
                        break
                    }

                    await MainActor.run {
                        self?.handleLoadMoreState(data: data)
                    }
                } else {
                    await MainActor.run {
                        self?.tableView?.mj_footer?.endRefreshing()
                    }
                }
            }
        })
    }

    // 处理加载更多状态
    private func handleLoadMoreState(data: PagingState<UiTimeline>) {
        guard case let .success(successData) = onEnum(of: data) else { return }

        switch onEnum(of: successData.appendState) {
        case .loading:
            break
        case let .notLoading(notLoading):
            if notLoading.endOfPaginationReached {
                tableView?.mj_footer?.endRefreshingWithNoMoreData()
            } else {
                tableView?.mj_footer?.endRefreshing()
            }
        case .error:
            tableView?.mj_footer?.endRefreshing()
        }
    }

    // 处理状态变化
    func handleStateChange(_ state: PagingState<UiTimeline>, loadingState: TimelineLoadingState) {
        switch onEnum(of: state) {
        case .loading:
            os_log("[📔][TimelineDataManager] state: loading", log: .default, type: .debug)
        case let .success(data):
            os_log("[📔][TimelineDataManager] state: success, itemCount: %{public}d", log: .default, type: .debug, data.itemCount)
            loadingState.clearLoadingRows()
            tableView?.reloadData()
            tableView?.mj_header?.endRefreshing()
            handleLoadMoreState(data: state)
        case let .error(error):
            os_log("[📔][TimelineDataManager] state: error, error: %{public}@", log: .default, type: .error, String(describing: error))
            if let throwable = error.error as? KotlinThrowable {
                os_log("[📔][TimelineDataManager] error details: %{public}@", log: .default, type: .error, throwable.message ?? "Unknown error")
            }
            loadingState.clearLoadingRows()
            tableView?.mj_header?.endRefreshing()
            tableView?.mj_footer?.endRefreshing()
        case .empty:
            os_log("[📔][TimelineDataManager] state: empty", log: .default, type: .debug)
            loadingState.clearLoadingRows()
            tableView?.reloadData()
            tableView?.mj_header?.endRefreshing()
            tableView?.mj_footer?.endRefreshingWithNoMoreData()
        }
    }

    // 刷新
    func refresh() {
        tableView?.mj_header?.beginRefreshing()
    }
}
