import UIKit
import MJRefresh
import JXSegmentedView

// 让JXPagingListContainerView实现JXSegmentedViewListContainer协议
extension JXPagingListContainerView: JXSegmentedViewListContainer {}

class ProfileNewRefreshViewController: UIViewController {
    var pagingView: JXPagingView!
    var userHeaderView: ProfileNewHeaderView!
    var segmentedView: JXSegmentedView!
    var segmentedDataSource: JXSegmentedTitleDataSource!
    var isHeaderRefreshed = false
    var titles = ["推文", "媒体", "喜欢"]
    private var refreshControl: ProfileStretchRefreshControl?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.backgroundColor = .systemBackground
        
        // 配置头部视图
        userHeaderView = ProfileNewHeaderView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 300))
        
        // 配置分段控制器
        segmentedView = JXSegmentedView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 50))
        segmentedDataSource = JXSegmentedTitleDataSource()
        segmentedDataSource.titles = titles
        segmentedDataSource.titleNormalColor = .gray
        segmentedDataSource.titleSelectedColor = .label
        segmentedDataSource.titleNormalFont = .systemFont(ofSize: 15)
        segmentedDataSource.titleSelectedFont = .systemFont(ofSize: 15)
        segmentedDataSource.isTitleColorGradientEnabled = true
        segmentedView.dataSource = segmentedDataSource
        
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
        return Int(userHeaderView.getContentHeight())
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
    
    private let avatarView: UIView = {
        let view = UIView()
        view.backgroundColor = .gray.withAlphaComponent(0.3)
        view.layer.cornerRadius = 40
        view.clipsToBounds = true
        return view
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
        label.text = "示例用户"
        label.font = .boldSystemFont(ofSize: 20)
        return label
    }()
    
    private let handleLabel: UILabel = {
        let label = UILabel()
        label.text = "@example"
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
    
    private var randomDescriptions = [
        "这是一个简短的描述。",
        "这是一个较长的描述，包含了更多的内容。我们用它来测试不同高度的情况。",
        "这是一个非常长的描述，包含了大量的内容。我们用它来测试多行文本的情况。这段文字会自动换行，并且会占用更多的空间。这样我们就能测试不同高度的header是否能正常工作。",
        "iOS开发者，热爱Swift和SwiftUI。喜欢探索新技术，分享开发经验。业余时间喜欢写技术博客，参与开源项目。同时也是一个咖啡爱好者，喜欢尝试不同的咖啡豆。",
        "Full Stack Developer | Open Source Contributor | Tech Blogger\n热爱编程，专注于移动开发和跨平台解决方案\n喜欢探索新技术，分享开发经验\n业余时间参与开源项目，写技术博客\n同时也是一个咖啡和摄影爱好者"
    ]
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
        loadBannerImage()
        setRandomDescription()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setRandomDescription() {
        let randomDescription = randomDescriptions.randomElement() ?? ""
        descriptionLabel.text = randomDescription
        layoutContent()
    }
    
    private func setupUI() {
        backgroundColor = .systemBackground
        
        // Banner
        addSubview(bannerImageView)
        bannerImageView.frame = CGRect(x: 0, y: 0, width: frame.width, height: 150)
        
        // Blur effect
        addSubview(blurEffectView)
        blurEffectView.frame = bannerImageView.frame
        
        // Avatar
        addSubview(avatarView)
        avatarView.frame = CGRect(x: 16, y: 110, width: 80, height: 80)
        
        // Follow Button
        addSubview(followButton)
        followButton.frame = CGRect(x: frame.width - 100, y: 160, width: 80, height: 30)
        
        // Name Label
        addSubview(nameLabel)
        nameLabel.frame = CGRect(x: 16, y: 200, width: frame.width - 32, height: 24)
        
        // Handle Label
        addSubview(handleLabel)
        handleLabel.frame = CGRect(x: 16, y: 224, width: frame.width - 32, height: 20)
        
        // Description Label
        addSubview(descriptionLabel)
    }
    
    private func layoutContent() {
        // 计算description的高度
        let descriptionWidth = frame.width - 32
        let descriptionSize = descriptionLabel.sizeThatFits(CGSize(width: descriptionWidth, height: .greatestFiniteMagnitude))
        
        // 更新description的frame
        descriptionLabel.frame = CGRect(x: 16, y: 250, width: descriptionWidth, height: descriptionSize.height)
        
        // 更新整体高度
        let totalHeight = 250 + descriptionSize.height + 16 // 16是底部padding
        frame.size.height = totalHeight
    }
    
    private func loadBannerImage() {
        // 加载示例banner图片
        if let url = URL(string: "https://pbs.twimg.com/profile_banners/426425493/1688198987/1500x500") {
            URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
                if let data = data, let image = UIImage(data: data) {
                    DispatchQueue.main.async {
                        self?.bannerImageView.image = image
                    }
                }
            }.resume()
        }
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
} 
