import SwiftUI
import shared

struct BlueskyStatusComponent: View {
    @Environment(\.appSettings) private var appSettings
    @State var showDeleteAlert = false
    @State var showReportAlert = false
    @Environment(\.openURL) private var openURL
    let bluesky: UiStatus.Bluesky
    let event: BlueskyStatusEvent
    var body: some View {
        VStack(alignment: .leading) {
            if let repostBy = bluesky.repostBy {
                StatusRetweetHeaderComponent(
                    iconSystemName: "arrow.left.arrow.right",
                    nameMarkdown: repostBy.extra.nameMarkdown,
                    text: String(localized: "bluesky_status_repost_header")
                )
            }
            CommonStatusComponent(
                content: bluesky.extra.contentMarkdown,
                contentWarning: nil,
                user: bluesky.user,
                medias: bluesky.medias,
                timestamp: bluesky.indexedAt.epochSeconds,
                headerTrailing: { EmptyView() },
                onMediaClick: { media in event.onMediaClick(media: media) },
                sensitive: false,
                card: bluesky.card
            )
            if let quote = bluesky.quote {
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
            if appSettings.appearanceSettings.showActions {
                Spacer()
                    .frame(height: 8)
                HStack {
                    Button(
                        action: {
                            event.onReplyClick(data: bluesky)
                        },
                        label: {
                            HStack {
                                Image(systemName: "arrowshape.turn.up.left")
                                if let humanizedReplyCount = bluesky.matrices.humanizedReplyCount, appSettings.appearanceSettings.showNumbers {
                                    Text(humanizedReplyCount)
                                }
                            }
                        }
                    )
                    .opacity(0.6)
                    Spacer()
                    Menu(content: {
                        Button(action: {
                            event.onReblogClick(data: bluesky)
                        }, label: {
                            Label("bluesky_status_action_reblog", systemImage: "arrow.left.arrow.right")
                        })
                        Button(action: {
                            event.onQuoteClick(data: bluesky)
                        }, label: {
                            Label("bluesky_status_action_quote", systemImage: "quote.bubble.fill")
                        })
                    }, label: {
                        Image(systemName: "arrow.left.arrow.right")
                            .font(.caption)
                        if let humanizedRepostCount = bluesky.matrices.humanizedRepostCount, appSettings.appearanceSettings.showNumbers {
                            Text(humanizedRepostCount)
                                .font(.caption)
                        }
                    })
                    .if(!bluesky.reaction.reposted) { view in
                        view.opacity(0.6)
                    }
                    .if(bluesky.reaction.reposted) { view in
                        view
                            .tint(.accentColor)
                    }
                    Spacer()
                    Button(
                        action: {
                            event.onLikeClick(data: bluesky)
                        },
                        label: {
                            HStack {
                                Image(systemName: "star")
                                if let humanizedFavouriteCount = bluesky.matrices.humanizedLikeCount, appSettings.appearanceSettings.showNumbers {
                                    Text(humanizedFavouriteCount)
                                }
                            }
                            .if(bluesky.reaction.liked) { view in
                                view.foregroundStyle(.red, .red)
                            }
                        }
                    )
                    .if(!bluesky.reaction.liked) { view in
                        view.opacity(0.6)
                    }
                    .if(bluesky.reaction.liked) { view in
                        view
                            .tint(.red)
                    }
                    Spacer()
                    Menu {
                        if bluesky.isFromMe {
                            Button(role: .destructive, action: {
                                showDeleteAlert = true
                            }, label: {
                                Label("bluesky_status_action_delete", systemImage: "trash")
                            })
                        } else {
                            Button(
                                action: {
                                    showReportAlert = true
                                },
                                label: {
                                    Label("bluesky_status_action_repott", systemImage: "exclamationmark.shield")
                                }
                            )
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .opacity(0.6)
                    }
                }
                .frame(maxWidth: 600)
                .buttonStyle(.borderless)
                .tint(.primary)
                .font(.caption)
            }
        }
        .alert("bluesky_alert_title_delete", isPresented: $showDeleteAlert, actions: {
            Button(role: .cancel) {
                showDeleteAlert = false
            } label: {
                Text("cancel")
            }
            Button(role: .destructive) {
                event.onDeleteClick(accountKey: bluesky.accountKey, statusKey: bluesky.statusKey)
                showDeleteAlert = false
            } label: {
                Text("delete")
            }
        }, message: {
            Text("bluesky_alert_desc_delete")
        })
        .confirmationDialog("bluesky_alert_title_report", isPresented: $showReportAlert) {
            Button("bluesky_report_reason_spam") {
                event.onReportClick(data: bluesky, reason: BlueskyReportStatusStateReportReason.spam)
                showReportAlert = false
            }
            Button("bluesky_report_reason_illegal") {
                event.onReportClick(data: bluesky, reason: BlueskyReportStatusStateReportReason.violation)
                showReportAlert = false
            }
            Button("bluesky_report_reason_misleading") {
                event.onReportClick(data: bluesky, reason: BlueskyReportStatusStateReportReason.misleading)
                showReportAlert = false
            }
            Button("bluesky_report_reason_sexual") {
                event.onReportClick(data: bluesky, reason: BlueskyReportStatusStateReportReason.sexual)
                showReportAlert = false
            }
            Button("bluesky_report_reason_rude") {
                event.onReportClick(data: bluesky, reason: BlueskyReportStatusStateReportReason.rude)
                showReportAlert = false
            }
            Button("bluesky_report_reason_other") {
                event.onReportClick(data: bluesky, reason: BlueskyReportStatusStateReportReason.other)
                showReportAlert = false
            }
            Button("cancel", role: .cancel) {
                showReportAlert = false
            }
        } message: {
            Text("bluesky_alert_desc_report")
        }
    }
}

protocol BlueskyStatusEvent {
    func onMediaClick(media: UiMedia)
    func onReplyClick(data: UiStatus.Bluesky)
    func onReblogClick(data: UiStatus.Bluesky)
    func onQuoteClick(data: UiStatus.Bluesky)
    func onLikeClick(data: UiStatus.Bluesky)
    func onReportClick(data: UiStatus.Bluesky, reason: BlueskyReportStatusStateReportReason)
    func onDeleteClick(accountKey: MicroBlogKey, statusKey: MicroBlogKey)
}
