import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct TimelinePagingContent: View {
    private let data: PagingState<UiTimelineV2>
    private let detailStatusKey: MicroBlogKey?
    private let loadingCount: Int
    private let contentMaxWidth: CGFloat?
    private let contentHorizontalPadding: CGFloat

    public init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey? = nil,
        loadingCount: Int = 5,
        contentMaxWidth: CGFloat? = nil,
        contentHorizontalPadding: CGFloat = 0
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.loadingCount = loadingCount
        self.contentMaxWidth = contentMaxWidth
        self.contentHorizontalPadding = contentHorizontalPadding
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .empty:
            contentLayout {
                ListEmptyView()
            }
        case .error(let error):
            contentLayout {
                ListErrorView(error: error.error) {
                    _ = error.onRetry()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            }
        case .loading:
            ForEach(0..<loadingCount, id: \.self) { index in
                contentLayout {
                    loadingRow(index: index, totalCount: loadingCount)
                }
            }
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        let count = Int(success.itemCount)
        let rows = TimelinePagingRows(success: success, count: count)
        ForEach(rows) { row in
            contentLayout {
                TimelinePagingRowView(
                    row: row,
                    totalCount: count,
                    detailStatusKey: detailStatusKey,
                    onDisplay: { index in
                        _ = success.get(index: Int32(index))
                    }
                )
            }
        }

        switch onEnum(of: success.appendState) {
        case .error(let error):
            contentLayout {
                ListErrorView(error: error.error) {
                    success.retry()
                }
                .frame(maxWidth: .infinity, alignment: .center)
            }
        case .loading:
            contentLayout {
                ProgressView()
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        case .notLoading:
            EmptyView()
        }
    }

    private func loadingRow(index: Int, totalCount: Int) -> some View {
        AdaptiveTimelineCard(index: index, totalCount: totalCount) {
            TimelinePlaceholderView()
                .padding(.horizontal)
                .padding(.vertical, 12)
        }
    }

    @ViewBuilder
    private func contentLayout<Content: View>(
        @ViewBuilder content: () -> Content
    ) -> some View {
        if let contentMaxWidth {
            content()
                .frame(maxWidth: contentMaxWidth, alignment: .leading)
                .padding(.horizontal, contentHorizontalPadding)
        } else if contentHorizontalPadding > 0 {
            content()
                .padding(.horizontal, contentHorizontalPadding)
        } else {
            content()
        }
    }
}

public struct TimelinePagingListContent: View {
    private let data: PagingState<UiTimelineV2>
    private let detailStatusKey: MicroBlogKey?
    private let loadingCount: Int
    private let usesDefaultHorizontalPadding: Bool

    public init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey? = nil,
        loadingCount: Int = 5,
        usesDefaultHorizontalPadding: Bool = false
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.loadingCount = loadingCount
        self.usesDefaultHorizontalPadding = usesDefaultHorizontalPadding
    }

    public var body: some View {
        TimelinePagingContent(
            data: data,
            detailStatusKey: detailStatusKey,
            loadingCount: loadingCount
        )
        .modifier(TimelinePagingListRowStyle(usesDefaultHorizontalPadding: usesDefaultHorizontalPadding))
    }
}

private struct TimelinePagingListRowStyle: ViewModifier {
    let usesDefaultHorizontalPadding: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if usesDefaultHorizontalPadding {
            content
                .listRowSeparator(.hidden)
                .padding(.horizontal)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
        } else {
            content
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .listRowBackground(Color.clear)
        }
    }
}

private struct TimelinePagingRow: Identifiable {
    let id: String
    let index: Int
    let item: UiTimelineV2?
}

private struct TimelinePagingRowView: View {
    let row: TimelinePagingRow
    let totalCount: Int
    let detailStatusKey: MicroBlogKey?
    let onDisplay: (Int) -> Void

    var body: some View {
        Group {
            if let item = row.item {
                AdaptiveTimelineCard(index: row.index, totalCount: totalCount) {
                    TimelineView(data: item, detailStatusKey: detailStatusKey)
                        .padding(.horizontal)
                        #if os(macOS)
                        .padding(.top, 12)
                        .padding(.bottom, 8)
                        #else
                        .padding(.vertical, 12)
                        #endif
                }
            } else {
                AdaptiveTimelineCard(index: row.index, totalCount: totalCount) {
                    TimelinePlaceholderView()
                        .padding(.horizontal)
                        .padding(.vertical, 12)
                }
            }
        }
        .onAppear {
            onDisplay(row.index)
        }
    }
}

private struct TimelinePagingRows: @MainActor RandomAccessCollection {
    let success: PagingStateSuccess<UiTimelineV2>
    let count: Int

    var startIndex: Int { 0 }
    var endIndex: Int { count }

    func index(after index: Int) -> Int { index + 1 }
    func index(before index: Int) -> Int { index - 1 }
    func index(_ index: Int, offsetBy distance: Int) -> Int { index + distance }
    func distance(from start: Int, to end: Int) -> Int { end - start }

    subscript(position: Int) -> TimelinePagingRow {
        let item = success.peek(index: Int32(position))
        return TimelinePagingRow(
            id: item?.itemKey ?? "placeholder-\(position)",
            index: position,
            item: item
        )
    }
}

public struct TimelineGalleryItemView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.timelineAppearance.showMedia) private var showMedia
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.timelineMediaOpenAction) private var timelineMediaOpenAction

    private let item: UiTimelineV2?
    private let placeholderVariant: Int
    private let onOpenMedia: ((UiTimelineV2.Post, UiMedia) -> Void)?

    public init(
        item: UiTimelineV2?,
        placeholderVariant: Int = 0,
        onOpenMedia: ((UiTimelineV2.Post, UiMedia) -> Void)? = nil
    ) {
        self.item = item
        self.placeholderVariant = placeholderVariant
        self.onOpenMedia = onOpenMedia
    }

    public var body: some View {
        Group {
            if let item {
                switch onEnum(of: item) {
                case .post(let post):
                    postTile(post)
                case .timelinePostItem:
                    if let post = item.timelineContentPost {
                        postTile(post)
                    } else {
                        fallbackTile(item)
                    }
                case .feed(let feed):
                    feedTile(feed)
                default:
                    fallbackTile(item)
                }
            } else {
                placeholderTile
            }
        }
        .background(Color.flareSecondarySystemGroupedBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.flareSeparator.opacity(0.45), lineWidth: 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private func postTile(_ post: UiTimelineV2.Post) -> some View {
        let contents: [UiRichText] = if post.translationDisplayState == .translated,
                                        let translation = post.content.translation {
            translateConfig.showOriginalWithTranslation ? [post.content.original, translation] : [translation]
        } else {
            [post.content.original]
        }
        VStack(alignment: .leading, spacing: 8) {
            if showMedia, let media = post.images.first {
                MediaView(data: media)
                    .aspectRatio(CGFloat(max(media.aspectRatio ?? 1, 0.3)), contentMode: .fit)
                    .clipped()
                    .highPriorityGesture(
                        TapGesture().onEnded {
                            handleMediaTap(post: post, media: media)
                        }
                    )
            } else if contents.contains(where: { !$0.isEmpty }) {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(contents.enumerated()), id: \.offset) { _, content in
                        if !content.isEmpty {
                            RichText(text: content)
                                .font(.subheadline)
                                .lineLimit(5)
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 8)
            }

            if let user = post.user {
                HStack(spacing: 6) {
                    AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                        .frame(width: 24, height: 24)
                    RichText(text: user.name)
                        .font(.caption)
                        .lineLimit(1)
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onTapGesture {
            post.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }

    private func handleMediaTap(post: UiTimelineV2.Post, media: UiMedia) {
        if post.mediaClickPolicy == .openPostClickEvent {
            post.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        } else if let onOpenMedia {
            onOpenMedia(post, media)
        } else if let timelineMediaOpenAction {
            let medias = Array(post.images)
            let index = medias.firstIndex { $0.url == media.url } ?? 0
            timelineMediaOpenAction(post, media, index)
        } else {
            post.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }

    @ViewBuilder
    private func feedTile(_ feed: UiTimelineV2.Feed) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if showMedia, let media = feed.media {
                NetworkImage(data: media.url, customHeader: media.customHeaders)
                    .aspectRatio(CGFloat(max(media.aspectRatio, 0.3)), contentMode: .fit)
                    .clipped()
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    if let title = feed.title, !title.isEmpty {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(3)
                    }
                    if let description = feed.description_, !description.isEmpty {
                        Text(description)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(4)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 8)
            }

            HStack(spacing: 6) {
                if let icon = feed.source.icon, !icon.isEmpty {
                    NetworkImage(data: icon)
                        .frame(width: 20, height: 20)
                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                }
                Text(feed.source.name)
                    .font(.caption)
                    .lineLimit(1)
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onTapGesture {
            feed.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }

    private func fallbackTile(_ item: UiTimelineV2) -> some View {
        TimelineView(data: item, detailStatusKey: nil)
            .padding(10)
    }

    private var placeholderTile: some View {
        VStack(alignment: .leading, spacing: 8) {
            Rectangle()
                .fill(.placeholder)
                .aspectRatio(placeholderVariant.isMultiple(of: 2) ? 0.75 : 1.2, contentMode: .fit)
            UserLoadingView()
                .padding(8)
        }
        .redacted(reason: .placeholder)
    }
}
