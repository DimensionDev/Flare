import SwiftUI
import shared

struct BlueskyStatusComponent: View {
    let bluesky: UiStatus.Bluesky
    var body: some View {
        VStack(alignment: .leading) {
            if let repostBy = bluesky.repostBy {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: repostBy.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: bluesky.extra.contentMarkdown, user: bluesky.user, medias: bluesky.medias, timestamp: bluesky.indexedAt.epochSeconds) {
                EmptyView()
            }
            if let card = bluesky.card {
                LinkPreview(card: card)
            }
            if !bluesky.medias.isEmpty {
                MediaComponent(medias: bluesky.medias)
            }
            if let quote = bluesky.quote {
                QuotedStatus(data: quote)
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
                    .if(bluesky.reaction.reposted) { view in
                        view.foregroundStyle(.link)
                    }
                }
                .if(!bluesky.reaction.reposted) { view in
                    view.opacity(0.6)
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "star")
                        if let humanizedFavouriteCount = bluesky.matrices.humanizedLikeCount {
                            Text(humanizedFavouriteCount)
                        }
                    }
                    .if(bluesky.reaction.liked) { view in
                        view.foregroundStyle(.red, .red)
                    }
                }
                .if(!bluesky.reaction.liked) { view in
                    view.opacity(0.6)
                }
                Spacer()

                Menu {
                    if bluesky.isFromMe {
                        Button(role: .destructive,action: {

                        }, label: {
                            Label("Delete Toot", systemImage: "trash")
                        })
                    } else {
                        Button(action: {

                        }, label: {
                            Label("Report", systemImage: "exclamationmark.shield")
                        })
                    }
                } label: {
                    Label(
                        title: { EmptyView() },
                        icon: { Image(systemName: "ellipsis") }
                    )
                    .opacity(0.6)
                }
            }
            .buttonStyle(.borderless)
            .tint(.primary)
            .font(.caption)
        }
    }
}
