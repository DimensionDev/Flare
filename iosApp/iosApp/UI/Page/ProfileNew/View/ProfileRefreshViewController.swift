import UIKit
import MJRefresh
import JXSegmentedView
import SwiftUI
import shared
import MarkdownUI
import Generated

// 让JXPagingListContainerView实现JXSegmentedViewListContainer协议
extension JXPagingListContainerView: JXSegmentedViewListContainer {}

class ProfileNewRefreshViewController: UIViewController {
    // 数据源
    private var userInfo: ProfileUserInfo?
    private var state: ProfileState?
    private var selectedTab: Binding<Int>?
    private var horizontalSizeClass: UserInterfaceSizeClass?
    private var appSettings: AppSettings?
    private var toProfileMedia: ((MicroBlogKey) -> Void)?
    private var accountType: AccountType?
    private var userKey: MicroBlogKey?
    private var tabStore: ProfileTabSettingStore?
    
    var pagingView: JXPagingView!
    var userHeaderView: ProfileNewHeaderView!
    var segmentedView: JXSegmentedView!
    var segmentedDataSource: JXSegmentedTitleDataSource!
    var isHeaderRefreshed = false
    private var titles: [String] = []
    private var refreshControl: ProfileStretchRefreshControl?
    
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
        }
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
        return 50
    }
    
    func viewForPinSectionHeader(in pagingView: JXPagingView) -> UIView {
        return segmentedView
    }
    
    func numberOfLists(in pagingView: JXPagingView) -> Int {
        return titles.count
    }
    
    func pagingView(_ pagingView: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
        let list = ProfileNewListViewController()
        list.isNeedHeader = true
        list.isNeedFooter = true
        
        // 设置不同tab的示例数据
        switch index {
        case 0:
            list.dataSource = (0...20).map { "推文 \($0)" }
        case 1:
            list.dataSource = (0...20).map { "媒体 \($0)" }
        case 2:
            list.dataSource = (0...20).map { "喜欢 \($0)" }
        default:
            break
        }
        
        return list
    }
}

// 添加 JXSegmentedViewDelegate
extension ProfileNewRefreshViewController: JXSegmentedViewDelegate {
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        // 更新选中状态
        selectedTab?.wrappedValue = index
        
        // 更新当前选中的标签页的presenter
        if let tabStore = tabStore, index < tabStore.availableTabs.count {
            let selectedTab = tabStore.availableTabs[index]
            tabStore.updateCurrentPresenter(for: selectedTab)
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
        moreButton.frame = CGRect(x: frame.width - 40, y: 120, width: 30, height: 30)
        
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
        handleLabel.text = "@\(userInfo.profile.handle)"
        
        // 设置头像
        if let url = URL(string: userInfo.profile.avatar) {
            URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
                if let data = data, let image = UIImage(data: data) {
                    DispatchQueue.main.async {
                        self?.avatarView.image = image
                    }
                }
            }.resume()
        }
        
        // 设置banner
        if let url = URL(string: userInfo.profile.banner ?? ""),
           !(userInfo.profile.banner ?? "").isEmpty,
           (userInfo.profile.banner ?? "").range(of: "^https?://.*example\\.com.*$", options: .regularExpression) == nil {
            URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
                if let data = data, let image = UIImage(data: data) {
                    // 检查图片是否有效
                    if image.size.width > 10 && image.size.height > 10 {
                        DispatchQueue.main.async {
                            self?.bannerImageView.image = image
                        }
                    } else {
                        // 如果图片无效，使用头像作为背景
                        self?.setupDynamicBannerBackground(avatarUrl: userInfo.profile.avatar)
                    }
                }
            }.resume()
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
            descriptionLabel.text = description
            let descriptionWidth = frame.width - 32
            let descriptionSize = descriptionLabel.sizeThatFits(CGSize(width: descriptionWidth, height: .greatestFiniteMagnitude))
            descriptionLabel.frame = CGRect(x: 16, y: currentY, width: descriptionWidth, height: descriptionSize.height)
            currentY = descriptionLabel.frame.maxY + 16
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
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            if let data = data, let image = UIImage(data: data) {
                DispatchQueue.main.async {
                    self?.bannerImageView.image = image
                    self?.bannerImageView.contentMode = .scaleAspectFill
                    self?.blurEffectView.alpha = 0.7 // 增加模糊效果
                }
            }
        }.resume()
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
