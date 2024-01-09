import SwiftUI
import shared
import MarkdownUI

struct QuotedStatus: View {
    let data: UiStatus
    let onMediaClick: (UiMedia) -> Void
    let onUserClick: (UiUser) -> Void
    let onStatusClick: (UiStatus) -> Void
    var body: some View {
        switch onEnum(of: data) {
        case .mastodon(let mastodon):
            QuotedContent(
                content: mastodon.extra.contentMarkdown,
                user: mastodon.user,
                medias: mastodon.media,
                timestamp: mastodon.createdAt.epochSeconds,
                onMediaClick: onMediaClick,
                onUserClick: {
                    onUserClick(mastodon.user)
                },
                onStatusClick: {
                    onStatusClick(mastodon)
                },
                sensitive: mastodon.sensitive
            )
        case .mastodonNotification: EmptyView()
        case .misskey(let misskey):
            QuotedContent(
                content: misskey.extra.contentMarkdown,
                user: misskey.user,
                medias: misskey.media,
                timestamp: misskey.createdAt.epochSeconds,
                onMediaClick: onMediaClick,
                onUserClick: {
                    onUserClick(misskey.user)
                },
                onStatusClick: {
                    onStatusClick(data)
                },
                sensitive: misskey.sensitive
            )
        case .misskeyNotification: EmptyView()
        case .bluesky(let bluesky):
            QuotedContent(
                content: bluesky.extra.contentMarkdown,
                user: bluesky.user,
                medias: bluesky.medias,
                timestamp: bluesky.indexedAt.epochSeconds,
                onMediaClick: onMediaClick,
                onUserClick: {
                    onUserClick(bluesky.user)
                },
                onStatusClick: {
                    onStatusClick(data)
                },
                sensitive: false
            )
        case .blueskyNotification: EmptyView()
        }
    }
}

private struct QuotedContent: View {
    @Environment(\.appSettings) private var appSettings
    @State var showMedia: Bool = false
    let content: String
    let user: UiUser
    let medias: [UiMedia]
    let timestamp: Int64
    let onMediaClick: (UiMedia) -> Void
    let onUserClick: () -> Void
    let onStatusClick: () -> Void
    let sensitive: Bool
    var body: some View {
        Button(action: onStatusClick, label: {
            VStack(alignment: .leading) {
                Spacer()
                    .frame(height: 8)
                Button(action: onUserClick, label: {
                    UserAvatar(data: user.avatarUrl, size: 20)
                    Markdown(user.extra.nameMarkdown)
                        .lineLimit(1)
                        .font(.subheadline)
                        .markdownInlineImageProvider(.emoji)
                    Text(user.handle)
                        .lineLimit(1)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                    Spacer()
                    dateFormatter(Date(timeIntervalSince1970: .init(integerLiteral: timestamp)))
                        .font(.caption)
                        .foregroundColor(.gray)
                })
                .buttonStyle(.plain)
                .padding(.horizontal)
                Markdown(content)
                    .font(.body)
                    .markdownInlineImageProvider(.emoji)
                    .padding(.horizontal)
                Spacer()
                    .frame(height: 8)
                if !medias.isEmpty {
                    if appSettings.appearanceSettings.showMedia, showMedia {
                        MediaComponent(
                            hideSensitive: sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                            medias: medias,
                            onMediaClick: onMediaClick
                        )
                    } else {
                        Button {
                            withAnimation {
                                showMedia = true
                            }
                        } label: {
                            Label("Show Medias", systemImage: "photo")
                        }
                    }
                }
            }
        })
        .buttonStyle(.plain)
#if !os(macOS)
        .background(Color(UIColor.secondarySystemBackground))
#else
        .background(Color(NSColor.windowBackgroundColor))
#endif
        .cornerRadius(8)
    }
}
