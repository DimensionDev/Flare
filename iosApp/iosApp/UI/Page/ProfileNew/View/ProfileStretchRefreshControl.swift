import UIKit

class ProfileStretchRefreshControl: UIControl {
    weak var headerView: ProfileNewHeaderView?
    private let activityIndicator = UIActivityIndicatorView(style: .medium)
    private var isRefreshing: Bool = false
    var refreshHandler: (() -> Void)?
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        activityIndicator.hidesWhenStopped = true
        addSubview(activityIndicator)
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        // 将指示器放在Banner图片的中间
        activityIndicator.center = CGPoint(x: bounds.midX, y: 75) // Banner高度150的一半
    }
    
    func beginRefreshing() {
        guard !isRefreshing else { return }
        isRefreshing = true
        activityIndicator.startAnimating()
        refreshHandler?()
    }
    
    func endRefreshing() {
        isRefreshing = false
        activityIndicator.stopAnimating()
        // 恢复Banner图片大小
        headerView?.updateBannerStretch(withOffset: 0)
    }
    
    func scrollViewDidScroll(withOffset offset: CGFloat) {
        if offset < 0 {
            // 更新Banner拉伸效果
            headerView?.updateBannerStretch(withOffset: abs(offset))
            
            // 如果下拉超过阈值且没有在刷新，开始刷新
            if abs(offset) > 60 && !isRefreshing {
                beginRefreshing()
            }
        }
    }
} 