import SwiftUI
import OrderedCollections
import shared
import MarkdownUI

struct ProfileScreen: View {
    @State var viewModel: ProfileViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    
    init(userKey: MicroBlogKey?) {
        _viewModel = State(initialValue: ProfileViewModel(userKey: userKey))
    }
    
    var body: some View {
        let title: LocalizedStringKey = if case .success(let user) = onEnum(of: viewModel.model.userState) {
            LocalizedStringKey(user.data.extra.nameMarkdown)
        } else {
            "Profile"
        }
        HStack {
            if horizontalSizeClass != .compact {
                ScrollView {
                    VStack {
                        ProfileHeader(user: viewModel.model.userState, relation: viewModel.model.relationState, isMe: viewModel.model.isMe, onFollowClick: { user, relation in viewModel.model.follow(user: user, data: relation) })
                        LargeProfileImagePreviews(state: viewModel.model.mediaState)
                    }
                }
                .frame(width: 384)
            }
            List {
                if horizontalSizeClass == .compact {
                    ProfileHeader(user: viewModel.model.userState, relation: viewModel.model.relationState, isMe: viewModel.model.isMe, onFollowClick: { user, relation in viewModel.model.follow(user: user, data: relation) })
                        .listRowInsets(EdgeInsets())
                        .padding(.bottom)
                    SmallProfileMediaPreviews(state: viewModel.model.mediaState)
                        .listRowInsets(.none)
                }
                StatusTimelineComponent(data: viewModel.model.listState, mastodonEvent: statusEvent, misskeyEvent: statusEvent, blueskyEvent: statusEvent)
            }
            .listStyle(.plain)
        }
        .if(horizontalSizeClass == .compact, transform: { view in
            view
                .ignoresSafeArea(edges: .top)
        })
        .if(horizontalSizeClass != .compact, transform: { view in
            view
                .navigationBarTitleDisplayMode(.inline)
                .navigationTitle(title)
        })
        .toolbar {
            Menu {
                if case .success(let user) = onEnum(of: viewModel.model.userState) {
                    if case .success(let isMe) = onEnum(of: viewModel.model.isMe), !isMe.data.boolValue {
                        if case .success(let relation) = onEnum(of: viewModel.model.relationState) {
                            switch onEnum(of: relation.data) {
                            case .bluesky(let blueskyRelation):
                                BlueskyMenu(relation: blueskyRelation, onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) }, onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) })
                            case .mastodon(let mastodonRelation):
                                MastodonMenu(relation: mastodonRelation, onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) }, onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) })
                            case .misskey(let misskeyRelation):
                                MisskeyMenu(relation: misskeyRelation, onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) }, onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) })
                            }
                        }
                        Button(action: { viewModel.model.report(user: user.data) }, label: {
                            Label("Report", systemImage: "exclamationmark.bubble")
                        })
                    }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
            
        }
        .activateViewModel(viewModel: viewModel)
    }
}

struct LargeProfileImagePreviews : View {
    let state: UiState<LazyPagingItemsProxy<UiMedia>>
    var body: some View {
        switch onEnum(of: state) {
        case .error(_):
            EmptyView()
        case .loading(_):
            EmptyView()
        case .success(let success):
            if success.data.isSuccess {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    ForEach(0..<min(success.data.itemCount, 6), id: \.self) { index in
                        let item = success.data.peek(index: index)
                        if let media = item {
                            let image = media as? UiMediaImage
                            let shouldBlur = image?.sensitive ?? false
                            MediaItemComponent(media: media)
                                .if(shouldBlur, transform: { view in
                                    view.blur(radius: 32)
                                })
                                .onAppear(perform: {
                                    success.data.get(index: index)
                                })
                                .aspectRatio(1, contentMode: .fill)
                                .clipped()
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .padding(.horizontal)
            }
        }
    }
}

struct SmallProfileMediaPreviews : View {
    let state: UiState<LazyPagingItemsProxy<UiMedia>>
    var body: some View {
        switch onEnum(of: state) {
        case .error(_):
            EmptyView()
        case .loading(_):
            EmptyView()
        case .success(let success):
            if success.data.isSuccess {
                ScrollView(.horizontal) {
                    LazyHStack(content: {
                        ForEach(0..<min(success.data.itemCount, 6), id: \.self) { index in
                            let item = success.data.peek(index: index)
                            if let media = item {
                                let image = media as? UiMediaImage
                                let shouldBlur = image?.sensitive ?? false
                                MediaItemComponent(media: media)
                                    .if(shouldBlur, transform: { view in
                                        view.blur(radius: 32)
                                    })
                                    .onAppear(perform: {
                                        success.data.get(index: index)
                                    })
                                    .aspectRatio(1, contentMode: .fill)
                                    .clipped()
                                    .frame(width: 48, height: 48)
                            }
                        }
                    })
                }
            }
        }
    }
}

struct BlueskyMenu: View {
    let relation: UiRelationBluesky
    let onMuteClick: () -> Void
    let onBlockClick: () -> Void
    var body: some View {
        Button(action: onMuteClick, label: {
            let text = if relation.muting {
                "Unmute"
            } else {
                "Mute"
            }
            let icon = if relation.muting {
                "speaker"
            } else {
                "speaker.slash"
            }
            Label(text, systemImage: icon)
        })
        Button(action: onBlockClick, label: {
            let text = if relation.blocking {
                "Unblock"
            } else {
                "Block"
            }
            let icon = if relation.blocking {
                "xmark.circle"
            } else {
                "checkmark.circle"
            }
            Label(text, systemImage: icon)
        })
    }
}
struct MastodonMenu: View {
    let relation: UiRelationMastodon
    let onMuteClick: () -> Void
    let onBlockClick: () -> Void
    var body: some View {
        Button(action: onMuteClick, label: {
            let text = if relation.muting {
                "Unmute"
            } else {
                "Mute"
            }
            let icon = if relation.muting {
                "speaker"
            } else {
                "speaker.slash"
            }
            Label(text, systemImage: icon)
        })
        Button(action: onBlockClick, label: {
            let text = if relation.blocking {
                "Unblock"
            } else {
                "Block"
            }
            let icon = if relation.blocking {
                "xmark.circle"
            } else {
                "checkmark.circle"
            }
            Label(text, systemImage: icon)
        })
    }
}
struct MisskeyMenu: View {
    let relation: UiRelationMisskey
    let onMuteClick: () -> Void
    let onBlockClick: () -> Void
    var body: some View {
        Button(action: onMuteClick, label: {
            let text = if relation.muted {
                "Unmute"
            } else {
                "Mute"
            }
            let icon = if relation.muted {
                "speaker"
            } else {
                "speaker.slash"
            }
            Label(text, systemImage: icon)
        })
        Button(action: onBlockClick, label: {
            let text = if relation.blocking {
                "Unblock"
            } else {
                "Block"
            }
            let icon = if relation.blocking {
                "xmark.circle"
            } else {
                "checkmark.circle"
            }
            Label(text, systemImage: icon)
        })
    }
}

struct ProfileHeader: View {
    let user: UiState<UiUser>
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiUser, UiRelation) -> Void
    
    var body: some View {
        switch onEnum(of: user) {
        case .error(_):
            Text("error")
        case .loading:
            CommonProfileHeader(bannerUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", displayName: "test", handle: "test@test.test", description: "tefewfewfewfewfewst", headerTrailing: {
                Text("header")
            }, handleTrailing: {
                Text("handle")
            }, content: {
                Text("content")
            })
            .redacted(reason: .placeholder)
        case .success(let data):
            ProfileHeaderSuccess(user: data.data, relation: relation, isMe: isMe, onFollowClick: { relation in onFollowClick(data.data, relation) })
        }
    }
}

struct ProfileHeaderSuccess: View {
    let user: UiUser
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        switch onEnum(of: user) {
        case .mastodon(let mastodon):
            MastodonProfileHeader(user: mastodon, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        case .misskey(let misskey):
            MisskeyProfileHeader(user: misskey, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        case .bluesky(let bluesky):
            BlueskyProfileHeader(user: bluesky, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        }
    }
}

struct MastodonProfileHeader: View {
    let user: UiUser.Mastodon
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown, headerTrailing: {
            MastodonFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        }, content: {
            VStack {
                MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)
                FieldsView(fields: user.extra.fieldsMarkdown)
            }
        })
    }
}

struct MatrixView : View {
    let followCount: String
    let fansCount: String
    var body: some View {
        HStack {
            Text(followCount)
            Text("following")
            Divider()
            Text(fansCount)
            Text("followers")
        }
        .font(.caption)
    }
}

struct FieldsView : View {
    let fields: ImmutableListWrapper<KotlinPair<NSString, NSString>>
    var body: some View {
        if fields.size > 0 {
            VStack(alignment: .leading) {
                ForEach(0..<fields.size, id: \.self) { index in
                    let key = fields.get(index: index).first as? String ?? ""
                    let value = fields.get(index: index).second as? String ?? ""
                    Text(key)
                        .font(.caption)
                    Markdown(value)
                        .font(.body)
                        .markdownInlineImageProvider(.emoji)
                    if index != fields.size - 1 {
                        Divider()
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
            .background(Color(uiColor: UIColor.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            EmptyView()
        }
        
    }
}

struct MastodonFollowButton: View {
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        if case .success(let success) = onEnum(of: isMe), !success.data.boolValue, case .success(let relationData) = onEnum(of: relation), let mastodonRelation = relationData.data as? UiRelationMastodon {
            let text = if mastodonRelation.blocking {
                "Blocked"
            } else if mastodonRelation.following {
                "Following"
            } else if mastodonRelation.requested {
                "Requested"
            } else {
                "Follow"
            }
            Button(action: {
                onFollowClick(mastodonRelation)
            }, label: {
                Text(text)
            })
            .buttonStyle(.borderless)
        } else {
            EmptyView()
        }
    }
}

struct MisskeyProfileHeader: View {
    let user: UiUser.Misskey
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown, headerTrailing: { MisskeyFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick) }, content: {
            VStack {
                MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)
                FieldsView(fields: user.extra.fieldsMarkdown)
            }
        })
    }
}

struct MisskeyFollowButton: View {
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        if case .success(let success) = onEnum(of: isMe), !success.data.boolValue, case .success(let relationData) = onEnum(of: relation), let actualRelation = relationData.data as? UiRelationMisskey {
            let text = if actualRelation.blocking {
                "Blocked"
            } else if actualRelation.following {
                "Following"
            } else if actualRelation.hasPendingFollowRequestFromYou {
                "Requested"
            } else {
                "Follow"
            }
            Button(action: {
                onFollowClick(actualRelation)
            }, label: {
                Text(text)
            })
            .buttonStyle(.borderless)
        } else {
            EmptyView()
        }
    }
}

struct BlueskyProfileHeader: View {
    let user: UiUser.Bluesky
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown, headerTrailing: { BlueskyFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick) }, content: {
            MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)
        })
    }
}

struct BlueskyFollowButton: View {
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        if case .success(let success) = onEnum(of: isMe), !success.data.boolValue, case .success(let relationData) = onEnum(of: relation), let actualRelation = relationData.data as? UiRelationBluesky {
            let text = if actualRelation.blocking {
                "Blocked"
            } else if actualRelation.following {
                "Following"
            } else {
                "Follow"
            }
            Button(action: {
                onFollowClick(actualRelation)
            }, label: {
                Text(text)
            })
            .buttonStyle(.borderless)
        } else {
            EmptyView()
        }
    }
}

@Observable
class ProfileViewModel: MoleculeViewModelProto {
    let presenter: ProfilePresenter
    var model: ProfileState
    typealias Model = ProfileState
    typealias Presenter = ProfilePresenter
    
    init(userKey: MicroBlogKey?) {
        self.presenter = ProfilePresenter(userKey: userKey)
        self.model = presenter.models.value
    }
}
