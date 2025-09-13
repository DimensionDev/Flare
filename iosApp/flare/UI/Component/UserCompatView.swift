import SwiftUI
import KotlinSharedUI

struct UserCompatView<TrailingContent: View>: View {
    @Environment(\.openURL) private var openURL
    let data: UiUserV2
    let trailing: () -> TrailingContent
    var body: some View {
        HStack {
            AvatarView(data: data.avatar)
                .frame(width: 44, height: 44)
            VStack(
                alignment: .leading
            ) {
                RichText(text: data.name)
                    .lineLimit(1)
                Text(data.handle)
                    .lineLimit(1)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}
