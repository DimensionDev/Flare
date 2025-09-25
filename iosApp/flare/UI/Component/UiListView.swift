import SwiftUI
import KotlinSharedUI

struct UiListView: View {
    let data: UiList
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            Label {
                Text(data.title)
            } icon: {
                if let image = data.avatar {
                    AvatarView(data: image)
                        .frame(width: 24, height: 24)
                } else {
                    Image("fa-list")
                }
            }
            if let desc = data.description_, !desc.isEmpty {
                Text(desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct UiListPlaceholder: View {
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            HStack {
                Rectangle()
                    .fill(.placeholder)
                    .frame(width: 24, height: 24)
                    .clipShape(.circle)
                Text("list title")
            }
            Text("list description")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .redacted(reason: .placeholder)
    }
}
