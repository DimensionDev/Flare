import UIKit
import MJRefresh
import JXSegmentedView
import SwiftUI
import shared
import MarkdownUI
import Generated
import os.log
import Kingfisher

extension JXPagingListContainerView: JXSegmentedViewListContainer {}

class ProfileNewRefreshViewController: UIViewController {
    // MARK: - Properties
    private var userInfo: ProfileUserInfo?
    private var state: ProfileState?
    private var selectedTab: Binding<Int>?
    private var horizontalSizeClass: UserInterfaceSizeClass?
    private var appSettings: AppSettings?
    private var toProfileMedia: ((MicroBlogKey) -> Void)?
    private var accountType: AccountType?
    private var userKey: MicroBlogKey?
    private var tabStore: ProfileTabSettingStore?
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
        return button
    }()
    private var lastContentOffset: CGFloat = 0
    private let navigationBarHeight: CGFloat = 44
    private var isNavigationBarHidden = false
    
    // 配置方法
    func configure(
        userInfo: ProfileUserInfo?,
        state: ProfileState,
        selectedTab: Binding<Int>,
        horizontalSizeClass: UserInterfaceSizeClass?,
        appSettings: AppSettings,
        toProfileMedia: @escaping (MicroBlogKey) -> Void,
        accountType: AccountType,
        userKey: MicroBlogKey?,
        tabStore: ProfileTabSettingStore
    ) {
        self.userInfo = userInfo
        self.state = state
        self.selectedTab = selectedTab
        self.horizontalSizeClass = horizontalSizeClass
        self.appSettings = appSettings
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey
        self.tabStore = tabStore
        
        // 确保导航栏已经初始化
        if navigationBar == nil {
            setupNavigationBar()
        }
        
        // 根据是否是自己的 profile 来控制导航栏显示
        let isOwnProfile = userKey == nil
        navigationBar.isHidden = true  // 始终隐藏自定义导航栏
        navigationBar.alpha = 0
        
        // 如果是自己的 profile，显示系统导航栏，隐藏返回按钮
        if isOwnProfile {
            navigationController?.setNavigationBarHidden(false, animated: false)
            segmentedBackButton.isHidden = true
        } else {
               // 默认显示系统导航栏
               // 每次切换tab后，上级就刷新，然后 这边就又有问题了，无解， 此bug 得解决上层刷新问题。
        navigationController?.setNavigationBarHidden(false, animated: false)
        
            // 如果是其他用户的 profile，初始时隐藏系统导航栏和返回按钮
            // navigationController?.setNavigationBarHidden(true, animated: false)
            segmentedBackButton.isHidden = true
        }
    
        // 更新UI
        updateUI()
    }

    private func updateUI() {
        guard let userInfo = userInfo else { return }
        
        // 更新头部视图
        userHeaderView?.configure(with: userInfo)
        
        // 更新标签页
        if let tabStore = tabStore {
            // 从 tabStore.availableTabs 获取标题
            titles = tabStore.availableTabs.map { tab in
                switch tab.metaData.title {
                case .text(let title):
                    return title
                case .localized(let key):
                    return NSLocalizedString(key, comment: "")
                }
            }
            segmentedDataSource.titles = titles
            segmentedView.reloadData()
            
            // 如果有选中的标签，更新选中状态
            if let selectedTab = selectedTab {
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
        
        // 配置头部视图 - 只设置宽度，让高度自适应
        userHeaderView = ProfileNewHeaderView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 0))
        
        // 设置头部视图的回调
        userHeaderView.onAvatarTap = { [weak self] in
            // TODO: 处理头像点击
        }
        
        userHeaderView.onBannerTap = { [weak self] in
            // TODO: 处理banner点击
        }
        
        userHeaderView.onFollowsCountTap = { [weak self] in
            // TODO: 处理关注数点击
        }
        
        userHeaderView.onFansCountTap = { [weak self] in
            // TODO: 处理粉丝数点击
        }
        
        userHeaderView.onMoreMenuTap = { [weak self] in
            guard let self = self,
                  let state = self.state,
                  case .success(let isMe) = onEnum(of: state.isMe),
                  !isMe.data.boolValue else { return }
            
            // 创建更多菜单
            let alertController = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
            
            // 添加屏蔽/取消屏蔽选项
            if case .success(let relation) = onEnum(of: state.relationState),
               case .success(let actions) = onEnum(of: state.actions),
               actions.data.size > 0 {
                for index in 0..<actions.data.size {
                    let item = actions.data.get(index: index)
                    let title = switch onEnum(of: item) {
                    case .block(let block): block.relationState(relation: relation.data) ?
                        NSLocalizedString("unblock", comment: "") :
                        NSLocalizedString("block", comment: "")
                    case .mute(let mute): mute.relationState(relation: relation.data) ?
                        NSLocalizedString("unmute", comment: "") :
                        NSLocalizedString("mute", comment: "")
                    }
                    
                    alertController.addAction(UIAlertAction(title: title, style: .default) { _ in
                        if case .success(let user) = onEnum(of: state.userState) {
                            Task {
                                try? await item.invoke(userKey: user.data.key, relation: relation.data)
                            }
                        }
                    })
                }
            }
            
            // 添加举报选项
            if case .success(let user) = onEnum(of: state.userState) {
                alertController.addAction(UIAlertAction(title: NSLocalizedString("report", comment: ""), style: .destructive) { _ in
                    state.report(userKey: user.data.key)
                })
            }
            
            // 添加取消选项
            alertController.addAction(UIAlertAction(title: NSLocalizedString("cancel", comment: ""), style: .cancel))
            
            // 显示菜单
            self.present(alertController, animated: true)
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
        let lineWidth = 1/UIScreen.main.scale
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
    
    @objc private func handlePanGesture(_ gesture: UIPanGestureRecognizer) {
        let offset = pagingView.mainTableView.contentOffset.y
        refreshControl?.scrollViewDidScroll(withOffset: offset)
        updateNavigationBarVisibility(with: offset)
    }
    
    private func refreshContent() {
         // 模拟刷新过程
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
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
                    if let timelineVC = currentList as? ProfileNewTimelineViewController,
                       let timelineState = timelineVC.presenter?.models.value as? TimelineState {
                        // 触发时间线刷新
                        try? await timelineState.refresh()
                    } else if let mediaVC = currentList as? ProfileMediaViewController,
                              let state = mediaVC.state {
                        // 触发媒体列表刷新
                        try? await state.refresh()
                    }
                    
                    await MainActor.run {
                        self.isHeaderRefreshed = true
                        self.refreshControl?.endRefreshing()
                    }
                }
            }
        }
            
       
    }
    
    private func setupNavigationBar() {
        // 设置导航栏frame
        navigationBar.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: navigationBarHeight)
        
        // 设置导航栏项
        let navigationItem = UINavigationItem()
        let backButton = UIBarButtonItem(image: UIImage(systemName: "chevron.left"), style: .plain, target: self, action: #selector(backButtonTapped))
        navigationItem.leftBarButtonItem = backButton
        navigationBar.items = [navigationItem]
        
        // 添加到视图
        view.addSubview(navigationBar)
        
        // 设置返回按钮事件
        segmentedBackButton.addTarget(self, action: #selector(backButtonTapped), for: .touchUpInside)
    }
    
    @objc private func backButtonTapped() {
        // 返回上一页
        if let navigationController = self.parent?.navigationController {
            navigationController.popViewController(animated: true)
        } else {
            self.dismiss(animated: true)
        }
    }
    
    private func updateNavigationBarVisibility(with offset: CGFloat) {
        let threshold: CGFloat = 100 // 触发导航栏隐藏的阈值
        let isOwnProfile = userKey == nil
        
        // 如果是自己的 profile，不显示返回按钮
        if isOwnProfile {
            return
        }
        
        // 判断滚动方向和位置
        if offset > lastContentOffset && offset > threshold {
            // 向上滚动且超过阈值，显示返回按钮，隐藏系统导航栏
            if !isNavigationBarHidden {
                UIView.animate(withDuration: 0.3) {
                    self.navigationBar.alpha = 0
                    self.segmentedBackButton.isHidden = false
                    self.navigationController?.setNavigationBarHidden(true, animated: true)
                }
                isNavigationBarHidden = true
            }
        } else if offset < lastContentOffset || offset < threshold {
            // 向下滚动或回到顶部，隐藏返回按钮，显示系统导航栏
            if isNavigationBarHidden {
                UIView.animate(withDuration: 0.3) {
                    self.navigationBar.alpha = 0
                    self.segmentedBackButton.isHidden = true
                    self.navigationController?.setNavigationBarHidden(false, animated: true)
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
}

// MARK: - UIGestureRecognizerDelegate
extension ProfileNewRefreshViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
}

// MARK: - JXPagingViewDelegate
extension ProfileNewRefreshViewController: JXPagingViewDelegate {
    func tableHeaderViewHeight(in pagingView: JXPagingView) -> Int {
        // 获取内容高度并添加额外的空间
        let contentHeight = userHeaderView.getContentHeight()
        // 添加额外的 padding 确保内容不会被遮挡
        let totalHeight = contentHeight // 添加 100 点的额外空间
        return Int(totalHeight)
    }
    
    func tableHeaderView(in pagingView: JXPagingView) -> UIView {
        return userHeaderView
    }
    
    func heightForPinSectionHeader(in pagingView: JXPagingView) -> Int {
        // 获取安全区域高度
        let window = UIApplication.shared.windows.first { $0.isKeyWindow }
        let safeAreaTop = window?.safeAreaInsets.top ?? 0
        
        // 返回 tab bar 高度 + 安全区域高度
        return Int(50 + safeAreaTop)
    }
    
    func viewForPinSectionHeader(in pagingView: JXPagingView) -> UIView {
        // 创建一个容器视图，包含安全区域的空白和 segmentedView
        let containerView = UIView()
        containerView.backgroundColor = .systemBackground
        
        // 获取安全区域高度
        let window = UIApplication.shared.windows.first { $0.isKeyWindow }
        let safeAreaTop = window?.safeAreaInsets.top ?? 0
        
        // 调整 segmentedView 的位置，放在安全区域下方
        segmentedView.frame = CGRect(x: 0, y: safeAreaTop, width: view.bounds.width, height: 50)
        containerView.addSubview(segmentedView)
        
        // 添加返回按钮到 segmentedView，初始时隐藏
        segmentedBackButton.frame = CGRect(x: 16, y: safeAreaTop + 10, width: 30, height: 30)
        segmentedBackButton.isHidden = true
        containerView.addSubview(segmentedBackButton)
        
        return containerView
    }
    
    func numberOfLists(in pagingView: JXPagingView) -> Int {
        return tabStore?.availableTabs.count ?? 0
    }
    
    func pagingView(_ pagingView: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
        // 如果已经存在，直接返回
        if let existingVC = listViewControllers[index] {
            return existingVC
        }
        
        guard let tabStore = tabStore,
              index < tabStore.availableTabs.count else {
            let emptyVC = UIViewController()
            return emptyVC as! JXPagingViewListViewDelegate
        }
        
        let tab = tabStore.availableTabs[index]
        
        if tab is FLProfileMediaTabItem {
            let mediaVC = ProfileMediaViewController()
            if let state = state {
                mediaVC.updatePresenter(state)  // 直接传入 ProfileState
            }
            if let appSettings = appSettings {
                mediaVC.configure(with: appSettings)
            }
            // 保存到字典中
            listViewControllers[index] = mediaVC
            return mediaVC
        } else {
            let timelineVC = ProfileNewTimelineViewController()
            if let presenter = tabStore.currentPresenter {
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
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        os_log("[📔][ProfileNewScreen]选择标签页: index=%{public}d", log: .default, type: .debug, index)
        
        // 更新选中状态
        selectedTab?.wrappedValue = index
        
        // 更新当前选中的标签页的presenter
        if let tabStore = tabStore, index < tabStore.availableTabs.count {
            let selectedTab = tabStore.availableTabs[index]
            
            // 直接更新 presenter
            tabStore.updateCurrentPresenter(for: selectedTab)
            
            // 获取当前的列表视图并更新其 presenter
            if let currentList = self.pagingView.validListDict[index] {
                if let timelineVC = currentList as? ProfileNewTimelineViewController,
                   let presenter = tabStore.currentPresenter {
                    // 更新 timeline presenter
                    timelineVC.updatePresenter(presenter)
                } else if let mediaVC = currentList as? ProfileMediaViewController,
                          let state = self.state {
                    // 更新 media presenter
                    mediaVC.updatePresenter(state)
                }
            }
        }
    }
    
    func segmentedView(_ segmentedView: JXSegmentedView, didClickSelectedItemAt index: Int) {
        // 如果点击已选中的标签，可以触发刷新
        if let currentList = pagingView.validListDict[index] as? ProfileNewListViewController {
            currentList.tableView.mj_header?.beginRefreshing()
        }
    }
}

// 头部视图
class ProfileNewHeaderView: UIView {
    private let bannerImageView: UIImageView = {
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        return imageView
    }()
    
    private let blurEffectView: UIVisualEffectView = {
        let blurEffect = UIBlurEffect(style: .light)
        let view = UIVisualEffectView(effect: blurEffect)
        view.alpha = 0 // 初始时不模糊
        return view
    }()
    
    private let avatarView: UIImageView = {
        let imageView = UIImageView()
        imageView.backgroundColor = .gray.withAlphaComponent(0.3)
        imageView.layer.cornerRadius = 40
        imageView.clipsToBounds = true
        imageView.contentMode = .scaleAspectFill
        return imageView
    }()
    
    private let followButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("关注", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.backgroundColor = .systemBlue
        button.layer.cornerRadius = 15
        return button
    }()
    
    private let nameLabel: UILabel = {
        let label = UILabel()
        label.font = .boldSystemFont(ofSize: 20)
        return label
    }()
    
    private let handleLabel: UILabel = {
        let label = UILabel()
        label.textColor = .gray
        label.font = .systemFont(ofSize: 15)
        return label
    }()
    
    private let descriptionLabel: UILabel = {
        let label = UILabel()
        label.numberOfLines = 0
        label.font = .systemFont(ofSize: 15)
        return label
    }()
    
    private let followsCountLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 14)
        label.textColor = .gray
        return label
    }()
    
    private let fansCountLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 14)
        label.textColor = .gray
        return label
    }()
    
    private let markStackView: UIStackView = {
        let stackView = UIStackView()
        stackView.axis = .horizontal
        stackView.spacing = 4
        stackView.alignment = .center
        return stackView
    }()
    
    private let moreButton: UIButton = {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: "ellipsis.circle"), for: .normal)
        button.isHidden = true // 默认隐藏
        return button
    }()
    
    var onFollowsCountTap: (() -> Void)?
    var onFansCountTap: (() -> Void)?
    var onAvatarTap: (() -> Void)?
    var onBannerTap: (() -> Void)?
    var onMoreMenuTap: (() -> Void)?
    
  
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
      
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
 
    
    private func setupUI() {
        backgroundColor = .systemBackground
        
        // Banner with tap gesture
        addSubview(bannerImageView)
        bannerImageView.frame = CGRect(x: 0, y: 0, width: frame.width, height: 150)
        let bannerTap = UITapGestureRecognizer(target: self, action: #selector(bannerTapped))
        bannerImageView.addGestureRecognizer(bannerTap)
        bannerImageView.isUserInteractionEnabled = true
        
        // Blur effect
        addSubview(blurEffectView)
        blurEffectView.frame = bannerImageView.frame
        
        // Avatar with tap gesture
        addSubview(avatarView)
        avatarView.frame = CGRect(x: 16, y: 110, width: 80, height: 80)
        let avatarTap = UITapGestureRecognizer(target: self, action: #selector(avatarTapped))
        avatarView.addGestureRecognizer(avatarTap)
        avatarView.isUserInteractionEnabled = true
        
        // Follow Button
        addSubview(followButton)
        followButton.frame = CGRect(x: frame.width - 100, y: 160, width: 80, height: 30)
        
        // More Menu Button
        moreButton.addTarget(self, action: #selector(moreMenuTapped), for: .touchUpInside)
        addSubview(moreButton)
        moreButton.frame = CGRect(x: frame.width - 44, y: 60, width: 30, height: 30)
        
        // Name Label
        addSubview(nameLabel)
        nameLabel.frame = CGRect(x: 16, y: avatarView.frame.maxY + 10, width: frame.width - 32, height: 24)
        
        // Handle Label and Mark Stack
        addSubview(handleLabel)
        handleLabel.frame = CGRect(x: 16, y: nameLabel.frame.maxY + 4, width: frame.width - 32, height: 20)
        
        addSubview(markStackView)
        markStackView.frame = CGRect(x: handleLabel.frame.maxX + 4, y: nameLabel.frame.maxY + 4, width: 100, height: 20)
        
        // Follows/Fans Count with tap gesture
        addSubview(followsCountLabel)
        followsCountLabel.frame = CGRect(x: 16, y: handleLabel.frame.maxY + 6, width: 100, height: 20)
        let followsTap = UITapGestureRecognizer(target: self, action: #selector(followsCountTapped))
        followsCountLabel.addGestureRecognizer(followsTap)
        followsCountLabel.isUserInteractionEnabled = true
        
        addSubview(fansCountLabel)
        fansCountLabel.frame = CGRect(x: 120, y: handleLabel.frame.maxY + 6, width: 100, height: 20)
        let fansTap = UITapGestureRecognizer(target: self, action: #selector(fansCountTapped))
        fansCountLabel.addGestureRecognizer(fansTap)
        fansCountLabel.isUserInteractionEnabled = true
        
        // Description Label
        addSubview(descriptionLabel)
        descriptionLabel.frame = CGRect(x: 16, y: followsCountLabel.frame.maxY + 10, width: frame.width - 32, height: 0)
    }
    
    private func layoutContent() {
        // 计算description的高度
        let descriptionWidth = frame.width - 32
        let descriptionSize = descriptionLabel.sizeThatFits(CGSize(width: descriptionWidth, height: .greatestFiniteMagnitude))
        
        // 更新description的frame
        descriptionLabel.frame = CGRect(x: 16, y: 280, width: descriptionWidth, height: descriptionSize.height)
        
        // 获取最后一个子视图的底部位置
        var maxY: CGFloat = 0
        for subview in subviews {
            let subviewBottom = subview.frame.maxY
            if subviewBottom > maxY {
                maxY = subviewBottom
            }
        }
        
        // 更新整体高度，添加底部padding
        frame.size.height = maxY + 16 // 16是底部padding
    }
    
    
    
    // 更新Banner拉伸效果
    func updateBannerStretch(withOffset offset: CGFloat) {
        let normalHeight: CGFloat = 150
        let stretchedHeight = normalHeight + max(0, offset)
        
        // 更新Banner图片frame
        bannerImageView.frame = CGRect(x: 0, y: min(0, -offset), width: frame.width, height: stretchedHeight)
        blurEffectView.frame = bannerImageView.frame
        
        // 根据拉伸程度设置模糊效果
        let blurAlpha = min(offset / 100, 0.3)  // 最大模糊度0.3
        blurEffectView.alpha = blurAlpha
    }
    
    func getContentHeight() -> CGFloat {
        return frame.height
    }
    
    func configure(with userInfo: ProfileUserInfo) {
        // 设置用户名
        nameLabel.text = userInfo.profile.name.markdown
        
        // 设置用户handle
        handleLabel.text = "\(userInfo.profile.handle)"
        
        // 设置头像 - 使用 Kingfisher 缓存
        if let url = URL(string: userInfo.profile.avatar) {
            avatarView.kf.setImage(
                with: url,
                options: [
                    .transition(.fade(0.25)),
                    .processor(DownsamplingImageProcessor(size: CGSize(width: 160, height: 160))),
                    .scaleFactor(UIScreen.main.scale),
                    .cacheOriginalImage
                ]
            )
        }
        
        // 设置banner - 使用 Kingfisher 缓存
        if let url = URL(string: userInfo.profile.banner ?? ""),
           !(userInfo.profile.banner ?? "").isEmpty,
           (userInfo.profile.banner ?? "").range(of: "^https?://.*example\\.com.*$", options: .regularExpression) == nil {
            bannerImageView.kf.setImage(
                with: url,
                options: [
                    .transition(.fade(0.25)),
                    .processor(DownsamplingImageProcessor(size: CGSize(width: UIScreen.main.bounds.width * 2, height: 300))),
                    .scaleFactor(UIScreen.main.scale),
                    .cacheOriginalImage
                ]
            ) { result in
                switch result {
                case .success(let imageResult):
                    // 检查图片是否有效
                    if imageResult.image.size.width > 10 && imageResult.image.size.height > 10 {
                        // 图片有效，保持现状
                    } else {
                        // 如果图片无效，使用头像作为背景
                        self.setupDynamicBannerBackground(avatarUrl: userInfo.profile.avatar)
                    }
                case .failure(_):
                    // 加载失败，使用头像作为背景
                    self.setupDynamicBannerBackground(avatarUrl: userInfo.profile.avatar)
                }
            }
        } else {
            // 如果没有banner，使用头像作为背景
            setupDynamicBannerBackground(avatarUrl: userInfo.profile.avatar)
        }
        
        // 设置关注/粉丝数
        followsCountLabel.text = "\(userInfo.followCount) 关注"
        fansCountLabel.text = "\(userInfo.fansCount) 粉丝"
        
        // 更新关注按钮状态
        updateFollowButton(with: userInfo)
        
        // 设置用户标记
        markStackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for mark in userInfo.profile.mark {
            let imageView = UIImageView()
            imageView.tintColor = .gray
            imageView.alpha = 0.6
            
            switch mark {
            case .cat:
                imageView.image = UIImage(systemName: "cat")
            case .verified:
                imageView.image = UIImage(systemName: "checkmark.circle.fill")
            case .locked:
                imageView.image = UIImage(systemName: "lock.fill")
            case .bot:
                imageView.image = UIImage(systemName: "cpu")
            default:
                continue
            }
            
            imageView.frame = CGRect(x: 0, y: 0, width: 16, height: 16)
            markStackView.addArrangedSubview(imageView)
        }
        
        // 更新更多按钮状态
        moreButton.isHidden = userInfo.isMe
        
        // 开始流式布局，从关注/粉丝数下方开始
        var currentY = followsCountLabel.frame.maxY + 10
        
        // 设置描述文本
        if let description = userInfo.profile.description_?.markdown, !description.isEmpty {
            let descriptionView = UIHostingController(
                rootView: Markdown(description)
                    .markdownInlineImageProvider(.emoji)
            )
            addSubview(descriptionView.view)
            descriptionView.view.frame = CGRect(x: 16, y: currentY, width: frame.width - 32, height: 0)
            descriptionView.view.sizeToFit()
            currentY = descriptionView.view.frame.maxY + 16
        }
        
        // 设置用户位置和URL
        if let bottomContent = userInfo.profile.bottomContent {
            switch onEnum(of: bottomContent) {
            case .fields(let data):
                let fieldsView = UserInfoFieldsView(fields: data.fields)
                let hostingController = UIHostingController(rootView: fieldsView)
                hostingController.view.frame = CGRect(x: 16, y: currentY, width: frame.width - 32, height: 0)
                addSubview(hostingController.view)
                hostingController.view.sizeToFit()
                currentY = hostingController.view.frame.maxY + 16
                
            case .iconify(let data):
                let stackView = UIStackView()
                stackView.axis = .horizontal
                stackView.spacing = 8
                stackView.alignment = .center
                
                if let locationValue = data.items[.location] {
                    let locationView = UIHostingController(
                        rootView: Label(
                            title: {
                                Markdown(locationValue.markdown)
                                    .font(.caption2)
                                    .markdownInlineImageProvider(.emoji)
                            },
                            icon: {
                                Image(uiImage: Asset.Image.Attributes.location.image
                                    .withRenderingMode(.alwaysTemplate))
                                    .imageScale(.small)
                            }
                        )
                        .labelStyle(CompactLabelStyle())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(.systemGray6))
                        .cornerRadius(6)
                    )
                    locationView.view.sizeToFit()
                    stackView.addArrangedSubview(locationView.view)
                }
                
                if let urlValue = data.items[.url] {
                    let urlView = UIHostingController(
                        rootView: Label(
                            title: {
                                Markdown(urlValue.markdown)
                                    .font(.caption2)
                                    .markdownInlineImageProvider(.emoji)
                            },
                            icon: {
                                Image(uiImage: Asset.Image.Attributes.globe.image
                                    .withRenderingMode(.alwaysTemplate))
                                    .imageScale(.small)
                            }
                        )
                        .labelStyle(CompactLabelStyle())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(.systemGray6))
                        .cornerRadius(6)
                    )
                    urlView.view.sizeToFit()
                    stackView.addArrangedSubview(urlView.view)
                }
                
                if stackView.arrangedSubviews.count > 0 {
                    addSubview(stackView)
                    stackView.frame = CGRect(x: 16, y: currentY, width: frame.width - 32, height: 30)
                    currentY = stackView.frame.maxY + 16
                }
            }
        }
        
        // 更新视图总高度
        frame.size.height = currentY
    }
    
    private func setupDynamicBannerBackground(avatarUrl: String?) {
        guard let avatarUrl = avatarUrl, let url = URL(string: avatarUrl) else { return }
        
        bannerImageView.kf.setImage(
            with: url,
            options: [
                .transition(.fade(0.25)),
                .processor(DownsamplingImageProcessor(size: CGSize(width: UIScreen.main.bounds.width * 2, height: 300))),
                .scaleFactor(UIScreen.main.scale),
                .cacheOriginalImage
            ]
        ) { [weak self] _ in
            self?.blurEffectView.alpha = 0.7 // 增加模糊效果
        }
    }
    
    private func updateFollowButton(with userInfo: ProfileUserInfo) {
        // 根据用户关系更新关注按钮状态
        if userInfo.isMe {
            followButton.isHidden = true
        } else {
            followButton.isHidden = false
            if let relation = userInfo.relation {
                let title = if relation.blocking {
                    NSLocalizedString("profile_header_button_blockedblocked", comment: "")
                } else if relation.following {
                    NSLocalizedString("profile_header_button_following", comment: "")
                } else if relation.hasPendingFollowRequestFromYou {
                    NSLocalizedString("profile_header_button_requested", comment: "")
                } else {
                    NSLocalizedString("profile_header_button_follow", comment: "")
                }
                followButton.setTitle(title, for: .normal)
            }
        }
    }
    
    @objc private func avatarTapped() {
        onAvatarTap?()
    }
    
    @objc private func bannerTapped() {
        onBannerTap?()
    }
    
    @objc private func followsCountTapped() {
        onFollowsCountTap?()
    }
    
    @objc private func fansCountTapped() {
        onFansCountTap?()
    }
    
    @objc private func moreMenuTapped() {
        onMoreMenuTap?()
    }
} 
