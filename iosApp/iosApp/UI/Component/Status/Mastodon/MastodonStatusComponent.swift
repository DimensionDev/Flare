import SwiftUI
import MarkdownUI
import shared

struct MastodonStatusComponent: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @State var showDeleteAlert = false
    @State var showReportAlert = false
    let mastodon: UiStatus.Mastodon
    let event: MastodonStatusEvent
    var body: some View {
        let actual = mastodon.reblogStatus ?? mastodon
        VStack(alignment: .leading) {
            if mastodon.reblogStatus != nil {
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: mastodon.user.extra.nameMarkdown,
                    text: String(localized: "mastodon_status_reblog_header", comment: "Status header for mastodon reblog")
                )
            }
            CommonStatusComponent(
                content: actual.extra.contentMarkdown,
                contentWarning: actual.contentWarningText,
                user: actual.user,
                medias: actual.medias,
                timestamp: actual.createdAt.epochSeconds,
                headerTrailing: {
                    MastodonVisibilityIcon(visibility: actual.visibility)
                },
                onMediaClick: { index, preview in
                    event.onMediaClick(statusKey: actual.statusKey, index: index, preview: preview)
                },
                sensitive: actual.sensitive,
                card: actual.card,
                onUserClicked: { user in
                    openURL(URL(string: AppDeepLink.Profile.shared.invoke(accountKey: actual.accountKey, userKey: user.userKey))!)
                }
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
                            if actual.canReblog {
                                Image(systemName: "arrow.left.arrow.right")
                            } else {
                                Image(systemName: "slash.circle")
                            }
                            if let humanizedReblogCount = actual.matrices.humanizedReblogCount, appSettings.appearanceSettings.showNumbers {
                                Text(humanizedReblogCount)
                            }
                        }
                        .if(actual.reaction.reblogged) { view in
                            view.foregroundStyle(.link)
                        }
                    })
                    .if(!actual.reaction.reblogged) { view in
                        view.opacity(0.6)
                    }
                    .disabled(!actual.canReblog)
                    Spacer()
                    Button(action: {
                        event.onLikeClick(status: actual)
                    }, label: {
                        HStack {
                            Image(systemName: "star")
                            if let humanizedFavouriteCount = actual.matrices.humanizedFavouriteCount, appSettings.appearanceSettings.showNumbers {
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
                                Label("mastodon_status_action_bookmark_remove", systemImage: "bookmark.slash")
                            } else {
                                Label("mastodon_status_action_bookmark_add", systemImage: "bookmark")
                            }
                        })
                        if actual.isFromMe {
                            Button(role: .destructive, action: {
                                showDeleteAlert = true
                            }, label: {
                                Label("mastodon_status_action_delete", systemImage: "trash")
                            })
                        } else {
                            Button(action: {
                                showReportAlert = true
                            }, label: {
                                Label("mastodon_status_action_report", systemImage: "exclamationmark.shield")
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
        .alert("mastodon_alert_title_delete", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: mastodon.accountKey, statusKey: actual.statusKey)
                showDeleteAlert = false
            } label: {
                Text("delete")
            }
        }, message: {
            Text("mastodon_alert_desc_delete")
        })
        .alert("mastodon_alert_title_report", isPresented: $showReportAlert, actions: {
            Button(role: .cancel) {
                showReportAlert = false
            } label: {
                Text("cancel")
            }
            Button(role: .destructive) {
                event.onReportClick(status: actual)
                showReportAlert = false
            } label: {
                Text("report")
            }
        }, message: {
            Text("mastodon_alert_desc_report")
        })
    }
}

protocol MastodonStatusEvent {
    func onReplyClick(status: UiStatus.Mastodon)
    func onReblogClick(status: UiStatus.Mastodon)
    func onLikeClick(status: UiStatus.Mastodon)
    func onBookmarkClick(status: UiStatus.Mastodon)
    func onMediaClick(statusKey: MicroBlogKey, index: Int, preview: String?)
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey)
    func onReportClick(status: UiStatus.Mastodon)
}
