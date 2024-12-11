import SwiftUI
import shared
import MarkdownUI
import OrderedCollections

//user profile 入口
struct ProfileWithUserNameScreen: View {
    @State private var presenter: ProfileWithUserNameAndHostPresenter
    private let accountType: AccountType
    let toProfileMedia: (MicroBlogKey) -> Void

    init(accountType: AccountType, userName: String, host: String, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.accountType = accountType
        presenter = .init(userName: userName, host: host, accountType: accountType)
        self.toProfileMedia = toProfileMedia
    }

    var body: some View {

        // 自动更新UI，根据presenter models
        ObservePresenter(presenter: presenter) { state in
            ZStack {
                //UiState Kotlin 的密封类
                switch onEnum(of: state.user) {
                case .error:
                    Text("error")
                case .loading:
                    List {
                        CommonProfileHeader(user: createSampleUser(), relation: UiStateLoading(), isMe: UiStateLoading(), onFollowClick: { _ in })
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
