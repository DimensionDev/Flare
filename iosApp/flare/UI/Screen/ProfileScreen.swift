import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct ProfileScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    @StateObject private var presenter: KotlinPresenter<ProfileState>
    @State private var selectedTab: Int = 0
    
    var body: some View {
        ZStack {
            if horizontalSizeClass == .regular {
                regularBody
            }
            else {
                compatBody
            }
        }
        .background(Color(.systemGroupedBackground))
        .toolbar {
            if horizontalSizeClass == .regular, case .success(let tabState) = onEnum(of: presenter.state.tabs) {
                let tabs = tabState.data.cast(ProfileState.Tab.self)
                if tabs.count > 1 {
                    ToolbarItemGroup {
                        ForEach(0..<tabs.count, id: \.self) { index in
                            let tab = tabs[index]
                            let text = switch onEnum(of: tab) {
                            case .media: LocalizedStringResource(stringLiteral: "profile_tab_media")
                            case .timeline(let timeline):
                                switch timeline.type {
                                case .likes: LocalizedStringResource(stringLiteral: "profile_tab_likes")
                                case .status: LocalizedStringResource(stringLiteral: "profile_tab_status")
                                case .statusWithReplies: LocalizedStringResource(stringLiteral: "profile_tab_replies")
                                }
                            }
                            Button {
                                withAnimation(.spring) {
                                    selectedTab = index
                                }
                            } label: {
                                Text(text)
                                    .foregroundStyle(selectedTab == index ? Color.accentColor : .primary)
                                    .fontWeight(selectedTab == index ? .bold : .regular)
                            }
//                            .onTapGesture {
//                            }
//                            .padding(.horizontal)
//                            .padding(.vertical, 8)
//                            .foregroundStyle(selectedTab == index ? Color.white : .primary)
//                            .backport
//                            .glassEffect(selectedTab == index ? .tinted(.accentColor) : .regular, in: .capsule, fallbackBackground: selectedTab == index ? Color.accentColor : Color(.systemBackground))
                        }
                    }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer()
                }
            }
            if !presenter.state.actions.isEmpty {
                ToolbarItem(
                    placement: .primaryAction
                ) {
                    StatusActionsView(data: presenter.state.actions, useText: false, allowSpacer: false)
                }
            }
        }
        .background(Color(.systemGroupedBackground))
    }
    
    var regularBody: some View {
        HStack(
            spacing: nil,
        ) {
            ScrollView {
                ListCardView {
                    ProfileHeader(
                        user: presenter.state.userState,
                        relation: presenter.state.relationState,
                        isMe: presenter.state.isMe,
                        onFollowClick: { user, relation in
                            presenter.state.follow(userKey: user.key, data: relation)
                        },
                        onFollowingClick: onFollowingClick,
                        onFansClick: onFansClick
                    )
                    .padding(.bottom)
                }
            }
            .padding(.leading)
            .frame(width: 400)
            StateView(state: presenter.state.tabs) { tabsArray in
                let tabs = tabsArray.cast(ProfileState.Tab.self)
                let selectedTabItem = tabs[selectedTab]
                switch onEnum(of: selectedTabItem) {
                case .timeline(let timeline):
                    ProfileTimelineWaterFallView(presenter: timeline.presenter)
                        .id(timeline.type.name)
                case .media(let media):
                    ProfileTimelineWaterFallView(presenter: media.presenter.getMediaTimelinePresenter())
                        .id("media")
                }
            }
        }
    }
    
    var compatBody: some View {
        List {
            ProfileHeader(
                user: presenter.state.userState,
                relation: presenter.state.relationState,
                isMe: presenter.state.isMe,
                onFollowClick: { user, relation in
                    presenter.state.follow(userKey: user.key, data: relation)
                },
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick
            )
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            StateView(state: presenter.state.tabs) { tabsArray in
                let tabs = tabsArray.cast(ProfileState.Tab.self)
                Picker(selection: $selectedTab) {
                    ForEach(0..<tabs.count, id: \.self) { index in
                        let tab = tabs[index]
                        let text = switch onEnum(of: tab) {
                        case .media: LocalizedStringResource(stringLiteral: "profile_tab_media")
                        case .timeline(let timeline):
                            switch timeline.type {
                            case .likes: LocalizedStringResource(stringLiteral: "profile_tab_likes")
                            case .status: LocalizedStringResource(stringLiteral: "profile_tab_status")
                            case .statusWithReplies: LocalizedStringResource(stringLiteral: "profile_tab_replies")
                            }
                        }
                        Text(text)
                            .tag(index)
                    }
                } label: {
                    
                }
                .pickerStyle(.segmented)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding()
                .listRowBackground(Color.clear)
                let selectedTabItem = tabs[selectedTab]
                switch onEnum(of: selectedTabItem) {
                case .timeline(let timeline):
                    ProfileTimelineView(presenter: timeline.presenter)
                        .id(timeline.type.name)
                case .media(let media):
                    ProfileTimelineView(presenter: media.presenter.getMediaTimelinePresenter())
                        .id("media")
                }
            }
        }
        .detectScrolling()
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
        .edgesIgnoringSafeArea(.top)
    }
}

extension ProfileScreen {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey?,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void
    ) {
        self.init(
            accountType: accountType,
            userKey: userKey,
            onFollowingClick: onFollowingClick,
            onFansClick: onFansClick,
            presenter: .init(presenter: ProfilePresenter(accountType: accountType, userKey: userKey))
        )
    }
}

struct ProfileHeader: View {
    let user: UiState<UiProfile>
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiProfile, UiRelation) -> Void
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    var body: some View {
        switch onEnum(of: user) {
        case .error:
            Text("error")
        case .loading:
            CommonProfileHeader(
                user: createSampleUser(),
                relation: relation,
                isMe: isMe,
                onFollowClick: { _ in },
                onFollowingClick: {},
                onFansClick: {}
            )
                .redacted(reason: .placeholder)
        case .success(let data):
            ProfileHeaderSuccess(
                user: data.data,
                relation: relation,
                isMe: isMe,
                onFollowClick: { relation in onFollowClick(data.data, relation) },
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick
            )
        }
    }
}

struct ProfileHeaderSuccess: View {
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    var body: some View {
        CommonProfileHeader(
            user: user,
            relation: relation,
            isMe: isMe,
            onFollowClick: onFollowClick,
            onFollowingClick: {
                onFollowingClick(user.key)
            },
            onFansClick: {
                onFansClick(user.key)
            }
        )
    }
}

struct ProfileWithUserNameAndHostScreen: View {
    @StateObject private var presenter: KotlinPresenter<UserState>
    let accountType: AccountType
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    
    init(userName: String, host: String, accountType: AccountType, onFollowingClick: @escaping (MicroBlogKey) -> Void, onFansClick: @escaping (MicroBlogKey) -> Void) {
        self.accountType = accountType
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
        self._presenter = .init(wrappedValue: .init(presenter: ProfileWithUserNameAndHostPresenter(userName: userName, host: host, accountType: accountType)))
    }
    var body: some View {
        StateView(state: presenter.state.user) { user in
            ProfileScreen(accountType: accountType, userKey: user.key, onFollowingClick: onFollowingClick, onFansClick: onFansClick)
        } loadingContent: {
            ProgressView()
        }
    }
}

struct ProfileTimelineView: View {
    @StateObject private var presenter: KotlinPresenter<TimelineState>
    
    init(presenter: TimelinePresenter) {
        self._presenter = .init(wrappedValue: .init(presenter: presenter))
    }
    
    var body: some View {
        TimelinePagingView(data: presenter.state.listState)
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .padding(.horizontal)
            .listRowBackground(Color.clear)
//            .refreshable {
//                try? await presenter.state.refresh()
//            }
    }
}

struct ProfileTimelineWaterFallView: View {
    @StateObject private var presenter: KotlinPresenter<TimelineState>
    
    init(presenter: TimelinePresenter) {
        self._presenter = .init(wrappedValue: .init(presenter: presenter))
    }
    
    var body: some View {
        TimelinePagingContent(data: presenter.state.listState, detailStatusKey: nil, key: presenter.key)
            .refreshable {
                try? await presenter.state.refresh()
            }
    }
}
