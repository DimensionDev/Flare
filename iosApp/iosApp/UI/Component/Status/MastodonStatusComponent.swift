import SwiftUI
import MarkdownUI
import shared

struct MastodonStatusComponent: View {
    @State var showDeleteAlert = false
    @State var showReportAlert = false

    let mastodon: UiStatus.Mastodon
    let event: MastodonStatusEvent

    var body: some View {
        let actual = mastodon.reblogStatus ?? mastodon
        VStack(alignment: .leading) {
            if mastodon.reblogStatus != nil {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: mastodon.user.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: actual.extra.contentMarkdown, user: actual.user, medias: actual.media, timestamp: actual.createdAt.epochSeconds, headerTrailing: {
                MastodonVisibilityIcon(visibility: actual.visibility)
            }, onMediaClick: { media in event.onMediaClick(media: media) }, sensitive: actual.sensitive)
            if let card = mastodon.card {
                LinkPreview(card: card)
            }
            Spacer()
                .frame(height: 8)
            HStack {
                Button(action: {
                    event.onReplyClick(status: actual)
                }) {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = actual.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                }
                .opacity(0.6)
                Spacer()
                Button(action: {
                    event.onReblogClick(status: actual)
                }) {
                    HStack {
                        if actual.canReblog {
                            Image(systemName: "arrow.left.arrow.right")
                        } else {
                            Image(systemName: "slash.circle")
                        }
                        if let humanizedReblogCount = actual.matrices.humanizedReblogCount {
                            Text(humanizedReblogCount)
                        }
                    }
                    .if(actual.reaction.reblogged) { view in
                        view.foregroundStyle(.link)
                    }
                }
                .if(!actual.reaction.reblogged) { view in
                    view.opacity(0.6)
                }
                .disabled(!actual.canReblog)
                Spacer()
                Button(action: {
                    event.onLikeClick(status: actual)
                }) {
                    HStack {
                        Image(systemName: "star")
                        if let humanizedFavouriteCount = actual.matrices.humanizedFavouriteCount {
                            Text(humanizedFavouriteCount)
                        }
                    }
                    .if(actual.reaction.liked) { view in
                        view.foregroundStyle(.red, .red)
                    }
                }
                .if(!actual.reaction.liked) { view in
                    view.opacity(0.6)
                }
                Spacer()
                Menu {
                    Button(action: {
                        event.onBookmarkClick(status: actual)
                    }, label: {
                        if actual.reaction.bookmarked {
                            Label("Remove bookmark", systemImage: "bookmark.slash")
                        } else {
                            Label("Add bookmark", systemImage: "bookmark")
                        }
                    })
                    if actual.isFromMe {
                        Button(role: .destructive,action: {
                            showDeleteAlert = true
                        }, label: {
                            Label("Delete Toot", systemImage: "trash")
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
                    .opacity(0.6)
                }
            }
            .foregroundStyle(.primary)
            .buttonStyle(.borderless)
            .tint(.primary)
            .font(.caption)
        }
        .alert("Delete Toot", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("Cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: mastodon.accountKey, statusKey: actual.statusKey)
                showDeleteAlert = false
            } label: {
                Text("Delete")
            }
        }, message: {
            Text("Confirm delete this toot?")
        })
        .alert("Report Toot", isPresented: $showReportAlert, actions: {
            Button(role: .cancel) {
                showReportAlert = false
            } label: {
                Text("Cancel")
            }
            Button(role: .destructive) {
                event.onReportClick(status: actual)
                showReportAlert = false
            } label: {
                Text("Report")
            }
        }, message: {
            Text("Confirm report this toot?")
        })
    }
}


protocol MastodonStatusEvent {
    func onReplyClick(status: UiStatus.Mastodon)
    func onReblogClick(status: UiStatus.Mastodon)
    func onLikeClick(status: UiStatus.Mastodon)
    func onBookmarkClick(status: UiStatus.Mastodon)
    func onMediaClick(media: UiMedia)
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey)
    func onReportClick(status: UiStatus.Mastodon)
}
