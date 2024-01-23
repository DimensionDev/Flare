import SwiftUI
import MarkdownUI
import shared

struct XQTStatusComponent: View {
    @Environment(\.appSettings) private var appSettings
    @State var showDeleteAlert = false
    @State var showReportAlert = false
    let xqt: UiStatus.XQT
    let event: XQTStatusEvent
    var body: some View {
        let actual = xqt.retweet ?? xqt
        VStack(alignment: .leading) {
            if xqt.retweet != nil {
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: xqt.user.extra.nameMarkdown,
                    text: "boosted a status"
                )
            }
            CommonStatusComponent(
                content: actual.extra.contentMarkdown,
                contentWarning: nil,
                user: actual.user,
                medias: actual.medias,
                timestamp: actual.createdAt.epochSeconds,
                headerTrailing: {
                    EmptyView()
                },
                onMediaClick: { media in event.onMediaClick(media: media) },
                sensitive: actual.sensitive,
                card: actual.card
            )
            if appSettings.appearanceSettings.showActions {
                Spacer()
                    .frame(height: 8)
                HStack {
                    Button(action: {
                        event.onReplyClick(status: actual)
                    }, label: {
                        HStack {
                            Image(systemName: "arrowshape.turn.up.left")
                            if let humanizedReplyCount = actual.matrices.humanizedReplyCount, appSettings.appearanceSettings.showNumbers {
                                Text(humanizedReplyCount)
                            }
                        }
                    })
                    .opacity(0.6)
                    Spacer()
                    Button(action: {
                        event.onReblogClick(status: actual)
                    }, label: {
                        HStack {
                            Image(systemName: "arrow.left.arrow.right")
                            if let humanizedReblogCount = actual.matrices.humanizedRetweetCount, appSettings.appearanceSettings.showNumbers {
                                Text(humanizedReblogCount)
                            }
                        }
                        .if(actual.reaction.retweeted) { view in
                            view.foregroundStyle(.link)
                        }
                    })
                    .if(!actual.reaction.retweeted) { view in
                        view.opacity(0.6)
                    }
                    Spacer()
                    Button(action: {
                        event.onLikeClick(status: actual)
                    }, label: {
                        HStack {
                            Image(systemName: "star")
                            if let humanizedFavouriteCount = actual.matrices.humanizedLikeCount, appSettings.appearanceSettings.showNumbers {
                                Text(humanizedFavouriteCount)
                            }
                        }
                        .if(actual.reaction.liked) { view in
                            view.foregroundStyle(.red, .red)
                        }
                    })
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
                            Button(role: .destructive, action: {
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
                .frame(maxWidth: 600)
                .foregroundStyle(.primary)
                .buttonStyle(.borderless)
                .tint(.primary)
                .font(.caption)
            }
        }
        .alert("Delete Toot", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("Cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: xqt.accountKey, statusKey: actual.statusKey)
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

protocol XQTStatusEvent {
    func onReplyClick(status: UiStatus.XQT)
    func onReblogClick(status: UiStatus.XQT)
    func onLikeClick(status: UiStatus.XQT)
    func onBookmarkClick(status: UiStatus.XQT)
    func onMediaClick(media: UiMedia)
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey)
    func onReportClick(status: UiStatus.XQT)
}
