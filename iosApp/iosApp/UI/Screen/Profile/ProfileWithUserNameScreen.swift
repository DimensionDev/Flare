import SwiftUI
import shared

struct ProfileWithUserNameScreen: View {
    @State private var viewModel: ProfileWithUserNameViewModel
    let toProfileMedia: (MicroBlogKey) -> Void

    init(userName: String, host: String, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        viewModel = .init(userName: userName, host: host)
        self.toProfileMedia = toProfileMedia
    }
    var body: some View {
        ZStack {
            switch onEnum(of: viewModel.model) {
            case .error:
                Text("error")
            case .loading:
                List {
                    CommonProfileHeader(
                        bannerUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500",
                        avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg",
                        displayName: "test",
                        handle: "test@test.test",
                        description: "tefewfewfewfewfewst",
                        headerTrailing: {
                            Text("header")
                        }, handleTrailing: {
                            Text("handle")
                        }, content: {
                            Text("content")
                        }
                    )
                    .redacted(reason: .placeholder)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets())
                }
            case .success(let data):
                ProfileScreen(userKey: data.data.userKey, toProfileMedia: toProfileMedia)
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class ProfileWithUserNameViewModel: MoleculeViewModelProto {
    let presenter: ProfileWithUserNameAndHostPresenter
    var model: UiState<UiUser>
    typealias Model = UiState<UiUser>
    typealias Presenter = ProfileWithUserNameAndHostPresenter

    init(userName: String, host: String) {
        presenter = .init(userName: userName, host: host)
        model = presenter.models.value
    }
}
