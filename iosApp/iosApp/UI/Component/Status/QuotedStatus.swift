import SwiftUI
import shared
import MarkdownUI

struct QuotedStatus: View {
    let data: UiStatus
    let onMediaClick: (UiMedia) -> Void
    var body: some View {
        switch onEnum(of: data) {
        case .mastodon(let mastodon): QuotedContent(content: mastodon.content, user: mastodon.user, medias: mastodon.media, timestamp: mastodon.createdAt.epochSeconds, onMediaClick: onMediaClick, sensitive: mastodon.sensitive)
        case .mastodonNotification(_): EmptyView()
        case .misskey(let misskey): QuotedContent(content: misskey.content, user: misskey.user, medias: misskey.media, timestamp: misskey.createdAt.epochSeconds, onMediaClick: onMediaClick, sensitive: misskey.sensitive)
        case .misskeyNotification(_): EmptyView()
        case .bluesky(let bluesky): QuotedContent(content: bluesky.content, user: bluesky.user, medias: bluesky.medias, timestamp: bluesky.indexedAt.epochSeconds, onMediaClick: onMediaClick, sensitive: false)
        case .blueskyNotification(_): EmptyView()
        }
    }
}

private struct QuotedContent: View {
    let content: String
    let user: UiUser
    let medias: [UiMedia]
    let timestamp: Int64
    let onMediaClick: (UiMedia) -> Void
    let sensitive: Bool

    var body: some View {
        VStack {
            HStack {
                UserAvatar(data: user.avatarUrl, size: 20)
                Markdown(user.extra.nameMarkdown)
                    .lineLimit(1)
                    .font(.subheadline)
                    .markdownInlineImageProvider(.emoji)
                Text(user.handle)
                    .lineLimit(1)
                    .font(.subheadline)
                    .foregroundColor(.gray)
            }
            Markdown(content)
                .font(.body)
                .markdownInlineImageProvider(.emoji)
            if !medias.isEmpty {
                Spacer()
                    .frame(height: 8)
                MediaComponent(hideSensitive: sensitive, medias: medias, onMediaClick: onMediaClick)
            }
        }
    }
}
