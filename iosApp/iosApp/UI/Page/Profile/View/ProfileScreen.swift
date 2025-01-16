import MarkdownUI
import OrderedCollections
import os.log
import shared
import SwiftUI

struct ProfileScreen: View {
    // MicroBlogKey host+id
    // 已集成到 Profile 页面的 tab 中，不再需要单独导航
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?

    // 包含 user relationState， isme，listState - userTimeline，mediaState，canSendMessage
    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @StateObject private var tabStore: ProfileTabSettingStore
    @State private var selectedTab: Int = 0
    @State private var userInfo: ProfileUserInfo? // 添加用户信息状态

    // 横屏 竖屏
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.appSettings) private var appSettings

    init(accountType: AccountType, userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey

        let timelineStore = TimelineStore(accountType: accountType)
        _presenterWrapper = StateObject(wrappedValue: ProfilePresenterWrapper(accountType: accountType, userKey: userKey))

        // 初始化 tabStore
        let tabStore = ProfileTabSettingStore(timelineStore: timelineStore)
        _tabStore = StateObject(wrappedValue: tabStore)

        os_log("[📔][ProfileScreen - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")
    }

    var body: some View {
        ObservePresenter(presenter: presenterWrapper.presenter) { state in
            let userInfo = ProfileUserInfo.from(state: state as! ProfileState)

            ProfileScreenContent(
                userInfo: userInfo,
                state: state as! ProfileState,
                selectedTab: $selectedTab,
                horizontalSizeClass: horizontalSizeClass,
                appSettings: appSettings,
                toProfileMedia: toProfileMedia,
                accountType: accountType,
                userKey: userKey,
                tabStore: tabStore
            )
        }
    }
}

private struct ProfileScreenContent: View {
    let userInfo: ProfileUserInfo? // 使用可选的用户信息
    let state: ProfileState
    @Binding var selectedTab: Int
    let horizontalSizeClass: UserInterfaceSizeClass?
    let appSettings: AppSettings
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let tabStore: ProfileTabSettingStore

    var title: LocalizedStringKey {
        if let info = userInfo {
            return LocalizedStringKey(info.profile.name.markdown)
        }
        return "loading"
    }

    var body: some View {
        let _ = os_log("[📔][ProfileScreen]ProfileScreenContent body 被调用", log: .default, type: .debug)

        ZStack {
            PagingContainerView {
                if let info = userInfo {
                    CommonProfileHeader(
                        userInfo: info,
                        state: state,
                        onFollowClick: { relation in
                            os_log("[📔][ProfileScreen]点击关注按钮: userKey=%{public}@", log: .default, type: .debug, info.profile.key.description)
                            state.follow(userKey: info.profile.key, data: relation)
                        }
                    )
                }
            } pinnedView: {
                // Tab Bar
                let _ = os_log("[📔][ProfileScreen]pinnedView: availableTabs=%{public}d", log: .default, type: .debug, tabStore.availableTabs.count)

                VStack {
                    ProfileTabBarView(
                        tabs: tabStore.availableTabs,
                        selectedTab: $selectedTab,
                        onTabSelected: { index in
                            os_log("[📔][ProfileScreen]选择标签页: index=%{public}d", log: .default, type: .debug, index)
                            withAnimation {
                                selectedTab = index
                                if index < tabStore.availableTabs.count {
                                    let selectedTab = tabStore.availableTabs[index]
                                    tabStore.updateCurrentPresenter(for: selectedTab)
                                }
                            }
                        }
                    )
                    .onAppear {
                        os_log("[📔][ProfileScreen][pinnedView]ProfileTabBarView 已加载: selectedTab=%{public}d, tabsCount=%{public}d", log: .default, type: .debug, selectedTab, tabStore.availableTabs.count)
                    }
                    .onDisappear {
                        os_log("[📔][ProfileScreen][pinnedView]ProfileTabBarView 已卸载", log: .default, type: .debug)
                    }
                }
            } content: {
                // Content
                ProfileContentView(
                    tabs: tabStore.availableTabs,
                    selectedTab: $selectedTab,
                    refresh: {
                        os_log("[📔][ProfileScreen]刷新内容", log: .default, type: .debug)
                        try? await state.refresh()
                    },
                    accountType: accountType,
                    userKey: userKey,
                    tabStore: tabStore
                )
                .onAppear {
                    os_log("[📔][ProfileScreen][content]ProfileContentView 已加载: selectedTab=%{public}d, tabsCount=%{public}d", log: .default, type: .debug, selectedTab, tabStore.availableTabs.count)
                }
                .onDisappear {
                    os_log("[📔][ProfileScreen][content]ProfileContentView 已卸载", log: .default, type: .debug)
                }
            }
        }
        .onAppear {
            os_log("[📔][ProfileScreen]视图出现", log: .default, type: .debug)
        }
        .onDisappear {
            os_log("[📔][ProfileScreen]视图消失", log: .default, type: .debug)
        }
        #if os(iOS)
        .if(horizontalSizeClass == .compact, transform: { view in
            view.ignoresSafeArea(edges: .top)
        })
        #endif
        .if(horizontalSizeClass != .compact, transform: { view in
            view
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            #endif
            .navigationTitle(title)
        })
    }
}
