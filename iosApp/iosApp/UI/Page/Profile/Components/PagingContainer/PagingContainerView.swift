import SwiftUI
import shared

// MARK: - SwiftUI Wrapper
struct PagingContainerView<Content: View>: View {
   let headerView: AnyView
   let pinnedView: AnyView
   let contentView: Content
   
   init(
       @ViewBuilder headerView: () -> some View,
       @ViewBuilder pinnedView: () -> some View,
       @ViewBuilder content: () -> Content
   ) {
       self.headerView = AnyView(headerView())
       self.pinnedView = AnyView(pinnedView())
       self.contentView = content()
   }
   
   var body: some View {
       PagingViewRepresentable(
           headerView: headerView,
           pinnedView: pinnedView,
           contentView: contentView
       )
       .ignoresSafeArea()
   }
}

// MARK: - UIKit Bridge
private struct PagingViewRepresentable<Content: View>: UIViewControllerRepresentable {
   let headerView: AnyView
   let pinnedView: AnyView
   let contentView: Content
   
   func makeUIViewController(context: Context) -> PagingViewController<Content> {
       let controller = PagingViewController<Content>(
           headerView: headerView,
           pinnedView: pinnedView,
           contentView: contentView
       )
       return controller
   }
   
   func updateUIViewController(_ uiViewController: PagingViewController<Content>, context: Context) {
       uiViewController.update(
           headerView: headerView,
           pinnedView: pinnedView,
           contentView: contentView
       )
   }
}

// MARK: - UIKit Implementation
private class PagingViewController<Content: View>: UIViewController, JXPagingViewDelegate {
   private var pagingView: JXPagingView!
   private var headerVC: UIHostingController<AnyView>!
   private var pinnedVC: UIHostingController<AnyView>!
   private var listContainerVC: PagingListContainerViewController!
   
   // 用于存储初始的 frame
   private var initialHeaderFrame: CGRect = .zero
   private var initialPinnedFrame: CGRect = .zero
   
   init(headerView: AnyView, pinnedView: AnyView, contentView: Content) {
       super.init(nibName: nil, bundle: nil)
       setupViewControllers(headerView: headerView, pinnedView: pinnedView, contentView: contentView)
   }
   
   required init?(coder: NSCoder) {
       fatalError("init(coder:) has not been implemented")
   }
   
   override func viewDidLoad() {
       super.viewDidLoad()
       setupPagingView()
   }
   
   override func viewDidLayoutSubviews() {
       super.viewDidLayoutSubviews()
       
       // 存储初始 frame
       if initialHeaderFrame == .zero {
           initialHeaderFrame = headerVC.view.frame
       }
       if initialPinnedFrame == .zero {
           initialPinnedFrame = pinnedVC.view.frame
       }
       
       // 确保 pinnedView 全宽显示
       pinnedVC.view.frame.size.width = view.bounds.width
   }
   
   private func setupViewControllers(headerView: AnyView, pinnedView: AnyView, contentView: Content) {
       headerVC = UIHostingController(rootView: headerView)
       pinnedVC = UIHostingController(rootView: pinnedView)
       listContainerVC = PagingListContainerViewController(view: AnyView(contentView))
       
       // 添加子视图控制器
       addChild(headerVC)
       addChild(pinnedVC)
       addChild(listContainerVC)
       
       // 完成子视图控制器的添加
       headerVC.didMove(toParent: self)
       pinnedVC.didMove(toParent: self)
       listContainerVC.didMove(toParent: self)
       
       // 设置视图背景色
       headerVC.view.backgroundColor = .clear
       pinnedVC.view.backgroundColor = .clear
       listContainerVC.view.backgroundColor = .clear
       
       // 移除 pinnedView 的自动布局约束
       pinnedVC.view.translatesAutoresizingMaskIntoConstraints = true
   }
   
   private func setupPagingView() {
       pagingView = JXPagingView(delegate: self)
       view.addSubview(pagingView)
       pagingView.translatesAutoresizingMaskIntoConstraints = false
       NSLayoutConstraint.activate([
           pagingView.topAnchor.constraint(equalTo: view.topAnchor),
           pagingView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
           pagingView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
           pagingView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
       ])
       
       // 配置分页视图
       pagingView.mainTableView.backgroundColor = .clear
       pagingView.listContainerView.listCellBackgroundColor = .clear
       
       // 禁用主表格的刷新
       pagingView.mainTableView.refreshControl = nil
       pagingView.mainTableView.bounces = false
       
       // 设置内容偏移
       let topSafeArea = view.safeAreaInsets.top
       pagingView.mainTableView.contentInset = UIEdgeInsets(top: topSafeArea, left: 0, bottom: 0, right: 0)
       pagingView.mainTableView.scrollIndicatorInsets = pagingView.mainTableView.contentInset
   }
   
   func update(headerView: AnyView, pinnedView: AnyView, contentView: Content) {
       headerVC.rootView = headerView
       pinnedVC.rootView = pinnedView
       listContainerVC = PagingListContainerViewController(view: AnyView(contentView))
       pagingView.reloadData()
   }
   
   // MARK: - JXPagingViewDelegate
   
   func tableHeaderViewHeight(in pagingView: JXPagingView) -> Int {
       return Int(headerVC.view.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize).height)
   }
   
   func tableHeaderView(in pagingView: JXPagingView) -> UIView {
       return headerVC.view
   }
   
   func heightForPinSectionHeader(in pagingView: JXPagingView) -> Int {
       return Int(pinnedVC.view.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize).height)
   }
   
   func viewForPinSectionHeader(in pagingView: JXPagingView) -> UIView {
       return pinnedVC.view
   }
   
   func numberOfLists(in pagingView: JXPagingView) -> Int {
       return 1  // 因为我们只有一个内容视图
   }
   
   func pagingView(_ pagingView: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
       return listContainerVC
   }
   
   func mainTableViewDidScroll(_ scrollView: UIScrollView) {
       // 获取安全区域高度
       let topSafeArea = view.safeAreaInsets.top
       
       // 计算滚动偏移量（考虑安全区域）
       let offsetY = scrollView.contentOffset.y + topSafeArea
       
       // 处理 pinnedView 的位置和外观
       if offsetY >= initialHeaderFrame.height {
           // 固定在安全区域下方，并添加背景色
           pinnedVC.view.frame.origin.y = topSafeArea
           pinnedVC.view.backgroundColor = UIColor(Colors.Background.swiftUIPrimary)
       } else {
           // 跟随滚动
           pinnedVC.view.frame.origin.y = initialHeaderFrame.height - offsetY
           pinnedVC.view.backgroundColor = .clear
       }
       
       // 添加视差效果
       let headerOffset = min(0, -offsetY / 2)
       headerVC.view.transform = CGAffineTransform(translationX: 0, y: headerOffset)
   }
} 
