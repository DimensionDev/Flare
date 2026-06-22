import AppleFontAwesome
import KotlinSharedUI
import SwiftUI

public struct UiRssView: View {
    private let data: UiRssSource

    public init(data: UiRssSource) {
        self.data = data
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label {
                if let title = data.title, !title.isEmpty {
                    Text(title)
                } else {
                    Text(data.host)
                }
            } icon: {
                if let favIcon = data.favIcon, !favIcon.isEmpty {
                    NetworkImage(data: favIcon)
                        .frame(width: 24, height: 24)
                } else {
                    Image(fontAwesome: .squareRss)
                }
            }

            Text(data.url)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .truncationMode(.middle)
        }
    }
}

extension UiRssSource: @retroactive Identifiable {}
