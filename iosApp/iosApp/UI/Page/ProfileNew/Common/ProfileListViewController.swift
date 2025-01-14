import UIKit
import MJRefresh


class ProfileNewListViewController: UIViewController {
    lazy var tableView: UITableView = UITableView(frame: CGRect.zero, style: .plain)
    var dataSource: [String] = [String]()
    var isNeedHeader = false
    var isNeedFooter = false
    var isHeaderRefreshed = false
    var listViewDidScrollCallback: ((UIScrollView) -> ())?

    override func viewDidLoad() {
        super.viewDidLoad()

        tableView.backgroundColor = .systemBackground
        tableView.tableFooterView = UIView()
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "cell")
        view.addSubview(tableView)
        
        if isNeedHeader {
            tableView.mj_header = MJRefreshNormalHeader(refreshingTarget: self, refreshingAction: #selector(headerRefresh))
        }
        if isNeedFooter {
            tableView.mj_footer = MJRefreshAutoNormalFooter(refreshingTarget: self, refreshingAction: #selector(loadMore))
            if #available(iOS 11.0, *) {
                tableView.contentInsetAdjustmentBehavior = .never
            }
        } else {
            //列表的contentInsetAdjustmentBehavior失效，需要自己设置底部inset
            tableView.contentInset = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 0)
        }
        beginFirstRefresh()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        tableView.frame = view.bounds
    }

    func beginFirstRefresh() {
        if !isHeaderRefreshed {
            if isNeedHeader {
                tableView.mj_header?.beginRefreshing()
            } else {
                isHeaderRefreshed = true
                tableView.reloadData()
            }
        }
    }

    @objc func headerRefresh() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            self.tableView.mj_header?.endRefreshing()
            self.isHeaderRefreshed = true
            self.tableView.reloadData()
        }
    }

    @objc func loadMore() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            self.dataSource.append("加载更多成功")
            self.tableView.reloadData()
            self.tableView.mj_footer?.endRefreshing()
        }
    }
}

// MARK: - UITableViewDataSource, UITableViewDelegate
extension ProfileNewListViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return isHeaderRefreshed ? dataSource.count : 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath)
        cell.textLabel?.text = dataSource[indexPath.row]
        return cell
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 50
    }
    
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        listViewDidScrollCallback?(scrollView)
    }
}

// MARK: - JXPagingViewListViewDelegate
extension ProfileNewListViewController: JXPagingViewListViewDelegate {
    func listView() -> UIView { view }
    
    func listScrollView() -> UIScrollView { tableView }
    
    func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> ()) {
        self.listViewDidScrollCallback = callback
    }
} 
