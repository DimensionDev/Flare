import SwiftUI

class PagingListContainerViewController: UIViewController, JXPagingViewListViewDelegate {
    private var scrollCallback: ((UIScrollView) -> Void)?
    private let hostingController: UIHostingController<AnyView>
    private var scrollView: UIScrollView?

    init(view: AnyView) {
        hostingController = UIHostingController(rootView: view)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupHostingController()
        findScrollView()
    }

    private func setupHostingController() {
        addChild(hostingController)
        view.addSubview(hostingController.view)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        hostingController.didMove(toParent: self)
    }

    private func findScrollView() {
        // 递归查找 UIScrollView
        func findScrollView(in view: UIView) -> UIScrollView? {
            if let scrollView = view as? UIScrollView {
                return scrollView
            }
            for subview in view.subviews {
                if let scrollView = findScrollView(in: subview) {
                    return scrollView
                }
            }
            return nil
        }

        // 在视图层次结构中查找 UIScrollView
        if let foundScrollView = findScrollView(in: hostingController.view) {
            scrollView = foundScrollView
            // 设置滚动回调
            scrollView?.delegate = self
        }
    }

    // MARK: - JXPagingViewListViewDelegate

    func listView() -> UIView {
        view
    }

    func listScrollView() -> UIScrollView {
        // 如果找到了滚动视图就返回，否则返回一个空的滚动视图
        scrollView ?? UIScrollView()
    }

    func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> Void) {
        scrollCallback = callback
    }
}

// MARK: - UIScrollViewDelegate

extension PagingListContainerViewController: UIScrollViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        scrollCallback?(scrollView)
    }
}

struct PagingListContainerView<Content: View>: UIViewControllerRepresentable {
    let content: Content

    func makeUIViewController(context _: Context) -> PagingListContainerViewController {
        PagingListContainerViewController(view: AnyView(content))
    }

    func updateUIViewController(_: PagingListContainerViewController, context _: Context) {
        // 更新内容
    }
}
