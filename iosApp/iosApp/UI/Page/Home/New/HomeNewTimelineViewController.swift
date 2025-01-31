import JXSegmentedView
import MJRefresh
import os
import shared
import SwiftUI
import UIKit

class HomeNewTimelineViewController: UIViewController {
    var tableView: UITableView!
    var presenter: TimelinePresenter?
    private var scrollCallback: ((UIScrollView) -> Void)?
    private var timelineStore: TimelineStore?
    private var currentKey: String = ""

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        os_log("[📔][HomeNewTimelineViewController] viewDidLoad", log: .default, type: .debug)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        os_log("[📔][HomeNewTimelineViewController] viewWillAppear", log: .default, type: .debug)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        os_log("[📔][HomeNewTimelineViewController] viewDidAppear", log: .default, type: .debug)
        // 恢复滚动位置
        if let store = timelineStore {
            let position = store.getScrollPosition(for: currentKey)
            tableView.setContentOffset(CGPoint(x: 0, y: position), animated: false)
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // 保存滚动位置
        if let store = timelineStore {
            store.saveScrollPosition(tableView.contentOffset.y, for: currentKey)
            store.saveContentSize(tableView.contentSize, for: currentKey)
        }
    }

    deinit {
        presenter = nil
        scrollCallback = nil
        timelineStore = nil
    }

    private func setupUI() {
        os_log("[📔][HomeNewTimelineViewController] setupUI start", log: .default, type: .debug)
        // 配置 tableView
        tableView = UITableView()
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.register(HomeNewTimelineCell.self, forCellReuseIdentifier: "TimelineCell")
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
        os_log("[📔][HomeNewTimelineViewController] setupUI end", log: .default, type: .debug)
    }

    private func setupRefreshControl() {
        // 下拉刷新
        tableView.mj_header = MJRefreshNormalHeader(refreshingBlock: { [weak self] in
            Task {
                if let timelineState = self?.presenter?.models.value as? TimelineState {
                    try? await timelineState.refresh()
                    await MainActor.run {
                        self?.tableView.mj_header?.endRefreshing()
                    }
                }
            }
        })
    }

    func configure(with store: TimelineStore, key: String) {
        timelineStore = store
        currentKey = key
    }

    func updatePresenter(_ presenter: TimelinePresenter) {
        os_log("[📔][HomeNewTimelineViewController] updatePresenter start", log: .default, type: .debug)
        self.presenter = presenter
        Task { @MainActor in
            for await state in presenter.models {
                if let timelineState = state as? TimelineState {
                    os_log("[📔][HomeNewTimelineViewController] received new state", log: .default, type: .debug)
                    self.handleState(timelineState.listState)
                }
            }
        }
        os_log("[📔][HomeNewTimelineViewController] updatePresenter end", log: .default, type: .debug)
    }

    private func handleState(_ state: PagingState<UiTimeline>) {
        os_log("[📔][HomeNewTimelineViewController] handleState start", log: .default, type: .debug)
        switch onEnum(of: state) {
        case .loading:
            os_log("[📔][HomeNewTimelineViewController] state: loading", log: .default, type: .debug)
        // 显示加载状态
        case let .success(data):
            os_log("[📔][HomeNewTimelineViewController] state: success, itemCount: %{public}d", log: .default, type: .debug, data.itemCount)
            // 更新数据
            tableView.reloadData()
            tableView.mj_header?.endRefreshing()

            // 恢复滚动位置
            if let store = timelineStore {
                let contentSize = store.getContentSize(for: currentKey)
                if tableView.contentSize != contentSize {
                    let position = store.getScrollPosition(for: currentKey)
                    tableView.setContentOffset(CGPoint(x: 0, y: position), animated: false)
                }
            }
        case let .error(error):
            os_log("[📔][HomeNewTimelineViewController] state: error", log: .default, type: .debug)
            // 显示错误状态
            tableView.mj_header?.endRefreshing()
        case .empty:
            os_log("[📔][HomeNewTimelineViewController] state: empty", log: .default, type: .debug)
            // 显示空状态
            tableView.reloadData()
            tableView.mj_header?.endRefreshing()
        }
        os_log("[📔][HomeNewTimelineViewController] handleState end", log: .default, type: .debug)
    }

    func refresh() {
        tableView.mj_header?.beginRefreshing()
    }
}

// - UITableViewDataSource

extension HomeNewTimelineViewController: UITableViewDataSource {
    func tableView(_: UITableView, numberOfRowsInSection _: Int) -> Int {
        if let timelineState = presenter?.models.value as? TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            return Int(data.itemCount)
        }
        return 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "TimelineCell", for: indexPath) as! HomeNewTimelineCell
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

extension HomeNewTimelineViewController: UITableViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        scrollCallback?(scrollView)

        // 保存滚动位置
        if let timelineStore {
            timelineStore.saveScrollPosition(scrollView.contentOffset.y, for: currentKey)
            timelineStore.saveContentSize(scrollView.contentSize, for: currentKey)
        }
    }
}

// - JXPagingViewListViewDelegate

extension HomeNewTimelineViewController: JXPagingViewListViewDelegate {
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
