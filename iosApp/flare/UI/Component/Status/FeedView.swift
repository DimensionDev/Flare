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
            if let image = data.image {
                AdaptiveGrid(singleFollowsImageAspect: false, spacing: 4, maxColumns: 3) {
                    Color.clear
                        .overlay {
                            NetworkImage(data: image)
                        }
                        .clipped()
                }
                .clipped()
            }
            Text(data.title)
            if let desc = data.description_ {
                Text(desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
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
        }
//        .onTapGesture {
//            showDetail = true
//        }
//        .sheet(isPresented: $showDetail) {
//            if let url = URL(string: data.url) {
//                SafariView(url: url)
//            } else {
//                Text("Invalid URL")
//            }
//        }
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}
