import AVKit
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import OrderedCollections
import shared
import SwiftUI

struct ProfileTabScreen: View {
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?

    @State private var presenter: ProfileNewPresenter
    @State private var mediaPresenter: ProfileMediaPresenter
    @State private var selectedTabIndex: Int = 0
    @State private var scrollOffset: CGFloat = 0
    @StateObject private var tabStore: ProfileTabSettingStore

    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey

        _presenter = .init(initialValue: ProfileNewPresenter(accountType: accountType, userKey: userKey))
        _mediaPresenter = .init(initialValue: ProfileMediaPresenter(accountType: accountType, userKey: userKey))

        // 初始化 tabStore
        let tabStore = ProfileTabSettingStore(userKey: userKey)
        _tabStore = StateObject(wrappedValue: tabStore)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            ZStack(alignment: .top) {
                // 内容区
                contentView(state: state)

                // 固定TabBar - 当滚动超过header高度时固定在顶部
                if let loadedUserInfo = ProfileUserInfo.from(state: state as! ProfileNewState),
                   scrollOffset > 0
                {
                    VStack(spacing: 0) {
                        TabBarView(
                            selectedIndex: $selectedTabIndex,
                            tabs: tabStore.availableTabs
                        )
                        .background(Color(UIColor.systemBackground))
                        .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 2)
                    }
                    .transition(.opacity)
                    .zIndex(100)
                }
            }
            .ignoresSafeArea(edges: .top)
        }
    }

    @ViewBuilder
    private func contentView(state: ProfileNewState) -> some View {
        switch onEnum(of: state.userState) {
        case .error:
            Text("error")
        case .loading:
            loadingView()
        case let .success(data):
            if let loadedUserInfo = ProfileUserInfo.from(state: state) {
                scrollableContentView(userInfo: loadedUserInfo, state: state)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    @ViewBuilder
    private func loadingView() -> some View {
        CommonProfileHeader(
            userInfo: ProfileUserInfo(
                profile: createSampleUser(),
                relation: nil,
                isMe: false,
                followCount: "0",
                fansCount: "0",
                fields: [:],
                canSendMessage: false
            ),
            state: nil,
            onFollowClick: { _ in }
        )
        .redacted(reason: .placeholder)
        .listRowSeparator(.hidden)
        .listRowInsets(EdgeInsets())
    }

    @ViewBuilder
    private func scrollableContentView(userInfo: ProfileUserInfo, state: ProfileNewState) -> some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 0) {
                    // 头部个人信息
                    CommonProfileHeader(
                        userInfo: userInfo,
                        state: state,
                        onFollowClick: { _ in }
                    )
                    .background(
                        GeometryReader { headerGeometry in
                            Color.clear
                                .preference(key: ScrollOffsetPreferenceKey.self,
                                            value: headerGeometry.frame(in: .global).height)
                                .onAppear {
                                    scrollOffset = headerGeometry.frame(in: .global).height
                                }
                        }
                    )

                    // TabBar (在滚动时会被固定的TabBar覆盖)
                    TabBarView(
                        selectedIndex: $selectedTabIndex,
                        tabs: tabStore.availableTabs
                    )
                    .background(Color(UIColor.systemBackground))

                    // 支持左右滑动的TabView
                    TabView(selection: $selectedTabIndex) {
                        ForEach(0 ..< min(tabStore.availableTabs.count, 3), id: \.self) { index in
                            tabContent(for: tabStore.availableTabs[index])
                                .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .frame(
                        height: geometry.size.height - (geometry.safeAreaInsets.top + 40)
                    )
                }
            }
            .onPreferenceChange(ScrollOffsetPreferenceKey.self) { value in
                scrollOffset = value
            }
            .coordinateSpace(name: "scroll")
        }
    }

    // 固定标签内容
    @ViewBuilder
    private func tabContent(for tab: FLTabItem) -> some View {
        if tab is FLProfileMediaTabItem {
            ProfileMediaListScreen(
                accountType: accountType,
                userKey: userKey,
                currentMediaPresenter: mediaPresenter
            )
        } else if let presenter = tabStore.getOrCreatePresenter(for: tab) {
            ProfileTimelineView(presenter: presenter)
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

// 简化版标签栏视图
struct TabBarView: View {
    @Binding var selectedIndex: Int
    let tabs: [FLTabItem]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(0 ..< min(tabs.count, 3), id: \.self) { index in
                Button {
                    withAnimation {
                        selectedIndex = index
                    }
                } label: {
                    VStack(spacing: 4) {
                        Text(getTabTitle(tab: tabs[index]))
                            .fontWeight(selectedIndex == index ? .semibold : .regular)
                            .foregroundStyle(selectedIndex == index ? Color.primary : Color.gray)

                        // 下划线指示器
                        Rectangle()
                            .frame(height: 3)
                            .foregroundStyle(selectedIndex == index ? Color.primary : Color.clear)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 8)
    }

    private func getTabTitle(tab: FLTabItem) -> String {
        switch tab.metaData.title {
        case let .text(title):
            title
        case let .localized(key):
            NSLocalizedString(key, comment: "")
        }
    }
}

// 用于跟踪滚动位置
struct ScrollOffsetPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}
