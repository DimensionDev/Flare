import SwiftUI
import shared
import NetworkImage

struct MisskeyStatusComponent: View {
    @State var showDeleteAlert = false
    @State var showReportAlert = false
    let misskey: UiStatus.Misskey
    let event: MisskeyStatusEvent
    var body: some View {
        let actual = misskey.renote ?? misskey
        VStack {
            if misskey.renote != nil {
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: misskey.user.extra.nameMarkdown,
                    text: "boosted a status"
                )
            }
            CommonStatusComponent(
                content: actual.extra.contentMarkdown,
                user: actual.user,
                medias: actual.media,
                timestamp: actual.createdAt.epochSeconds,
                headerTrailing: {
                    MisskeyVisibilityIcon(visibility: actual.visibility)
                }, onMediaClick: { media in event.onMediaClick(media: media) }, sensitive: actual.sensitive)
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
                }, label: {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = actual.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                })
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
                            if actual.canRenote {
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
                }, label: {
                    if actual.reaction.myReaction != nil {
                        Image(systemName: "minus")
                    } else {
                        Image(systemName: "plus")
                    }
                })
                Spacer()
                Menu {
                    if actual.isFromMe {
                        Button(role: .destructive, action: {
                            showDeleteAlert = true
                        }, label: {
                            Label("Delete Note", systemImage: "trash")
                        })
                    } else {
                        Button(action: {
                            showReportAlert = true
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
        .alert("Delete Status", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("Cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: actual.accountKey, statusKey: actual.statusKey)
                showDeleteAlert = false
            } label: {
                Text("Delete")
            }
        }, message: {
            Text("Confirm delete this status?")
        })
        .alert("Report Status", isPresented: $showReportAlert, actions: {
            Button(role: .cancel) {
                showReportAlert = false
            } label: {
                Text("Cancel")
            }
            Button(role: .destructive) {
                event.onReportClick(data: actual)
                showReportAlert = false
            } label: {
                Text("Report")
            }
        }, message: {
            Text("Confirm report this status?")
        })
    }
}

protocol MisskeyStatusEvent {
    func onMediaClick(media: UiMedia)
    func onReactionClick(data: UiStatus.Misskey, reaction: UiStatus.MisskeyEmojiReaction)
    func onReplyClick(data: UiStatus.Misskey)
    func onReblogClick(data: UiStatus.Misskey)
    func onQuoteClick(data: UiStatus.Misskey)
    func onAddReactionClick(data: UiStatus.Misskey)
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey)
    func onReportClick(data: UiStatus.Misskey)
}
