import SwiftUI
import WaterfallGrids
import KotlinSharedUI

struct GalleryTimelinePagingView: View {
    @Environment(\.refresh) private var refreshAction: RefreshAction?
    let data: PagingState<UiTimelineV2>

    var body: some View {
        GeometryReader { proxy in
            let isWide = proxy.size.width >= 600
            let target: CGFloat = isWide ? 240 : 160
            let columnCount = max(Int((proxy.size.width / target).rounded(.down)), 2)
            let columns: [WaterfallItems.Column] = Array(repeating: .init(spacing: 8), count: columnCount)
            ScrollView {
                galleryContent(columns: columns)
                    .padding(8)
            }
            .refreshable {
                await refreshAction?()
            }
            .detectScrolling()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemGroupedBackground))
        }
    }

    @ViewBuilder
    private func galleryContent(columns: [WaterfallItems.Column]) -> some View {
        switch onEnum(of: data) {
        case .empty:
            ListEmptyView()
        case .error(let error):
            ListErrorView(error: error.error) { error.onRetry() }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading:
            LazyWaterfallGrid(items: .columns(columns), spacing: 8, data: Array(0..<8)) { _ in
                GalleryPlaceholderTile()
            }
        case .success(let success):
            LazyWaterfallGrid(items: .columns(columns), spacing: 8, data: TimelineCollection(data: success)) { data in
                Group {
                    if let item = data.data {
                        GalleryTimelineTile(item: item)
                    } else {
                        GalleryPlaceholderTile()
                    }
                }
                .onAppear {
                    _ = success.get(index: Int32(data.index))
                }
            }
            switch onEnum(of: success.appendState) {
            case .error(let error):
                ListErrorView(error: error.error) { success.retry() }
                    .frame(maxWidth: .infinity, alignment: .center)
            case .loading:
                ProgressView()
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .center)
            case .notLoading(let notLoading):
                if notLoading.endOfPaginationReached {
                    Text("end_of_list")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .center)
                } else {
                    EmptyView()
                }
            }
        }
    }
}

struct GalleryTimelineTile: View {
    let item: UiTimelineV2

    var body: some View {
        switch onEnum(of: item) {
        case .post(let post):
            GalleryPostTile(post: post)
        case .feed(let feed):
            GalleryFeedTile(feed: feed)
        default:
            EmptyView()
        }
    }
}

private struct GalleryTileImage: View {
    let url: String
    let customHeader: [String: String]?
    let aspectRatio: CGFloat
    var body: some View {
        NetworkImage(data: url, customHeader: customHeader)
    }
}

struct GalleryPostTile: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.appearanceSettings.showMedia) private var showMedia
    let post: UiTimelineV2.Post

    private var firstImage: (any UiMedia)? { post.images.first }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let media = firstImage, showMedia {
                let preview: String? = {
                    switch onEnum(of: media) {
                    case .image(let image): return image.previewUrl
                    case .video(let video): return video.thumbnailUrl
                    case .gif(let gif): return gif.previewUrl
                    case .audio: return nil
                    }
                }()
                if let preview {
                    GalleryTileImage(
                        url: preview,
                        customHeader: nil,
                        aspectRatio: media.aspectRatio ?? 1.0
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        let route = DeeplinkRoute.MediaStatusMedia(
                            statusKey: post.statusKey,
                            accountType: post.accountType,
                            index: 0,
                            preview: preview
                        )
                        if let url = URL(string: route.toUri()) {
                            openURL(url)
                        }
                    }
                }
            } else if !post.content.isEmpty {
                RichText(text: post.content)
                    .font(.caption)
                    .lineLimit(5)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding([.horizontal, .top], 8)
            }
            if let user = post.user {
                HStack(spacing: 6) {
                    AvatarView(data: user.avatar)
                        .frame(width: 20, height: 20)
                    RichText(text: user.name)
                        .font(.caption)
                        .lineLimit(1)
                }
                .padding(.horizontal, 8)
                .padding(.top, 4)
                .padding(.bottom, 8)
            }
        }
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .onTapGesture {
            post.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}

struct GalleryFeedTile: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.appearanceSettings.showMedia) private var showMedia
    let feed: UiTimelineV2.Feed

    private var descriptionText: String? {
        let description = feed.description_ ?? feed.description
        return description.isEmpty ? nil : description
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let media = feed.media, showMedia {
                GalleryTileImage(
                    url: media.url,
                    customHeader: media.customHeaders,
                    aspectRatio: CGFloat(media.aspectRatio)
                )
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    if let title = feed.title {
                        Text(title)
                            .font(.caption.weight(.semibold))
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if let desc = descriptionText {
                        Text(desc)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding([.horizontal, .top], 8)
            }
            HStack(spacing: 6) {
                if let icon = feed.source.icon, !icon.isEmpty {
                    NetworkImage(data: icon)
                        .frame(width: 20, height: 20)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
                Text(feed.source.name)
                    .font(.caption)
                    .lineLimit(1)
            }
            .padding(.horizontal, 8)
            .padding(.top, 4)
            .padding(.bottom, 8)
        }
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .onTapGesture {
            feed.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}

struct GalleryPlaceholderTile: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(Color.gray.opacity(0.2))
                .aspectRatio(1, contentMode: .fit)
            HStack(spacing: 6) {
                Circle().fill(Color.gray.opacity(0.2)).frame(width: 20, height: 20)
                Text("Loading")
                    .font(.caption)
                    .redacted(reason: .placeholder)
            }
            .padding(.horizontal, 8)
            .padding(.top, 4)
            .padding(.bottom, 8)
        }
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
