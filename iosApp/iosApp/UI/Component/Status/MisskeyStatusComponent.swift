import SwiftUI
import shared
import NetworkImage

struct MisskeyStatusComponent: View {
    let misskey: UiStatus.Misskey
    let event: MisskeyStatusEvent
    var body: some View {
        VStack {
            let actual = misskey.renote ?? misskey
            if misskey.renote != nil {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: misskey.user.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: actual.extra.contentMarkdown, user: actual.user, medias: actual.media, timestamp: actual.createdAt.epochSeconds, headerTrailing: {
                MisskeyVisibilityIcon(visibility: actual.visibility)
            }, onMediaClick: { media in event.onMediaClick(media: media) })

            if let quote = misskey.quote {
                QuotedStatus(data: quote, onMediaClick: event.onMediaClick)
            }

            if misskey.reaction.emojiReactions.count > 0 {
                ScrollView(.horizontal) {
                    LazyHStack {
                        ForEach(1...misskey.reaction.emojiReactions.count, id: \.self) { index in
                            let reaction = misskey.reaction.emojiReactions[index - 1]
                            Button(action: {
                                event.onReactionClick(data: actual, reaction: reaction)
                            }, label: {
                                HStack {
                                    NetworkImage(url: URL(string: reaction.url))
                                    Text(reaction.humanizedCount)
                                }
                            })
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }

            Spacer()
                .frame(height: 8)
            HStack {
                Button(action: {
                    event.onReplyClick(data: actual)
                }) {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = actual.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                }
                Spacer()
                Menu(content: {
                    Button(action: {
                        event.onReblogClick(data: actual)
                    }, label: {
                        Label("Renote", systemImage: "arrow.left.arrow.right")
                    })
                    Button(action: {
                        event.onQuoteClick(data: actual)
                    }, label: {
                        Label("Quote", systemImage: "quote.bubble.fill")
                    })
                }, label: {
                    Label(
                        title: { EmptyView() },
                        icon: {
                            if (actual.canRenote) {
                                Image(systemName: "arrow.left.arrow.right")
                            } else {
                                Image(systemName: "slash.circle")
                            }
                        }
                    )
                })
                .disabled(!actual.canRenote)
                Spacer()
                Button(action: {
                    event.onAddReactionClick(data: actual)
                }) {
                    if actual.reaction.myReaction != nil {
                        Image(systemName: "minus")
                    } else {
                        Image(systemName: "plus")
                    }
                }
                Spacer()

                Menu {
                    if actual.isFromMe {
                        Button(role: .destructive, action: {
                            event.onDeleteClick(data: actual)
                        }, label: {
                            Label("Delete Note", systemImage: "trash")
                        })
                    } else {
                        Button(action: {
                            event.onReportClick(data: actual)
                        }, label: {
                            Label("Report", systemImage: "exclamationmark.shield")
                        })
                    }
                } label: {
                    Label(
                        title: { EmptyView() },
                        icon: { Image(systemName: "ellipsis") }
                    )
                }
            }
            .foregroundStyle(.primary)
            .buttonStyle(.borderless)
            .tint(.primary)
            .opacity(0.6)
            .font(.caption)
        }
    }
}

protocol MisskeyStatusEvent {
    func onMediaClick(media: UiMedia)
    func onReactionClick(data: UiStatus.Misskey, reaction: UiStatus.MisskeyEmojiReaction)
    func onReplyClick(data: UiStatus.Misskey)
    func onReblogClick(data: UiStatus.Misskey)
    func onQuoteClick(data: UiStatus.Misskey)
    func onAddReactionClick(data: UiStatus.Misskey)
    func onDeleteClick(data: UiStatus.Misskey)
    func onReportClick(data: UiStatus.Misskey)
}
