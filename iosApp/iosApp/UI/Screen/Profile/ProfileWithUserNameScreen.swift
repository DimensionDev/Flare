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
                switch onEnum(of: state) {
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
                    ProfileScreen(accountType: accountType, userKey: data.data.userKey, toProfileMedia: toProfileMedia)
                }
            }
        }
    }
}
