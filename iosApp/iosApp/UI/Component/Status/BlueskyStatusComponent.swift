import SwiftUI
import shared

struct BlueskyStatusComponent: View {
    let bluesky: UiStatus.Bluesky
    let event: BlueskyStatusEvent
    var body: some View {
        VStack(alignment: .leading) {
            if let repostBy = bluesky.repostBy {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: repostBy.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: bluesky.extra.contentMarkdown, user: bluesky.user, medias: bluesky.medias, timestamp: bluesky.indexedAt.epochSeconds, headerTrailing: { EmptyView() }, onMediaClick: { media in event.onMediaClick(media: media) })
            if let card = bluesky.card {
                LinkPreview(card: card)
            }
            if let quote = bluesky.quote {
                QuotedStatus(data: quote, onMediaClick: event.onMediaClick)
            }
            Spacer()
                .frame(height: 8)
            HStack {
                Button(action: {
                    event.onReplyClick(data: bluesky)
                }) {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = bluesky.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                }
                Spacer()

                Menu(content: {
                    Button(action: {
                        event.onReblogClick(data: bluesky)
                    }, label: {
                        Label("Reblog", systemImage: "arrow.left.arrow.right")
                    })
                    Button(action: {
                        event.onQuoteClick(data: bluesky)
                    }, label: {
                        Label("Quote", systemImage: "quote.bubble.fill")
                    })
                }, label: {
                    Label(
                        title: { EmptyView() },
                        icon: {
                            Image(systemName: "arrow.left.arrow.right")
                        }
                    )
                })
                .if(!bluesky.reaction.reposted) { view in
                    view.opacity(0.6)
                }
                Spacer()
                Button(action: {
                    event.onLikeClick(data: bluesky)
                }) {
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
                            event.onDeleteClick(data: bluesky)
                        }, label: {
                            Label("Delete Toot", systemImage: "trash")
                        })
                    } else {
                        Button(action: {
                            event.onReportClick(data: bluesky)
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

protocol BlueskyStatusEvent {
    func onMediaClick(media: UiMedia)
    func onReplyClick(data: UiStatus.Bluesky)
    func onReblogClick(data: UiStatus.Bluesky)
    func onQuoteClick(data: UiStatus.Bluesky)
    func onLikeClick(data: UiStatus.Bluesky)
    func onReportClick(data: UiStatus.Bluesky)
    func onDeleteClick(data: UiStatus.Bluesky)
}
