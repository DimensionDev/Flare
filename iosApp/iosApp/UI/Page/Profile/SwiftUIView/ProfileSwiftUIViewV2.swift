
import shared
import SwiftUI

struct ProfileSwiftUIViewV2: View {
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let showBackButton: Bool

    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @Environment(FlareTheme.self) private var theme
    @Environment(TimelineExtState.self) private var timelineState
    @Environment(\.appSettings) private var appSettings

    @State private var showUserNameInNavBar = false

    init(accountType: AccountType, userKey: MicroBlogKey?, showBackButton: Bool = true) {
        self.accountType = accountType
        self.userKey = userKey
        self.showBackButton = showBackButton

        let presenterWrapper = ProfilePresenterWrapper(accountType: accountType, userKey: userKey)
        _presenterWrapper = StateObject(wrappedValue: presenterWrapper)
    }

    var body: some View {
        @Bindable var bindableTimelineState = timelineState

        ZStack(alignment: .bottomTrailing) {
            if presenterWrapper.isInitialized {
                ObservePresenter(presenter: presenterWrapper.profilePresenter) { state in
                    ScrollViewReader { proxy in
                        List {
                            EmptyView().id("profile-top")

                            // Header
                            if let realUserInfo = ProfileUserInfo.from(state: state) {
                                ProfileHeaderSwiftUIViewV2(
                                    userInfo: realUserInfo,
                                    scrollProxy: proxy,
                                    presenter: presenterWrapper.profilePresenter
                                )
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                            }

                            // Tab Bar
                            if !presenterWrapper.availableTabs.isEmpty {
                                ProfileTabBarViewV2(
                                    selectedTabKey: $presenterWrapper.selectedTabKey,
                                    availableTabs: presenterWrapper.availableTabs
                                )
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                                .id("tabbar")
                            }

                            if let currentTabViewModel = presenterWrapper.currentTabViewModel {
                                let isCurrentTab = presenterWrapper.isCurrentTabActive

                                if currentTabViewModel.isMediaTab {
                                    ProfileWaterfallContentView(
                                        timelineViewModel: currentTabViewModel.timelineViewModel,
                                        selectedTabKey: presenterWrapper.selectedTabKey,
                                        isCurrentTab: isCurrentTab
                                    )
                                    .listRowInsets(EdgeInsets())
                                    .listRowSeparator(.hidden)
                                } else {
                                    ProfileTimelineContentView(
                                        timelineViewModel: currentTabViewModel.timelineViewModel,
                                        isCurrentTab: isCurrentTab
                                    )
                                    .listRowInsets(EdgeInsets())
                                    .listRowSeparator(.hidden)
                                }
                            }
                        }
                        .listStyle(.plain)
                        .edgesIgnoringSafeArea(.top)
                        .refreshable {
                            await presenterWrapper.refreshCurrentTab()
                        }
                        .onScrollGeometryChange(for: ScrollGeometry.self) { geometry in
                            geometry
                        } action: { _, newValue in
                            handleProfileScrollChange(newValue.contentOffset.y, bindableTimelineState: $bindableTimelineState.showFloatingButton)
                        }
                        .onChange(of: timelineState.scrollToTopTrigger) { _, _ in
                            let isCurrentTab = presenterWrapper.isCurrentTabActive
                            guard isCurrentTab else {
                                FlareLog.debug("⏸️ [ProfileSwiftUIViewV2] Skipping scroll to top - not current tab")
                                return
                            }

                            FlareLog.debug("🔝 [ProfileSwiftUIViewV2] 返回顶部触发 - isCurrentTab: \(isCurrentTab)")
                            withAnimation(.easeInOut(duration: 0.5)) {
                                proxy.scrollTo("profile-top", anchor: .top)
                            }
                        }
                    }
                    .navigationTitle(showUserNameInNavBar ? (ProfileUserInfo.from(state: state)?.profile.name.markdown ?? "") : "")
                    .navigationBarTitleDisplayMode(.inline)
                }
            } else {
                VStack(spacing: 16) {
                    ProgressView()
                    Text("Initializing Profile...")
                        .foregroundColor(theme.labelColor)
                }
            }

            if presenterWrapper.isInitialized,
               !appSettings.appearanceSettings.hideScrollToTopButton
            {
                VStack(spacing: 12) {
                    FloatingScrollToTopButton()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, FloatingButtonConfig.screenPadding)
                .padding(.bottom, FloatingButtonConfig.bottomExtraMargin)
            }
        }
        .task(id: userKey?.description) {
            await presenterWrapper.setup()
        }
        .onReceive(NotificationCenter.default.publisher(for: .timelineItemUpdated)) { _ in
            // 🔑 只有当前Tab才响应通知刷新
            let isCurrentTab = presenterWrapper.isCurrentTabActive
            guard isCurrentTab else {
                FlareLog.debug("⏸️ [ProfileSwiftUIViewV2] Skipping refresh - not current tab")
                return
            }

            FlareLog.debug("📬 [ProfileSwiftUIViewV2] Received timelineItemUpdated notification - isCurrentTab: \(isCurrentTab)")

            // 刷新当前Tab
            Task {
                await presenterWrapper.refreshCurrentTab()
            }
        }
        .onAppear {
            presenterWrapper.resumeCurrentViewModel()
        }
        .onDisappear {
            timelineState.tabBarOffset = 0
            presenterWrapper.pauseAllViewModels()
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let userKey {
                    ProfileMoreButtonView(
                        presenter: presenterWrapper.profilePresenter,
                        userKey: userKey
                    )
                }
            }
        }
    }
}

extension ProfileSwiftUIViewV2 {
    private func handleProfileScrollChange(_ offsetY: CGFloat, bindableTimelineState: Binding<Bool>) {
        // FlareLog.debug("📜 [ProfileSwiftUIViewV2] Profile滚动检测 - offsetY: \(offsetY), tabBarOffset: \(timelineState.tabBarOffset)")

        if let currentTabViewModel = presenterWrapper.currentTabViewModel {
            currentTabViewModel.timelineViewModel.handleScrollOffsetChange(
                offsetY,
                showFloatingButton: bindableTimelineState,
                timelineState: timelineState,
                isHomeTab: true
            )
        } else {
            FlareLog.debug("📜 [ProfileSwiftUIViewV2] 直接调用TabBar更新逻辑")
            timelineState.updateTabBarOffset(currentOffset: offsetY, isHomeTab: true)

            let shouldShowFloatingButton = offsetY > 50
            if timelineState.showFloatingButton != shouldShowFloatingButton {
                timelineState.showFloatingButton = shouldShowFloatingButton
            }
        }

        let shouldShow = offsetY > 100
        if showUserNameInNavBar != shouldShow { showUserNameInNavBar = shouldShow }
    }
}
