import MarkdownUI
import OrderedCollections
import shared
import SwiftUI
import os.log

struct ProfileScreen: View {
    //MicroBlogKey host+id
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?

    //包含 user relationState， isme，listState - userTimeline，mediaState，canSendMessage
    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @StateObject private var tabStore: ProfileTabStore
    @State private var selectedTab: Int = 0
    @State private var userInfo: ProfileUserInfo?  // 添加用户信息状态

    //横屏 竖屏
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.appSettings) private var appSettings

    init(accountType: AccountType, userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey
        _presenterWrapper = StateObject(wrappedValue: ProfilePresenterWrapper(accountType: accountType, userKey: userKey))
        
        _tabStore = StateObject(wrappedValue: ProfileTabStore(accountType: accountType, userKey: userKey))
        os_log("[📔][ProfileScreen - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")
    }

    var body: some View {
        ObservePresenter(presenter: presenterWrapper.presenter) { state in
            os_log("[📔][ProfileScreen]ObservePresenter 状态变化", log: .default, type: .debug)
            let content = ProfileScreenContent(
                userInfo: ProfileUserInfo.from(state: state),
                state: state,
                selectedTab: $selectedTab,
                horizontalSizeClass: horizontalSizeClass,
                appSettings: appSettings,
                toProfileMedia: toProfileMedia,
                accountType: accountType,
                userKey: userKey,
                presenter: presenterWrapper.presenter,
                tabStore: tabStore
            )
            
            return content.onAppear {
                os_log("[📔][ProfileScreen]开始观察 presenter 状态", log: .default, type: .debug)
            }
        }
    }
}

private struct ProfileScreenContent: View {
    let userInfo: ProfileUserInfo?  // 使用可选的用户信息
    let state: ProfileState
    @Binding var selectedTab: Int
    let horizontalSizeClass: UserInterfaceSizeClass?
    let appSettings: AppSettings
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let presenter: ProfilePresenter
    let tabStore: ProfileTabStore
    
    // 将 sortedTabs 移到这里作为计算属性
    private var sortedTabs: [ProfileStateTab] {
        os_log("[📔][ProfileScreen]准备获取 sortedTabs", log: .default, type: .debug)
        let tabs = ProfileTabBarView.sortedTabs(presenter.tabs)
        os_log("[📔][ProfileScreen]获取 sortedTabs 完成: count=%{public}d", log: .default, type: .debug, tabs.count)
        return tabs
    }
    
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
                    ProfileHeaderView(
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
                ProfileTabBarView(
                    tabs: sortedTabs,  // 使用计算属性
                    selectedTab: $selectedTab,
                    onTabSelected: { index in
                        os_log("[📔][ProfileScreen]选择标签页: index=%{public}d", log: .default, type: .debug, index)
                        withAnimation {
                            selectedTab = index
                        }
                    }
                )
                .onAppear {
                    os_log("[📔][ProfileScreen][pinnedView]ProfileTabBarView 已加载: selectedTab=%{public}d, tabsCount=%{public}d", log: .default, type: .debug, selectedTab, sortedTabs.count)
                }
                .onDisappear {
                    os_log("[📔][ProfileScreen][pinnedView]ProfileTabBarView 已卸载", log: .default, type: .debug)
                }
            } content: {
                // Content
                ProfileContentView(
                    tabs: sortedTabs,  // 使用计算属性
                    selectedTab: $selectedTab,
                    refresh: { 
                        os_log("[📔][ProfileScreen]刷新内容", log: .default, type: .debug)
                        try? await state.refresh() 
                    },
                    presenter: presenter,
                    accountType: accountType,
                    userKey: userKey,
                    tabStore: tabStore
                )
                .onAppear {
                    os_log("[📔][ProfileScreen][content]ProfileContentView 已加载: selectedTab=%{public}d, tabsCount=%{public}d", log: .default, type: .debug, selectedTab, sortedTabs.count)
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
    
//    @ViewBuilder
//    private var sideProfileHeader: some View {
//        ScrollView {
//            VStack {
//                //header
//                ProfileHeaderView(
//                    user: state.userState,
//                    relation: state.relationState,
//                    isMe: state.isMe,
//                    onFollowClick: { user, relation in
//                        state.follow(userKey: user.key, data: relation)
//                    }
//                )
//                //medias
//                if case .success(let userState) = onEnum(of: state.userState) {
//                    Button(action: {
//                        toProfileMedia(userState.data.key)
//                    }, label: {
//                        LargeProfileImagePreviews(state: state.mediaState, appSetting: appSettings)
//                    })
//                    .buttonStyle(.borderless)
//                }
//            }
//        }
//        .background(Colors.Background.swiftUIPrimary)
//        #if os(iOS)
//        .frame(width: 384)
//        #endif
//    }
//    
//    @ViewBuilder
//    private var profileContent: some View {
//        GeometryReader { geometry in
//            VStack(spacing: 0) {
//                // TabView 部分
//                if case .success(let tabs) = onEnum(of: state.tabs) {
//                    let sortedTabs = ProfileTabBarView.sortedTabs(tabs.data)
//                    ProfileTabBarView(
//                        tabs: state.tabs,
//                        selectedTab: $selectedTab,
//                        onTabSelected: { index in
//                            withAnimation {
//                                selectedTab = index
//                            }
//                        }
//                    )
//                    
//                    ProfileContentView(
//                        tabs: sortedTabs,
//                        selectedTab: $selectedTab,
//                        refresh: { try? await state.refresh() },
//                        presenter: presenter,
//                        accountType: accountType,
//                        userKey: userKey,
//                        tabStore: tabStore
//                    )
//                    .frame(height: geometry.size.height * 0.5)
//                } else {
//                    List {
//                        StatusTimelineComponent(
//                            data: state.listState,
//                            detailKey: nil
//                        )
//                        .listRowBackground(Colors.Background.swiftUIPrimary)
//                    }
//                    .listStyle(.plain)
//                    .scrollContentBackground(.hidden)
//                    .refreshable {
//                        try? await state.refresh()
//                    }
//                }
//            }
//            .background(Colors.Background.swiftUIPrimary)
//            .scrollDismissesKeyboard(.immediately)
//            .scrollIndicators(.hidden)
//        }
//    }
//}
//
//struct LargeProfileImagePreviews: View {
//    let state: PagingState<ProfileMedia>
//    let appSetting: AppSettings
//    var body: some View {
//        switch onEnum(of: state) {
//        case .error:
//            EmptyView()
//        case .loading:
//            EmptyView()
//        case .empty: EmptyView()
//        case .success(let success):
//            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
//                ForEach(0..<min(success.itemCount, 6), id: \.self) { index in
//                    let item = success.peek(index: index)
//                    if let media = item?.media {
//                        let image = media as? UiMediaImage
//                         
//                        let shouldBlur = appSetting.appearanceSettings.showSensitiveContent && (image?.sensitive ?? false)
//
//                        
//                        MediaItemComponent(media: media)
//                            .if(shouldBlur, transform: { view in
//                                view.blur(radius: 32)
//                            })
//                            .onAppear(perform: {
//                                success.get(index: index)
//                            })
//                            .aspectRatio(1, contentMode: .fill)
//                            .clipped()
//                            .allowsHitTesting(false)// 防止点击不了，修饰符，不接受点击事件，而是将事件穿透到外层的button。
//                    }
//                }
//            }
//            .clipShape(RoundedRectangle(cornerRadius: 8))
//            .padding(.horizontal)
//        }
//    }
//}
//
//struct SmallProfileMediaPreviews: View {
//    let state: PagingState<ProfileMedia>
//    let appSetting: AppSettings
//    var body: some View {
//        switch onEnum(of: state) {
//        case .error:
//            EmptyView()
//        case .loading:
//            EmptyView()
//        case .success(let success):
//            ScrollView(.horizontal) {
//                LazyHStack(content: {
//                    ForEach(0..<min(success.itemCount, 6), id: \.self) { index in
//                        let item = success.peek(index: index)
//                        if let media = item?.media {
//                            let image = media as? UiMediaImage
//                            
//                            let shouldBlur = !appSetting.appearanceSettings.showSensitiveContent && (image?.sensitive ?? false)
//
//                          
//                            
//                            MediaItemComponent(media: media)
//                                .if(shouldBlur, transform: { view in
//                                    view.blur(radius: 32)
//                                })
//                                .onAppear(perform: {
//                                    success.get(index: index)
//                                })
//                                .aspectRatio(1, contentMode: .fill)
//                                .clipped()
//                                .frame(width: 48, height: 48)
//                                .allowsHitTesting(false)
//                        }
//                    }
//                })
//            }
//        case .empty: EmptyView()
//        }
//    }
//}
//
//struct ProfileHeader: View {
//    let user: UiState<UiProfile>
//    let relation: UiState<UiRelation>
//    let isMe: UiState<KotlinBoolean>
//    let onFollowClick: (UiUserV2, UiRelation) -> Void
//    var body: some View {
//        switch onEnum(of: user) {
//        case .error:
//            Text("error")
//        case .loading:
//            CommonProfileHeader(
//                user: createSampleUser(),
//                relation: relation,
//                isMe: isMe,
//                onFollowClick: { _ in }
//            )
//            .redacted(reason: .placeholder)
//        case .success(let data):
//            ProfileHeaderSuccess(
//                user: data.data,
//                relation: relation,
//                isMe: isMe,
//                onFollowClick: { relation in onFollowClick(data.data, relation) }
//            )
//        }
//    }
//}
//
////struct ProfileHeaderSuccess: View {
////    let user: UiProfile
////    let relation: UiState<UiRelation>
////    let isMe: UiState<KotlinBoolean>
////    let onFollowClick: (UiRelation) -> Void
////    var body: some View {
////        CommonProfileHeader(user: user, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
////    }
////}
////
////struct MatrixView: View {
////    let followCount: String
////    let fansCount: String
////    var body: some View {
////        HStack {
////            Text(followCount)
////                .fontWeight(.bold)
////            Text("正在关注")
////                .foregroundColor(.secondary)
////                .font(.footnote)
////            Divider()
////            Text(fansCount)
////                .fontWeight(.bold)
////            Text("关注者")
////                .foregroundColor(.secondary)
////                .font(.footnote)
////        }
////        .font(.caption)
////    }
////}
//
//// pawoo  的 一些个人 table Info List
////struct FieldsView: View {
////    let fields: [String: UiRichText]
////    var body: some View {
////        if fields.count > 0 {
////            VStack(alignment: .leading) {
////                let keys = fields.map {
////                    $0.key
////                }.sorted()  
////                ForEach(0..<keys.count, id: \.self) { index in
////                    let key = keys[index]
////                    Text(key)
////                        .font(.subheadline)
////                        .foregroundColor(.secondary)
////                    Markdown(fields[key]?.markdown ?? "").markdownTextStyle(textStyle: {
////                        FontFamilyVariant(.normal)
////                        FontSize(.em(0.9))
////                        
//////                        LineSpacing(1.3)
////                        ForegroundColor(.primary)
////                    }).markdownInlineImageProvider(.emoji)
////                        .padding(.vertical, 4)
////                    if index != keys.count - 1 {
////                        Divider()
////                    }
////                }
////                .padding(.horizontal)
////            }
////            .padding(.vertical)
////            #if os(iOS)
////                .background(Color(UIColor.secondarySystemBackground))
////            #else
////                .background(Color(NSColor.windowBackgroundColor))
////            #endif
////                .clipShape(RoundedRectangle(cornerRadius: 8))
////        } else {
////            EmptyView()
////        }
////    }
////}
//
//struct ScrollOffsetPreferenceKey: PreferenceKey {
//    static var defaultValue: CGFloat = 0
//    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
//        value = nextValue()
//    }
//}
