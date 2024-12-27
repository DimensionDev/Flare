import MarkdownUI
import OrderedCollections
import shared
import SwiftUI
 
struct ProfileScreen: View {
    //MicroBlogKey host+id
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?

    //包含 user relationState， isme，listState - userTimeline，mediaState，canSendMessage
    @StateObject private var presenterWrapper: ProfilePresenterWrapper
    @State private var selectedTab: Int = 0

    //横屏 竖屏
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.appSettings) private var appSettings

    init(accountType: AccountType, userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey
        _presenterWrapper = StateObject(wrappedValue: ProfilePresenterWrapper(accountType: accountType, userKey: userKey))
    }

    var body: some View {
        ObservePresenter(presenter: presenterWrapper.presenter) { state in
            ProfileScreenContent(
                state: state,
                selectedTab: $selectedTab,
                horizontalSizeClass: horizontalSizeClass,
                appSettings: appSettings,
                toProfileMedia: toProfileMedia,
                accountType: accountType,
                userKey: userKey,
                presenter: presenterWrapper.presenter
            )
        }
    }
}

// 使用 ObservableObject 包装 presenter 以确保其生命周期
class ProfilePresenterWrapper: ObservableObject {
    let presenter: ProfilePresenter
    
    init(accountType: AccountType, userKey: MicroBlogKey?) {
        self.presenter = .init(accountType: accountType, userKey: userKey)
    }
}

private struct ProfileScreenContent: View {
    let state: ProfileState
    @Binding var selectedTab: Int
    let horizontalSizeClass: UserInterfaceSizeClass?
    let appSettings: AppSettings
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let presenter: ProfilePresenter
    
    var title: LocalizedStringKey {
        if case .success(let user) = onEnum(of: state.userState) {
            LocalizedStringKey(user.data.name.markdown)
        } else {
            LocalizedStringKey("loading")
        }
    }
    
    var body: some View {
        ZStack {
            Colors.Background.swiftUIPrimary.ignoresSafeArea()
            
            if horizontalSizeClass != .compact {
                // 宽屏布局
                #if os(macOS)
                HSplitView {
                    sideProfileHeader
                    profileContent
                }
                #else
                HStack {
                    sideProfileHeader
                    profileContent
                }
                #endif
            } else {
                // 紧凑布局
                PagingContainerView {
                    // Header
                    ProfileHeaderView(
                        user: state.userState,
                        relation: state.relationState,
                        isMe: state.isMe,
                        onFollowClick: { user, relation in
                            state.follow(userKey: user.key, data: relation)
                        }
                    )
                } pinnedView: {
                    // Tab Bar
                    if case .success(let tabs) = onEnum(of: state.tabs) {
                        ProfileTabBarView(
                            tabs: state.tabs,
                            selectedTab: $selectedTab,
                            onTabSelected: { index in
                                withAnimation {
                                    selectedTab = index
                                }
                            }
                        )
                    }
                } content: {
                    // Content
                    if case .success(let tabs) = onEnum(of: state.tabs) {
                        let sortedTabs = ProfileTabBarView.sortedTabs(tabs.data)
                        ProfileContentView(
                            tabs: sortedTabs,
                            selectedTab: $selectedTab,
                            refresh: { try? await state.refresh() },
                            presenter: presenter,
                            accountType: accountType,
                            userKey: userKey
                        )
                    } else {
                        List {
                            StatusTimelineComponent(
                                data: state.listState,
                                detailKey: nil
                            )
                            .listRowBackground(Colors.Background.swiftUIPrimary)
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                        .refreshable {
                            try? await state.refresh()
                        }
                    }
                }
            }
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
        .toolbar {
            if case .success(let isMe) = onEnum(of: state.isMe), !isMe.data.boolValue {
                Menu {
                    if case .success(let user) = onEnum(of: state.userState) {
                        if case .success(let relation) = onEnum(of: state.relationState),
                           case .success(let actions) = onEnum(of: state.actions),
                           actions.data.size > 0
                        {
                            ForEach(0..<actions.data.size, id: \.self) { index in
                                let item = actions.data.get(index: index)
                                Button(action: {
                                    Task {
                                        try? await item.invoke(userKey: user.data.key, relation: relation.data)
                                    }
                                }, label: {
                                    let text = switch onEnum(of: item) {
                                    case .block(let block): if block.relationState(relation: relation.data) {
                                            String(localized: "unblock")
                                        } else {
                                            String(localized: "block")
                                        }
                                    case .mute(let mute): if mute.relationState(relation: relation.data) {
                                            String(localized: "unmute")
                                        } else {
                                            String(localized: "mute")
                                        }
                                    }
                                    let icon = switch onEnum(of: item) {
                                    case .block(let block): if block.relationState(relation: relation.data) {
                                            "xmark.circle"
                                        } else {
                                            "checkmark.circle"
                                        }
                                    case .mute(let mute): if mute.relationState(relation: relation.data) {
                                            "speaker"
                                        } else {
                                            "speaker.slash"
                                        }
                                    }
                                    Label(text, systemImage: icon)
                                })
                            }
                        }
                        Button(action: { state.report(userKey: user.data.key) }, label: {
                            Label("report", systemImage: "exclamationmark.bubble")
                        })
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
    }
    
    @ViewBuilder
    private var sideProfileHeader: some View {
        ScrollView {
            VStack {
                //header
                ProfileHeaderView(
                    user: state.userState,
                    relation: state.relationState,
                    isMe: state.isMe,
                    onFollowClick: { user, relation in
                        state.follow(userKey: user.key, data: relation)
                    }
                )
                //medias
                if case .success(let userState) = onEnum(of: state.userState) {
                    Button(action: {
                        toProfileMedia(userState.data.key)
                    }, label: {
                        LargeProfileImagePreviews(state: state.mediaState, appSetting: appSettings)
                    })
                    .buttonStyle(.borderless)
                }
            }
        }
        .background(Colors.Background.swiftUIPrimary)
        #if os(iOS)
        .frame(width: 384)
        #endif
    }
    
    @ViewBuilder
    private var profileContent: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                // TabView 部分
                if case .success(let tabs) = onEnum(of: state.tabs) {
                    let sortedTabs = ProfileTabBarView.sortedTabs(tabs.data)
                    ProfileTabBarView(
                        tabs: state.tabs,
                        selectedTab: $selectedTab,
                        onTabSelected: { index in
                            withAnimation {
                                selectedTab = index
                            }
                        }
                    )
                    
                    ProfileContentView(
                        tabs: sortedTabs,
                        selectedTab: $selectedTab,
                        refresh: { try? await state.refresh() },
                        presenter: presenter,
                        accountType: accountType,
                        userKey: userKey
                    )
                    .frame(height: geometry.size.height * 0.5)
                } else {
                    List {
                        StatusTimelineComponent(
                            data: state.listState,
                            detailKey: nil
                        )
                        .listRowBackground(Colors.Background.swiftUIPrimary)
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable {
                        try? await state.refresh()
                    }
                }
            }
            .background(Colors.Background.swiftUIPrimary)
            .scrollDismissesKeyboard(.immediately)
            .scrollIndicators(.hidden)
        }
    }
}

struct LargeProfileImagePreviews: View {
    let state: PagingState<ProfileMedia>
    let appSetting: AppSettings
    var body: some View {
        switch onEnum(of: state) {
        case .error:
            EmptyView()
        case .loading:
            EmptyView()
        case .empty: EmptyView()
        case .success(let success):
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                ForEach(0..<min(success.itemCount, 6), id: \.self) { index in
                    let item = success.peek(index: index)
                    if let media = item?.media {
                        let image = media as? UiMediaImage
                         
                        let shouldBlur = appSetting.appearanceSettings.showSensitiveContent && (image?.sensitive ?? false)

                        
                        MediaItemComponent(media: media)
                            .if(shouldBlur, transform: { view in
                                view.blur(radius: 32)
                            })
                            .onAppear(perform: {
                                success.get(index: index)
                            })
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                            .allowsHitTesting(false)// 防止点击不了，修饰符，不接受点击事件，而是将事件穿透到外层的button。
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .padding(.horizontal)
        }
    }
}

struct SmallProfileMediaPreviews: View {
    let state: PagingState<ProfileMedia>
    let appSetting: AppSettings
    var body: some View {
        switch onEnum(of: state) {
        case .error:
            EmptyView()
        case .loading:
            EmptyView()
        case .success(let success):
            ScrollView(.horizontal) {
                LazyHStack(content: {
                    ForEach(0..<min(success.itemCount, 6), id: \.self) { index in
                        let item = success.peek(index: index)
                        if let media = item?.media {
                            let image = media as? UiMediaImage
                            
                            let shouldBlur = !appSetting.appearanceSettings.showSensitiveContent && (image?.sensitive ?? false)

                          
                            
                            MediaItemComponent(media: media)
                                .if(shouldBlur, transform: { view in
                                    view.blur(radius: 32)
                                })
                                .onAppear(perform: {
                                    success.get(index: index)
                                })
                                .aspectRatio(1, contentMode: .fill)
                                .clipped()
                                .frame(width: 48, height: 48)
                                .allowsHitTesting(false)
                        }
                    }
                })
            }
        case .empty: EmptyView()
        }
    }
}

struct ProfileHeader: View {
    let user: UiState<UiProfile>
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiUserV2, UiRelation) -> Void
    var body: some View {
        switch onEnum(of: user) {
        case .error:
            Text("error")
        case .loading:
            CommonProfileHeader(
                user: createSampleUser(),
                relation: relation,
                isMe: isMe,
                onFollowClick: { _ in }
            )
            .redacted(reason: .placeholder)
        case .success(let data):
            ProfileHeaderSuccess(
                user: data.data,
                relation: relation,
                isMe: isMe,
                onFollowClick: { relation in onFollowClick(data.data, relation) }
            )
        }
    }
}

//struct ProfileHeaderSuccess: View {
//    let user: UiProfile
//    let relation: UiState<UiRelation>
//    let isMe: UiState<KotlinBoolean>
//    let onFollowClick: (UiRelation) -> Void
//    var body: some View {
//        CommonProfileHeader(user: user, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
//    }
//}
//
//struct MatrixView: View {
//    let followCount: String
//    let fansCount: String
//    var body: some View {
//        HStack {
//            Text(followCount)
//                .fontWeight(.bold)
//            Text("正在关注")
//                .foregroundColor(.secondary)
//                .font(.footnote)
//            Divider()
//            Text(fansCount)
//                .fontWeight(.bold)
//            Text("关注者")
//                .foregroundColor(.secondary)
//                .font(.footnote)
//        }
//        .font(.caption)
//    }
//}

// pawoo  的 一些个人 table Info List
//struct FieldsView: View {
//    let fields: [String: UiRichText]
//    var body: some View {
//        if fields.count > 0 {
//            VStack(alignment: .leading) {
//                let keys = fields.map {
//                    $0.key
//                }.sorted()  
//                ForEach(0..<keys.count, id: \.self) { index in
//                    let key = keys[index]
//                    Text(key)
//                        .font(.subheadline)
//                        .foregroundColor(.secondary)
//                    Markdown(fields[key]?.markdown ?? "").markdownTextStyle(textStyle: {
//                        FontFamilyVariant(.normal)
//                        FontSize(.em(0.9))
//                        
////                        LineSpacing(1.3)
//                        ForegroundColor(.primary)
//                    }).markdownInlineImageProvider(.emoji)
//                        .padding(.vertical, 4)
//                    if index != keys.count - 1 {
//                        Divider()
//                    }
//                }
//                .padding(.horizontal)
//            }
//            .padding(.vertical)
//            #if os(iOS)
//                .background(Color(UIColor.secondarySystemBackground))
//            #else
//                .background(Color(NSColor.windowBackgroundColor))
//            #endif
//                .clipShape(RoundedRectangle(cornerRadius: 8))
//        } else {
//            EmptyView()
//        }
//    }
//}

struct ScrollOffsetPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}
