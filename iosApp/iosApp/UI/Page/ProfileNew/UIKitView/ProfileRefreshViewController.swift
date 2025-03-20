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
    // - Properties

    private var userInfo: ProfileUserInfo?
    private var state: ProfileNewState?
    private var selectedTab: Binding<Int>?
    private var isShowAppBar: Binding<Bool?>?
    private var isShowsegmentedBackButton: Binding<Bool>?
    private var horizontalSizeClass: UserInterfaceSizeClass?
    private var appSettings: AppSettings?
    private var toProfileMedia: ((MicroBlogKey) -> Void)?
    private var accountType: AccountType?
    private var userKey: MicroBlogKey?
    private var tabStore: ProfileTabSettingStore?
    private var mediaPresenterWrapper: ProfileMediaPresenterWrapper?
    private var listViewControllers: [Int: JXPagingViewListViewDelegate] = [:]

    // UI Components
    var pagingView: JXPagingView!
    var userHeaderView: ProfileNewHeaderView!
    var segmentedView: JXSegmentedView!
    var segmentedDataSource: JXSegmentedTitleDataSource!
    var isHeaderRefreshed = false
    private var titles: [String] = []
    private var refreshControl: ProfileStretchRefreshControl?

    // Navigation Components
    private var navigationBar: UINavigationBar = {
        let nav = UINavigationBar()
        nav.backgroundColor = .systemBackground
        return nav
    }()

    private var segmentedBackButton: UIButton = {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: "chevron.left"), for: .normal)
        button.isHidden = true
        button.isUserInteractionEnabled = true

        // 设置图标在按钮中的内边距，这样图标保持原来大小，但按钮区域更大
        button.contentEdgeInsets = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)

        return button
    }()

    private var lastContentOffset: CGFloat = 0
    private let navigationBarHeight: CGFloat = 44
    private var isNavigationBarHidden = false

    // 配置方法
    func configure(
        userInfo: ProfileUserInfo?,
        state: ProfileNewState,
        selectedTab: Binding<Int>,
        isShowAppBar: Binding<Bool?>,
        isShowsegmentedBackButton: Binding<Bool>,
        horizontalSizeClass: UserInterfaceSizeClass?,
        appSettings: AppSettings,
        toProfileMedia: @escaping (MicroBlogKey) -> Void,
        accountType: AccountType,
        userKey: MicroBlogKey?,
        tabStore: ProfileTabSettingStore,
        mediaPresenterWrapper: ProfileMediaPresenterWrapper
    ) {
        self.userInfo = userInfo
        self.state = state
        self.selectedTab = selectedTab
        self.isShowAppBar = isShowAppBar
        self.isShowsegmentedBackButton = isShowsegmentedBackButton
        self.horizontalSizeClass = horizontalSizeClass
        self.appSettings = appSettings
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey
        self.tabStore = tabStore
        self.mediaPresenterWrapper = mediaPresenterWrapper

        // 根据是否是自己的 profile 来控制导航栏显示
        let isOwnProfile = userKey == nil

        // 根据 isShowAppBar 的状态来控制导航栏显示
        if let showAppBar = isShowAppBar.wrappedValue {
            navigationController?.setNavigationBarHidden(!showAppBar, animated: false)
            updateNavigationButtons(isOwnProfile: isOwnProfile, showAppBar: showAppBar)
        } else {
            // 初始状态，显示导航栏
            navigationController?.setNavigationBarHidden(false, animated: false)
            updateNavigationButtons(isOwnProfile: isOwnProfile, showAppBar: true)
            // 设置初始状态
            isShowAppBar.wrappedValue = true
        }

        // 更新UI
        updateUI()

        // 根据 isShowAppBar 状态更新 isShowsegmentedBackButton
        if let showAppBar = isShowAppBar.wrappedValue {
            isShowsegmentedBackButton.wrappedValue = !showAppBar
        } else {
            isShowsegmentedBackButton.wrappedValue = false
        }

        // 配置头部视图
        if let userInfo {
            userHeaderView?.configure(with: userInfo, state: state)

            // 设置关注按钮回调
            userHeaderView?.onFollowClick = { [weak self] relation in
                os_log("[📔][ProfileRefreshViewController]点击关注按钮: userKey=%{public}@", log: .default, type: .debug, userInfo.profile.key.description)
                state.follow(userKey: userInfo.profile.key, data: relation)
            }
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

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .systemBackground

        // 设置导航栏
        setupNavigationBar()

        // 初始时显示系统导航栏，隐藏自定义导航栏和返回按钮
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationBar.alpha = 0
        segmentedBackButton.isHidden = true
        isNavigationBarHidden = false

        // 允许系统返回手势
        navigationController?.interactivePopGestureRecognizer?.isEnabled = true

        // 配置头部视图 - 只设置宽度，让高度自适应
        userHeaderView = ProfileNewHeaderView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 0))

        // 新的配置代码
        if let userInfo {
            userHeaderView?.configure(with: userInfo, state: state)
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
        indicator.indicatorColor = .systemBlue
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
        updateNavigationBarVisibility(with: offset)
    }

    private func refreshContent() {
        // 模拟刷新过程
        let workItem = DispatchWorkItem {
            self.isHeaderRefreshed = true
            self.refreshControl?.endRefreshing()
            self.pagingView.reloadData()

            // 触发当前列表的刷新
            if let currentList = self.pagingView.validListDict[self.segmentedView.selectedIndex] as? ProfileNewListViewController {
                currentList.headerRefresh()
            }
            Task {
                // 获取当前选中的列表视图
                if let currentList = self.pagingView.validListDict[self.segmentedView.selectedIndex] {
                    if let timelineVC = currentList as? TimelineViewController,
                       let timelineState = timelineVC.presenter?.models.value as? TimelineState {
                        // 触发时间线刷新
                        try? await timelineState.refresh()
                    } else if let mediaVC = currentList as? ProfileMediaViewController,
                              let mediaPresenterWrapper = self.mediaPresenterWrapper,
                              case let .success(data) = onEnum(of: mediaPresenterWrapper.presenter.models.value.mediaState) {
                        // 触发媒体列表刷新
                        data.retry()
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

        // 设置返回按钮事件
        segmentedBackButton.addTarget(self, action: #selector(backButtonTapped), for: .touchUpInside)

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

        // 创建更多菜单
        let alertController = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)

        // 添加屏蔽/取消屏蔽选项
        if case let .success(relation) = onEnum(of: state.relationState),
           case let .success(actions) = onEnum(of: state.actions),
           actions.data.size > 0 {
            for index in 0 ..< actions.data.size {
                let item = actions.data.get(index: index)
                let title = switch onEnum(of: item) {
                case let .block(block): block.relationState(relation: relation.data) ?
                    NSLocalizedString("unblock", comment: "") :
                    NSLocalizedString("block", comment: "")
                case let .mute(mute): mute.relationState(relation: relation.data) ?
                    NSLocalizedString("unmute", comment: "") :
                    NSLocalizedString("mute", comment: "")
                }

                alertController.addAction(UIAlertAction(title: title, style: .default) { [weak self] _ in
                    if case let .success(user) = onEnum(of: state.userState) {
                        Task {
                            try? await item.invoke(userKey: user.data.key, relation: relation.data)
                            // 显示操作成功的 Toast
                            await MainActor.run {
                                if let window = self?.view.window {
                                    let (icon, message) = switch onEnum(of: item) {
                                    case let .block(block):
                                        if block.relationState(relation: relation.data) {
                                            (UIImage(systemName: "checkmark.circle"), NSLocalizedString("user_unblock", comment: ""))
                                        } else {
                                            (UIImage(systemName: "checkmark.circle"), NSLocalizedString("user_block", comment: ""))
                                        }
                                    case let .mute(mute):
                                        if mute.relationState(relation: relation.data) {
                                            (UIImage(systemName: "checkmark.circle"), "Unmuted")
                                        } else {
                                            (UIImage(systemName: "checkmark.circle"), "Muted")
                                        }
                                    }
                                    let toastView = ToastView(icon: icon, message: message)
                                    toastView.show(in: window)
                                }
                            }
                        }
                    }
                })
            }
        }

        // 添加举报选项
        if case let .success(user) = onEnum(of: state.userState) {
            alertController.addAction(UIAlertAction(title: NSLocalizedString("report", comment: ""), style: .destructive) { [weak self] _ in
                state.report(userKey: user.data.key)
                // 显示举报成功的 Toast
                if let window = self?.view.window {
                    let toastView = ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("report", comment: ""))
                    toastView.show(in: window)
                }
            })
        }

        // 添加取消选项
        alertController.addAction(UIAlertAction(title: NSLocalizedString("cancel", comment: ""), style: .cancel))

        // 显示菜单
        present(alertController, animated: true)
    }

    private func updateNavigationBarVisibility(with offset: CGFloat) {
        let threshold: CGFloat = 100 // 触发导航栏隐藏的阈值
        let isOwnProfile = userKey == nil

        // 如果是自己的 profile，不处理导航栏隐藏
        if isOwnProfile {
            return
        }

        // 判断滚动方向和位置
        if offset > lastContentOffset && offset > threshold {
            // 向上滚动且超过阈值，隐藏导航栏，显示返回按钮
            if !isNavigationBarHidden {
                UIView.animate(withDuration: 0.3) {
                    self.isShowAppBar?.wrappedValue = false
                    self.isShowsegmentedBackButton?.wrappedValue = true
                    self.updateNavigationButtons(isOwnProfile: isOwnProfile, showAppBar: false)
                }
                isNavigationBarHidden = true
            }
        } else if offset < lastContentOffset || offset < threshold {
            // 向下滚动或回到顶部，显示导航栏，隐藏返回按钮
            if isNavigationBarHidden {
                UIView.animate(withDuration: 0.3) {
                    self.isShowAppBar?.wrappedValue = true
                    self.isShowsegmentedBackButton?.wrappedValue = false
                    self.updateNavigationButtons(isOwnProfile: isOwnProfile, showAppBar: true)
                }
                isNavigationBarHidden = false
            }
        }

        lastContentOffset = offset
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let isOwnProfile = userKey == nil
        // 如果是自己的 profile，显示系统导航栏
        if isOwnProfile {
            navigationController?.setNavigationBarHidden(false, animated: animated)
        } else {
            // 如果是其他用户的 profile，初始时隐藏系统导航栏
            navigationController?.setNavigationBarHidden(true, animated: animated)
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // 离开时恢复系统导航栏状态
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    deinit {
        cleanupListViewControllers()
    }

    private func cleanupListViewControllers() {
        listViewControllers.removeAll()
    }

    private func updateNavigationButtons(isOwnProfile: Bool, showAppBar: Bool) {
        // 如果是自己的 profile，不显示任何返回按钮
        if isOwnProfile {
            navigationController?.navigationBar.topItem?.leftBarButtonItem = nil
            navigationController?.navigationBar.topItem?.rightBarButtonItem = nil
            segmentedBackButton.isHidden = true
            return
        }

        // 其他用户的 profile
        if showAppBar {
            // 显示系统导航栏时，使用系统默认的返回按钮
            navigationController?.navigationBar.topItem?.leftBarButtonItem = nil // 不设置自定义返回按钮，使用系统默认的
            let moreButton = UIBarButtonItem(image: UIImage(systemName: "ellipsis.circle"), style: .plain, target: self, action: #selector(handleMoreMenuTap))
            navigationController?.navigationBar.topItem?.rightBarButtonItem = moreButton
            segmentedBackButton.isHidden = true
        } else {
            // 隐藏系统导航栏时
            navigationController?.navigationBar.topItem?.leftBarButtonItem = nil
            navigationController?.navigationBar.topItem?.rightBarButtonItem = nil
            segmentedBackButton.isHidden = false
        }
    }

    @objc private func handleBackButtonTap() {
        os_log("[📔][ProfileRefreshViewController]点击返回按钮", log: .default, type: .debug)
        navigationController?.popViewController(animated: true)
    }
}

// - UIGestureRecognizerDelegate

extension ProfileNewRefreshViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(_: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith _: UIGestureRecognizer) -> Bool {
        true
    }
}

// - JXPagingViewDelegate

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
        // 获取安全区域高度
        let window = UIApplication.shared.windows.first { $0.isKeyWindow }
        let safeAreaTop = window?.safeAreaInsets.top ?? 0

        // 返回 tab bar 高度 + 安全区域高度
        return Int(50 + safeAreaTop)
    }

    func viewForPinSectionHeader(in _: JXPagingView) -> UIView {
        // 创建一个容器视图，包含安全区域的空白和 segmentedView
        let containerView = UIView()
        containerView.backgroundColor = .systemBackground
        containerView.isUserInteractionEnabled = true

        // 获取安全区域高度
        let window = UIApplication.shared.windows.first { $0.isKeyWindow }
        let safeAreaTop = window?.safeAreaInsets.top ?? 0

        // 调整 segmentedView 的位置，放在安全区域下方
        segmentedView.frame = CGRect(x: 0, y: safeAreaTop, width: view.bounds.width, height: 50)

        // 创建一个按钮容器，确保它在 segmentedView 之上
        let buttonContainer = UIView(frame: CGRect(x: 0, y: 0, width: 80, height: 50 + safeAreaTop)) // 增加容器宽度
        buttonContainer.isUserInteractionEnabled = true
        buttonContainer.backgroundColor = .clear

        // 设置返回按钮的位置和大小 - 增加点击区域
        segmentedBackButton.frame = CGRect(x: 8, y: safeAreaTop + 5, width: 44, height: 44) // 增加按钮区域
        segmentedBackButton.removeTarget(nil, action: nil, for: .allEvents)
        segmentedBackButton.addTarget(self, action: #selector(handleBackButtonTap), for: .touchUpInside)

        // 按照正确的层级添加视图
        buttonContainer.addSubview(segmentedBackButton)
        containerView.addSubview(segmentedView)
        containerView.addSubview(buttonContainer)

        os_log("[📔][ProfileRefreshViewController]设置返回按钮: frame=%{public}@", log: .default, type: .debug, NSCoder.string(for: segmentedBackButton.frame))

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
            // 保存到字典中
            listViewControllers[index] = mediaVC
            return mediaVC
        } else {
            let timelineVC = TimelineViewController()
//            timelineVC.shouldShowLoadMore = true
            if let presenter = tabStore.currentPresenter {
                os_log("[📔][ProfileNewRefreshViewController] updatePresenter start", log: .default, type: .debug)

                timelineVC.updatePresenter(presenter)
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

        // 发送标签页切换通知
        NotificationCenter.default.post(name: Notification.Name.appBarIndexDidChange, object: index)

        // 更新当前选中的标签页的presenter
        if let tabStore, index < tabStore.availableTabs.count {
            let selectedTab = tabStore.availableTabs[index]

            // 直接更新 presenter
            tabStore.updateCurrentPresenter(for: selectedTab)

            // 获取当前的列表视图并更新其 presenter
            if let currentList = pagingView.validListDict[index] {
                if let timelineVC = currentList as? TimelineViewController,
                   let presenter = tabStore.currentPresenter {
                    os_log("[📔][ProfileNewRefreshViewController] updatePresenter start", log: .default, type: .debug)

                    // 更新 timeline presenter
                    timelineVC.updatePresenter(presenter)
                } else if let mediaVC = currentList as? ProfileMediaViewController,
                          let mediaPresenterWrapper {
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
