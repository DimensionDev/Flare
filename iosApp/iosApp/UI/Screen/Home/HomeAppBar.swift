import Awesome
import shared
import SwiftUI
import MarkdownUI

struct HomeAppBar: ToolbarContent {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    let router: Router
    let accountType: AccountType
    @Binding var showSettings: Bool
    @Binding var showLogin: Bool
    @State private var presenter = ActiveAccountPresenter()
 
    var body: some ToolbarContent {
        if !(accountType is AccountTypeGuest) {
            // 左边的用户头像按钮
            ToolbarItem(placement: .navigation) {
                ObservePresenter(presenter: presenter) { state in
                    switch onEnum(of: state.user) {
                    case .loading:
                        Button {
                            showSettings = true
                        } label: {
                            userAvatarPlaceholder(size: 28)
                                .clipShape(Circle())
                        }
                    case .error:
                        Button {
                            showSettings = true
                        } label: {
                            Awesome.Classic.Solid.user.image
                                .foregroundColor(.init(.accentColor))
                        }
                    case .success(let data):
                        Button {
                            showSettings = true
                        } label: {
                            UserAvatar(data: data.data.avatar, size: 28)
                                .clipShape(Circle())
                        }
                    }
                }
            }
        } else {
            // 访客模式下显示登录按钮
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showLogin = true
                } label: {
                    Text("Login")
                }
            }
        }
    }
}
