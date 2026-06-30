import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct ArticleScreen: View {
    @StateObject private var presenter: KotlinPresenter<ArticlePresenterState>
    @Environment(\.openURL) private var openURL

    private let accountType: AccountType
    private let articleKey: MicroBlogKey
    private let onOpenProfile: (AccountType, MicroBlogKey) -> Void
    private let onOpenMedia: ([any UiMedia], Int, String?) -> Void
    private let onShareArticle: ((AccountType, MicroBlogKey, String) -> Void)?
    private let onDownloadFile: ((UiArticleBlockFile) -> Void)?

    public init(
        accountType: AccountType,
        articleKey: MicroBlogKey,
        onOpenProfile: @escaping (AccountType, MicroBlogKey) -> Void = { _, _ in },
        onOpenMedia: @escaping ([any UiMedia], Int, String?) -> Void = { _, _, _ in },
        onShareArticle: ((AccountType, MicroBlogKey, String) -> Void)? = nil,
        onDownloadFile: ((UiArticleBlockFile) -> Void)? = nil
    ) {
        self.accountType = accountType
        self.articleKey = articleKey
        self.onOpenProfile = onOpenProfile
        self.onOpenMedia = onOpenMedia
        self.onShareArticle = onShareArticle
        self.onDownloadFile = onDownloadFile
        self._presenter = .init(wrappedValue: .init(
            presenter: ArticlePresenter(accountType: accountType, articleKey: articleKey)
        ))
    }

    public var body: some View {
        StateView(state: presenter.state.article) { article in
            ArticleContentView(
                article: article,
                accountType: accountType,
                onOpenProfile: onOpenProfile,
                onOpenMedia: onOpenMedia,
                onOpenURL: openArticleURL,
                onDownloadFile: onDownloadFile
            )
        } errorContent: { error in
            ContentUnavailableView {
                Label {
                    Text("Failed to load article", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(systemName: "exclamationmark.triangle")
                }
            } description: {
                Text(error.message ?? "Unknown error")
            }
        } loadingContent: {
            ArticleLoadingView()
        }
        .articleNavigationBarTitleDisplayMode()
        .toolbar {
            if let article = loadedArticle,
               let sourceURL = article.sourceUrl.flatMap(URL.init(string:)) {
                ToolbarItem {
                    Button {
                        openURL(sourceURL)
                    } label: {
                        Image(systemName: "safari")
                    }
                    .accessibilityLabel(Text("deep_link_account_picker_open_in_browser", bundle: FlareAppleUILocalization.bundle))
                }
                if let onShareArticle {
                    ToolbarItem {
                        Button {
                            onShareArticle(accountType, articleKey, sourceURL.absoluteString)
                        } label: {
                            Image(fontAwesome: .shareNodes)
                        }
                        .accessibilityLabel(Text("fx_share", bundle: FlareAppleUILocalization.bundle))
                    }
                }
            }
        }
    }

    private var loadedArticle: UiArticle? {
        switch onEnum(of: presenter.state.article) {
        case .success(let data):
            return data.data
        case .loading, .error:
            return nil
        }
    }

    private func openArticleURL(_ value: String) {
        guard let url = URL(string: value) else { return }
        openURL(url)
    }
}

private struct ArticleContentView: View {
    let article: UiArticle
    let accountType: AccountType
    let onOpenProfile: (AccountType, MicroBlogKey) -> Void
    let onOpenMedia: ([any UiMedia], Int, String?) -> Void
    let onOpenURL: (String) -> Void
    let onDownloadFile: ((UiArticleBlockFile) -> Void)?

    private var blocks: [any UiArticleBlock] {
        Array(article.content.blocks)
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                if let cover = article.cover {
                    ArticleCoverView(
                        cover: cover,
                        title: article.title,
                        onOpenMedia: openMedia
                    )
                }

                LazyVStack(alignment: .leading, spacing: 16) {
                    ArticleHeaderView(
                        article: article,
                        accountType: accountType,
                        onOpenProfile: onOpenProfile
                    )

                    Divider()

                    ForEach(blocks, id: \.key) { block in
                        ArticleBlockView(
                            block: block,
                            onOpenURL: onOpenURL,
                            onOpenMedia: openMedia,
                            onDownloadFile: onDownloadFile
                        )
                    }
                }
                .frame(maxWidth: 680, alignment: .leading)
                .padding(.horizontal)
                .padding(.vertical, 8)
                .textSelection(.enabled)
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 24)
        }
        .ignoresSafeArea(edges: article.cover == nil ? Edge.Set() : .top)
    }

    private var articleMedias: [any UiMedia] {
        var medias: [any UiMedia] = []
        if let cover = article.cover {
            medias.append(cover)
        }
        blocks.forEach { block in
            switch onEnum(of: block) {
            case .image(let image):
                medias.append(image.media)
            case .video(let video):
                medias.append(video.media)
            case .text, .file, .embed, .contentGate:
                break
            }
        }
        return medias
    }

    private func openMedia(_ media: any UiMedia) {
        let medias = articleMedias
        let index = medias.firstIndex { $0.url == media.url } ?? 0
        onOpenMedia(medias, index, media.mediaPreviewURL)
    }
}

private struct ArticleCoverView: View {
    let cover: UiMediaImage
    let title: String
    let onOpenMedia: (any UiMedia) -> Void

    var body: some View {
        Button {
            onOpenMedia(cover)
        } label: {
            ArticleRemoteImage(
                url: cover.url,
                preview: cover.previewUrl,
                customHeaders: cover.customHeaders
            )
            .frame(height: 260)
            .frame(maxWidth: .infinity)
            .clipped()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(title))
    }
}

private struct ArticleHeaderView: View {
    let article: UiArticle
    let accountType: AccountType
    let onOpenProfile: (AccountType, MicroBlogKey) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(article.title)
                .font(.title2)
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let author = article.author {
                switch onEnum(of: author) {
                case .profile(let profileAuthor):
                    UserCompatView(
                        data: profileAuthor.profile,
                        trailing: {
                            Group {
                                if let publishDate = article.publishDate {
                                    DateTimeText(data: publishDate, fullTime: true)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        },
                        onClicked: {
                            onOpenProfile(accountType, profileAuthor.profile.key)
                        }
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)
                case .rss(let rssAuthor):
                    ArticleRssAuthorView(
                        author: rssAuthor,
                        sourceUrl: article.sourceUrl,
                        publishDate: article.publishDate
                    )
                }
            } else if let publishDate = article.publishDate {
                DateTimeText(data: publishDate, fullTime: true)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct ArticleRssAuthorView: View {
    let author: UiArticleAuthorRss
    let sourceUrl: String?
    let publishDate: UiDateTime?

    private var host: String? {
        sourceUrl.flatMap(URL.init(string:))?.host()
    }

    var body: some View {
        if author.siteName != nil || author.byline != nil || publishDate != nil {
            VStack(alignment: .leading, spacing: 4) {
                if let siteName = author.siteName {
                    HStack(spacing: 4) {
                        if let host {
                            FavTabIcon(host: host)
                                .frame(width: 16, height: 16)
                        } else if let iconUrl = author.iconUrl {
                            NetworkImage(data: iconUrl)
                                .frame(width: 16, height: 16)
                                .clipShape(.rect(cornerRadius: 4))
                        }
                        Text(siteName)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 8) {
                    if let byline = author.byline {
                        Text(byline)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    if let publishDate {
                        DateTimeText(data: publishDate, fullTime: true)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
}

private struct ArticleBlockView: View {
    let block: any UiArticleBlock
    let onOpenURL: (String) -> Void
    let onOpenMedia: (any UiMedia) -> Void
    let onDownloadFile: ((UiArticleBlockFile) -> Void)?

    var body: some View {
        switch onEnum(of: block) {
        case .text(let text):
            RichText(text: text.richText)
                .frame(maxWidth: .infinity, alignment: .leading)
        case .image(let image):
            ArticleImageBlockView(
                media: image.media,
                onOpenMedia: onOpenMedia
            )
        case .video(let video):
            ArticleVideoBlockView(
                media: video.media,
                onOpenMedia: onOpenMedia
            )
        case .file(let file):
            ArticleFileBlockView(block: file, onDownloadFile: onDownloadFile)
        case .embed(let embed):
            ArticleEmbedBlockView(block: embed, onOpenURL: onOpenURL)
        case .contentGate(let gate):
            ArticleContentGateBlockView(block: gate, onOpenURL: onOpenURL)
        }
    }
}

private struct ArticleImageBlockView: View {
    let media: UiMediaImage
    let onOpenMedia: (any UiMedia) -> Void

    var body: some View {
        Button {
            onOpenMedia(media)
        } label: {
            ArticleMediaFrame(aspectRatio: articleAspectRatio(media.aspectRatio)) {
                ArticleRemoteImage(
                    url: media.url,
                    preview: media.previewUrl,
                    customHeaders: media.customHeaders
                )
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(media.description_ ?? FlareAppleUILocalization.string("Image", fallback: "Image")))
    }
}

private struct ArticleVideoBlockView: View {
    let media: UiMediaVideo
    let onOpenMedia: (any UiMedia) -> Void

    var body: some View {
        ArticleMediaFrame(aspectRatio: articleAspectRatio(media.aspectRatio)) {
            MediaVideoView(data: media)
        }
            .contentShape(Rectangle())
            .overlay {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture {
                        onOpenMedia(media)
                    }
            }
    }
}

private struct ArticleMediaFrame<Content: View>: View {
    let aspectRatio: CGFloat
    private let content: Content

    init(
        aspectRatio: CGFloat,
        @ViewBuilder content: () -> Content
    ) {
        self.aspectRatio = aspectRatio
        self.content = content()
    }

    var body: some View {
        GeometryReader { proxy in
            content
                .frame(width: proxy.size.width, height: proxy.size.height)
                .clipped()
        }
        .aspectRatio(aspectRatio, contentMode: .fit)
        .frame(maxWidth: .infinity, alignment: .leading)
        .clipShape(.rect(cornerRadius: 12))
        .contentShape(Rectangle())
    }
}

private struct ArticleFileBlockView: View {
    let block: UiArticleBlockFile
    let onDownloadFile: ((UiArticleBlockFile) -> Void)?

    private var extensionName: String? {
        let extensionName = block.extension?.trimmedNonEmpty ?? URL(string: block.url)?.pathExtension.trimmedNonEmpty
        return extensionName?.uppercased()
    }

    var body: some View {
        if let onDownloadFile {
            Button {
                onDownloadFile(block)
            } label: {
                content(canDownload: true)
            }
            .buttonStyle(.plain)
        } else {
            content(canDownload: false)
        }
    }

    private func content(canDownload: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.fill")
                .font(.title3)
                .foregroundStyle(.secondary)
                .frame(width: 28, height: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(block.name)
                    .font(.body)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let extensionName {
                    Text(extensionName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if canDownload {
                Image(fontAwesome: .download)
                    .foregroundStyle(.secondary)
                    .frame(width: 18, height: 18)
            }
        }
        .padding(12)
        .background(Color.flareSecondarySystemGroupedBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .articleCardBlockOutline()
        .contentShape(Rectangle())
    }
}

private struct ArticleEmbedBlockView: View {
    let block: UiArticleBlockEmbed
    let onOpenURL: (String) -> Void

    var body: some View {
        Group {
            if let url = block.url {
                Button {
                    onOpenURL(url)
                } label: {
                    content
                }
                .buttonStyle(.plain)
            } else {
                content
            }
        }
    }

    private var content: some View {
        HStack(spacing: 12) {
            if let imageUrl = block.imageUrl {
                NetworkImage(data: imageUrl)
                    .frame(width: 64, height: 64)
                    .clipShape(.rect(cornerRadius: 8))
            } else {
                Image(fontAwesome: .globe)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 28, height: 28)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(block.title ?? block.url ?? block.htmlFallback ?? "")
                    .font(.body)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let description = block.description_ {
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                }
                if let url = block.url {
                    Text(url)
                        .font(.caption)
                        .foregroundStyle(.tint)
                        .lineLimit(1)
                }
            }
        }
        .padding(12)
        .background(Color.flareSecondarySystemGroupedBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .articleCardBlockOutline()
        .contentShape(Rectangle())
    }
}

private struct ArticleContentGateBlockView: View {
    let block: UiArticleBlockContentGate
    let onOpenURL: (String) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(fontAwesome: .lock)
                .font(.title3)
                .foregroundStyle(.tint)
                .frame(width: 28, height: 28)

            VStack(alignment: .leading, spacing: 8) {
                Text("Subscription required", bundle: FlareAppleUILocalization.bundle)
                    .font(.body.weight(.semibold))
                Text(descriptionText)
                    .font(.body)
                    .foregroundStyle(.secondary)

                if let actionUrl = block.actionUrl, !actionUrl.isEmpty {
                    Button {
                        onOpenURL(actionUrl)
                    } label: {
                        Text("deep_link_account_picker_open_in_browser", bundle: FlareAppleUILocalization.bundle)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .background(Color.flareSecondarySystemGroupedBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .articleCardBlockOutline()
    }

    private var descriptionText: String {
        switch onEnum(of: block.reason) {
        case .subscriptionRequired:
            return FlareAppleUILocalization.string(
                "This article contains subscriber-only content.",
                fallback: "This article contains subscriber-only content."
            )
        }
    }
}

private struct ArticleRemoteImage: View {
    let url: String
    let preview: String?
    let customHeaders: [String: String]?

    var body: some View {
        if let preview {
            NetworkImage(data: url, placeholder: preview, customHeader: customHeaders)
        } else {
            NetworkImage(data: url, customHeader: customHeaders)
        }
    }
}

private struct ArticleLoadingView: View {
    var body: some View {
        ScrollView {
            LazyVStack {
                LazyVStack(alignment: .leading, spacing: 16) {
                    Rectangle()
                        .fill(.placeholder)
                        .frame(height: 32)
                        .clipShape(.rect(cornerRadius: 8))
                    HStack(spacing: 12) {
                        Circle()
                            .fill(.placeholder)
                            .frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 8) {
                            Rectangle()
                                .fill(.placeholder)
                                .frame(width: 160, height: 14)
                            Rectangle()
                                .fill(.placeholder)
                                .frame(width: 96, height: 12)
                        }
                    }
                    Divider()
                    ForEach(0..<6, id: \.self) { index in
                        Rectangle()
                            .fill(.placeholder)
                            .frame(height: index == 5 ? 80 : 16)
                            .clipShape(.rect(cornerRadius: 8))
                    }
                }
                .redacted(reason: .placeholder)
                .frame(maxWidth: 680, alignment: .leading)
                .padding()
            }
            .frame(maxWidth: .infinity)
        }
    }
}

private extension View {
    @ViewBuilder
    func articleCardBlockOutline(cornerRadius: CGFloat = 12) -> some View {
        #if os(macOS)
        overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(Color.flareSeparator.opacity(0.45), lineWidth: 1)
        }
        #else
        self
        #endif
    }

    @ViewBuilder
    func articleNavigationBarTitleDisplayMode() -> some View {
        #if os(iOS)
        navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }

}

private func articleAspectRatio(_ value: Float) -> CGFloat {
    let ratio = CGFloat(value)
    guard ratio.isFinite, ratio > 0 else { return 16 / 9 }
    return min(max(ratio, 0.2), 4)
}
