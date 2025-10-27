import SwiftUI
import KotlinSharedUI
import Kingfisher

struct FeedView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentFeed
//    @State private var showDetail = false
    var body: some View {
        VStack(
            alignment: .leading
        ) {
            HStack {
                if let sourceIcon = data.sourceIcon, !sourceIcon.isEmpty {
                    NetworkImage(data: sourceIcon)
                        .frame(width: 20, height: 20)
                }
                Text(data.source)
                    .font(.footnote)
                Spacer()
                if let date = data.createdAt {
                    DateTimeText(data: date)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Text(data.title)
            HStack {
                if let desc = data.description_ {
                    Text(desc)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(5)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if let image = data.image {
                    NetworkImage(data: image, customHeader: data.imageHeaders)
                        .frame(width: 72, height: 72)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}
