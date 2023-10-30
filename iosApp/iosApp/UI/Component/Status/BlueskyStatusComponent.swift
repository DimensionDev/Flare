import SwiftUI
import shared

struct BlueskyStatusComponent: View {
    let bluesky: UiStatus.Bluesky
    var body: some View {
        VStack(alignment: .leading) {
            if let repostBy = bluesky.repostBy {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: repostBy.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: bluesky.extra.contentMarkdown, avatar: bluesky.user.avatarUrl, name: bluesky.user.extra.nameMarkdown, handle: bluesky.user.handle, userKey: bluesky.user.userKey, medias: bluesky.medias, timestamp: bluesky.indexedAt.epochSeconds) {
                EmptyView()
            }
            if let card = bluesky.card {
                LinkPreview(card: card)
            }
            Spacer()
                .frame(height: 8)
            HStack {
                Button(action: {
                }) {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = bluesky.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "arrow.left.arrow.right")
                        if let humanizedReblogCount = bluesky.matrices.humanizedRepostCount {
                            Text(humanizedReblogCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "star")
                        if let humanizedFavouriteCount = bluesky.matrices.humanizedLikeCount {
                            Text(humanizedFavouriteCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    Image(systemName: "ellipsis")
                }
            }
            .buttonStyle(.borderless)
            .tint(.primary)
            .opacity(0.6)
            .font(.caption)
        }
    }
}
