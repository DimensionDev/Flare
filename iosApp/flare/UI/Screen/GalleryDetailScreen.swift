import SwiftUI
@preconcurrency import KotlinSharedUI

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
                presenter.state.refresh()
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
                    GalleryCompactToolbarTitle(post: presenter.state.detail.galleryPost) {
                        navigateToProfile($0)
                    }
                }

                ToolbarItemGroup(placement: .primaryAction) {
                    Button {
                        presenter.state.detail.galleryPost?.shareAction?.onClicked(
                            ClickContext(launcher: AppleUriLauncher(openUrl: openURL))
                        )
                    } label: {
                        Image("fa-share-nodes")
                    }
                    .disabled(presenter.state.detail.galleryPost?.shareAction == nil)

                    Button {
                        showInfoSheet = true
                    } label: {
                        Image("fa-chevron-down")
                    }
                    .disabled(presenter.state.detail.galleryPost == nil)
                }
            }
        }
    }

    private func compactBody(detail: GalleryDetail) -> some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                GalleryImagesView(
                    detail: detail,
                    openMedia: { image in navigateToMedia(post: detail.post, media: image) }
                )
                .padding(.bottom, 10)

                GalleryAfterImagesContent(
                    post: detail.post,
                    comments: presenter.state.comments,
                    recommendations: presenter.state.recommendations,
                    commentLimit: 3,
                    onProfile: navigateToProfile,
                    onAction: { presenter.state.performAction(action: $0) },
                    onViewMoreComments: {
                        onNavigate(.galleryComments(accountType, statusKey))
                    },
                    onOpenMedia: { post, media in navigateToMedia(post: post, media: media) }
                )
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 24)
        }
        .refreshable {
            presenter.state.refresh()
        }
        .sheet(isPresented: $showInfoSheet) {
            NavigationStack {
                ScrollView {
                    LazyVStack(spacing: 2) {
                        GalleryAfterImagesContent(
                            post: detail.post,
                            comments: presenter.state.comments,
                            recommendations: presenter.state.recommendations,
                            commentLimit: 3,
                            onProfile: navigateToProfile,
                            onAction: { presenter.state.performAction(action: $0) },
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
                    onOpenMedia: { image in navigateToMedia(post: detail.post, media: image) }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                Divider()

                GallerySideBar(
                    selectedTab: $selectedTab,
                    post: detail.post,
                    comments: presenter.state.comments,
                    recommendations: presenter.state.recommendations,
                    onProfile: navigateToProfile,
                    onAction: { presenter.state.performAction(action: $0) }
                )
                .frame(width: sidebarWidth(for: proxy.size.width))
                .frame(maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            }
        }
        .refreshable {
            presenter.state.refresh()
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

    private func navigateToMedia(post: UiTimelineV2.Post, media: UiMedia) {
        let route = post.statusMediaRoute(media: media)
        if let url = URL(string: route.toUri()) {
            openURL(url)
        }
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
        detail.post.galleryImages
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
                    NetworkImage(data: image.url, customHeader: image.customHeaders)
                        .scaledToFill()
                        .aspectRatio(CGFloat(image.aspectRatio), contentMode: .fit)
                        .frame(maxWidth: .infinity)
                        .clipped()
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
        detail.post.galleryImages
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
                                NetworkImage(data: image.url, customHeader: image.customHeaders)
                                    .scaledToFill()
                                    .aspectRatio(CGFloat(image.aspectRatio), contentMode: .fit)
                                    .frame(maxWidth: .infinity)
                                    .clipped()
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
    let post: UiTimelineV2.Post
    let comments: PagingState<UiTimelineV2>
    let recommendations: PagingState<UiTimelineV2>
    let commentLimit: Int?
    let onProfile: (UiProfile) -> Void
    let onAction: (ActionMenu.Item) -> Void
    let onViewMoreComments: () -> Void
    let onOpenMedia: (UiTimelineV2.Post, UiMedia) -> Void

    var body: some View {
        VStack(spacing: 2) {
            GalleryAuthorCard(post: post, onProfile: onProfile, onAction: onAction)
            GalleryInfoCard(post: post, onAction: onAction)
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
    let post: UiTimelineV2.Post
    let onProfile: (UiProfile) -> Void
    let onAction: (ActionMenu.Item) -> Void

    var body: some View {
        ListCardView(index: 0, totalCount: 2) {
            HStack(spacing: 12) {
                if let user = post.user {
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
                    Text(post.galleryTitle)
                        .font(.headline)
                        .fontWeight(.semibold)
                        .fixedSize(horizontal: false, vertical: true)
                    if let user = post.user {
                        Text(user.handle.canonical)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 8)
                if let action = post.bookmarkAction {
                    Button {
                        onAction(action)
                    } label: {
                        StatusActionIcon(icon: action.icon)
                    }
                    .foregroundStyle(action.color?.swiftColor ?? .primary)
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
    }
}

private struct GalleryInfoCard: View {
    let post: UiTimelineV2.Post
    let onAction: (ActionMenu.Item) -> Void

    var body: some View {
        ListCardView(index: 1, totalCount: 2) {
            VStack(alignment: .leading, spacing: 8) {
                GalleryMetadataRow(post: post, onAction: onAction)
                if !post.content.isEmpty {
                    RichText(text: post.content)
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
    let post: UiTimelineV2.Post
    let onAction: (ActionMenu.Item) -> Void

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            DateTimeText(data: post.createdAt, fullTime: true)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            ForEach(Array(post.countedActions.enumerated()), id: \.offset) { _, action in
                Button {
                    onAction(action)
                } label: {
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        StatusActionIcon(icon: action.icon)
                        if let count = action.count?.humanized, !count.isEmpty {
                            Text(count)
                        }
                    }
                    .font(.caption)
                }
                .foregroundStyle(action.color?.swiftColor ?? .secondary)
                .buttonStyle(.plain)
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
    @Environment(\.openURL) private var openURL
    let item: UiTimelineV2?
    let onOpenMedia: (UiTimelineV2.Post, UiMedia) -> Void

    var body: some View {
        if let post = item as? UiTimelineV2.Post {
            VStack(alignment: .leading, spacing: 8) {
                if let media = post.images.first {
                    MediaView(data: media)
                        .aspectRatio(CGFloat(media.aspectRatio ?? 1), contentMode: .fit)
                        .clipShape(.rect(cornerRadius: 12))
                        .onTapGesture {
                            handleMediaTap(post: post, media: media)
                        }
                } else if !post.content.isEmpty {
                    RichText(text: post.content)
                        .lineLimit(5)
                        .padding(8)
                }
                HStack(spacing: 6) {
                    if let avatar = post.user?.avatar {
                        AvatarView(data: avatar.url, customHeader: avatar.customHeaders)
                            .frame(width: 24, height: 24)
                    }
                    Text(post.user?.name.raw ?? post.galleryTitle)
                        .font(.caption)
                        .lineLimit(1)
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 12))
            .contentShape(Rectangle())
            .onTapGesture {
                post.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
            }
        } else {
            RoundedRectangle(cornerRadius: 12)
                .fill(.placeholder)
                .aspectRatio(0.75, contentMode: .fit)
                .redacted(reason: .placeholder)
        }
    }

    private func handleMediaTap(post: UiTimelineV2.Post, media: UiMedia) {
        if post.mediaClickPolicy == .openPostClickEvent {
            post.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        } else {
            onOpenMedia(post, media)
        }
    }
}

private struct GallerySideBar: View {
    @Binding var selectedTab: GallerySideTab
    let post: UiTimelineV2.Post
    let comments: PagingState<UiTimelineV2>
    let recommendations: PagingState<UiTimelineV2>
    let onProfile: (UiProfile) -> Void
    let onAction: (ActionMenu.Item) -> Void

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
                        GalleryAuthorCard(post: post, onProfile: onProfile, onAction: onAction)
                        GalleryInfoCard(post: post, onAction: onAction)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
            case .comments:
                TimelinePagingContent(
                    data: comments,
                    detailStatusKey: nil,
                    key: "gallery_comments_\(post.statusKey.description())",
                    suppressInitialRefreshIndicator: true
                )
            case .recommend:
                GalleryTimelinePagingView(data: recommendations)
            }
        }
    }
}

private struct GalleryCompactToolbarTitle: View {
    let post: UiTimelineV2.Post?
    let onProfile: (UiProfile) -> Void

    var body: some View {
        HStack(spacing: 10) {
            if let user = post?.user {
                AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                    .frame(width: 32, height: 32)
                    .onTapGesture {
                        onProfile(user)
                    }
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(post?.galleryTitle ?? "")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .lineLimit(1)
                Text(post?.user?.handle.canonical ?? "")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: 220, alignment: .leading)
        }
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
        TimelinePagingContent(
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

private extension UiState where T == GalleryDetail {
    var galleryPost: UiTimelineV2.Post? {
        if case .success(let success) = onEnum(of: self) {
            return success.data.post
        }
        return nil
    }
}

private extension UiTimelineV2.Post {
    var galleryImages: [UiMediaImage] {
        images.compactMap { media in
            if case .image(let image) = onEnum(of: media) {
                return image
            }
            return nil
        }
    }

    var galleryTitle: String {
        contentWarning?.raw ?? ""
    }

    var bookmarkAction: ActionMenu.Item? {
        for action in actions {
            if case .item(let item) = onEnum(of: action), item.isBookmarkAction {
                return item
            }
        }
        return nil
    }

    var shareAction: ActionMenu.Item? {
        for action in actions {
            if case .item(let item) = onEnum(of: action), item.isShareAction {
                return item
            }
        }
        return nil
    }

    var countedActions: [ActionMenu.Item] {
        actions.compactMap { action in
            guard case .item(let item) = onEnum(of: action), item.count != nil else {
                return nil
            }
            return item
        }
    }

    func statusMediaRoute(media: UiMedia) -> DeeplinkRoute.MediaStatusMedia {
        let mediaIndex = images.firstIndex { $0.url == media.url } ?? 0
        let preview: String? = switch onEnum(of: media) {
        case .image(let image): image.previewUrl
        case .video(let video): video.thumbnailUrl
        case .gif(let gif): gif.previewUrl
        case .audio: nil
        }
        return DeeplinkRoute.MediaStatusMedia(
            statusKey: statusKey,
            accountType: accountType,
            index: Int32(mediaIndex),
            preview: preview
        )
    }
}

private extension ActionMenu.Item {
    var isBookmarkAction: Bool {
        guard let text, case .localized(let localized) = onEnum(of: text) else {
            return false
        }
        return localized.type == .bookmark || localized.type == .unbookmark
    }

    var isShareAction: Bool {
        guard let text, case .localized(let localized) = onEnum(of: text) else {
            return false
        }
        return localized.type == .share
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
            videoAutoplay: videoAutoplay,
            showLinkPreview: showLinkPreview,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: postActionStyle,
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
