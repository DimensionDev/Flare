import SwiftUI
import shared

struct ProfileScreen: View {
    @State var viewModel: ProfileViewModel
    
    init(userKey: MicroBlogKey?) {
        viewModel = ProfileViewModel(userKey: userKey)
    }
    
    var body: some View {
        List {
            ProfileHeader(user: viewModel.model.userState, relation: viewModel.model.relationState)
                .listRowInsets(EdgeInsets())
            StatusTimelineStateBuilder(data: viewModel.model.listState)
        }
        .listStyle(.plain)
        .edgesIgnoringSafeArea(.top)
        .toolbar {
            Menu {
                
            } label: {
                Image(systemName: "ellipsis.circle")
            }

        }
        .activateViewModel(viewModel: viewModel)
    }
}

struct ProfileHeader: View {
    let user: UiState<UiUser>
    let relation: UiState<UiRelation>
    
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
            ProfileHeaderSuccess(user: data.data, relation: relation)
        }
    }
}

struct ProfileHeaderSuccess: View {
    let user: UiUser
    let relation: UiState<UiRelation>
    var body: some View {
        switch onEnum(of: user) {
        case .mastodon(let mastodon):
            MastodonProfileHeader(user: mastodon, relation: relation)
        case .misskey(let misskey):
            MisskeyProfileHeader(user: misskey, relation: relation)
        }
    }
}

struct MastodonProfileHeader: View {
    let user: UiUser.Mastodon
    let relation: UiState<UiRelation>
    var body: some View {
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown)
    }
}

struct MisskeyProfileHeader: View {
    let user: UiUser.Misskey
    let relation: UiState<UiRelation>
    var body: some View {
        CommonProfileHeader(bannerUrl: user.bannerUrl, avatarUrl: user.avatarUrl, displayName: user.extra.nameMarkdown, handle: user.handle, description: user.extra.descriptionMarkdown)
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
        self.model = presenter.models.value!
    }
}

#Preview {
    ProfileScreen(userKey: nil)
}
