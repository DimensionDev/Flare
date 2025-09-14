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
                Text(data.handle)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .lineLimit(1)
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}

extension UserCompatView {
    init(data: UiUserV2) where TrailingContent == EmptyView {
        self.data = data
        self.trailing = {
            EmptyView()
        }
    }
}
