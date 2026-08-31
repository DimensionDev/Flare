import SwiftUI
import KotlinSharedUI
import FlareAppleCore

struct UserListView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimelineV2.UserList

    var body: some View {
        VStack(spacing: 8) {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(data.users, id: \.key) { user in
                        UserCompatView(
                            data: user,
                            onClicked: {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        )
                            .padding(8)
                            .frame(width: 280)
                            .clipShape(.rect(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.flareSeparator, lineWidth: 1)
                            )
                    }
                }
            }
            .scrollIndicators(.hidden)
            if let status = data.post {
                VStack(spacing: 0) {
                    StatusView(data: status, isQuote: true, forceHideActions: true)
                        .padding(8)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(.rect(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.flareSeparator, lineWidth: 1)
                )
            }
        }
    }
}
