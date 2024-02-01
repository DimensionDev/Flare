import SwiftUI
import shared
import NetworkImage

struct MisskeyStatusComponent: View {
    @Environment(\.appSettings) private var appSettings
    @State var showDeleteAlert = false
    @State var showReportAlert = false
    @Environment(\.openURL) private var openURL
    let misskey: UiStatus.Misskey
    let event: MisskeyStatusEvent
    var body: some View {
        let actual = misskey.renote ?? misskey
        VStack {
            if misskey.renote != nil {
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: misskey.user.extra.nameMarkdown,
                    text: String(localized: "misskey_status_renote_header")
                )
            }
            CommonStatusComponent(
                content: actual.extra.contentMarkdown,
                contentWarning: actual.contentWarningText,
                user: actual.user,
                medias: actual.media,
                timestamp: actual.createdAt.epochSeconds,
                headerTrailing: {
                    MisskeyVisibilityIcon(visibility: actual.visibility)
                },
                onMediaClick: { media in event.onMediaClick(media: media) },
                sensitive: actual.sensitive,
                card: actual.card
            )
            if let quote = misskey.quote {
                Spacer()
                    .frame(height: 8)
                QuotedStatus(
                    data: quote,
                    onMediaClick: event.onMediaClick,
                    onUserClick: { user in
                        openURL(URL(string: AppDeepLink.Profile.shared.invoke(userKey: user.userKey))!)
                    },
                    onStatusClick: { _ in
                        event.onQuoteClick(data: quote)
                    }
                )
            }
            if misskey.reaction.emojiReactions.count > 0, appSettings.appearanceSettings.misskey.showReaction {
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
            if appSettings.appearanceSettings.showActions {
                Spacer()
                    .frame(height: 8)
                HStack {
                    Button(action: {
                        event.onReplyClick(data: actual)
                    }, label: {
                        HStack {
                            Image(systemName: "arrowshape.turn.up.left")
                            if let humanizedReplyCount = actual.matrices.humanizedReplyCount, appSettings.appearanceSettings.showNumbers {
                                Text(humanizedReplyCount)
                            }
                        }
                    })
                    Spacer()
                    Menu(content: {
                        Button(action: {
                            event.onReblogClick(data: actual)
                        }, label: {
                            Label("misskey_status_action_renote", systemImage: "arrow.left.arrow.right")
                        })
                        Button(action: {
                            event.onQuoteClick(data: actual)
                        }, label: {
                            Label("misskey_status_action_quote", systemImage: "quote.bubble.fill")
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
                        .font(.caption)
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
                                Label("misskey_status_action_delete", systemImage: "trash")
                            })
                        } else {
                            Button(action: {
                                showReportAlert = true
                            }, label: {
                                Label("misskey_status_action_report", systemImage: "exclamationmark.shield")
                            })
                        }
                    } label: {
                        Label(
                            title: { EmptyView() },
                            icon: { Image(systemName: "ellipsis") }
                        )
                    }
                }
                .frame(maxWidth: 600)
                .foregroundStyle(.primary)
                .buttonStyle(.borderless)
                .tint(.primary)
                .opacity(0.6)
                .font(.caption)
            }
        }
        .alert("misskey_alert_title_delete", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: actual.accountKey, statusKey: actual.statusKey)
                showDeleteAlert = false
            } label: {
                Text("delete")
            }
        }, message: {
            Text("misskey_alert_desc_delete")
        })
        .alert("misskey_alert_title_report", isPresented: $showReportAlert, actions: {
            Button(role: .cancel) {
                showReportAlert = false
            } label: {
                Text("cancel")
            }
            Button(role: .destructive) {
                event.onReportClick(data: actual)
                showReportAlert = false
            } label: {
                Text("report")
            }
        }, message: {
            Text("misskey_alert_desc_report")
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
