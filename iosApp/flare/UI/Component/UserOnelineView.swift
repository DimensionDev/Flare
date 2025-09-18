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
            HStack {
                RichText(text: data.name)
                Text(data.handle)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .lineLimit(1)
    }
}
