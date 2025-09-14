import SwiftUI
import KotlinSharedUI
import Kingfisher

struct FeedView: View {
    let data: UiTimeline.ItemContentFeed
    var body: some View {
        VStack(
            alignment: .leading
        ) {
            if let image = data.image {
                AdaptiveMosaic([image], singleMode: .force16x9) { image in
                    NetworkImage(data: image)
                        .clipped()
                }
                .clipped()
            }
            Text(data.title)
            if let desc = data.description_ {
                Text(desc)
                    .font(.caption)
            }
            HStack {
                NetworkImage(data: data.sourceIcon)
                    .frame(width: 20, height: 20)
                Text(data.source)
                    .font(.footnote)
                Spacer()
                if let date = data.createdAt {
                    DateTimeText(data: date)
                }
            }
        }
    }
}

