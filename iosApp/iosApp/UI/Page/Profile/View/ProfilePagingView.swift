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

class ProfilePagingViewController: UIViewController, JXPagingViewDelegate {
    private var pagingView: JXPagingView!
    private var headerView: UIView!
    private var tabs: [ProfileStateTab] = []
    private var selectedIndex: Int = 0
    
    let appSettings: AppSettings
    let listState: PagingState<UiTimeline>
    let refresh: () async throws -> Void
    let presenter: ProfilePresenter
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let selectedTabBinding: Binding<Int>
    
    init(tabs: [ProfileStateTab],
         appSettings: AppSettings,
         listState: PagingState<UiTimeline>,
         refresh: @escaping () async throws -> Void,
         presenter: ProfilePresenter,
         accountType: AccountType,
         userKey: MicroBlogKey?,
         selectedTab: Binding<Int>) {
        self.tabs = tabs
        self.appSettings = appSettings
        self.listState = listState
        self.refresh = refresh
        self.presenter = presenter
        self.accountType = accountType
        self.userKey = userKey
        self.selectedTabBinding = selectedTab
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // 设置 header view
        let headerContent = ObservePresenter(presenter: presenter) { state in
            let user = { () -> UiProfile in 
                if case .success(let data) = onEnum(of: state.userState) {
                    return data.data
                }
                return createSampleUser()
            }()
            return CommonProfileHeader(
                user: user,
                relation: state.relationState,
                isMe: state.isMe,
                onFollowClick: { relation in
                    if case .success(let data) = onEnum(of: state.userState) {
                        Task {
                            try? await state.follow(userKey: data.data.key, data: relation)
                        }
                    }
                }
            )
        }
        headerView = UIHostingController(rootView: headerContent).view
        
        // 设置 paging view
        pagingView = JXPagingView(delegate: self)
        pagingView.mainTableView.backgroundColor = .clear
        pagingView.listContainerView.backgroundColor = .clear
        
        view.addSubview(pagingView)
        pagingView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            pagingView.topAnchor.constraint(equalTo: view.topAnchor),
            pagingView.leftAnchor.constraint(equalTo: view.leftAnchor),
            pagingView.rightAnchor.constraint(equalTo: view.rightAnchor),
            pagingView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    // MARK: - JXPagingViewDelegate
    
    func tableHeaderViewHeight(in pagingView: JXPagingView) -> Int {
        return Int(headerView.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize).height)
    }
    
    func tableHeaderView(in pagingView: JXPagingView) -> UIView {
        return headerView
    }
    
    func heightForPinSectionHeader(in pagingView: JXPagingView) -> Int {
        return 44  // Tab bar height
    }
    
    func viewForPinSectionHeader(in pagingView: JXPagingView) -> UIView {
        let tabView = ProfileTabHeader(
            tabs: tabs,
            selectedTab: selectedTabBinding,
            onTabSelected: { [weak self] index in
                guard let self = self else { return }
                withAnimation {
                    self.selectedTabBinding.wrappedValue = index
                }
                self.pagingView?.listContainerView.didClickSelectedItem(at: index)
            }
        )
        return UIHostingController(rootView: tabView).view
    }
    
    func numberOfLists(in pagingView: JXPagingView) -> Int {
        return tabs.count
    }
    
    func pagingView(_ pagingView: JXPagingView, initListAtIndex index: Int) -> JXPagingViewListViewDelegate {
        return ProfileTabListViewController(
            tab: tabs[index],
            refresh: refresh,
            presenter: presenter,
            accountType: accountType,
            userKey: userKey
        )
    }
}

// ProfilePagingView - SwiftUI wrapper
struct ProfilePagingView: UIViewControllerRepresentable {
    let tabs: [ProfileStateTab]
    let appSettings: AppSettings
    let listState: PagingState<UiTimeline>
    let refresh: () async throws -> Void
    let presenter: ProfilePresenter
    let accountType: AccountType
    let userKey: MicroBlogKey?
    @Binding var selectedTab: Int
    
    func makeUIViewController(context: Context) -> ProfilePagingViewController {
        return ProfilePagingViewController(
            tabs: tabs,
            appSettings: appSettings,
            listState: listState,
            refresh: refresh,
            presenter: presenter,
            accountType: accountType,
            userKey: userKey,
            selectedTab: $selectedTab
        )
    }
    
    func updateUIViewController(_ uiViewController: ProfilePagingViewController, context: Context) {
        // 不需要在这里更新，因为使用了 Binding
    }
} 
