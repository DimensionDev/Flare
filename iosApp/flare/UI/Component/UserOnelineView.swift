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
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .lineLimit(1)
    }
}

extension UserOnelineView {
    init(data: UiUserV2) where TrailingContent == EmptyView {
        self.data = data
        self.trailing = {
            EmptyView()
        }
    }
}
