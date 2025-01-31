import JXSegmentedView
import MJRefresh
import os
import shared
import SwiftUI
import UIKit

class HomeNewViewController: UIViewController {
    // - Properties

    private let tabStore: TabSettingsStore
    private let timelineStore: TimelineStore
    private let accountType: AccountType
    private var listViewControllers: [Int: JXPagingViewListViewDelegate] = [:]

    // UI Components
    private var pagingView: JXPagingView!
    private var segmentedView: JXSegmentedView!
    private var segmentedDataSource: JXSegmentedTitleDataSource!

    // - Initialization

    init(tabStore: TabSettingsStore, timelineStore: TimelineStore, accountType: AccountType) {
        self.tabStore = tabStore
        self.timelineStore = timelineStore
        self.accountType = accountType
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // - Lifecycle Methods

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        os_log("[📔][HomeNewViewController] viewDidLoad", log: .default, type: .debug)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        os_log("[📔][HomeNewViewController] viewWillAppear", log: .default, type: .debug)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        os_log("[📔][HomeNewViewController] viewDidAppear", log: .default, type: .debug)
    }

    // - Private Methods

    private func setupUI() {
        os_log("[📔][HomeNewViewController] setupUI start", log: .default, type: .debug)
        view.backgroundColor = .systemBackground

        // 配置分段控制器
        setupSegmentedView()

        // 配置分页视图
        setupPagingView()

        // 初始化第一个标签页
        if tabStore.availableTabs.count > 0 {
            let index = 0
            let selectedTab = tabStore.availableTabs[index]
            tabStore.updateSelectedTab(selectedTab)

            if let currentList = pagingView.validListDict[index] as? HomeNewTimelineViewController,
               let presenter = tabStore.currentPresenter
            {
                currentList.updatePresenter(presenter)
            }
        }

        // 添加通知监听
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleTabsUpdate),
            name: NSNotification.Name("TabsDidUpdate"),
            object: nil
        )

        os_log("[📔][HomeNewViewController] setupUI end", log: .default, type: .debug)
    }

    @objc private func handleTabsUpdate() {
        updateSegmentedTitles()
        // 重新加载分页视图
        pagingView.reloadData()
    }

    private func setupSegmentedView() {
        os_log("[📔][HomeNewViewController] setupSegmentedView start", log: .default, type: .debug)
        segmentedView = JXSegmentedView()
        segmentedDataSource = JXSegmentedTitleDataSource()

        // 配置数据源
        segmentedDataSource.titleNormalColor = .gray
        segmentedDataSource.titleSelectedColor = .label
        segmentedDataSource.titleNormalFont = .systemFont(ofSize: 15)
        segmentedDataSource.titleSelectedFont = .systemFont(ofSize: 15)
        segmentedDataSource.isTitleColorGradientEnabled = true

        // 设置item宽度和间距
        // segmentedDataSource.itemWidth = 80          // 固定宽度为50
        segmentedDataSource.itemSpacing = 20 // 间距为10
        segmentedDataSource.isItemSpacingAverageEnabled = false // 不均分间距，保持靠左对齐

        // 配置指示器
        let indicator = JXSegmentedIndicatorLineView()
        indicator.indicatorColor = .systemBlue
        indicator.indicatorWidth = 30
        segmentedView.indicators = [indicator]

        // 设置数据源和代理
        segmentedView.dataSource = segmentedDataSource
        segmentedView.delegate = self

        // 更新标题
        updateSegmentedTitles()

        // 设置默认选中项
        if tabStore.availableTabs.count > 0 {
            segmentedView.defaultSelectedIndex = 0
        }

        os_log("[📔][HomeNewViewController] setupSegmentedView end", log: .default, type: .debug)
    }

    private func setupPagingView() {
        os_log("[📔][HomeNewViewController] setupPagingView start", log: .default, type: .debug)
        pagingView = JXPagingView(delegate: self)
        view.addSubview(pagingView)

        // 设置约束
        pagingView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            pagingView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            pagingView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pagingView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pagingView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        // 关联 segmentedView
        segmentedView.listContainer = pagingView.listContainerView

        // 设置背景色
        pagingView.mainTableView.backgroundColor = .clear
        pagingView.listContainerView.backgroundColor = .clear

        // 完全禁用主表格的滚动
        pagingView.mainTableView.isScrollEnabled = false
        pagingView.mainTableView.bounces = false
        pagingView.mainTableView.alwaysBounceVertical = false
        pagingView.pinSectionHeaderVerticalOffset = 0

        os_log("[📔][HomeNewViewController] setupPagingView end", log: .default, type: .debug)
    }

    private func updateSegmentedTitles() {
        os_log("[📔][HomeNewViewController] updateSegmentedTitles start", log: .default, type: .debug)
        let titles = tabStore.availableTabs.map { tab in
            switch tab.metaData.title {
            case let .text(title):
                title
            case let .localized(key):
                NSLocalizedString(key, comment: "")
            }
        }
        os_log("[📔][HomeNewViewController] titles: %{public}@", log: .default, type: .debug, titles)
        segmentedDataSource.titles = titles
        segmentedView.reloadData()
        os_log("[📔][HomeNewViewController] updateSegmentedTitles end", log: .default, type: .debug)
    }

    // - Public Methods

    func updateSelectedTab(_ index: Int) {
        segmentedView.defaultSelectedIndex = index
    }

    func updateAppBarVisibility(_: Bool?) {
        // 这里可以根据需要实现 AppBar 的显示/隐藏逻辑
        // 如果不需要这个功能，可以留空
    }
}

// - JXPagingViewDelegate

extension HomeNewViewController: JXPagingViewDelegate {
    func tableHeaderViewHeight(in _: JXPagingView) -> Int {
        0
    }

    func tableHeaderView(in _: JXPagingView) -> UIView {
        UIView()
    }

    func heightForPinSectionHeader(in _: JXPagingView) -> Int {
        // 获取安全区域高度
        let window = UIApplication.shared.windows.first { $0.isKeyWindow }
        let safeAreaTop = window?.safeAreaInsets.top ?? 0

        // 返回 segmentedView 高度(50) + 安全区域高度
        return Int(0 + 44)
    }

    func viewForPinSectionHeader(in _: JXPagingView) -> UIView {
        // 创建一个容器视图，包含 segmentedView
        let containerView = UIView()
        // containerView.backgroundColor = .systemPink // 容器视图背景色
        containerView.isUserInteractionEnabled = true

        // 设置容器视图的 frame，固定高度为44
        containerView.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 44)

        let avatarWidth: CGFloat = 40
        let settingsWidth: CGFloat = 40

        // 调整 segmentedView 的位置和高度
        // x 从头像宽度开始，宽度 = 总宽度 - 头像宽度 - 设置按钮宽度
        segmentedView.frame = CGRect(x: avatarWidth,
                                     y: 0,
                                     width: view.bounds.width - avatarWidth - settingsWidth,
                                     height: 44)
        // segmentedView.backgroundColor = .systemYellow // segmentedView 背景色

        // 创建左边的头像按钮容器
        let avatarContainer = UIView(frame: CGRect(x: 0, y: 0, width: avatarWidth, height: 44))
        avatarContainer.isUserInteractionEnabled = true
        // avatarContainer.backgroundColor = .systemGreen // 头像容器背景色

        // 创建头像按钮，居中显示
        let avatarButtonSize: CGFloat = 28  // 头像大小
        let avatarButton = UIButton(frame: CGRect(
            x: (avatarWidth - avatarButtonSize) / 2,  // 水平居中
            y: (44 - avatarButtonSize) / 2,           // 垂直居中
            width: avatarButtonSize,
            height: avatarButtonSize
        ))
        avatarButton.backgroundColor = .clear
        if let user = tabStore.currentUser {
            // 设置用户头像
            let hostingController = UIHostingController(rootView: 
                UserAvatar(data: user.avatar, size: avatarButtonSize)
                    .clipShape(Circle())
            )
            hostingController.view.frame = avatarButton.bounds
            hostingController.view.backgroundColor = .clear
            hostingController.view.isUserInteractionEnabled = false  // 禁用 SwiftUI 视图的交互
            avatarButton.addSubview(hostingController.view)
        } else {
            // 设置默认头像
            let hostingController = UIHostingController(rootView: 
                userAvatarPlaceholder(size: avatarButtonSize)
                    .clipShape(Circle())
            )
            hostingController.view.frame = avatarButton.bounds
            hostingController.view.backgroundColor = .clear
            hostingController.view.isUserInteractionEnabled = false  // 禁用 SwiftUI 视图的交互
            avatarButton.addSubview(hostingController.view)
        }
        avatarButton.addTarget(self, action: #selector(handleAvatarTap), for: .touchUpInside)

        // 为了调试，添加点击区域可视化
        // #if DEBUG
        // avatarButton.layer.borderWidth = 1
        // avatarButton.layer.borderColor = UIColor.red.cgColor
        // #endif

        // 创建右边的设置按钮容器
        let settingsContainer = UIView(frame: CGRect(x: view.bounds.width - settingsWidth,
                                                     y: 0,
                                                     width: settingsWidth,
                                                     height: 44))
        settingsContainer.isUserInteractionEnabled = true
        // settingsContainer.backgroundColor = .systemOrange // 设置按钮容器背景色

        // 创建设置按钮
        let settingsButton = UIButton(frame: CGRect(x: 0, y: 0, width: settingsWidth, height: 44))
        // settingsButton.backgroundColor = .systemPurple // 设置按钮背景色
        if !(accountType is AccountTypeGuest) {
            settingsButton.setImage(UIImage(systemName: "line.3.horizontal"), for: .normal)
            settingsButton.addTarget(self, action: #selector(handleSettingsTap), for: .touchUpInside)
        } else {
//            settingsButton.setTitle("Login", for: .normal)
//            settingsButton.addTarget(self, action: #selector(handleLoginTap), for: .touchUpInside)
        }
        settingsButton.tintColor = .label

        // 按照正确的层级添加视图
        avatarContainer.addSubview(avatarButton)
        settingsContainer.addSubview(settingsButton)
        containerView.addSubview(segmentedView)
        containerView.addSubview(avatarContainer)
        containerView.addSubview(settingsContainer)

        return containerView
    }

    @objc private func handleAvatarTap() {
        if accountType is AccountTypeGuest {
            // 未登录用户，显示登录界面
            NotificationCenter.default.post(name: NSNotification.Name("ShowLogin"), object: nil)
        } else {
            // 已登录用户，显示设置界面
            NotificationCenter.default.post(name: NSNotification.Name("ShowSettings"), object: nil)
        }
    }

    @objc private func handleSettingsTap() {
        NotificationCenter.default.post(name: NSNotification.Name("ShowTabSettings"), object: nil)
    }

    @objc private func handleLoginTap() {
        NotificationCenter.default.post(name: NSNotification.Name("ShowLogin"), object: nil)
    }

    func numberOfLists(in _: JXPagingView) -> Int {
        tabStore.availableTabs.count
    }

    func pagingView(_: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
        if let existingVC = listViewControllers[index] {
            return existingVC
        }

        let timelineVC = HomeNewTimelineViewController()
        if index < tabStore.availableTabs.count {
            let tab = tabStore.availableTabs[index]
            if let presenter = tabStore.getOrCreatePresenter(for: tab) {
                timelineVC.updatePresenter(presenter)
                timelineVC.configure(with: timelineStore, key: tab.key)
            }
        }
        listViewControllers[index] = timelineVC
        return timelineVC
    }
}

// - JXSegmentedViewDelegate

extension HomeNewViewController: JXSegmentedViewDelegate {
    func segmentedView(_: JXSegmentedView, didSelectedItemAt index: Int) {
        os_log("[📔][HomeNewViewController]选择标签页: index=%{public}d", log: .default, type: .debug, index)

        // 更新当前选中的标签页的presenter
        if index < tabStore.availableTabs.count {
            let selectedTab = tabStore.availableTabs[index]

            // 更新 TabSettingsStore
            tabStore.updateSelectedTab(selectedTab)

            // 获取当前的列表视图并更新其 presenter
            if let currentList = pagingView.validListDict[index] as? HomeNewTimelineViewController,
               let presenter = tabStore.currentPresenter
            {
                // 更新 timeline presenter
                currentList.updatePresenter(presenter)
            }
        }
    }

    func segmentedView(_: JXSegmentedView, didClickSelectedItemAt index: Int) {
        // 如果点击已选中的标签，可以触发刷新
        if let currentList = pagingView.validListDict[index] as? HomeNewTimelineViewController {
            Task { @MainActor in
                currentList.refresh()
            }
        }
    }
}
