
import shared
import SwiftUI

struct ProfileSwiftUIViewV2: View {
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let showBackButton: Bool

    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @Environment(FlareTheme.self) private var theme
    @EnvironmentObject private var timelineState: TimelineExtState
    @Environment(\.appSettings) private var appSettings

    init(accountType: AccountType, userKey: MicroBlogKey?, showBackButton: Bool = true) {
        self.accountType = accountType
        self.userKey = userKey
        self.showBackButton = showBackButton

        let presenterWrapper = ProfilePresenterWrapper(accountType: accountType, userKey: userKey)
        _presenterWrapper = StateObject(wrappedValue: presenterWrapper)
    }

    var body: some View {
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

                            // Content
                            if let currentTabViewModel = presenterWrapper.currentTabViewModel {
                                if currentTabViewModel.isMediaTab {
                                    ProfileWaterfallContentView(
                                        timelineViewModel: currentTabViewModel.timelineViewModel,
                                        selectedTabKey: presenterWrapper.selectedTabKey
                                    )
                                    .listRowInsets(EdgeInsets())
                                    .listRowSeparator(.hidden)
                                } else {
                                    ProfileTimelineContentView(
                                        timelineViewModel: currentTabViewModel.timelineViewModel
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
                            handleProfileScrollChange(newValue.contentOffset.y)
                        }
                        .onChange(of: timelineState.scrollToTopTrigger) { _, _ in
                            FlareLog.debug("🔝 [ProfileSwiftUIViewV2] 返回顶部触发")
                            withAnimation(.easeInOut(duration: 0.5)) {
                                proxy.scrollTo("profile-top", anchor: .top)
                            }
                        }
                    }
                }
            } else {
                // 加载状态
                VStack(spacing: 16) {
                    ProgressView()
                    Text("Initializing Profile...")
                        .foregroundColor(theme.labelColor)
                }
            }

            // 🔥 添加浮动按钮
            if presenterWrapper.isInitialized,
               !appSettings.appearanceSettings.hideScrollToTopButton {
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
        .onAppear {
            presenterWrapper.resumeCurrentViewModel()
        }
        .onDisappear {
            timelineState.tabBarOffset = 0
            presenterWrapper.clearAllViewModels()
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let userKey = userKey {
                    ProfileMoreButtonView(
                        presenter: presenterWrapper.profilePresenter,
                        userKey: userKey
                    )
                }
            }
        }
    }
}

// MARK: - Profile Scroll Handling
extension ProfileSwiftUIViewV2 {
    /// 处理Profile页面滚动变化（复用Home架构）
    /// 包含浮动按钮控制和TabBar自动隐藏显示
    /// - Parameter offsetY: 滚动偏移量
    private func handleProfileScrollChange(_ offsetY: CGFloat) {
        // 添加Profile专用日志
        FlareLog.debug("📜 [ProfileSwiftUIViewV2] Profile滚动检测 - offsetY: \(offsetY), tabBarOffset: \(timelineState.tabBarOffset)")

        // 🔥 使用当前Tab的TimelineViewModel处理滚动
        if let currentTabViewModel = presenterWrapper.currentTabViewModel {
            currentTabViewModel.timelineViewModel.handleScrollOffsetChange(
                offsetY,
                showFloatingButton: $timelineState.showFloatingButton,
                timelineState: timelineState,
                isHomeTab: true  // Profile页面总是被视为当前Tab
            )
        } else {
            // 如果没有TimelineViewModel，直接调用TabBar更新逻辑
            FlareLog.debug("📜 [ProfileSwiftUIViewV2] 直接调用TabBar更新逻辑")
            timelineState.updateTabBarOffset(currentOffset: offsetY, isHomeTab: true)

            // 手动处理浮动按钮显示
            let shouldShowFloatingButton = offsetY > 50
            if timelineState.showFloatingButton != shouldShowFloatingButton {
                timelineState.showFloatingButton = shouldShowFloatingButton
            }
        }
    }
}
