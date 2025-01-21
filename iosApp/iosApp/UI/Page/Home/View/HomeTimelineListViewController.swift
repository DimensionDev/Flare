import SwiftUI
import UIKit

class HomeTimelineListViewController: UIViewController, JXPagingViewListViewDelegate {
    private let hostingController: UIHostingController<TimelineScreen>
    private var scrollCallback: ((UIScrollView) -> Void)?
    private var scrollView: UIScrollView?
    private var lastContentOffset: CGFloat = 0
    
    init(hostingController: UIHostingController<TimelineScreen>) {
        self.hostingController = hostingController
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    deinit {
        scrollCallback = nil
        scrollView = nil
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // 恢复滚动位置
        if let scrollView = scrollView {
            scrollView.setContentOffset(CGPoint(x: 0, y: lastContentOffset), animated: false)
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // 保存滚动位置
        if let scrollView = scrollView {
            lastContentOffset = scrollView.contentOffset.y
        }
    }
    
    private func setupUI() {
        addChild(hostingController)
        view.addSubview(hostingController.view)
        hostingController.didMove(toParent: self)
        
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        // 延迟获取 ScrollView
        DispatchQueue.main.async { [weak self] in
            self?.scrollView = self?.findScrollView(in: self?.hostingController.view)
            // 设置滚动代理
            self?.scrollView?.delegate = self
        }
    }
    
    private func findScrollView(in view: UIView?) -> UIScrollView? {
        // 递归查找 UIScrollView
        if let scrollView = view as? UIScrollView {
            return scrollView
        }
        
        for subview in view?.subviews ?? [] {
            if let scrollView = findScrollView(in: subview) {
                return scrollView
            }
        }
        
        return nil
    }
    
    // MARK: - Public Methods
    
    func scrollToTop() {
        if let scrollView = scrollView {
            scrollView.setContentOffset(.zero, animated: true)
            lastContentOffset = 0
        }
    }
    
    // MARK: - JXPagingViewListViewDelegate
    
    func listView() -> UIView {
        return view
    }
    
    func listScrollView() -> UIScrollView {
        // 如果还没找到 ScrollView，再次尝试查找
        if scrollView == nil {
            scrollView = findScrollView(in: hostingController.view)
        }
        
        // 如果还是没找到，创建一个临时的
        return scrollView ?? {
            let tempScrollView = UIScrollView()
            tempScrollView.alwaysBounceVertical = true
            return tempScrollView
        }()
    }
    
    func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> Void) {
        scrollCallback = callback
    }
}

// MARK: - UIScrollViewDelegate
extension HomeTimelineListViewController: UIScrollViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        // 调用回调
        scrollCallback?(scrollView)
        // 保存滚动位置
        lastContentOffset = scrollView.contentOffset.y
    }
} 