import JXPagingView
import JXSegmentedView
import MJRefresh
import os
import shared
import SwiftUI
import UIKit

class NewTimelineViewController: UIViewController {
    var tableView: UITableView!
    var presenter: TimelinePresenter?
    private var scrollCallback: ((UIScrollView) -> Void)?

    // 是否显示加载更多
    var shouldShowLoadMore: Bool = true

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        os_log("[📔][NewTimelineViewController] viewDidLoad", log: .default, type: .debug)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        os_log("[📔][NewTimelineViewController] viewWillAppear", log: .default, type: .debug)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        os_log("[📔][NewTimelineViewController] viewDidAppear", log: .default, type: .debug)
    }

    deinit {
        presenter = nil
        scrollCallback = nil
    }

    private func setupUI() {
        os_log("[📔][NewTimelineViewController] setupUI start", log: .default, type: .debug)
        // 配置 tableView
        tableView = UITableView()
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.register(BaseTimelineCell.self, forCellReuseIdentifier: "TimelineCell")
        view.addSubview(tableView)

        // 设置约束
        tableView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        // 设置代理
        tableView.delegate = self
        tableView.dataSource = self

        // 配置刷新控件
        setupRefreshControl()
        os_log("[📔][NewTimelineViewController] setupUI end", log: .default, type: .debug)
    }

    private func setupRefreshControl() {
        os_log("[📔][NewTimelineViewController] setupRefreshControl start", log: .default, type: .debug)
        // 下拉刷新
        tableView.mj_header = MJRefreshNormalHeader(refreshingBlock: { [weak self] in
            Task {
//                if let timelineState = self?.presenter?.models.value as? TimelineState {
//                    try? await timelineState.refresh()
                await MainActor.run {
                    self?.tableView.mj_header?.endRefreshing()
                }
//                }
            }
        })

        // 上拉加载更多
        if shouldShowLoadMore {
            os_log("[📔][NewTimelineViewController] 配置上拉加载更多", log: .default, type: .debug)
            tableView.mj_footer = MJRefreshAutoNormalFooter(refreshingBlock: { [weak self] in
                Task {
//                    if let timelineState = self?.presenter?.models.value as? TimelineState {
//                        try? await timelineState.loadMore()
                    await MainActor.run {
                        self?.tableView.mj_footer?.endRefreshing()
                    }
//                    }
                }
            })
        }
        os_log("[📔][NewTimelineViewController] setupRefreshControl end", log: .default, type: .debug)
    }

    func updatePresenter(_ presenter: TimelinePresenter) {
        os_log("[📔][NewTimelineViewController] updatePresenter start", log: .default, type: .debug)
        self.presenter = presenter
        Task { @MainActor in
            for await state in presenter.models {
                if let timelineState = state as? TimelineState {
                    os_log("[📔][NewTimelineViewController] received new state", log: .default, type: .debug)
                    self.handleState(timelineState.listState)
                }
            }
        }
        os_log("[📔][NewTimelineViewController] updatePresenter end", log: .default, type: .debug)
    }

    private func handleState(_ state: PagingState<UiTimeline>) {
        os_log("[📔][NewTimelineViewController] handleState start", log: .default, type: .debug)
        switch onEnum(of: state) {
        case .loading:
            os_log("[📔][NewTimelineViewController] state: loading", log: .default, type: .debug)
        case let .success(data):
            os_log("[📔][NewTimelineViewController] state: success, itemCount: %{public}d", log: .default, type: .debug, data.itemCount)
            tableView.reloadData()
            tableView.mj_header?.endRefreshing()
            tableView.mj_footer?.endRefreshing()
        case let .error(error):
            os_log("[📔][NewTimelineViewController] state: error", log: .default, type: .debug)
            tableView.mj_header?.endRefreshing()
            tableView.mj_footer?.endRefreshing()
        case .empty:
            os_log("[📔][NewTimelineViewController] state: empty", log: .default, type: .debug)
            tableView.reloadData()
            tableView.mj_header?.endRefreshing()
            tableView.mj_footer?.endRefreshing()
        }
        os_log("[📔][NewTimelineViewController] handleState end", log: .default, type: .debug)
    }

    func refresh() {
        tableView.mj_header?.beginRefreshing()
    }
}

// - UITableViewDataSource
extension NewTimelineViewController: UITableViewDataSource {
    func tableView(_: UITableView, numberOfRowsInSection _: Int) -> Int {
        if let timelineState = presenter?.models.value as? TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            return Int(data.itemCount)
        }
        return 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "TimelineCell", for: indexPath) as! BaseTimelineCell
        if let timelineState = presenter?.models.value as? TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState),
           data.itemCount > 0,
           let item = data.get(index: Int32(indexPath.row))
        {
            cell.configure(with: item)
        }
        return cell
    }
}

// - UITableViewDelegate
extension NewTimelineViewController: UITableViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        scrollCallback?(scrollView)
    }
}

// - JXPagingViewListViewDelegate
extension NewTimelineViewController: JXPagingViewListViewDelegate {
    func listView() -> UIView {
        view
    }

    func listScrollView() -> UIScrollView {
        tableView
    }

    func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> Void) {
        scrollCallback = callback
    }
}
