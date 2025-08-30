import JXPagingView
import JXSegmentedView
import MJRefresh
import os
import shared
import SwiftUI
import UIKit

class TimelineViewController: UIViewController {
    private var tableView: UITableView!
    var presenter: TimelinePresenter?
    private var scrollCallback: ((UIScrollView) -> Void)?

    private let loadingState = TimelineLoadingState()
    private var dataManager: TimelineDataManager?
    private var pendingPresenter: TimelinePresenter?

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()

        // 如果有待处理的presenter，在UI设置完成后进行更新
        if let presenter = pendingPresenter {
            updatePresenter(presenter)
            pendingPresenter = nil
        }
    }

    private func setupUI() {
        // 设置主view的背景色
        // view.backgroundColor = .systemBackground

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
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        // 设置代理
        tableView.delegate = self
        tableView.dataSource = self
    }

    func updatePresenter(_ presenter: TimelinePresenter) {
        // 如果 tableView 还未初始化，先保存 presenter
        guard isViewLoaded else {
            pendingPresenter = presenter
            return
        }

        self.presenter = presenter

        // 初始化数据管理器
        dataManager = TimelineDataManager(tableView: tableView, presenter: presenter)
        dataManager?.setupRefreshControl(loadingState: loadingState)

        // 监听状态变化
        Task { @MainActor in
            for await state in presenter.models {
                if let timelineState = state as? shared.TimelineState {
                    dataManager?.handleStateChange(timelineState.listState, loadingState: loadingState)
                }
            }
        }
    }

    func refresh() {
        dataManager?.refresh()
    }
}

// - UITableViewDataSource

extension TimelineViewController: UITableViewDataSource {
    func tableView(_: UITableView, numberOfRowsInSection _: Int) -> Int {
        if let timelineState = presenter?.models.value as? shared.TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            return Int(data.itemCount)
        }
        return 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "TimelineCell", for: indexPath) as! BaseTimelineCell

        if let timelineState = presenter?.models.value as? shared.TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            if let item = loadingState.handleDataLoading(at: indexPath.row, data: timelineState.listState) {
                cell.configure(with: item)
            } else {
                cell.showLoading()
            }

            // 检查预加载
            loadingState.checkAndTriggerPreload(currentRow: indexPath.row, data: timelineState.listState)
        } else {
            cell.showLoading()
        }

        return cell
    }
}

// - UITableViewDelegate

extension TimelineViewController: UITableViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        scrollCallback?(scrollView)

        // 获取可见cell的范围
        if let lastVisibleRow = tableView.indexPathsForVisibleRows?.last?.row,
           let timelineState = presenter?.models.value as? shared.TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            loadingState.checkAndTriggerPreload(currentRow: lastVisibleRow, data: timelineState.listState)
        }
    }
}

// - JXPagingViewListViewDelegate

extension TimelineViewController: JXPagingViewListViewDelegate {
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
