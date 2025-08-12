import Combine
import Kingfisher
import MarkdownUI
import OrderedCollections
import os.log
import shared
import SwiftUI

struct ProfileTabScreenUikit: View {
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let showBackButton: Bool

    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @StateObject private var mediaPresenterWrapper: ProfileMediaPresenterWrapper
    @StateObject private var tabStore: ProfileTabSettingStore
    @State private var selectedTab: Int = 0
    @State private var userInfo: ProfileUserInfo?
    @Environment(FlareTheme.self) private var theme: FlareTheme

    // 横屏 竖屏
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareMenuState.self) private var menuState

    init(
        accountType: AccountType, userKey: MicroBlogKey?, showBackButton: Bool = true
    ) {
        self.accountType = accountType
        self.userKey = userKey
        self.showBackButton = showBackButton

        let service = ProfilePresenterService.shared

        _presenterWrapper = StateObject(
            wrappedValue: service.getOrCreatePresenter(accountType: accountType, userKey: userKey))
        _mediaPresenterWrapper = StateObject(
            wrappedValue: service.getOrCreateMediaPresenter(accountType: accountType, userKey: userKey))
        _tabStore = StateObject(
            wrappedValue: service.getOrCreateTabStore(userKey: userKey))

        os_log(
            "[📔][ProfileNewScreen - optimized]优化初始化完成: accountType=%{public}@, userKey=%{public}@",
            log: .default, type: .debug,
            String(describing: accountType), userKey?.description ?? "nil"
        )

        os_log("[📔][ProfilePresenterService] %{public}@", log: .default, type: .debug, service.getCacheInfo())
    }

    var body: some View {
        ObservePresenter(presenter: presenterWrapper.presenter) { state in
            let userInfo = ProfileUserInfo.from(state: state as! ProfileNewState)

            // 打印 isShowAppBar 的值
            let _ = os_log(
                "[📔][ProfileTabScreen] userKey=%{public}@", log: .default, type: .debug,
                String(describing: userKey)
            )

            // 新增：初始化TimelineViewModel（类似TimelineViewSwiftUIV4的setupDataSource）
            let _ = Task { @MainActor in
                await ProfilePresenterService.shared.setupTimelineViewModel(
                    accountType: accountType,
                    userKey: userKey
                )
            }

            if userKey == nil {
                 
                ProfileNewRefreshViewControllerWrapper(
                    userInfo: userInfo,
                    state: state as! ProfileNewState,
                    selectedTab: $selectedTab,
                    isShowAppBar: Binding(
                        get: { presenterWrapper.isShowAppBar },
                        set: { presenterWrapper.updateNavigationState(showAppBar: $0) }
                    ),
                     
                    horizontalSizeClass: horizontalSizeClass,
                    appSettings: appSettings,
                    accountType: accountType,
                    userKey: userKey,
                    tabStore: tabStore,
                    mediaPresenterWrapper: mediaPresenterWrapper,
                    presenterWrapper: presenterWrapper,
                    theme: theme
                )
                .ignoresSafeArea(edges: .top)

            } else {
                ProfileNewRefreshViewControllerWrapper(
                    userInfo: userInfo,
                    state: state as! ProfileNewState,
                    selectedTab: $selectedTab,
                    isShowAppBar: Binding(
                        get: { presenterWrapper.isShowAppBar },
                        set: { presenterWrapper.updateNavigationState(showAppBar: $0) }
                    ),
                   
                    horizontalSizeClass: horizontalSizeClass,
                    appSettings: appSettings,
                    accountType: accountType,
                    userKey: userKey,
                    tabStore: tabStore,
                    mediaPresenterWrapper: mediaPresenterWrapper,
                    presenterWrapper: presenterWrapper,
                    theme: theme
                )
                .ignoresSafeArea(edges: .top)
            }
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
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let tabStore: ProfileTabSettingStore
    let mediaPresenterWrapper: ProfileMediaPresenterWrapper
    let presenterWrapper: ProfilePresenterWrapper
    let theme: FlareTheme

    func makeUIViewController(context _: Context) -> ProfileNewRefreshViewController {
        let controller = ProfileNewRefreshViewController()
        controller.configure(
            userInfo: userInfo,
            state: state,
            selectedTab: $selectedTab,
            isShowAppBar: $isShowAppBar,
            horizontalSizeClass: horizontalSizeClass,
            appSettings: appSettings,
            accountType: accountType,
            userKey: userKey,
            tabStore: tabStore,
            mediaPresenterWrapper: mediaPresenterWrapper,
            presenterWrapper: presenterWrapper,
            theme: theme
        )
        return controller
    }

    func updateUIViewController(
        _ uiViewController: ProfileNewRefreshViewController, context _: Context
    ) {
        if shouldUpdate(uiViewController) {
            uiViewController.configure(
                userInfo: userInfo,
                state: state,
                selectedTab: $selectedTab,
                isShowAppBar: $isShowAppBar,
                horizontalSizeClass: horizontalSizeClass,
                appSettings: appSettings,
                accountType: accountType,
                userKey: userKey,
                tabStore: tabStore,
                mediaPresenterWrapper: mediaPresenterWrapper,
                presenterWrapper: presenterWrapper,
                theme: theme
            )
        }
    }

    private func shouldUpdate(_ controller: ProfileNewRefreshViewController) -> Bool {
        controller.needsProfileUpdate(
            userInfo: userInfo,
            selectedTab: selectedTab,
            accountType: accountType,
            userKey: userKey
        )
    }
}
