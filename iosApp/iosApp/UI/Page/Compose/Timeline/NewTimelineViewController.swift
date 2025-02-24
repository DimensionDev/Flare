import JXPagingView
import JXSegmentedView
import MJRefresh
import os
import shared
import SwiftUI
import UIKit

// class NewTimelineViewController: UIViewController {
//     var tableView: UITableView!
//     var presenter: TimelinePresenter?
//     private var scrollCallback: ((UIScrollView) -> Void)?

//     // 预加载的行数
//     private let preloadDistance: Int = 10
//     // 是否显示加载更多
//     var shouldShowLoadMore: Bool = true

//     // 记录正在加载的行
//     private var loadingRows: Set<Int> = []

//     // 检查并触发预加载
//     private func checkAndTriggerPreload(currentRow: Int) {
//         guard let timelineState = presenter?.models.value as? TimelineState,
//               case let .success(data) = onEnum(of: timelineState.listState) else {
//             return
//         }

//         // 计算需要预加载的范围
//         let startRow = currentRow
//         let endRow = min(currentRow + preloadDistance, Int(data.itemCount) - 1) // 减1避免越界

//         // 确保索引有效
//         guard startRow >= 0, endRow >= startRow, endRow < Int(data.itemCount) else {
//             return
//         }

//         // 触发预加载
//         for row in startRow...endRow where !loadingRows.contains(row) {
//             if data.peek(index: Int32(row)) == nil {
//                 loadingRows.insert(row)
//                 os_log("[📔][NewTimelineViewController] 预加载触发get: row = %{public}d", log: .default, type: .debug, row)
//                 _ = data.get(index: Int32(row))
//             }
//         }
//     }

//     override func viewDidLoad() {
//         super.viewDidLoad()
//         setupUI()
//         os_log("[📔][NewTimelineViewController] viewDidLoad", log: .default, type: .debug)
//     }

//     override func viewWillAppear(_ animated: Bool) {
//         super.viewWillAppear(animated)
//         os_log("[📔][NewTimelineViewController] viewWillAppear", log: .default, type: .debug)
//     }

//     override func viewDidAppear(_ animated: Bool) {
//         super.viewDidAppear(animated)
//         os_log("[📔][NewTimelineViewController] viewDidAppear", log: .default, type: .debug)
//     }

//     deinit {
//         presenter = nil
//         scrollCallback = nil
//     }

//     private func setupUI() {
//         os_log("[📔][NewTimelineViewController] setupUI start", log: .default, type: .debug)
//         // 设置主view的背景色
//         view.backgroundColor = .systemBackground

//         // 配置 tableView
//         tableView = UITableView()
//         tableView.backgroundColor = .clear
//         tableView.separatorStyle = .none
//         tableView.register(BaseTimelineCell.self, forCellReuseIdentifier: "TimelineCell")
//         view.addSubview(tableView)

//         // 设置约束
//         tableView.translatesAutoresizingMaskIntoConstraints = false
//         NSLayoutConstraint.activate([
//             tableView.topAnchor.constraint(equalTo: view.topAnchor),
//             tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
//             tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
//             tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
//         ])

//         // 设置代理
//         tableView.delegate = self
//         tableView.dataSource = self

//         // 配置刷新控件
//         setupRefreshControl()
//         os_log("[📔][NewTimelineViewController] setupUI end", log: .default, type: .debug)
//     }

//     private func setupRefreshControl() {
//         os_log("[📔][NewTimelineViewController] setupRefreshControl start", log: .default, type: .debug)
//         // 下拉刷新
//         tableView.mj_header = MJRefreshNormalHeader(refreshingBlock: { [weak self] in
//             Task {
//                 if let timelineState = self?.presenter?.models.value as? TimelineState {
//                     try? await timelineState.refresh()
//                 }
//                 await MainActor.run {
//                     self?.loadingRows.removeAll()
//                     self?.tableView.mj_header?.endRefreshing()
//                 }
//             }
//         })

//         // 上拉加载更多
//         if shouldShowLoadMore {
//             os_log("[📔][NewTimelineViewController] 配置上拉加载更多", log: .default, type: .debug)
//             tableView.mj_footer = MJRefreshAutoNormalFooter(refreshingBlock: { [weak self] in
//                 Task {
//                     if let timelineState = self?.presenter?.models.value as? TimelineState,
//                        case let .success(data) = onEnum(of: timelineState.listState),
//                        let lastVisibleRow = self?.tableView.indexPathsForVisibleRows?.last?.row {
//                         // 触发加载下一页
//                         os_log("[📔][NewTimelineViewController] cell显示触发get: lastVisibleRow = %{public}d", log: .default, type: .debug, lastVisibleRow)

//                         _ = data.get(index: Int32(lastVisibleRow))

//                         // 等待直到加载状态改变或超时
//                         let startTime = Date()
//                         while Date().timeIntervalSince(startTime) < 1.0 { // 最多等待1秒
//                             if case .loading = onEnum(of: data.appendState) {
//                                 try? await Task.sleep(nanoseconds: UInt64(0.1 * Double(NSEC_PER_SEC)))
//                                 continue
//                             }
//                             break
//                         }

//                         await MainActor.run {
//                             // 根据appendState决定是否显示无更多数据
//                             switch onEnum(of: data.appendState) {
//                             case .loading:
//                                 break
//                             case let .notLoading(notLoading):
//                                 if notLoading.endOfPaginationReached {
//                                     self?.tableView.mj_footer?.endRefreshingWithNoMoreData()
//                                 } else {
//                                     self?.tableView.mj_footer?.endRefreshing()
//                                 }
//                             case .error:
//                                 self?.tableView.mj_footer?.endRefreshing()
//                             }
//                         }
//                     } else {
//                         await MainActor.run {
//                             self?.tableView.mj_footer?.endRefreshing()
//                         }
//                     }
//                 }
//             })
//         }
//         os_log("[📔][NewTimelineViewController] setupRefreshControl end", log: .default, type: .debug)
//     }

//     func updatePresenter(_ presenter: TimelinePresenter) {
//         os_log("[📔][NewTimelineViewController] updatePresenter start", log: .default, type: .debug)
//         self.presenter = presenter
//         Task { @MainActor in
//             for await state in presenter.models {
//                 if let timelineState = state as? TimelineState {
//                     os_log("[📔][NewTimelineViewController] received new state", log: .default, type: .debug)
//                     self.handleState(timelineState.listState)
//                 }
//             }
//         }
//         os_log("[📔][NewTimelineViewController] updatePresenter end", log: .default, type: .debug)
//     }

//     private func handleState(_ state: PagingState<UiTimeline>) {
//         os_log("[📔][NewTimelineViewController] handleState start", log: .default, type: .debug)
//         switch onEnum(of: state) {
//         case .loading:
//             os_log("[📔][NewTimelineViewController] state: loading", log: .default, type: .debug)
//         case let .success(data):
//             os_log("[📔][NewTimelineViewController] state: success, itemCount: %{public}d", log: .default, type: .debug, data.itemCount)
//             loadingRows.removeAll()
//             tableView.reloadData()
//             tableView.mj_header?.endRefreshing()

//             // 根据appendState控制footer状态
//             switch onEnum(of: data.appendState) {
//             case .loading:
//                 tableView.mj_footer?.beginRefreshing()
//             case let .notLoading(notLoading):
//                 if notLoading.endOfPaginationReached {
//                     tableView.mj_footer?.endRefreshingWithNoMoreData()
//                 } else {
//                     tableView.mj_footer?.endRefreshing()
//                 }
//             case .error:
//                 tableView.mj_footer?.endRefreshing()
//             }
//         case .error:
//             os_log("[📔][NewTimelineViewController] state: error", log: .default, type: .debug)
//             loadingRows.removeAll()
//             tableView.mj_header?.endRefreshing()
//             tableView.mj_footer?.endRefreshing()
//         case .empty:
//             os_log("[📔][NewTimelineViewController] state: empty", log: .default, type: .debug)
//             loadingRows.removeAll()
//             tableView.reloadData()
//             tableView.mj_header?.endRefreshing()
//             tableView.mj_footer?.endRefreshingWithNoMoreData()
//         }
//         os_log("[📔][NewTimelineViewController] handleState end", log: .default, type: .debug)
//     }

//     func refresh() {
//         tableView.mj_header?.beginRefreshing()
//     }
// }

// // - UITableViewDataSource & UITableViewDelegate
// extension NewTimelineViewController: UITableViewDataSource, UITableViewDelegate {
//     // MARK: - UITableViewDataSource

//     func tableView(_: UITableView, numberOfRowsInSection _: Int) -> Int {
//         if let timelineState = presenter?.models.value as? TimelineState,
//            case let .success(data) = onEnum(of: timelineState.listState)
//         {
//             // 直接返回itemCount，让cellForRowAt处理数据加载
//             let count = Int(data.itemCount)
//             os_log("[📔][NewTimelineViewController] numberOfRowsInSection: totalCount = %{public}d", log: .default, type: .debug, count)
//             return count
//         }
//         os_log("[📔][NewTimelineViewController] numberOfRowsInSection: return 0", log: .default, type: .debug)
//         return 0
//     }

//     func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
//         os_log("[📔][NewTimelineViewController] cellForRowAt: row = %{public}d", log: .default, type: .debug, indexPath.row)
//         let cell = tableView.dequeueReusableCell(withIdentifier: "TimelineCell", for: indexPath) as! BaseTimelineCell
//         if let timelineState = presenter?.models.value as? TimelineState,
//            case let .success(data) = onEnum(of: timelineState.listState)
//         {
//             os_log("[📔][NewTimelineViewController] cellForRowAt: itemCount = %{public}d", log: .default, type: .debug, data.itemCount)
//             // 先尝试使用peek获取数据
//             if let item = data.peek(index: Int32(indexPath.row)) {
//                 cell.configure(with: item)
//                 loadingRows.remove(indexPath.row)
//             } else {
//                 // 显示加载中状态
//                 cell.showLoading()
//                 // 如果peek返回nil，使用get触发加载
//                 if !loadingRows.contains(indexPath.row) {
//                     loadingRows.insert(indexPath.row)
//                     os_log("[📔][NewTimelineViewController] cell显示触发get: row = %{public}d", log: .default, type: .debug, indexPath.row)
//                     if let item = data.get(index: Int32(indexPath.row)) {
//                         cell.configure(with: item)
//                         loadingRows.remove(indexPath.row)
//                     }
//                 }
//             }

//             // 检查预加载
//             checkAndTriggerPreload(currentRow: indexPath.row)
//         } else {
//             os_log("[📔][NewTimelineViewController] cellForRowAt: 无数据", log: .default, type: .debug)
//             cell.showLoading()
//         }
//         return cell
//     }

//     // MARK: - UITableViewDelegate

//     func scrollViewDidScroll(_ scrollView: UIScrollView) {
//         scrollCallback?(scrollView)

//         // 获取可见cell的范围
//         if let lastVisibleRow = tableView.indexPathsForVisibleRows?.last?.row {
//             checkAndTriggerPreload(currentRow: lastVisibleRow)
//         }
//     }
// }

// // - JXPagingViewListViewDelegate
// extension NewTimelineViewController: JXPagingViewListViewDelegate {
//     func listView() -> UIView {
//         view
//     }

//     func listScrollView() -> UIScrollView {
//         tableView
//     }

//     func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> Void) {
//         scrollCallback = callback
//     }
// }
