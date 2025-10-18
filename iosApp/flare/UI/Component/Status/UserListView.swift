import SwiftUI
import KotlinSharedUI

struct UserListView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentUserList
    var body: some View {
        VStack {
            ScrollView(.horizontal) {
                HStack {
                    ForEach(data.users, id: \.key) { user in
                        UserCompatView(data: user)
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                            .frame(width: 280)
                            .clipShape(.rect(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(.separator), lineWidth: 1)
                            )
                            .onTapGesture {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                    }
                }
            }
            .scrollIndicators(.hidden)

            if let status = data.status {
                VStack {
                    StatusView(data: status, isQuote: true)
                        .padding()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(.rect(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(.separator), lineWidth: 1)
                )
            }
        }
    }
}
