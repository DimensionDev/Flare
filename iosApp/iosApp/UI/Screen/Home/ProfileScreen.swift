import SwiftUI
import shared

struct ProfileScreen: View {
    @State var viewModel: ProfileViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    
    init(userKey: MicroBlogKey?) {
        viewModel = ProfileViewModel(userKey: userKey)
    }
    
    var body: some View {
        List {
            ProfileHeader(user: viewModel.model.userState, relation: viewModel.model.relationState, isMe: viewModel.model.isMe, onFollowClick: { user, relation in viewModel.model.follow(user: user, data: relation) })
                .listRowInsets(EdgeInsets())
            StatusTimelineComponent(data: viewModel.model.listState, mastodonEvent: statusEvent, misskeyEvent: statusEvent, blueskyEvent: statusEvent)
        }
        .listStyle(.plain)
        .edgesIgnoringSafeArea(.top)
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
                        Button(action: /*@START_MENU_TOKEN@*/{}/*@END_MENU_TOKEN@*/, label: {
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
        })
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
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown, headerTrailing: { MisskeyFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick) })
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
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown, headerTrailing: { BlueskyFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick) })
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
