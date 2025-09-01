import MarkdownUI
import OrderedCollections
import os.log
import shared
import SwiftUI

struct ProfileWithUserNameScreen: View {
    @State private var presenter: ProfileWithUserNameAndHostPresenter
    private let accountType: AccountType
    @Environment(FlareRouter.self) var router
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, userName: String, host: String) {
        self.accountType = accountType
        presenter = .init(userName: userName, host: host, accountType: accountType)
        os_log("[📔][ProfileWithUserNameScreen - init]ProfileWithUserNameScreen: userName=%{public}@, host=%{public}@", log: .default, type: .debug, userName, host)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            ZStack {
                switch onEnum(of: state.user) {
                case .error:
                    Text("error")
                        .onAppear {
                            FlareLog.error("ProfileWithUserNameScreen 加载用户信息失败")
                        }
                case .loading:
                    List {
                        ProfileHeaderSwiftUIViewV2(
                            userInfo: ProfileUserInfo(
                                profile: createSampleUser(),
                                relation: nil,
                                isMe: false,
                                followCount: "0",
                                fansCount: "0",
                                fields: [:],
                                canSendMessage: false
                            ),
                            scrollProxy: nil,
                            presenter: nil
                        )
                        .redacted(reason: .placeholder)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                    }
                    .scrollContentBackground(.hidden)
                    .listRowBackground(theme.primaryBackgroundColor)
                    .onAppear {
                        FlareLog.debug("ProfileWithUserNameScreen 正在加载用户信息...")
                    }
                case let .success(data):
                    // (lldb) po state dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter$body$1@1d3717a0
//                    (lldb) p state
//                    (SharedUserState) 0x0000000000000000
//
//                    let loadedUserInfo = ProfileUserInfo.from(state: state as! ProfileNewState)

                    ProfileSwiftUIViewV2(
                        accountType: accountType,
                        userKey: data.data.key
                    )
                    .onAppear {
                        os_log("[📔][ProfileWithUserNameScreen]成功加载用户信息: userKey=%{public}@", log: .default, type: .debug, data.data.key.description)
                    }
                }
            }
        }
    }
}
