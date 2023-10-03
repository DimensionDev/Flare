import SwiftUI
import shared

struct ProfileScreen: View {
    @State var viewModel: ProfileViewModel
    
    init(userKey: MicroBlogKey?) {
        viewModel = ProfileViewModel(userKey: userKey)
    }
    
    var body: some View {
        List {
            
        }.activateViewModel(viewModel: viewModel)
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
            Text("loading")
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
        Text("")
    }
}

struct MisskeyProfileHeader: View {
    let user: UiUser.Misskey
    let relation: UiState<UiRelation>
    var body: some View {
        CommonProfileHeader(bannerUrl: "", avatarUrl: "", displayName: "", handle: "", description: "")
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
