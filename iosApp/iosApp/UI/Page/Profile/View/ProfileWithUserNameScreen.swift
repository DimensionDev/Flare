import SwiftUI
import shared
import MarkdownUI
import OrderedCollections
import os.log

//user profile 入口
struct ProfileWithUserNameScreen: View {
    @State private var presenter: ProfileWithUserNameAndHostPresenter
    private let accountType: AccountType
    let toProfileMedia: (MicroBlogKey) -> Void
    
    init(accountType: AccountType, userName: String, host: String, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.accountType = accountType
        presenter = .init(userName: userName, host: host, accountType: accountType)
        self.toProfileMedia = toProfileMedia
        os_log("[📔][ProfileWithUserNameScreen - init]ProfileWithUserNameScreen: userName=%{public}@, host=%{public}@", log: .default, type: .debug, userName, host)
    }

    var body: some View { 
        ObservePresenter(presenter: presenter) { state in
            ZStack {
                switch onEnum(of: state.user) {
                case .error:
                    Text("error")
                        .onAppear {
                            os_log("[📔][ProfileWithUserNameScreen]加载用户信息失败", log: .default, type: .error)
                        }
                case .loading:
                    List {
                        CommonProfileHeader(
                                      userInfo: ProfileUserInfo(
                                          profile: createSampleUser(),
                                          relation: nil,
                                          isMe: false,
                                          followCount: "0",
                                          fansCount: "0",
                                          fields: [:],
                                          canSendMessage: false
                                      ),
                                      state: nil,
                                      onFollowClick: { _ in }
                        )
                        .redacted(reason: .placeholder)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                    }
                    .onAppear {
                        os_log("[📔][ProfileWithUserNameScreen]正在加载用户信息...", log: .default, type: .debug)
                    }
                case .success(let data):
                    ProfileScreen(
                        accountType: accountType, 
                        userKey: data.data.key, 
                        toProfileMedia: toProfileMedia
                    )
                    .onAppear {
                        os_log("[📔][ProfileWithUserNameScreen]成功加载用户信息: userKey=%{public}@", log: .default, type: .debug, data.data.key.description)
                    }
                }
            }
        }
    } 
}
