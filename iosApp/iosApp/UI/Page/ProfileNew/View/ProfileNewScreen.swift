import SwiftUI
import shared
import os.log
import MarkdownUI
import OrderedCollections

struct ProfileNewScreen: View {
    //MicroBlogKey host+id
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let showBackButton: Bool
    
    //包含 user relationState， isme，listState - userTimeline，mediaState，canSendMessage
    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @StateObject private var mediaPresenterWrapper: ProfileMediaPresenterWrapper
    @StateObject private var tabStore: ProfileTabSettingStore
    @State private var selectedTab: Int = 0
    @State private var userInfo: ProfileUserInfo?
    @State private var isShowAppBar: Bool? = nil  // nil: 初始状态, true: 显示, false: 隐藏
    
    //横屏 竖屏
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.appSettings) private var appSettings
    
    init(accountType: AccountType, userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void, showBackButton: Bool = true) {
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey
        self.showBackButton = showBackButton
        
        let timelineStore = TimelineStore(accountType: accountType)
        _presenterWrapper = StateObject(wrappedValue: ProfilePresenterWrapper(accountType: accountType, userKey: userKey))
        _mediaPresenterWrapper = StateObject(wrappedValue: ProfileMediaPresenterWrapper(accountType: accountType, userKey: userKey))
        
        // 初始化 tabStore
        let tabStore = ProfileTabSettingStore(timelineStore: timelineStore, userKey: userKey)
        _tabStore = StateObject(wrappedValue: tabStore)
        
        os_log("[📔][ProfileNewScreen - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")
    }
    
    var body: some View {
        ObservePresenter(presenter: presenterWrapper.presenter) { state in
            let userInfo = ProfileUserInfo.from(state: state as! ProfileNewState)
            
            ProfileNewRefreshViewControllerWrapper(
                userInfo: userInfo,
                state: state as! ProfileNewState,
                selectedTab: $selectedTab,
                isShowAppBar: $isShowAppBar,
                horizontalSizeClass: horizontalSizeClass,
                appSettings: appSettings,
                toProfileMedia: toProfileMedia,
                accountType: accountType,
                userKey: userKey,
                tabStore: tabStore,
                mediaPresenterWrapper: mediaPresenterWrapper
            )
            .ignoresSafeArea(edges: .top)
        }
    }
}

struct ProfileNewRefreshViewControllerWrapper: UIViewControllerRepresentable {
    let userInfo: ProfileUserInfo?
    let state: ProfileNewState
    @Binding var selectedTab: Int
    @Binding var isShowAppBar: Bool?
    let horizontalSizeClass: UserInterfaceSizeClass?
    let appSettings: AppSettings
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let tabStore: ProfileTabSettingStore
    let mediaPresenterWrapper: ProfileMediaPresenterWrapper
    
    func makeUIViewController(context: Context) -> ProfileNewRefreshViewController {
        let controller = ProfileNewRefreshViewController()
        // 传递所有必要的数据给 ProfileNewRefreshViewController
        controller.configure(
            userInfo: userInfo,
            state: state,
            selectedTab: $selectedTab,
            isShowAppBar: $isShowAppBar,
            horizontalSizeClass: horizontalSizeClass,
            appSettings: appSettings,
            toProfileMedia: toProfileMedia,
            accountType: accountType,
            userKey: userKey,
            tabStore: tabStore,
            mediaPresenterWrapper: mediaPresenterWrapper
        )
        return controller
    }
    
    func updateUIViewController(_ uiViewController: ProfileNewRefreshViewController, context: Context) {
        // 更新 ViewController 的数据
        uiViewController.configure(
            userInfo: userInfo,
            state: state,
            selectedTab: $selectedTab,
            isShowAppBar: $isShowAppBar,
            horizontalSizeClass: horizontalSizeClass,
            appSettings: appSettings,
            toProfileMedia: toProfileMedia,
            accountType: accountType,
            userKey: userKey,
            tabStore: tabStore,
            mediaPresenterWrapper: mediaPresenterWrapper
        )
    }
}

