import SwiftUI
import MarkdownUI
import shared

struct XQTStatusComponent: View {
    @Environment(\.openURL) private var openURL
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
                    text: String(localized: "xqt_status_reblog_header", comment: "Status header for xqt reblog")
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
                onMediaClick: { index, preview in
                    event.onMediaClick(statusKey: actual.statusKey, index: index, preview: preview)
                },
                sensitive: actual.sensitive,
                card: actual.card,
                replyToHandle: actual.replyHandle,
                onUserClicked: { user in
                    openURL(URL(string: AppDeepLink.Profile.shared.invoke(accountKey: actual.accountKey, userKey: user.userKey))!)
                }
            )
            if let quote = actual.quote {
                Spacer()
                    .frame(height: 8)
                QuotedStatus(
                    data: quote,
                    onMediaClick: { index, preview in
                        event.onMediaClick(statusKey: quote.statusKey, index: index, preview: preview)
                    },
                    onUserClick: { user in
                        openURL(URL(string: AppDeepLink.Profile.shared.invoke(accountKey: quote.accountKey, userKey: user.userKey))!)
                    },
                    onStatusClick: { _ in
                        event.onQuoteClick(status: quote)
                    }
                )
            }
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
                    Menu(content: {
                        Button(action: {
                            event.onReblogClick(status: actual)
                        }, label: {
                            Label("xqt_status_action_reblog", systemImage: "arrow.left.arrow.right")
                        })
                        Button(action: {
                            event.onQuoteClick(status: actual)
                        }, label: {
                            Label("xqt_status_action_quote", systemImage: "quote.bubble.fill")
                        })
                    }, label: {
                        Image(systemName: "arrow.left.arrow.right")
                            .font(.caption)
                        if let humanizedRetweetCount = actual.matrices.humanizedRetweetCount, appSettings.appearanceSettings.showNumbers {
                            Text(humanizedRetweetCount)
                                .font(.caption)
                        }
                    })
                    .disabled(!actual.canRetweet)
                    .if(!actual.reaction.retweeted) { view in
                        view.opacity(0.6)
                    }
                    .if(actual.reaction.retweeted) { view in
                        view
                            .tint(.accentColor)
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
                                Label("xqt_status_action_remove_bookmark", systemImage: "bookmark.slash")
                            } else {
                                Label("xqt_status_action_add_bookmark", systemImage: "bookmark")
                            }
                        })
                        if actual.isFromMe {
                            Button(role: .destructive, action: {
                                showDeleteAlert = true
                            }, label: {
                                Label("xqt_status_action_delete", systemImage: "trash")
                            })
                        } else {
                            Button(action: {
                                showReportAlert = true
                            }, label: {
                                Label("xqt_status_action_report", systemImage: "exclamationmark.shield")
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
        .alert("xqt_status_alert_title_delete", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: xqt.accountKey, statusKey: actual.statusKey)
                showDeleteAlert = false
            } label: {
                Text("delete")
            }
        }, message: {
            Text("xqt_status_alert_message_delete")
        })
        .alert("xqt_status_alert_title_report", isPresented: $showReportAlert, actions: {
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
            Text("xqt_status_alert_message_report")
        })
    }
}

protocol XQTStatusEvent {
    func onReplyClick(status: UiStatus.XQT)
    func onReblogClick(status: UiStatus.XQT)
    func onLikeClick(status: UiStatus.XQT)
    func onBookmarkClick(status: UiStatus.XQT)
    func onMediaClick(statusKey: MicroBlogKey, index: Int, preview: String?)
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey)
    func onReportClick(status: UiStatus.XQT)
    func onQuoteClick(status: UiStatus.XQT)
}
