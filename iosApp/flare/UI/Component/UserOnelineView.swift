import SwiftUI
import KotlinSharedUI

struct UserOnelineView<TrailingContent: View>: View {
    @Environment(\.openURL) private var openURL
    let data: UiUserV2
    let trailing: () -> TrailingContent
    var body: some View {
        HStack {
            AvatarView(data: data.avatar)
                .frame(width: 20, height: 20)
            RichText(text: data.name)
            Text(data.handle)
                .font(.caption)
            trailing()
        }
        .lineLimit(1)
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}
