import JXPagingView
import JXSegmentedView
import MJRefresh
import shared
import SwiftUI
import UIKit

class ProfileNewTimelineViewController: UIViewController {
    // - Properties
    var presenter: TimelinePresenter?
    private var scrollCallback: ((UIScrollView) -> Void)?

    private lazy var tableView: UITableView = {
        let table = UITableView(frame: .zero, style: .plain)
        table.register(ProfileNewTimelineCell.self, forCellReuseIdentifier: "cell")
        table.delegate = self
        table.dataSource = self
        table.separatorStyle = .none
        table.backgroundColor = .clear
        // 去掉多余的分割线
        table.tableFooterView = UIView()
        return table
    }()

    // - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupRefresh()
    }

    deinit {
        presenter = nil
        scrollCallback = nil
    }

    //  - Setup
    private func setupUI() {
        view.addSubview(tableView)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupRefresh() {
        // 下拉刷新
        tableView.mj_header = MJRefreshNormalHeader(refreshingBlock: { [weak self] in
            Task {
//               if let timelineState = self?.presenter?.models.value as? TimelineState {
//                   try? await timelineState.refresh()
//                   await MainActor.run {
                self?.tableView.mj_header?.endRefreshing()
//                   }
//               }
            }
        })

        // 上拉加载更多
        tableView.mj_footer = MJRefreshAutoNormalFooter(refreshingBlock: { [weak self] in
            Task {
//               if let timelineState = self?.presenter?.models.value as? TimelineState {
//                   try? await timelineState.refresh()
//                   await MainActor.run {
                self?.tableView.mj_footer?.endRefreshing()
//                   }
//               }
            }
        })
    }

    //  - Public Methods
    func updatePresenter(_ presenter: TimelinePresenter) {
        self.presenter = presenter
        // 监听数据变化
        Task { @MainActor in
            for await state in presenter.models {
                if let timelineState = state as? TimelineState {
                    self.handleState(timelineState.listState)
                }
            }
        }
    }

    // - Private Methods

    private func handleState(_ state: PagingState<UiTimeline>) {
        switch onEnum(of: state) {
        case .loading:
            // 显示加载状态
            break
        case let .success(data):
            // 更新数据并刷新表格
            tableView.reloadData()
            // 结束刷新状态
            tableView.mj_header?.endRefreshing()
            tableView.mj_footer?.endRefreshing()
        case let .error(error):
            // 显示错误提示
            tableView.mj_header?.endRefreshing()
            tableView.mj_footer?.endRefreshing()
        case .empty:
            // 显示空状态
            tableView.reloadData()
            tableView.mj_header?.endRefreshing()
            tableView.mj_footer?.endRefreshing()
        }
    }
}

// - UITableViewDataSource & UITableViewDelegate
extension ProfileNewTimelineViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_: UITableView, numberOfRowsInSection _: Int) -> Int {
        if let timelineState = presenter?.models.value as? TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState)
        {
            return Int(data.itemCount)
        }
        return 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath) as! ProfileNewTimelineCell
        if let timelineState = presenter?.models.value as? TimelineState,
           case let .success(data) = onEnum(of: timelineState.listState),
           let item = data.get(index: Int32(indexPath.row))
        {
            cell.configure(with: item)
        }
        return cell
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        scrollCallback?(scrollView)
    }
}

// - JXPagingViewListViewDelegate
extension ProfileNewTimelineViewController: JXPagingViewListViewDelegate {
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
