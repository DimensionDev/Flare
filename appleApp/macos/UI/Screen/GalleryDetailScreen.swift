import SwiftUI
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

struct GalleryDetailScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<GalleryDetailPresenterState>

    private let accountType: AccountType
    private let statusKey: MicroBlogKey
    private let onNavigate: (Route) -> Void

    init(accountType: AccountType, statusKey: MicroBlogKey, onNavigate: @escaping (Route) -> Void) {
        self.accountType = accountType
        self.statusKey = statusKey
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: GalleryDetailPresenter(accountType: accountType, statusKey: statusKey)
            )
        )
    }

    var body: some View {
        StateView(state: presenter.state.detail) { detail in
            compatBody(detail: detail)
        } errorContent: { error in
            ListErrorView(error: error) {
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } loadingContent: {
            GalleryDetailLoadingView()
        }
        .background(Color.flareSystemGroupedBackground)
        .navigationTitle("")
    }

    private func compatBody(detail: GalleryDetail) -> some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                GalleryImagesView(
                    detail: detail,
                    openMedia: { image in navigateToMedia(detail: detail, media: image) }
                )

                Divider()
                    .padding(.vertical, 12)

                GalleryAfterImagesContent(
                    detail: detail,
                    comments: presenter.state.comments,
                    recommendations: presenter.state.recommendations,
                    commentLimit: 3,
                    onProfile: navigateToProfile,
                    onViewMoreComments: {
                        onNavigate(.galleryComments(accountType, statusKey))
                    },
                    onOpenMedia: { post, media in navigateToMedia(post: post, media: media) }
                )
                .padding(.horizontal, 16)
            }
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
        }
        .detectScrolling()
    }

    private func navigateToProfile(_ user: UiProfile) {
        let route = DeeplinkRoute.ProfileUser(accountType: accountType, userKey: user.key)
        if let url = URL(string: route.toUri()) {
            openURL(url)
        }
    }

    private func navigateToMedia(detail: GalleryDetail, media: UiMedia) {
        let medias = detail.images.map { $0 as any UiMedia }
        let index = medias.firstIndex { $0.url == media.url } ?? 0
        onNavigate(.mediaRaw(medias, index, media.mediaPreviewURL))
    }

    private func navigateToMedia(post: UiTimelineV2.Post, media: UiMedia) {
        let medias = post.galleryImagesForRawMedia.map { $0 as any UiMedia }
        let index = medias.firstIndex { $0.url == media.url } ?? 0
        onNavigate(.mediaRaw(medias, index, media.mediaPreviewURL))
    }
}

private struct GalleryImagesView: View {
    let detail: GalleryDetail
    let openMedia: (UiMediaImage) -> Void

    private var images: [UiMediaImage] {
        detail.images.map { $0 }
    }

    var body: some View {
        if images.isEmpty {
            Rectangle()
                .fill(.placeholder)
                .frame(height: 320)
                .redacted(reason: .placeholder)
        } else if detail.orientation == .horizontal {
            GeometryReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(0..<images.count, id: \.self) { index in
                            imageView(images[index])
                                .frame(width: proxy.size.width, height: proxy.size.height)
                        }
                    }
                }
                .scrollIndicators(.visible)
            }
            .frame(maxWidth: .infinity, minHeight: 420)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(0..<images.count, id: \.self) { index in
                    let image = images[index]
                    NetworkImage(data: image.url, customHeader: image.customHeaders, contentMode: .fit)
                        .aspectRatio(CGFloat(image.aspectRatio), contentMode: .fit)
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            openMedia(image)
                        }
                }
            }
        }
    }

    private func imageView(_ image: UiMediaImage) -> some View {
        NetworkImage(data: image.url, customHeader: image.customHeaders)
            .scaledToFit()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .onTapGesture {
                openMedia(image)
            }
    }
}

private struct GalleryAfterImagesContent: View {
    let detail: GalleryDetail
    let comments: PagingState<UiTimelineV2>
    let recommendations: PagingState<UiTimelineV2>
    let commentLimit: Int?
    let onProfile: (UiProfile) -> Void
    let onViewMoreComments: () -> Void
    let onOpenMedia: (UiTimelineV2.Post, UiMedia) -> Void

    var body: some View {
        VStack(spacing: 0) {
            GalleryAuthorCard(detail: detail, onProfile: onProfile)
            Divider()
            GalleryInfoCard(detail: detail)
        }

        Divider()
            .padding(.vertical, 12)

        GalleryCommentsPreview(
            comments: comments,
            limit: commentLimit,
            onViewMore: onViewMoreComments
        )

        Divider()
            .padding(.vertical, 12)

        GalleryRecommendationsGrid(
            recommendations: recommendations,
            onOpenMedia: onOpenMedia
        )
    }
}

private struct GalleryAuthorCard: View {
    @Environment(\.openURL) private var openURL
    let detail: GalleryDetail
    let onProfile: (UiProfile) -> Void

    var body: some View {
        HStack(spacing: 12) {
            if let user = detail.author {
                AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                    .frame(width: 44, height: 44)
                    .onTapGesture {
                        onProfile(user)
                    }
            } else {
                Circle()
                    .fill(.placeholder)
                    .frame(width: 44, height: 44)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(detail.title)
                    .font(.headline)
                    .fontWeight(.semibold)
                    .fixedSize(horizontal: false, vertical: true)
                if let user = detail.author {
                    RichText(text: user.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            Button {
                detail.onBookmark(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
            } label: {
                Image(fontAwesome: detail.isBookmarked ? .bookmarkFill : .bookmark)
            }
            .foregroundStyle(detail.isBookmarked ? Color.accentColor : Color.secondary)
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }
}

private struct GalleryInfoCard: View {
    let detail: GalleryDetail

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            GalleryMetadataRow(detail: detail)
            if let content = detail.content {
                RichText(text: content)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }
}

private struct GalleryMetadataRow: View {
    let detail: GalleryDetail

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            DateTimeText(data: detail.createdAt, fullTime: true)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            ForEach(Array(detail.matrix.enumerated()), id: \.offset) { _, item in
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    StatusActionIcon(icon: item.icon)
                    if !item.humanizedCount.isEmpty {
                        Text(item.humanizedCount)
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }
}

private struct GalleryCommentsPreview: View {
    let comments: PagingState<UiTimelineV2>
    let limit: Int?
    let onViewMore: () -> Void

    var body: some View {
        switch onEnum(of: comments) {
        case .empty:
            EmptyView()
        case .error(let error):
            ListErrorView(error: error.error) {
                error.onRetry()
            }
        case .loading:
            VStack(spacing: 0) {
                GallerySectionTitle("Comments")
                ForEach(0..<3, id: \.self) { index in
                    if index > 0 {
                        Divider()
                    }
                    TimelinePlaceholderView()
                        .padding(.horizontal)
                        .padding(.vertical, 12)
                }
            }
        case .success(let success):
            let total = Int(success.itemCount)
            let visible = limit.map { min(total, $0) } ?? total
            if visible > 0 {
                VStack(spacing: 0) {
                    GallerySectionTitle("Comments")
                    ForEach(0..<visible, id: \.self) { index in
                        if let item = success.peek(index: Int32(index)) {
                            if index > 0 {
                                Divider()
                            }
                            TimelineView(data: item, detailStatusKey: nil)
                                .padding(.horizontal)
                                .padding(.vertical, 12)
                            .onAppear {
                                success.get(index: Int32(index))
                            }
                        }
                    }
                    if let limit, total > limit || success.hasMorePages {
                        Button(action: onViewMore) {
                            Text("View more")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .padding(.top, 8)
                    }
                }
            }
        }
    }
}

private struct GalleryRecommendationsGrid: View {
    let recommendations: PagingState<UiTimelineV2>
    let onOpenMedia: (UiTimelineV2.Post, UiMedia) -> Void
    private let columnCount = 2

    var body: some View {
        switch onEnum(of: recommendations) {
        case .empty:
            EmptyView()
        case .error(let error):
            VStack(alignment: .leading, spacing: 8) {
                GallerySectionTitle("Recommendations")
                ListErrorView(error: error.error) {
                    error.onRetry()
                }
            }
        case .loading:
            VStack(alignment: .leading, spacing: 8) {
                GallerySectionTitle("Recommendations")
                HStack(alignment: .top, spacing: 8) {
                    ForEach(0..<columnCount, id: \.self) { column in
                        LazyVStack(spacing: 8) {
                            ForEach(0..<3, id: \.self) { index in
                                GalleryRecommendationTile(item: nil, onOpenMedia: onOpenMedia)
                                    .aspectRatio((index + column).isMultiple(of: 2) ? 0.75 : 1.25, contentMode: .fit)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        case .success(let success):
            let itemCount = Int(success.itemCount)
            if itemCount > 0 {
                VStack(alignment: .leading, spacing: 8) {
                    GallerySectionTitle("Recommendations")
                    HStack(alignment: .top, spacing: 8) {
                        ForEach(0..<columnCount, id: \.self) { column in
                            LazyVStack(spacing: 8) {
                                ForEach(indexes(for: column, itemCount: itemCount), id: \.self) { index in
                                    GalleryRecommendationTile(
                                        item: success.peek(index: Int32(index)),
                                        onOpenMedia: onOpenMedia
                                    )
                                    .onAppear {
                                        success.get(index: Int32(index))
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    appendFooter(success)
                }
            }
        }
    }

    private func indexes(for column: Int, itemCount: Int) -> [Int] {
        Array(stride(from: column, to: itemCount, by: columnCount))
    }

    @ViewBuilder
    private func appendFooter(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        switch onEnum(of: success.appendState) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        case .error:
            Button {
                success.retry()
            } label: {
                Text("Retry")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .padding(.top, 4)
        case .notLoading:
            EmptyView()
        }
    }
}

private struct GalleryRecommendationTile: View {
    let item: UiTimelineV2?
    let onOpenMedia: (UiTimelineV2.Post, UiMedia) -> Void

    var body: some View {
        TimelineGalleryItemView(item: item, onOpenMedia: onOpenMedia)
    }
}

private struct GallerySectionTitle: View {
    let title: LocalizedStringKey

    init(_ title: LocalizedStringKey) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .font(.headline)
            .fontWeight(.semibold)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 4)
            .padding(.bottom, 6)
    }
}

private struct GalleryDetailLoadingView: View {
    var body: some View {
        VStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 16)
                .fill(.placeholder)
                .frame(height: 420)
            RoundedRectangle(cornerRadius: 16)
                .fill(.placeholder)
                .frame(height: 72)
        }
        .padding(16)
        .redacted(reason: .placeholder)
    }
}

struct GalleryCommentsScreen: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey
    @StateObject private var presenter: KotlinPresenter<GalleryDetailPresenterState>

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.accountType = accountType
        self.statusKey = statusKey
        self._presenter = .init(
            wrappedValue: .init(
                presenter: GalleryDetailPresenter(accountType: accountType, statusKey: statusKey)
            )
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                TimelinePagingContent(
                    data: presenter.state.comments,
                    detailStatusKey: nil
                )
            }
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 16)
        }
        .detectScrolling()
        .background(Color.flareSystemGroupedBackground)
        .navigationTitle("Comments")
    }
}

private extension UiTimelineV2.Post {
    var galleryImagesForRawMedia: [UiMediaImage] {
        images.compactMap { media in
            if case .image(let image) = onEnum(of: media) {
                return image
            }
            return nil
        }
    }
}

private extension PagingStateSuccess<UiTimelineV2> {
    var hasMorePages: Bool {
        if case .notLoading(let notLoading) = onEnum(of: appendState) {
            return !notLoading.endOfPaginationReached
        }
        return true
    }
}
