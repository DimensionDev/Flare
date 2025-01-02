import SwiftUI
import shared

class ProfileTabListViewController: UIViewController, JXPagingViewListViewDelegate {
    private var hostingController: UIHostingController<AnyView>!
    private var scrollView: UIScrollView?
    private var listViewDidScrollCallback: ((UIScrollView) -> Void)?
    
    let currentTab: ProfileStateTab
    let refresh: () async throws -> Void
    let presenter: ProfilePresenter
    let accountType: AccountType
    let userKey: MicroBlogKey?
    
    init(tab: ProfileStateTab,
         refresh: @escaping () async throws -> Void,
         presenter: ProfilePresenter,
         accountType: AccountType,
         userKey: MicroBlogKey?) {
        self.currentTab = tab
        self.refresh = refresh
        self.presenter = presenter
        self.accountType = accountType
        self.userKey = userKey
        super.init(nibName: nil, bundle: nil)
        debugPrint("ProfileTabListViewController init with tab: \(tab)")
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func updateContent() {
        let content: AnyView = {
            switch onEnum(of: currentTab) {
            case .timeline(let timeline):
                debugPrint("Timeline data: \(timeline.data)")
                return AnyView(
                    ObservePresenter(presenter: presenter) { state in
                        List {
                            StatusTimelineComponent(
                                data: timeline.data,
                                detailKey: nil
                            )
                            .listRowBackground(Colors.Background.swiftUIPrimary)
                            .onAppear {
                                debugPrint("StatusTimelineComponent appeared for tab: \(self.currentTab)")
                                Task {
                                    try? await self.refresh()
                                }
                            }
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                        .refreshable {
                            debugPrint("Refreshing timeline...")
                            try? await self.refresh()
                        }
                    }
                )
            case .media:
                debugPrint("Loading media screen")
                return AnyView(
                    ProfileMediaListScreen(
                        accountType: accountType,
                        userKey: userKey
                    )
                )
            }
        }()
        
        if hostingController == nil {
            hostingController = UIHostingController(rootView: content)
            addChild(hostingController)
            view.addSubview(hostingController.view)
            hostingController.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
                hostingController.view.leftAnchor.constraint(equalTo: view.leftAnchor),
                hostingController.view.rightAnchor.constraint(equalTo: view.rightAnchor),
                hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            ])
            hostingController.didMove(toParent: self)
            
            // 查找并保存 scrollView
            for subview in hostingController.view.subviews {
                if let scrollView = subview as? UIScrollView {
                    self.scrollView = scrollView
                    debugPrint("Found scrollView for tab: \(currentTab)")
                    break
                }
            }
        } else {
            hostingController.rootView = content
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        debugPrint("ProfileTabListViewController viewDidLoad for tab: \(currentTab)")
        updateContent()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        debugPrint("ProfileTabListViewController viewWillAppear for tab: \(currentTab)")
        Task {
            try? await refresh()
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        debugPrint("ProfileTabListViewController viewDidAppear for tab: \(currentTab)")
    }
    
    // MARK: - JXPagingViewListViewDelegate
    
    func listView() -> UIView {
        debugPrint("listView called for tab: \(currentTab)")
        return view
    }
    
    func listScrollView() -> UIScrollView {
        debugPrint("listScrollView called for tab: \(currentTab)")
        return scrollView ?? UIScrollView()
    }
    
    func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> Void) {
        debugPrint("listViewDidScrollCallback called for tab: \(currentTab)")
        listViewDidScrollCallback = callback
    }
}
 
