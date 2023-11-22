import SwiftUI
import MarkdownUI
import shared

struct MastodonStatusComponent: View {
    let mastodon: UiStatus.Mastodon
    var body: some View {
        let actual = mastodon.reblogStatus ?? mastodon
        VStack(alignment: .leading) {
            if mastodon.reblogStatus != nil {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: mastodon.user.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: actual.extra.contentMarkdown, user: actual.user, medias: actual.media, timestamp: actual.createdAt.epochSeconds, headerTrailing: {
                MastodonVisibilityIcon(visibility: actual.visibility)
            })
            if let card = mastodon.card {
                LinkPreview(card: card)
            }
            Spacer()
                .frame(height: 8)
            HStack {
                Button(action: {
                }) {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = actual.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "arrow.left.arrow.right")
                        if let humanizedReblogCount = actual.matrices.humanizedReblogCount {
                            Text(humanizedReblogCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "star")
                        if let humanizedFavouriteCount = actual.matrices.humanizedFavouriteCount {
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
