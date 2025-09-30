import SwiftUI
@preconcurrency import KotlinSharedUI

struct ProfileScreen: View {
    let accountType: AccountType
    let userKey: MicroBlogKey?
    @StateObject private var presenter: KotlinPresenter<ProfileState>
    @State private var selectedTab: Int = 0

    var body: some View {
        List {
            ProfileHeader(
                user: presenter.state.userState,
                relation: presenter.state.relationState,
                isMe: presenter.state.isMe,
                onFollowClick: { user, relation in
                    presenter.state.follow(userKey: user.key, data: relation)
                }
            )
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            StateView(state: presenter.state.tabs) { tabsArray in
                let tabs = tabsArray.cast(ProfileState.Tab.self)
                Picker(selection: $selectedTab) {
                    ForEach(0..<tabs.count) { index in
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
                    TimelinePagingView(data: timeline.data)
                        .listRowSeparator(.hidden)
                        .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                        .padding(.horizontal)
                        .listRowBackground(Color.clear)
                case .media(let media):
                    EmptyView()
                }
            }
        }
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
        .background(Color(.systemGroupedBackground))
        .toolbar {
            Menu {
                if case .success(let user) = onEnum(of: presenter.state.userState) {
                    if case .success(let isMe) = onEnum(of: presenter.state.isMe), !isMe.data.boolValue {
                        if case .success(let relation) = onEnum(of: presenter.state.relationState),
                           case .success(let actionsArray) = onEnum(of: presenter.state.actions),
                           actionsArray.data.count > 0 {
                            let actions = actionsArray.data.cast(ProfileAction.self)
                            ForEach(0..<actions.count, id: \.self) { index in
                                let item = actions[index]
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
                        Button(action: { presenter.state.report(userKey: user.data.key) }, label: {
                            Label("report", systemImage: "exclamationmark.bubble")
                        })
                    }
                }
            } label: {
                Image(systemName: "ellipsis")
            }
        }
        .refreshable {
            try? await presenter.state.refresh()
        }
        .edgesIgnoringSafeArea(.top)
        .background(Color(.systemGroupedBackground))
    }
}

extension ProfileScreen {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey?
    ) {
        self.init(accountType: accountType, userKey: userKey, presenter: .init(presenter: ProfilePresenter(accountType: accountType, userKey: userKey)))
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

struct ProfileHeaderSuccess: View {
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        CommonProfileHeader(user: user, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
    }
}

struct ProfileWithUserNameAndHostScreen: View {
    @StateObject private var presenter: KotlinPresenter<UserState>
    let accountType: AccountType
    
    init(userName: String, host: String, accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: ProfileWithUserNameAndHostPresenter(userName: userName, host: host, accountType: accountType)))
    }
    var body: some View {
        StateView(state: presenter.state.user) { user in
            ProfileScreen(accountType: accountType, userKey: user.key)
        } loadingContent: {
            ProgressView()
        }
    }
}
