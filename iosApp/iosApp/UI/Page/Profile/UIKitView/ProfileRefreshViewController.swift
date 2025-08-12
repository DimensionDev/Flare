import Generated
import JXPagingView
import JXSegmentedView
import Kingfisher
import MarkdownUI
import MJRefresh
import os.log
import shared
import SwiftUI
import UIKit

extension JXPagingListContainerView: JXSegmentedViewListContainer {}

class ProfileNewRefreshViewController: UIViewController {
    private var theme: FlareTheme?
    private var userInfo: ProfileUserInfo?
    private var state: ProfileNewState?
    private var selectedTab: Binding<Int>?
    private var isShowAppBar: Binding<Bool?>?
    private var horizontalSizeClass: UserInterfaceSizeClass?
    private var appSettings: AppSettings?
    private var accountType: AccountType?
    private var userKey: MicroBlogKey?
    private var tabStore: ProfileTabSettingStore?
    private var mediaPresenterWrapper: ProfileMediaPresenterWrapper?
    private var presenterWrapper: ProfilePresenterWrapper?
    private var listViewControllers: [Int: JXPagingViewListViewDelegate] = [:]
    private var themeObserver: NSObjectProtocol?

    var pagingView: JXPagingView!
    var userHeaderView: ProfileNewHeaderView!
    var segmentedView: JXSegmentedView!
    var segmentedDataSource: JXSegmentedTitleDataSource!
    var isHeaderRefreshed = false
    private var titles: [String] = []
    private var refreshControl: ProfileStretchRefreshControl?

    private var navigationBar: UINavigationBar = {
        let nav = UINavigationBar()
        return nav
    }()

    private var lastContentOffset: CGFloat = 0
    private let navigationBarHeight: CGFloat = 44
    private var isNavigationBarHidden = false

    private static let BANNER_HEIGHT: CGFloat = 200
    private var isAppBarTitleVisible = false

    private var _cachedSafeAreaTop: CGFloat?

    func configure(
        userInfo: ProfileUserInfo?,
        state: ProfileNewState,
        selectedTab: Binding<Int>,
        isShowAppBar: Binding<Bool?>,
        horizontalSizeClass: UserInterfaceSizeClass?,
        appSettings: AppSettings,
        accountType: AccountType,
        userKey: MicroBlogKey?,
        tabStore: ProfileTabSettingStore,
        mediaPresenterWrapper: ProfileMediaPresenterWrapper,
        presenterWrapper: ProfilePresenterWrapper,
        theme: FlareTheme
    ) {
        self.userInfo = userInfo
        self.state = state
        self.selectedTab = selectedTab
        self.isShowAppBar = isShowAppBar
        self.horizontalSizeClass = horizontalSizeClass
        self.appSettings = appSettings
        self.accountType = accountType
        self.userKey = userKey
        self.tabStore = tabStore
        self.mediaPresenterWrapper = mediaPresenterWrapper
        self.presenterWrapper = presenterWrapper
        self.theme = theme

        setupThemeObserver()

        let isOwnProfile = userKey == nil

        if isOwnProfile {
            // 自己的Profile：根据原有逻辑控制AppBar
            if let showAppBar = isShowAppBar.wrappedValue {
                navigationController?.setNavigationBarHidden(!showAppBar, animated: false)
            } else {
                // 初始状态，显示导航栏
                navigationController?.setNavigationBarHidden(false, animated: false)
                isShowAppBar.wrappedValue = true
            }
        } else {
            // 其他用户Profile：AppBar永远显示
            navigationController?.setNavigationBarHidden(false, animated: false)

            isShowAppBar.wrappedValue = true
        }

        // 🔑 设置导航按钮
        setupNavigationButtons(isOwnProfile: isOwnProfile)

        // 更新UI
        updateUI()

        // 配置头部视图
        if let userInfo {
            userHeaderView?.configure(with: userInfo, state: state, theme: theme)

            // 设置关注按钮回调
            userHeaderView?.onFollowClick = { [weak self] relation in
                os_log("[📔][ProfileRefreshViewController]点击关注按钮: userKey=%{public}@", log: .default, type: .debug, userInfo.profile.key.description)
                state.follow(userKey: userInfo.profile.key, data: relation)
            }
        }

        if !isOwnProfile {
            navigationController?.navigationBar.alpha = 1.0
        }
    }

    private func updateUI() {
        guard let userInfo else { return }

        // 更新头部视图
        userHeaderView?.configure(with: userInfo)

        // 更新标签页
        if let tabStore {
            // 从 tabStore.availableTabs 获取标题
            titles = tabStore.availableTabs.map { tab in
                switch tab.metaData.title {
                case let .text(title):
                    title
                case let .localized(key):
                    NSLocalizedString(key, comment: "")
                }
            }
            segmentedDataSource.titles = titles
            segmentedView.reloadData()

            // 如果有选中的标签，更新选中状态
            if let selectedTab {
                segmentedView.defaultSelectedIndex = selectedTab.wrappedValue
            }

            pagingView.reloadData()
        }
    }

    private var cachedSafeAreaTop: CGFloat {
        if let cached = _cachedSafeAreaTop {
            return cached
        }
        let window = UIApplication.shared.windows.first { $0.isKeyWindow }
        let safeAreaTop = window?.safeAreaInsets.top ?? 0
        _cachedSafeAreaTop = safeAreaTop
        return safeAreaTop
    }

    private func clearSafeAreaCache() {
        _cachedSafeAreaTop = nil
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)

        // Clear safe area cache when device orientation changes
        coordinator.animate(alongsideTransition: nil) { _ in
            self.clearSafeAreaCache()
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        // 设置导航栏
        setupNavigationBar()

        // 初始时显示系统导航栏，隐藏自定义导航栏和返回按钮
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationBar.alpha = 0
        isNavigationBarHidden = false

        // 允许系统返回手势
        navigationController?.interactivePopGestureRecognizer?.isEnabled = true

        // 配置头部视图 - 只设置宽度，让高度自适应
        userHeaderView = ProfileNewHeaderView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 0))

        // 新的配置代码
        if let userInfo {
            userHeaderView?.configure(with: userInfo, state: state, theme: theme)
        }

        // 配置分段控制器
        segmentedView = JXSegmentedView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 50))
        segmentedDataSource = JXSegmentedTitleDataSource()
        segmentedDataSource.titles = titles // 使用动态titles
        segmentedDataSource.titleNormalColor = .gray
        segmentedDataSource.titleSelectedColor = .label
        segmentedDataSource.titleNormalFont = .systemFont(ofSize: 15)
        segmentedDataSource.titleSelectedFont = .systemFont(ofSize: 15)
        segmentedDataSource.isTitleColorGradientEnabled = true
        segmentedView.dataSource = segmentedDataSource

        // 添加选中回调
        segmentedView.delegate = self

        let indicator = JXSegmentedIndicatorLineView()
        indicator.indicatorColor = theme != nil ? UIColor(theme!.tintColor) : .systemBlue
        indicator.indicatorWidth = 30
        segmentedView.indicators = [indicator]

        // 添加底部分割线
        let lineWidth = 1 / UIScreen.main.scale
        let bottomLineView = UIView()
        bottomLineView.backgroundColor = .separator
        bottomLineView.frame = CGRect(x: 0, y: segmentedView.bounds.height - lineWidth, width: segmentedView.bounds.width, height: lineWidth)
        bottomLineView.autoresizingMask = .flexibleWidth
        segmentedView.addSubview(bottomLineView)

        // 配置PagingView
        pagingView = JXPagingView(delegate: self)
        view.addSubview(pagingView)

        // 关联segmentedView和pagingView
        segmentedView.listContainer = pagingView.listContainerView

        // 配置刷新控制器
        setupRefreshControl()

        // 添加滚动监听
        addScrollObserver()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        pagingView.frame = view.bounds
    }

    private func setupRefreshControl() {
        let refreshControl = ProfileStretchRefreshControl()
        refreshControl.headerView = userHeaderView
        refreshControl.refreshHandler = { [weak self] in
            self?.refreshContent()
        }
        userHeaderView.addSubview(refreshControl)
        refreshControl.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 150)
        self.refreshControl = refreshControl
    }

    private func addScrollObserver() {
        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePanGesture(_:)))
        pagingView.mainTableView.addGestureRecognizer(panGesture)
        panGesture.delegate = self
    }

    @objc private func handlePanGesture(_: UIPanGestureRecognizer) {
        let offset = pagingView.mainTableView.contentOffset.y
        refreshControl?.scrollViewDidScroll(withOffset: offset)

        let isOwnProfile = userKey == nil
        if !isOwnProfile {
            updateNavigationBarVisibility(with: offset)
        }
    }

    private func refreshContent() {
        let workItem = DispatchWorkItem {
            self.isHeaderRefreshed = true
            self.refreshControl?.endRefreshing()
            self.pagingView.reloadData()

            if let currentList = self.pagingView.validListDict[self.segmentedView.selectedIndex] as? ProfileNewListViewController {
                currentList.headerRefresh()
            }
            Task {
                if let currentList = self.pagingView.validListDict[self.segmentedView.selectedIndex] {
                    if let timelineVC = currentList as? TimelineViewController,
                       let presenterWrapper = self.presenterWrapper,
                       let timelineViewModel = presenterWrapper.getTimelineViewModel()
                    {
                        // 使用TimelineViewModel的现代化刷新方法
                        await timelineViewModel.handleRefresh()
                        os_log("[📔][ProfileRefreshViewController] TimelineViewModel刷新完成", log: .default, type: .debug)
                    } else if let timelineVC = currentList as? TimelineViewController,
                              let timelineState = timelineVC.presenter?.models.value as? TimelineState
                    {
                        // 降级到传统刷新方法（如果TimelineViewModel未初始化）
                        try? await timelineState.refresh()
                        os_log("[📔][ProfileRefreshViewController] 使用传统TimelineState刷新", log: .default, type: .debug)
                    } else if let mediaVC = currentList as? ProfileMediaViewController,
                              let mediaPresenterWrapper = self.mediaPresenterWrapper,
                              case let .success(data) = onEnum(of: mediaPresenterWrapper.presenter.models.value.mediaState)
                    {
                        // 触发媒体列表刷新
                        data.retry()
                        os_log("[📔][ProfileRefreshViewController] 媒体列表刷新完成", log: .default, type: .debug)
                    }

                    await MainActor.run {
                        self.isHeaderRefreshed = true
                        self.refreshControl?.endRefreshing()
                    }
                }
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: workItem)
    }

    private func setupNavigationBar() {
        // 设置导航栏frame
        navigationBar.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: navigationBarHeight)

        // 设置导航栏项
        let navigationItem = UINavigationItem()
        let backButton = UIBarButtonItem(image: UIImage(systemName: "chevron.left"), style: .plain, target: self, action: #selector(backButtonTapped))
        navigationItem.leftBarButtonItem = backButton

        // 添加更多按钮到导航栏右侧
        let moreButton = UIBarButtonItem(image: UIImage(systemName: "ellipsis.circle"), style: .plain, target: self, action: #selector(handleMoreMenuTap))
        navigationItem.rightBarButtonItem = moreButton

        navigationBar.items = [navigationItem]

        // 添加到视图
        view.addSubview(navigationBar)

        // 初始时隐藏 moreButton
        moreButton.isEnabled = false
        navigationItem.rightBarButtonItem = nil
    }

    @objc private func backButtonTapped() {
        // 返回上一页
        if let navigationController = parent?.navigationController {
            navigationController.popViewController(animated: true)
        } else {
            dismiss(animated: true)
        }
    }

    @objc private func handleMoreMenuTap() {
        os_log("[ProfileRefreshViewController] More menu button tapped", log: .default, type: .debug)

        guard let state,
              case let .success(isMe) = onEnum(of: state.isMe),
              !isMe.data.boolValue else { return }

        let alertController = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)

        if case let .success(user) = onEnum(of: state.userState) {
            alertController.addAction(UIAlertAction(title: NSLocalizedString("report", comment: ""), style: .destructive) { [weak self] _ in
                Task {
                    do {
                        try await state.report(userKey: user.data.key)
                        await MainActor.run {
                            ToastView(icon: UIImage(systemName: "checkmark.circle"), message: "Success").show()
                        }
                    } catch {
                        await MainActor.run {
                            ToastView(icon: UIImage(systemName: "exclamationmark.circle"), message: "Failed").show()
                        }
                    }
                }
            })
        }

        alertController.addAction(UIAlertAction(title: NSLocalizedString("cancel", comment: ""), style: .cancel))

        present(alertController, animated: true)
    }

    private func updateNavigationBarVisibility(with offset: CGFloat) {
        if offset > Self.BANNER_HEIGHT, !isAppBarTitleVisible {
            isAppBarTitleVisible = true
            UIView.animate(withDuration: 0.25) {
                self.navigationController?.navigationBar.alpha = 0.9
            }
            updateAppBarTitle(showUserName: true)
        } else if offset <= Self.BANNER_HEIGHT, isAppBarTitleVisible {
            isAppBarTitleVisible = false
            UIView.animate(withDuration: 0.25) {
                self.navigationController?.navigationBar.alpha = 1.0
            }
            updateAppBarTitle(showUserName: false)
        }

        lastContentOffset = offset
    }

    private func updateAppBarTitle(showUserName: Bool) {
        let isOwnProfile = userKey == nil

        // Only process title for other user profiles
        guard !isOwnProfile else { return }

        if showUserName {
            // Show user name title
            if let userInfo {
                let displayName = userInfo.profile.name.raw.isEmpty ? userInfo.profile.handle : userInfo.profile.name.raw
                navigationController?.navigationBar.topItem?.title = displayName
            }
        } else {
            // Hide title
            navigationController?.navigationBar.topItem?.title = nil
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let isOwnProfile = userKey == nil

        //  AppBar永远显示
        if isOwnProfile {
            navigationController?.setNavigationBarHidden(false, animated: animated)
        } else {
            navigationController?.setNavigationBarHidden(false, animated: animated)
            navigationController?.navigationBar.alpha = 1.0
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)

        // 离开页面时重置状态，不然 详情页会导致没appbar
        isShowAppBar?.wrappedValue = true
 
        // 确保系统导航栏状态正确
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    deinit {
        cleanupListViewControllers()

        if let themeObserver {
            NotificationCenter.default.removeObserver(themeObserver)
        }
    }

    private func cleanupListViewControllers() {
        listViewControllers.removeAll()
    }

    private func setupNavigationButtons(isOwnProfile: Bool) {
        if isOwnProfile {
            // 自己的Profile：清除所有导航按钮
            navigationController?.navigationBar.topItem?.leftBarButtonItem = nil
            navigationController?.navigationBar.topItem?.rightBarButtonItem = nil
        } else {
            // 其他用户Profile：只设置更多按钮，使用系统默认返回按钮
            navigationController?.navigationBar.topItem?.leftBarButtonItem = nil // 使用系统默认返回按钮
            let moreButton = UIBarButtonItem(image: UIImage(systemName: "ellipsis.circle"), style: .plain, target: self, action: #selector(handleMoreMenuTap))
            navigationController?.navigationBar.topItem?.rightBarButtonItem = moreButton
        }
    }

    @objc private func handleBackButtonTap() {
        os_log("[📔][ProfileRefreshViewController]点击返回按钮", log: .default, type: .debug)

        // 在返回前重置导航状态 // 离开页面时重置状态，不然 详情页会导致没appbar
        isShowAppBar?.wrappedValue = true
 
        // 确保导航栏可见
        navigationController?.setNavigationBarHidden(false, animated: true)

        // 执行返回操作
        navigationController?.popViewController(animated: true)
    }

    private func setupThemeObserver() {
        if let existingObserver = themeObserver {
            NotificationCenter.default.removeObserver(existingObserver)
        }

        themeObserver = NotificationCenter.default.addObserver(
            forName: NSNotification.Name("FlareThemeDidChange"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.applyCurrentTheme()
        }
        applyCurrentTheme()
    }

    private func applyCurrentTheme() {
        guard let theme else { return }

        // 应用主题到视图控制器的主视图
        view.backgroundColor = UIColor(theme.primaryBackgroundColor)

        // 应用主题到 headerView
        userHeaderView?.theme = theme
        userHeaderView?.applyTheme()

        // 应用主题到 segmentedView
        segmentedDataSource.titleSelectedColor = UIColor(theme.labelColor)
        if let indicators = segmentedView.indicators as? [JXSegmentedIndicatorLineView] {
            for indicator in indicators {
                indicator.indicatorColor = UIColor(theme.tintColor)
            }
        }
        segmentedView.backgroundColor = UIColor(theme.primaryBackgroundColor)

        // 应用主题到所有列表视图控制器
        for (_, listVC) in listViewControllers {
            if let timelineVC = listVC as? TimelineViewController {
                timelineVC.view.backgroundColor = UIColor(theme.primaryBackgroundColor)
            } else if let mediaVC = listVC as? ProfileMediaViewController {
                mediaVC.view.backgroundColor = UIColor(theme.primaryBackgroundColor)
            }
        }
    }
}

extension ProfileNewRefreshViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(_: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith _: UIGestureRecognizer) -> Bool {
        true
    }
}

extension ProfileNewRefreshViewController: JXPagingViewDelegate {
    func tableHeaderViewHeight(in _: JXPagingView) -> Int {
        // 获取内容高度并添加额外的空间
        let contentHeight = userHeaderView.getContentHeight()
        // 添加额外的 padding 确保内容不会被遮挡
        let totalHeight = contentHeight // 添加 100 点的额外空间
        return Int(totalHeight)
    }

    func tableHeaderView(in _: JXPagingView) -> UIView {
        userHeaderView
    }

    func heightForPinSectionHeader(in _: JXPagingView) -> Int {
        let safeAreaTop = cachedSafeAreaTop

        // 布局常量
        let navigationBarHeight: CGFloat = 44 // AppBar标准高度
        let tabBarHeight: CGFloat = 50 // TabBar高度

        let isOwnProfile = userKey == nil

        if isOwnProfile {
            // Own profile: SafeArea + TabBar
            return Int(safeAreaTop + tabBarHeight)
        } else {
            // Other user profile: SafeArea + AppBar + TabBar
            return Int(safeAreaTop + navigationBarHeight + tabBarHeight)
        }
    }

    func viewForPinSectionHeader(in _: JXPagingView) -> UIView {
        let containerView = UIView()
        containerView.backgroundColor = .systemBackground
        containerView.isUserInteractionEnabled = true

        // Use cached safe area for performance optimization
        let safeAreaTop = cachedSafeAreaTop

        let navigationBarHeight: CGFloat = 44
        let tabBarHeight: CGFloat = 50

        let isOwnProfile = userKey == nil

        let tabBarY: CGFloat = if isOwnProfile {
            safeAreaTop
        } else {
            safeAreaTop + navigationBarHeight - 18
        }

        // 调整 segmentedView 的位置
        segmentedView.frame = CGRect(x: 0, y: tabBarY, width: view.bounds.width, height: tabBarHeight)

        // 创建一个按钮容器，确保它在 segmentedView 之上
        let buttonContainer = UIView(frame: CGRect(x: 0, y: 0, width: 80, height: 50 + safeAreaTop))
        buttonContainer.isUserInteractionEnabled = true
        buttonContainer.backgroundColor = .clear

        containerView.addSubview(segmentedView)
        if let theme {
            containerView.backgroundColor = UIColor(theme.primaryBackgroundColor)
        }
        return containerView
    }

    func numberOfLists(in _: JXPagingView) -> Int {
        tabStore?.availableTabs.count ?? 0
    }

    func pagingView(_: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
        // 如果已经存在，直接返回
        if let existingVC = listViewControllers[index] {
            return existingVC
        }

        guard let tabStore,
              index < tabStore.availableTabs.count
        else {
            let emptyVC = UIViewController()
            return emptyVC as! JXPagingViewListViewDelegate
        }

        let tab = tabStore.availableTabs[index]

        if tab is FLProfileMediaTabItem {
            let mediaVC = ProfileMediaViewController()
            if let mediaPresenterWrapper {
                mediaVC.updateMediaPresenter(presenterWrapper: mediaPresenterWrapper)
            }
            if let appSettings {
                mediaVC.configure(with: appSettings)
            }
            // 应用主题背景色
            if let theme {
                mediaVC.view.backgroundColor = UIColor(theme.primaryBackgroundColor)
            }
            // 保存到字典中
            listViewControllers[index] = mediaVC
            return mediaVC
        } else {
            let timelineVC = TimelineViewController()

            if let presenter = tabStore.currentPresenter {
                os_log("[📔][ProfileNewRefreshViewController] updatePresenter start", log: .default, type: .debug)

                timelineVC.updatePresenter(presenter)
            }
            // 应用主题背景色
            if let theme {
                timelineVC.view.backgroundColor = UIColor(theme.primaryBackgroundColor)
            }
            // 保存到字典中
            listViewControllers[index] = timelineVC
            return timelineVC
        }
    }
}

// 添加 JXSegmentedViewDelegate
extension ProfileNewRefreshViewController: JXSegmentedViewDelegate {
    func segmentedView(_: JXSegmentedView, didSelectedItemAt index: Int) {
        os_log("[📔][ProfileNewScreen]选择标签页: index=%{public}d", log: .default, type: .debug, index)

        // 更新选中状态
        selectedTab?.wrappedValue = index

        // 更新当前选中的标签页的presenter
        if let tabStore, index < tabStore.availableTabs.count {
            let selectedTab = tabStore.availableTabs[index]

            // 直接更新 presenter
            tabStore.updateCurrentPresenter(for: selectedTab)

            // 获取当前的列表视图并更新其 presenter
            if let currentList = pagingView.validListDict[index] {
                if let timelineVC = currentList as? TimelineViewController,
                   let presenter = tabStore.currentPresenter
                {
                    os_log("[📔][ProfileNewRefreshViewController] updatePresenter start", log: .default, type: .debug)

                    // 更新 timeline presenter
                    timelineVC.updatePresenter(presenter)
                } else if let mediaVC = currentList as? ProfileMediaViewController,
                          let mediaPresenterWrapper
                {
                    os_log("[📔][ProfileNewRefreshViewController] setupUI end", log: .default, type: .debug)

                    // 更新 media presenter
                    mediaVC.updateMediaPresenter(presenterWrapper: mediaPresenterWrapper)
                }
            }
        }
    }

    func segmentedView(_: JXSegmentedView, didClickSelectedItemAt index: Int) {
        // 如果点击已选中的标签，可以触发刷新
        if let currentList = pagingView.validListDict[index] as? ProfileNewListViewController {
            currentList.tableView.mj_header?.beginRefreshing()
        }
    }
}

extension ProfileNewRefreshViewController {
    func needsProfileUpdate(
        userInfo: ProfileUserInfo?,
        selectedTab: Int,
        accountType: AccountType,
        userKey: MicroBlogKey?
    ) -> Bool {
        // 1. 检查用户信息是否变化
        let userChanged = self.userInfo?.profile.key.description != userInfo?.profile.key.description

        // 2. 检查选中Tab是否变化
        let tabChanged = self.selectedTab?.wrappedValue != selectedTab

        // 3. 检查账户类型是否变化（更精确的比较）
        let currentAccountKey = (self.accountType as? AccountTypeSpecific)?.accountKey.description ?? String(describing: self.accountType)
        let newAccountKey = (accountType as? AccountTypeSpecific)?.accountKey.description ?? String(describing: accountType)
        let accountChanged = currentAccountKey != newAccountKey

        // 4. 检查用户Key是否变化
        let userKeyChanged = self.userKey?.description != userKey?.description

        // 5. 首次配置检查（如果当前userInfo为nil，说明是首次配置）
        let isFirstConfiguration = self.userInfo == nil && userInfo != nil

        let needsUpdate = userChanged || tabChanged || accountChanged || userKeyChanged || isFirstConfiguration

        if needsUpdate {
            os_log("[ProfileNewRefreshViewController] Update needed: user=%{public}@, tab=%{public}@, account=%{public}@, userKey=%{public}@, first=%{public}@",
                   log: .default, type: .debug,
                   userChanged ? "changed" : "same",
                   tabChanged ? "changed" : "same",
                   accountChanged ? "changed" : "same",
                   userKeyChanged ? "changed" : "same",
                   isFirstConfiguration ? "true" : "false")
        }

        return needsUpdate
    }
}
