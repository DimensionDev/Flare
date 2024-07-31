import SwiftUI
import shared

struct ProfileWithUserNameScreen: View {
    let presenter: ProfileWithUserNameAndHostPresenter
    private let accountType: AccountType
    let toProfileMedia: (MicroBlogKey) -> Void

    init(accountType: AccountType, userName: String, host: String, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.accountType = accountType
        presenter = .init(userName: userName, host: host, accountType: accountType)
        self.toProfileMedia = toProfileMedia
    }
    var body: some View {
        Observing(presenter.models) { state in
            ZStack {
                switch onEnum(of: state.user) {
                case .error:
                    Text("error")
                case .loading:
                    List {
                        CommonProfileHeader(user: createSampleUser(), relation: UiStateLoading(), isMe: UiStateLoading(), onFollowClick: {_ in })
                        .redacted(reason: .placeholder)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                    }
                case .success(let data):
                    ProfileScreen(accountType: accountType, userKey: data.data.key, toProfileMedia: toProfileMedia)
                }
            }
        }
    }
}
