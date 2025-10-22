import SwiftUI
import KotlinSharedUI

struct UiRssView: View {
    let data: UiRssSource
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            Label {
                if let title = data.title {
                    Text(title)
                } else {
                    Text(data.host)
                }
            } icon: {
                if let favIcon = data.favIcon, !favIcon.isEmpty {
                    NetworkImage(data: favIcon)
                        .frame(width: 24, height: 24)
                } else {
                    Image("fa-square-rss")
                }
            }
            Text(data.url)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
