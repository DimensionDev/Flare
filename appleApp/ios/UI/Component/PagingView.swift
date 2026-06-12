import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct UserPagingView: View {
    @Environment(\.openURL) private var openURL
    let data: PagingState<UiProfile>
    var body: some View {
        PagingView(data: data) { user in
            UserCompatView(data: user)
                .onTapGesture {
                    user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
        } loadingContent: {
            UserLoadingView()
                .padding(.vertical, 8)
        }
    }
}
