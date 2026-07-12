import SwiftUI
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

struct GalleryDetailScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<GalleryDetailPresenterState>
    @State private var showInfoSheet = false
    @State private var selectedTab: GallerySideTab = .info

    private let accountType: AccountType
    private let statusKey: MicroBlogKey
    private let onNavigate: (Route) -> Void
    private var isBigScreen: Bool { horizontalSizeClass == .regular }

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
            if isBigScreen {
                bigScreenBody(detail: detail)
            } else {
                compactBody(detail: detail)
            }
        } errorContent: { error in
            ListErrorView(error: error) {
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } loadingContent: {
            GalleryDetailLoadingView()
        }
        .environment(\.timelineAppearance, timelineAppearance.galleryCardTimeline())
        .background(Color(.systemGroupedBackground))
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !isBigScreen {
                ToolbarItem(placement: .principal) {
                    GalleryCompactToolbarTitle(detailState: presenter.state.detail) {
                        navigateToProfile($0)
                    }
                }

                ToolbarItemGroup(placement: .primaryAction) {
                    Button {
                        switch onEnum(of: presenter.state.detail) {
                        case .success(let success):
                            navigateToShare(success.data)
                        case .loading, .error:
                            break
                        }
                    } label: {
                        Image(fontAwesome: .shareNodes)
                    }
                    .disabled({
                        switch onEnum(of: presenter.state.detail) {
                        case .success:
                            return false
                        case .loading, .error:
                            return true
                        }
                    }())

                    Button {
                        switch onEnum(of: presenter.state.detail) {
                        case .success:
                            showInfoSheet = true
                        case .loading, .error:
                            break
                        }
                    } label: {
                        Image(fontAwesome: .chevronDown)
                    }
                    .disabled({
                        switch onEnum(of: presenter.state.detail) {
                        case .success:
                            return false
                        case .loading, .error:
                            return true
                        }
                    }())
                }
            }
        }
    }

    private func compactBody(detail: GalleryDetail) -> some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                GalleryImagesView(
                    detail: detail,
                    openMedia: { image in navigateToMedia(detail: detail, media: image) }
                )
                .padding(.bottom, 10)

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
            .padding(.bottom, 24)
        }
        .sheet(isPresented: $showInfoSheet) {
            NavigationStack {
                ScrollView {
                    LazyVStack(spacing: 2) {
                        GalleryAfterImagesContent(
                            detail: detail,
                            comments: presenter.state.comments,
                            recommendations: presenter.state.recommendations,
                            commentLimit: 3,
                            onProfile: navigateToProfile,
                            onViewMoreComments: {
                                showInfoSheet = false
                                onNavigate(.galleryComments(accountType, statusKey))
                            },
                            onOpenMedia: { post, media in navigateToMedia(post: post, media: media) }
                        )
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
                .navigationTitle("Info")
                .navigationBarTitleDisplayMode(.inline)
            }
            .presentationDetents([.medium, .large])
        }
    }

    private func bigScreenBody(detail: GalleryDetail) -> some View {
        GeometryReader { proxy in
            HStack(spacing: 0) {
                GalleryBigScreenImagePane(
                    detail: detail,
                    onOpenMedia: { image in navigateToMedia(detail: detail, media: image) }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                Divider()

                GallerySideBar(
                    selectedTab: $selectedTab,
                    detail: detail,
                    comments: presenter.state.comments,
                    recommendations: presenter.state.recommendations,
                    onProfile: navigateToProfile,
                )
                .frame(width: sidebarWidth(for: proxy.size.width))
                .frame(maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            }
        }
    }

    private func sidebarWidth(for width: CGFloat) -> CGFloat {
        min(max(width * 0.34, 320), 420)
    }

    private func navigateToProfile(_ user: UiProfile) {
        let route = DeeplinkRoute.ProfileUser(accountType: accountType, userKey: user.key)
        if let url = URL(string: route.toUri()) {
            openURL(url)
        }
    }

    private func navigateToShare(_ detail: GalleryDetail) {
        onNavigate(.statusShareSheet(detail.accountType, detail.statusKey, detail.url, nil, nil))
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

private enum GallerySideTab: String, CaseIterable, Identifiable {
    case info
    case comments
    case recommend

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .info: "Info"
        case .comments: "Comments"
        case .recommend: "Recommend"
        }
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
            TabView {
                ForEach(0..<images.count, id: \.self) { index in
                    let image = images[index]
                    NetworkImage(data: image.url, customHeader: image.customHeaders)
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            openMedia(image)
                        }
                }
            }
            .tabViewStyle(.page(indexDisplayMode: images.count > 1 ? .automatic : .never))
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
}

private struct GalleryBigScreenImagePane: View {
    let detail: GalleryDetail
    let onOpenMedia: (UiMediaImage) -> Void

    private var images: [UiMediaImage] {
        detail.images.map { $0 }
    }

    var body: some View {
        ZStack {
            Color(.systemBackground)
            if images.isEmpty {
                Rectangle()
                    .fill(.placeholder)
                    .frame(height: 320)
                    .redacted(reason: .placeholder)
            } else if detail.orientation == .horizontal {
                TabView {
                    ForEach(0..<images.count, id: \.self) { index in
                        let image = images[index]
                        NetworkImage(data: image.url, customHeader: image.customHeaders)
                            .scaledToFit()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                onOpenMedia(image)
                            }
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: images.count > 1 ? .automatic : .never))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                GeometryReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(0..<images.count, id: \.self) { index in
                                let image = images[index]
                                NetworkImage(data: image.url, customHeader: image.customHeaders, contentMode: .fit)
                                    .aspectRatio(CGFloat(image.aspectRatio), contentMode: .fit)
                                    .frame(maxWidth: .infinity)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        onOpenMedia(image)
                                    }
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: images.count == 1 ? proxy.size.height : nil, alignment: .center)
                    }
                }
            }
        }
        .ignoresSafeArea(edges: .top)
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
        VStack(spacing: 2) {
            GalleryAuthorCard(detail: detail, onProfile: onProfile)
            GalleryInfoCard(detail: detail)
        }

        GalleryCommentsPreview(
            comments: comments,
            limit: commentLimit,
            onViewMore: onViewMoreComments
        )
        .padding(.top, 12)

        GalleryRecommendationsGrid(
            recommendations: recommendations,
            onOpenMedia: onOpenMedia
        )
        .padding(.top, 12)
    }
}

private struct GalleryAuthorCard: View {
    @Environment(\.openURL) private var openURL
    let detail: GalleryDetail
    let onProfile: (UiProfile) -> Void

    var body: some View {
        ListCardView(index: 0, totalCount: 2) {
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
}

private struct GalleryInfoCard: View {
    let detail: GalleryDetail

    var body: some View {
        ListCardView(index: 1, totalCount: 2) {
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
            VStack(spacing: 2) {
                GallerySectionTitle("Comments")
                ForEach(0..<3, id: \.self) { index in
                    ListCardView(index: index, totalCount: 3) {
                        TimelinePlaceholderView()
                            .padding(.horizontal)
                            .padding(.vertical, 12)
                    }
                }
            }
        case .success(let success):
            let total = Int(success.itemCount)
            let visible = limit.map { min(total, $0) } ?? total
            if visible > 0 {
                VStack(spacing: 2) {
                    GallerySectionTitle("Comments")
                    ForEach(0..<visible, id: \.self) { index in
                        if let item = success.peek(index: Int32(index)) {
                            ListCardView(index: index, totalCount: visible) {
                                TimelineView(data: item, detailStatusKey: nil)
                                    .padding(.horizontal)
                                    .padding(.vertical, 12)
                            }
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
                Text("action_retry")
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

private struct GallerySideBar: View {
    @Binding var selectedTab: GallerySideTab
    let detail: GalleryDetail
    let comments: PagingState<UiTimelineV2>
    let recommendations: PagingState<UiTimelineV2>
    let onProfile: (UiProfile) -> Void

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $selectedTab) {
                ForEach(GallerySideTab.allCases) { tab in
                    Text(tab.title).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            switch selectedTab {
            case .info:
                ScrollView {
                    LazyVStack(spacing: 2) {
                        GalleryAuthorCard(detail: detail, onProfile: onProfile)
                        GalleryInfoCard(detail: detail)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
            case .comments:
                UITimelinePagingView(
                    data: comments,
                    detailStatusKey: nil,
                    key: "gallery_comments_\(detail.statusKey.description())",
                    suppressInitialRefreshIndicator: true
                )
            case .recommend:
                UIGalleryTimelinePagingView(data: recommendations)
            }
        }
    }
}

private struct GalleryCompactToolbarTitle: View {
    let detailState: UiState<GalleryDetail>
    let onProfile: (UiProfile) -> Void

    var body: some View {
        switch onEnum(of: detailState) {
        case .success(let success):
            let detail = success.data
            let user = detail.author
            HStack(spacing: 10) {
                if let user {
                    AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                        .frame(width: 32, height: 32)
                        .onTapGesture {
                            onProfile(user)
                        }
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(detail.title)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .lineLimit(1)
                    if let user {
                        RichText(text: user.name)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: 220, alignment: .leading)
            }
        case .loading:
            GalleryCompactToolbarTitleLoading()
        case .error:
            EmptyView()
        }
    }
}

private struct GalleryCompactToolbarTitleLoading: View {
    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(.placeholder)
                .frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 1) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(.placeholder)
                    .frame(width: 140, height: 14)
                RoundedRectangle(cornerRadius: 3)
                    .fill(.placeholder)
                    .frame(width: 84, height: 10)
            }
            .frame(maxWidth: 220, alignment: .leading)
        }
        .redacted(reason: .placeholder)
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
        UITimelinePagingView(
            data: presenter.state.comments,
            detailStatusKey: nil,
            key: "gallery_comments_\(statusKey.description())",
            suppressInitialRefreshIndicator: true
        )
        .environment(\.timelineAppearance, TimelineAppearance.companion.Default.galleryCardTimeline())
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Comments")
        .navigationBarTitleDisplayMode(.inline)
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

private extension TimelineAppearance {
    func galleryCardTimeline() -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: showMedia,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: expandContentWarning,
            expandMediaSize: expandMediaSize,
            limitMediaGridToNine: limitMediaGridToNine,
            videoAutoplay: videoAutoplay,
            showLinkPreview: showLinkPreview,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: postActionStyle,
            postActionLayout: postActionLayout,
            postActionFixedWidth: postActionFixedWidth,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: .card,
            aiConfig: aiConfig,
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}
