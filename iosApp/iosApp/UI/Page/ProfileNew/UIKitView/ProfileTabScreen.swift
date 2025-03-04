import MarkdownUI
import OrderedCollections
import SwiftUI
import os.log
import shared

struct ProfileTabScreen: View {
  // MicroBlogKey host+id
  let toProfileMedia: (MicroBlogKey) -> Void
  let accountType: AccountType
  let userKey: MicroBlogKey?
  let showBackButton: Bool

  // 包含 user relationState， isme，listState - userTimeline，mediaState，canSendMessage
  @StateObject private var presenterWrapper: ProfilePresenterWrapper
  @StateObject private var mediaPresenterWrapper: ProfileMediaPresenterWrapper
  @StateObject private var tabStore: ProfileTabSettingStore
  @StateObject private var menuState: FLNewAppState
  @StateObject private var gestureState: FLNewGestureState
  @State private var selectedTab: Int = 0
  @State private var userInfo: ProfileUserInfo?

  // 横屏 竖屏
  @Environment(\.horizontalSizeClass) private var horizontalSizeClass
  @Environment(\.appSettings) private var appSettings

  init(
    accountType: AccountType, userKey: MicroBlogKey?,
    toProfileMedia: @escaping (MicroBlogKey) -> Void, showBackButton: Bool = true
  ) {
    self.toProfileMedia = toProfileMedia
    self.accountType = accountType
    self.userKey = userKey
    self.showBackButton = showBackButton

    //        let timelineStore = TimelineStore(accountType: accountType)
    _presenterWrapper = StateObject(
      wrappedValue: ProfilePresenterWrapper(accountType: accountType, userKey: userKey))
    _mediaPresenterWrapper = StateObject(
      wrappedValue: ProfileMediaPresenterWrapper(accountType: accountType, userKey: userKey))

    // 初始化 tabStore
    let tabStore = ProfileTabSettingStore(userKey: userKey)
    _tabStore = StateObject(wrappedValue: tabStore)

    // 初始化手势和菜单状态
    _menuState = StateObject(wrappedValue: FLNewAppState(tabProvider: tabStore))
    _gestureState = StateObject(wrappedValue: FLNewGestureState(tabProvider: tabStore))

    os_log(
      "[📔][ProfileNewScreen - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default,
      type: .debug, String(describing: accountType), userKey?.description ?? "nil")
  }

  var body: some View {
    ObservePresenter(presenter: presenterWrapper.presenter) { state in
      let userInfo = ProfileUserInfo.from(state: state as! ProfileNewState)

      // 打印 isShowAppBar 的值
      let _ = {
        os_log(
          "[📔][ProfileTabScreen] userKey=%{public}@", log: .default, type: .debug,
          String(describing: userKey))
      }()

      if userKey == nil {
        //
        ProfileNewRefreshViewControllerWrapper(
          userInfo: userInfo,
          state: state as! ProfileNewState,
          selectedTab: $selectedTab,
          isShowAppBar: Binding(
            get: { presenterWrapper.isShowAppBar },
            set: { presenterWrapper.updateNavigationState(showAppBar: $0) }
          ),
          isShowsegmentedBackButton: Binding(
            get: { presenterWrapper.isShowsegmentedBackButton },
            set: { _ in }  // 只读绑定，因为这个值由 isShowAppBar 控制
          ),
          horizontalSizeClass: horizontalSizeClass,
          appSettings: appSettings,
          toProfileMedia: toProfileMedia,
          accountType: accountType,
          userKey: userKey,
          tabStore: tabStore,
          mediaPresenterWrapper: mediaPresenterWrapper
        )
        .ignoresSafeArea(edges: .top)
        .modifier(FLNewMenuGestureModifier(appState: menuState))

      } else {
        ProfileNewRefreshViewControllerWrapper(
          userInfo: userInfo,
          state: state as! ProfileNewState,
          selectedTab: $selectedTab,
          isShowAppBar: Binding(
            get: { presenterWrapper.isShowAppBar },
            set: { presenterWrapper.updateNavigationState(showAppBar: $0) }
          ),
          isShowsegmentedBackButton: Binding(
            get: { presenterWrapper.isShowsegmentedBackButton },
            set: { _ in }  // 只读绑定，因为这个值由 isShowAppBar 控制
          ),
          horizontalSizeClass: horizontalSizeClass,
          appSettings: appSettings,
          toProfileMedia: toProfileMedia,
          accountType: accountType,
          userKey: userKey,
          tabStore: tabStore,
          mediaPresenterWrapper: mediaPresenterWrapper
        )
        .ignoresSafeArea(edges: .top)
        .modifier(FLNewMenuGestureModifier(appState: menuState))
        .secondNavigation()
      }
    }
  }
}

struct ProfileNewRefreshViewControllerWrapper: UIViewControllerRepresentable {
  let userInfo: ProfileUserInfo?
  let state: ProfileNewState
  @Binding var selectedTab: Int
  @Binding var isShowAppBar: Bool?
  @Binding var isShowsegmentedBackButton: Bool
  let horizontalSizeClass: UserInterfaceSizeClass?
  let appSettings: AppSettings
  let toProfileMedia: (MicroBlogKey) -> Void
  let accountType: AccountType
  let userKey: MicroBlogKey?
  let tabStore: ProfileTabSettingStore
  let mediaPresenterWrapper: ProfileMediaPresenterWrapper

  func makeUIViewController(context _: Context) -> ProfileNewRefreshViewController {
    let controller = ProfileNewRefreshViewController()
    // 传递所有必要的数据给 ProfileNewRefreshViewController
    controller.configure(
      userInfo: userInfo,
      state: state,
      selectedTab: $selectedTab,
      isShowAppBar: $isShowAppBar,
      isShowsegmentedBackButton: $isShowsegmentedBackButton,
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

  func updateUIViewController(
    _ uiViewController: ProfileNewRefreshViewController, context _: Context
  ) {
    // 更新 ViewController 的数据
    uiViewController.configure(
      userInfo: userInfo,
      state: state,
      selectedTab: $selectedTab,
      isShowAppBar: $isShowAppBar,
      isShowsegmentedBackButton: $isShowsegmentedBackButton,
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
