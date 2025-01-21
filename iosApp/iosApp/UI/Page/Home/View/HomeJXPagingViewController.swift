import SwiftUI
import shared
import UIKit
import JXSegmentedView

extension JXPagingListContainerView: JXSegmentedViewListContainer {}

class HomeJXPagingViewController: UIViewController {
    private var pagingView: JXPagingView!
    private var segmentedView: JXSegmentedView!
    private var router: Router!
    private var accountType: AccountType!
    private var showSettings: Binding<Bool>!
    private var showLogin: Binding<Bool>!
    private var selectedHomeTab: Binding<Int>!
    private var timelineStore: TimelineStore!
    private var tabSettingsStore: TabSettingsStore!
    
    // 缓存列表视图控制器
    private var listViewControllers: [Int: HomeTimelineListViewController] = [:]
    
    // 记录上一次的索引
    private var lastSelectedIndex: Int = 0
    
    deinit {
        // 清理资源
        listViewControllers.removeAll()
    }
    
    func configure(
        router: Router,
        accountType: AccountType,
        showSettings: Binding<Bool>,
        showLogin: Binding<Bool>,
        selectedHomeTab: Binding<Int>,
        timelineStore: TimelineStore,
        tabSettingsStore: TabSettingsStore
    ) {
        self.router = router
        self.accountType = accountType
        self.showSettings = showSettings
        self.showLogin = showLogin
        self.selectedHomeTab = selectedHomeTab
        self.timelineStore = timelineStore
        self.tabSettingsStore = tabSettingsStore
        
        // 初始化 lastSelectedIndex
        self.lastSelectedIndex = selectedHomeTab.wrappedValue
        
        setupUI()
    }
    
    private func setupUI() {
        // 配置 JXSegmentedView
        segmentedView = JXSegmentedView(frame: CGRect(x: 0, y: 0, width: view.bounds.width, height: 44))
        let dataSource = JXSegmentedTitleDataSource()
        dataSource.titles = tabSettingsStore.availableTabs.map { tab in
            switch tab.metaData.title {
            case .text(let title): title
            case .localized(let key): NSLocalizedString(key, comment: "")
            }
        }
        dataSource.titleNormalColor = .gray
        dataSource.titleSelectedColor = .label
        dataSource.titleNormalFont = .systemFont(ofSize: 15)
        dataSource.titleSelectedFont = .systemFont(ofSize: 15, weight: .medium)
        dataSource.isTitleColorGradientEnabled = true
        segmentedView.dataSource = dataSource
        
        // 配置指示器
        let indicator = JXSegmentedIndicatorLineView()
        indicator.indicatorColor = UIColor.systemBlue
        indicator.indicatorWidth = 30
        segmentedView.indicators = [indicator]
        
        // 配置 JXPagingView
        pagingView = JXPagingView(delegate: self)
        view.addSubview(pagingView)
        pagingView.mainTableView.backgroundColor = .clear
        
        // 设置 listContainerView 的代理
        pagingView.listContainerView.delegate = self
        
        // 关联 - 直接设置，不需要类型转换
        segmentedView.listContainer = pagingView.listContainerView
        
        // 设置 segmentedView 代理
        segmentedView.delegate = self
        
        // 设置初始选中项
        if let index = selectedHomeTab?.wrappedValue,
           index >= 0 && index < tabSettingsStore.availableTabs.count {
            segmentedView.defaultSelectedIndex = index
            let tab = tabSettingsStore.availableTabs[index]
            timelineStore.updateCurrentPresenter(for: tab)
        } else {
            // 如果index无效，设置为0
            selectedHomeTab?.wrappedValue = 0
            segmentedView.defaultSelectedIndex = 0
            if !tabSettingsStore.availableTabs.isEmpty {
                let tab = tabSettingsStore.availableTabs[0]
                timelineStore.updateCurrentPresenter(for: tab)
            }
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        pagingView.frame = view.bounds
    }
    
    func updateContent() {
        pagingView.reloadData()
    }
    
    private func getOrCreateTimelineViewController(at index: Int) -> HomeTimelineListViewController {
        if let existingVC = listViewControllers[index] {
            return existingVC
        }
        
        // 获取当前 tab 的配置
        let tab = tabSettingsStore.availableTabs[index]
        
        // 为每个 tab 创建独立的 timelineStore
        let newTimelineStore = TimelineStore(accountType: accountType)
        // 立即设置当前 presenter
        newTimelineStore.updateCurrentPresenter(for: tab)
        
        let timelineView = TimelineScreen(timelineStore: newTimelineStore)
        let hostingController = UIHostingController(rootView: timelineView)
        let viewController = HomeTimelineListViewController(hostingController: hostingController)
        listViewControllers[index] = viewController
        return viewController
    }
}

// MARK: - JXPagingViewDelegate
extension HomeJXPagingViewController: JXPagingViewDelegate {
    func tableHeaderViewHeight(in pagingView: JXPagingView) -> Int {
        return 0
    }
    
    func tableHeaderView(in pagingView: JXPagingView) -> UIView {
        return UIView()
    }
    
    func heightForPinSectionHeader(in pagingView: JXPagingView) -> Int {
        return 44  // segmentedView 的高度
    }
    
    func viewForPinSectionHeader(in pagingView: JXPagingView) -> UIView {
        return segmentedView
    }
    
    func numberOfLists(in pagingView: JXPagingView) -> Int {
        return tabSettingsStore.availableTabs.count
    }
    
    func pagingView(_ pagingView: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
        return getOrCreateTimelineViewController(at: index)
    }
}
 // MARK: - JXPagingListContainerViewDelegate
extension HomeJXPagingViewController: JXPagingListContainerViewDelegate {
    func listContainerViewDidEndScrolling(_ listContainerView: JXPagingListContainerView) {
        // 获取当前页面索引
        let currentIndex = Int(listContainerView.scrollView.contentOffset.x / listContainerView.scrollView.bounds.width)
        
        // 只有当索引真的改变时才更新
        if currentIndex != lastSelectedIndex {
            // 更新 segmentedView 的选中状态
            segmentedView.selectItemAt(index: currentIndex)
            
            // 使用 async 延迟更新状态
            DispatchQueue.main.async {
                // 更新选中的 tab
                self.selectedHomeTab?.wrappedValue = currentIndex
                
                // 更新数据源
                let tab = self.tabSettingsStore.availableTabs[currentIndex]
                self.timelineStore.updateCurrentPresenter(for: tab)
            }
            
            // 更新上一次选中的索引
            lastSelectedIndex = currentIndex
        }
    }
    
    func listContainerView(_ listContainerView: JXPagingListContainerView, listDidAppearAt index: Int) {
        // 只有当索引真的改变时才更新
        if index != lastSelectedIndex {
          // 更新 segmentedView 的选中状态
            segmentedView.selectItemAt(index: index)
            
            // 使用 async 延迟更新状态
            DispatchQueue.main.async {
                // 更新选中的 tab
                self.selectedHomeTab?.wrappedValue = index
                
                // 更新数据源
                let tab = self.tabSettingsStore.availableTabs[index]
                self.timelineStore.updateCurrentPresenter(for: tab)
            }
            
            // 更新上一次选中的索引
            lastSelectedIndex = index
        }
    }
}

// MARK: - JXSegmentedViewDelegate
extension HomeJXPagingViewController: JXSegmentedViewDelegate {
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        // 只有当索引真的改变时才更新
        if index != lastSelectedIndex {
            // 同步分页视图
            pagingView.listContainerView.didClickSelectedItem(at: index)
            
            // 使用 async 延迟更新状态
            DispatchQueue.main.async {
                // 更新选中的tab
                self.selectedHomeTab?.wrappedValue = index
                
                // 更新数据源
                let tab = self.tabSettingsStore.availableTabs[index]
                self.timelineStore.updateCurrentPresenter(for: tab)
            }
            
            // 更新上一次选中的索引
            lastSelectedIndex = index
        }
    }
    
    func segmentedView(_ segmentedView: JXSegmentedView, didClickSelectedItemAt index: Int) {
        // 处理重复点击，可以添加回到顶部等功能
        if let viewController = listViewControllers[index] {
            viewController.scrollToTop()
        }
    }
} 
