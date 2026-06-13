import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct GalleryTimelinePagingView: NSViewControllerRepresentable {
    let data: PagingState<UiTimelineV2>

    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.openURL) private var openURL
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    func makeNSViewController(context: Context) -> GalleryTimelineController {
        let controller = GalleryTimelineController()
        apply(to: controller)
        return controller
    }

    func updateNSViewController(_ controller: GalleryTimelineController, context: Context) {
        apply(to: controller)
    }

    private func apply(to controller: GalleryTimelineController) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.appearance = StatusAppKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff
        )
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.update(data: data)
    }
}

final class GalleryTimelineController: NSViewController, NSCollectionViewDataSource, NSCollectionViewDelegateFlowLayout {
    private enum Item: Hashable {
        case empty
        case error(String)
        case placeholder(Int)
        case timeline(String, Int)
        case footerLoading
        case footerError(String)
        case footerEnd
    }

    private struct ScrollAnchor {
        let item: Item
        let distanceFromViewportTop: CGFloat
    }

    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?
    private var items: [Item] = []
    private var heightCache: [String: CGFloat] = [:]
    private var refreshControllerObject: AnyObject?
    private var isUserRefreshing = false
    private var pendingScrollAnchor: ScrollAnchor?
    private var isRestoringScrollAnchor = false
    private let scrollView = NSScrollView()
    private let collectionView = NSCollectionView()
    private let layout = AppKitWaterfallLayout()
    private let sizingPostTile = GalleryPostTileNSView()
    private let sizingFeedTile = GalleryFeedTileNSView()

    var refreshCallback: (() async -> Void)?
    var openURL: ((URL) -> Void)?
    var appearance = StatusAppKitAppearance(timeline: TimelineAppearance.companion.Default) {
        didSet {
            guard oldValue != appearance else { return }
            heightCache.removeAll(keepingCapacity: true)
            collectionView.collectionViewLayout?.invalidateLayout()
            updateBackgroundColors()
            reloadPreservingScrollAnchor()
        }
    }

    override func loadView() {
        view = FlippedView()
        view.wantsLayer = true
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        layout.columnCount = 2
        layout.minimumColumnSpacing = 8
        layout.minimumInteritemSpacing = 8
        layout.sectionInset = NSEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)

        collectionView.collectionViewLayout = layout
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.isSelectable = false
        collectionView.backgroundColors = [.clear]
        collectionView.register(GalleryTimelineCollectionItem.self, forItemWithIdentifier: GalleryTimelineCollectionItem.identifier)

        scrollView.documentView = collectionView
        scrollView.hasVerticalScroller = true
        scrollView.hasHorizontalScroller = false
        scrollView.autohidesScrollers = true
        scrollView.drawsBackground = false
        scrollView.automaticallyAdjustsContentInsets = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        setupRefreshController()
        updateBackgroundColors()
    }

    override func viewDidLayout() {
        super.viewDidLayout()
        updateColumnCount()
        collectionView.collectionViewLayout?.invalidateLayout()
        restorePendingScrollAnchorIfNeeded()
    }

    func update(data: PagingState<UiTimelineV2>) {
        currentData = data
        if case .success(let success) = onEnum(of: data) {
            currentSuccess = success
        } else {
            currentSuccess = nil
        }
        syncRefreshController(data: data)
        rebuildItems()
    }

    func numberOfSections(in collectionView: NSCollectionView) -> Int {
        1
    }

    func collectionView(_ collectionView: NSCollectionView, numberOfItemsInSection section: Int) -> Int {
        items.count
    }

    func collectionView(
        _ collectionView: NSCollectionView,
        itemForRepresentedObjectAt indexPath: IndexPath
    ) -> NSCollectionViewItem {
        let item = collectionView.makeItem(withIdentifier: GalleryTimelineCollectionItem.identifier, for: indexPath)
        guard let galleryItem = item as? GalleryTimelineCollectionItem else { return item }
        galleryItem.configure(
            content: content(at: indexPath.item),
            appearance: appearance,
            onOpenURL: openURL,
            retry: { [weak self] in self?.retryForCurrentState() }
        )
        return galleryItem
    }

    func collectionView(
        _ collectionView: NSCollectionView,
        willDisplay item: NSCollectionViewItem,
        forRepresentedObjectAt indexPath: IndexPath
    ) {
        guard items.indices.contains(indexPath.item) else { return }
        if case .timeline(_, let position) = items[indexPath.item] {
            _ = currentSuccess?.get(index: Int32(position))
        }
    }

    func collectionView(
        _ collectionView: NSCollectionView,
        layout collectionViewLayout: NSCollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> NSSize {
        guard items.indices.contains(indexPath.item) else {
            return CGSize(width: itemWidth(for: collectionView.bounds.width), height: 160)
        }
        let item = items[indexPath.item]
        let fullWidth = max(collectionView.bounds.width - layout.sectionInset.left - layout.sectionInset.right, 1)
        let width = itemUsesFullWidth(item) ? fullWidth : itemWidth(for: collectionView.bounds.width)
        switch item {
        case .empty, .error:
            return CGSize(width: fullWidth, height: 240)
        case .footerLoading, .footerError, .footerEnd:
            return CGSize(width: fullWidth, height: 60)
        case .placeholder:
            return CGSize(width: width, height: width + 40)
        case .timeline(let id, let index):
            let renderHash = currentSuccess?.peek(index: Int32(index))?.renderHash ?? 0
            let key = "g:\(id):\(renderHash):\(heightCacheWidthKey(for: width)):\(appearance.showMedia):\(appearance.avatarShapeID)"
            return CGSize(width: width, height: cachedHeight(key: key) {
                guard let data = currentSuccess?.peek(index: Int32(index)) else { return width + 40 }
                return estimatedHeight(for: data, width: width)
            })
        }
    }

    private func content(at index: Int) -> GalleryTimelineCollectionItem.Content {
        guard items.indices.contains(index) else { return .placeholder }
        switch items[index] {
        case .empty:
            return .empty
        case .error:
            if let currentData, case .error(let error) = onEnum(of: currentData) {
                return .error(error.error)
            }
            return .empty
        case .placeholder:
            return .placeholder
        case .timeline(_, let position):
            if let data = currentSuccess?.peek(index: Int32(position)) {
                return .timeline(data)
            }
            return .placeholder
        case .footerLoading:
            return .footerLoading
        case .footerError:
            if let success = currentSuccess,
               case .error(let error) = onEnum(of: success.appendState) {
                return .footerError(error.error)
            }
            return .footerLoading
        case .footerEnd:
            return .footerEnd
        }
    }

    private func rebuildItems() {
        guard let currentData else {
            items = []
            collectionView.reloadData()
            return
        }

        var nextItems: [Item] = []
        switch onEnum(of: currentData) {
        case .empty:
            nextItems.append(.empty)
        case .error(let error):
            nextItems.append(.error(error.error.message ?? "error"))
        case .loading:
            nextItems.append(contentsOf: (0..<8).map { .placeholder($0) })
        case .success(let success):
            let count = Int(success.itemCount)
            if count == 0 {
                nextItems.append(.empty)
            } else {
                nextItems.append(contentsOf: (0..<count).map { index in
                    let item = success.peek(index: Int32(index))
                    return .timeline(item?.itemKey ?? "\(index)", index)
                })
            }
            switch onEnum(of: success.appendState) {
            case .loading:
                nextItems.append(.footerLoading)
            case .error(let error):
                nextItems.append(.footerError(error.error.message ?? "append-error"))
            case .notLoading(let notLoading):
                if notLoading.endOfPaginationReached {
                    nextItems.append(.footerEnd)
                }
            }
        }

        let changed = nextItems != items
        let scrollAnchor = changed && allowsScrollAnchorRestoration ? captureScrollAnchor() : nil
        items = nextItems
        if changed {
            heightCache.removeAll(keepingCapacity: true)
        }
        collectionView.reloadData()
        restoreScrollAnchorIfNeeded(scrollAnchor)
    }

    private func retryForCurrentState() {
        guard let currentData else { return }
        switch onEnum(of: currentData) {
        case .error(let error):
            _ = error.onRetry()
        case .success(let success):
            success.retry()
        default:
            break
        }
    }

    private func itemUsesFullWidth(_ item: Item) -> Bool {
        switch item {
        case .empty, .error, .footerLoading, .footerError, .footerEnd:
            true
        case .placeholder, .timeline:
            false
        }
    }

    private func columnCount(for totalWidth: CGFloat) -> Int {
        guard totalWidth > 0 else { return 2 }
        let target: CGFloat = totalWidth >= 600 ? 240 : 160
        return max(Int((totalWidth / target).rounded(.down)), 2)
    }

    private func itemWidth(for totalWidth: CGFloat) -> CGFloat {
        let columns = columnCount(for: totalWidth)
        let inset = layout.sectionInset.left + layout.sectionInset.right
        let spacing = layout.minimumColumnSpacing * CGFloat(columns - 1)
        return floor(max(totalWidth - inset - spacing, 1) / CGFloat(columns))
    }

    private func updateColumnCount() {
        let columns = columnCount(for: collectionView.bounds.width)
        guard layout.columnCount != columns else { return }
        layout.columnCount = columns
        heightCache.removeAll(keepingCapacity: true)
    }

    private func cachedHeight(key: String, compute: () -> CGFloat) -> CGFloat {
        if let cached = heightCache[key] { return cached }
        let value = ceil(max(compute(), 1))
        heightCache[key] = value
        return value
    }

    private func heightCacheWidthKey(for width: CGFloat) -> Int {
        let scale = view.window?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2
        return Int((width * scale).rounded(.toNearestOrAwayFromZero))
    }

    private func updateBackgroundColors() {
        let color = NSColor.windowBackgroundColor
        view.layer?.backgroundColor = color.cgColor
        collectionView.backgroundColors = [color]
        scrollView.backgroundColor = color
    }

    private func estimatedHeight(for item: UiTimelineV2, width: CGFloat) -> CGFloat {
        switch onEnum(of: item) {
        case .post(let post):
            sizingPostTile.configure(post: post, appearance: appearance, loadsRemoteImages: false, onOpenURL: nil)
            return sizingPostTile.timelineHeight(for: width) ?? width + 40
        case .feed(let feed):
            sizingFeedTile.configure(feed: feed, appearance: appearance, loadsRemoteImages: false, onOpenURL: nil)
            return sizingFeedTile.timelineHeight(for: width) ?? width + 40
        default:
            return 180
        }
    }

    private func setupRefreshController() {
        guard #available(macOS 27.0, *) else { return }
        let refreshController = NSRefreshController()
        refreshController.target = self
        refreshController.action = #selector(handleRefresh)
        refreshControllerObject = refreshController
        scrollView.refreshController = refreshController
    }

    @objc private func handleRefresh() {
        isUserRefreshing = true
        Task { @MainActor in
            if let refreshCallback {
                await refreshCallback()
            }
            isUserRefreshing = false
            if let data = currentData, !pagingIsRefreshing(data) {
                endRefreshController()
            }
        }
    }

    private func pagingIsRefreshing(_ data: PagingState<UiTimelineV2>) -> Bool {
        switch onEnum(of: data) {
        case .loading:
            true
        case .success(let success):
            success.isRefreshing
        default:
            false
        }
    }

    private func syncRefreshController(data: PagingState<UiTimelineV2>) {
        if pagingIsRefreshing(data) {
            if !refreshControllerIsRefreshing, !isUserRefreshing {
                beginRefreshController()
            }
        } else if !isUserRefreshing, refreshControllerIsRefreshing {
            endRefreshController()
        }
    }

    private var refreshControllerIsRefreshing: Bool {
        guard #available(macOS 27.0, *),
              let refreshController = refreshControllerObject as? NSRefreshController else {
            return false
        }
        return refreshController.isRefreshing
    }

    private func beginRefreshController() {
        guard #available(macOS 27.0, *),
              let refreshController = refreshControllerObject as? NSRefreshController else {
            return
        }
        refreshController.beginRefreshing()
    }

    private func endRefreshController() {
        guard #available(macOS 27.0, *),
              let refreshController = refreshControllerObject as? NSRefreshController else {
            return
        }
        refreshController.endRefreshing()
    }

    private var effectiveViewportTop: CGFloat {
        scrollView.contentView.bounds.minY
    }

    private var allowsScrollAnchorRestoration: Bool {
        view.window != nil && !isRestoringScrollAnchor
    }

    private func captureScrollAnchor() -> ScrollAnchor? {
        guard allowsScrollAnchorRestoration,
              collectionView.bounds.height > 1 else {
            return nil
        }
        let viewportTop = effectiveViewportTop
        let viewportBottom = viewportTop + scrollView.contentView.bounds.height
        return collectionView.indexPathsForVisibleItems()
            .compactMap { indexPath -> (item: Item, frame: CGRect)? in
                guard items.indices.contains(indexPath.item) else { return nil }
                let item = items[indexPath.item]
                guard case .timeline = item else { return nil }
                let frame = collectionView.layoutAttributesForItem(at: indexPath)?.frame ?? .null
                guard !frame.isNull,
                      frame.maxY > viewportTop,
                      frame.minY < viewportBottom else {
                    return nil
                }
                return (item, frame)
            }
            .min { lhs, rhs in
                if abs(lhs.frame.minY - rhs.frame.minY) > 0.5 {
                    return lhs.frame.minY < rhs.frame.minY
                }
                return lhs.frame.minX < rhs.frame.minX
            }
            .map {
                ScrollAnchor(item: $0.item, distanceFromViewportTop: $0.frame.minY - viewportTop)
            }
    }

    @discardableResult
    private func restoreScrollAnchorIfNeeded(_ anchor: ScrollAnchor?) -> Bool {
        guard let anchor,
              allowsScrollAnchorRestoration,
              let index = items.firstIndex(of: anchor.item) else {
            return false
        }
        collectionView.layoutSubtreeIfNeeded()
        let indexPath = IndexPath(item: index, section: 0)
        guard let attributes = collectionView.layoutAttributesForItem(at: indexPath) else {
            pendingScrollAnchor = anchor
            return false
        }
        isRestoringScrollAnchor = true
        setContentOffsetY(attributes.frame.minY - anchor.distanceFromViewportTop)
        isRestoringScrollAnchor = false
        pendingScrollAnchor = nil
        return true
    }

    private func restorePendingScrollAnchorIfNeeded() {
        guard let pendingScrollAnchor else { return }
        _ = restoreScrollAnchorIfNeeded(pendingScrollAnchor)
    }

    private func reloadPreservingScrollAnchor() {
        let anchor = allowsScrollAnchorRestoration ? captureScrollAnchor() : nil
        collectionView.reloadData()
        restoreScrollAnchorIfNeeded(anchor)
    }

    private func setContentOffsetY(_ y: CGFloat) {
        let point = CGPoint(x: scrollView.contentView.bounds.minX, y: clampedContentOffsetY(y))
        scrollView.contentView.scroll(to: point)
        scrollView.reflectScrolledClipView(scrollView.contentView)
    }

    private func clampedContentOffsetY(_ offsetY: CGFloat) -> CGFloat {
        let contentHeight = collectionView.collectionViewLayout?.collectionViewContentSize.height ?? collectionView.bounds.height
        let viewportHeight = scrollView.contentView.bounds.height
        let maxY = max(0, contentHeight - viewportHeight)
        return min(max(offsetY, 0), maxY)
    }
}

final class GalleryTimelineCollectionItem: NSCollectionViewItem {
    static let identifier = NSUserInterfaceItemIdentifier("GalleryTimelineCollectionItem")

    enum Content {
        case empty
        case error(KotlinThrowable)
        case placeholder
        case timeline(UiTimelineV2)
        case footerLoading
        case footerError(KotlinThrowable)
        case footerEnd
    }

    private let tile = GalleryTimelineTileNSView()
    private let placeholder = GalleryPlaceholderTileNSView()
    private let empty = ListEmptyUIView()
    private let errorView = ListErrorUIView()
    private let loadingFooter = GalleryLoadingFooterNSView()
    private let endFooter = GalleryEndFooterNSView()
    private let footerErrorLabel = TimelineTextField(font: .preferredFont(forTextStyle: .footnote), color: .secondaryLabelColor, maximumLines: 2)

    override func loadView() {
        view = FlippedView()
        view.wantsLayer = true
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        tile.prepareForReuse()
    }

    func configure(
        content: Content,
        appearance: StatusAppKitAppearance,
        onOpenURL: ((URL) -> Void)?,
        retry: @escaping () -> Void
    ) {
        view.subviews.forEach { $0.removeFromSuperview() }

        let hosted: NSView
        switch content {
        case .empty:
            hosted = empty
        case .error(let error):
            errorView.onOpenURL = onOpenURL
            errorView.configure(error: error, onRetry: retry)
            hosted = errorView
        case .placeholder:
            hosted = placeholder
        case .timeline(let item):
            tile.configure(item: item, appearance: appearance, onOpenURL: onOpenURL)
            hosted = tile
        case .footerLoading:
            hosted = loadingFooter
        case .footerError(let error):
            footerErrorLabel.stringValue = error.message ?? LocalizedStrings.string("error_generic", fallback: "Something went wrong")
            footerErrorLabel.alignment = .center
            hosted = footerErrorLabel
        case .footerEnd:
            hosted = endFooter
        }

        view.addSubview(hosted)
        hosted.frame = view.bounds
        hosted.autoresizingMask = [.width, .height]
    }
}

private final class GalleryTimelineTileNSView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let postTile = GalleryPostTileNSView()
    private let feedTile = GalleryFeedTileNSView()
    private var installedTile: NSView?

    override func prepareForReuse() {
        super.prepareForReuse()
        postTile.prepareForReuse()
        feedTile.prepareForReuse()
        setInstalledTile(nil)
    }

    func configure(item: UiTimelineV2, appearance: StatusAppKitAppearance, onOpenURL: ((URL) -> Void)?) {
        switch onEnum(of: item) {
        case .post(let post):
            postTile.configure(post: post, appearance: appearance, loadsRemoteImages: true, onOpenURL: onOpenURL)
            setInstalledTile(postTile)
        case .feed(let feed):
            feedTile.configure(feed: feed, appearance: appearance, loadsRemoteImages: true, onOpenURL: onOpenURL)
            setInstalledTile(feedTile)
        default:
            setInstalledTile(nil)
        }
    }

    override func layout() {
        super.layout()
        installedTile?.frame = bounds
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        installedTile.map { childHeight(of: $0, for: width) } ?? 0
    }

    private func setInstalledTile(_ tile: NSView?) {
        guard installedTile !== tile else {
            needsLayout = true
            return
        }
        installedTile?.removeFromSuperview()
        installedTile = tile
        if let tile {
            addSubview(tile)
        }
        needsLayout = true
    }
}

private final class GalleryPostTileNSView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let imageView = RemoteImageView()
    private let playBadge = NSImageView()
    private let bodyText = RichTextUIView()
    private let avatar = AvatarUIView()
    private let userName = RichTextUIView()

    private var post: UiTimelineV2.Post?
    private var mediaPreviewURL: String?
    private var mediaAspectRatio: CGFloat?
    private var onOpenURL: ((URL) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.controlBackgroundColor.cgColor
        layer?.cornerRadius = 12
        layer?.masksToBounds = true

        playBadge.image = NSImage(systemSymbolName: "play.circle.fill", accessibilityDescription: nil)
        playBadge.symbolConfiguration = .init(pointSize: 28, weight: .semibold)
        playBadge.contentTintColor = .white

        [imageView, playBadge, bodyText, avatar, userName].forEach(addSubview)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleRootClick(_:))))
        imageView.addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleImageClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        post = nil
        mediaPreviewURL = nil
        mediaAspectRatio = nil
        imageView.configure(url: nil)
        bodyText.configure(rawText: "", font: .preferredFont(forTextStyle: .subheadline), maximumLines: 5)
        userName.configure(rawText: "", font: .preferredFont(forTextStyle: .caption1), maximumLines: 1)
        onOpenURL = nil
    }

    func configure(
        post: UiTimelineV2.Post,
        appearance: StatusAppKitAppearance,
        loadsRemoteImages: Bool,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.post = post
        self.onOpenURL = onOpenURL
        mediaPreviewURL = nil
        mediaAspectRatio = nil

        if appearance.showMedia,
           let media = post.images.first,
           let preview = media.galleryPreviewURL {
            mediaPreviewURL = preview
            mediaAspectRatio = media.galleryAspectRatio
            imageView.isHidden = false
            imageView.configure(url: loadsRemoteImages ? preview : nil)
            playBadge.isHidden = !media.galleryIsVideo
            bodyText.isHidden = true
        } else if !post.content.isEmpty {
            imageView.isHidden = true
            imageView.configure(url: nil)
            playBadge.isHidden = true
            bodyText.isHidden = false
            bodyText.configure(post.content, font: appearance.captionFont, maximumLines: 5)
        } else {
            imageView.isHidden = true
            imageView.configure(url: nil)
            playBadge.isHidden = true
            bodyText.isHidden = true
        }

        if let user = post.user {
            avatar.isHidden = false
            userName.isHidden = false
            if loadsRemoteImages {
                avatar.configure(profile: user, appearance: appearance)
            } else {
                avatar.configure(profile: nil, appearance: appearance)
            }
            userName.configure(user.name, font: appearance.captionFont, maximumLines: 1)
        } else {
            avatar.isHidden = true
            userName.isHidden = true
            avatar.configure(profile: nil, appearance: appearance)
            userName.configure(rawText: "", font: appearance.captionFont, maximumLines: 1)
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let width = bounds.width
        var y: CGFloat = 0

        if !imageView.isHidden {
            let ratio = max(mediaAspectRatio ?? 1, 0.3)
            let imageHeight = width / ratio
            imageView.frame = CGRect(x: 0, y: 0, width: width, height: imageHeight)
            playBadge.frame = CGRect(x: 12, y: max(imageHeight - 40, 0), width: 28, height: 28)
            y += imageHeight
        } else {
            imageView.frame = .zero
            playBadge.frame = .zero
        }

        if !bodyText.isHidden {
            let bodyWidth = max(width - 16, 0)
            let bodyHeight = childHeight(of: bodyText, for: bodyWidth)
            bodyText.frame = CGRect(x: 8, y: y + 8, width: bodyWidth, height: bodyHeight)
            y += bodyHeight + 8
        } else {
            bodyText.frame = .zero
        }

        if !userName.isHidden {
            avatar.frame = CGRect(x: 8, y: y + 6, width: 20, height: 20)
            let nameWidth = max(width - 42, 0)
            let nameHeight = childHeight(of: userName, for: nameWidth)
            userName.frame = CGRect(x: 34, y: y + 6, width: nameWidth, height: nameHeight)
        } else {
            avatar.frame = .zero
            userName.frame = .zero
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        var height: CGFloat = 0
        if !imageView.isHidden {
            let ratio = max(mediaAspectRatio ?? 1, 0.3)
            height += width / ratio
        }
        if !bodyText.isHidden {
            height += childHeight(of: bodyText, for: max(width - 16, 0)) + 8
        }
        if !userName.isHidden {
            height += 32
        }
        return ceil(max(height, 120))
    }

    @objc private func handleImageClick() {
        guard let post, let mediaPreviewURL else { return }
        let route = DeeplinkRoute.MediaStatusMedia(
            statusKey: post.statusKey,
            accountType: post.accountType,
            index: 0,
            preview: mediaPreviewURL
        )
        if let url = URL(string: route.toUri()) {
            onOpenURL?(url)
        }
    }

    @objc private func handleRootClick(_ recognizer: NSClickGestureRecognizer) {
        if !imageView.isHidden, imageView.frame.contains(recognizer.location(in: self)) {
            return
        }
        guard let post else { return }
        post.onClicked(ClickContext(launcher: makeGalleryLauncher(onOpenURL)))
    }
}

private final class GalleryFeedTileNSView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let imageView = RemoteImageView()
    private let title = TimelineTextField(font: .boldSystemFont(ofSize: 12), color: .labelColor, maximumLines: 2)
    private let body = TimelineTextField(font: .systemFont(ofSize: 12), color: .secondaryLabelColor, maximumLines: 3)
    private let sourceIcon = RemoteImageView()
    private let sourceName = TimelineTextField(font: .systemFont(ofSize: 12), color: .labelColor, maximumLines: 1)

    private var feed: UiTimelineV2.Feed?
    private var mediaAspectRatio: CGFloat?
    private var onOpenURL: ((URL) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.controlBackgroundColor.cgColor
        layer?.cornerRadius = 12
        layer?.masksToBounds = true
        [imageView, title, body, sourceIcon, sourceName].forEach(addSubview)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleRootClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        feed = nil
        mediaAspectRatio = nil
        onOpenURL = nil
        imageView.configure(url: nil)
        sourceIcon.configure(url: nil)
    }

    func configure(
        feed: UiTimelineV2.Feed,
        appearance: StatusAppKitAppearance,
        loadsRemoteImages: Bool,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.feed = feed
        self.onOpenURL = onOpenURL

        if appearance.showMedia, let media = feed.media {
            imageView.isHidden = false
            imageView.configure(url: loadsRemoteImages ? media.url : nil)
            mediaAspectRatio = max(CGFloat(media.aspectRatio), 0.3)
            title.isHidden = true
            body.isHidden = true
        } else {
            imageView.isHidden = true
            imageView.configure(url: nil)
            mediaAspectRatio = nil
            title.stringValue = feed.title ?? ""
            body.stringValue = feed.description_ ?? feed.description
            title.isHidden = title.stringValue.isEmpty
            body.isHidden = body.stringValue.isEmpty
        }

        if let icon = feed.source.icon, !icon.isEmpty {
            sourceIcon.isHidden = false
            sourceIcon.configure(url: loadsRemoteImages ? icon : nil, cornerRadius: 4)
        } else {
            sourceIcon.isHidden = true
            sourceIcon.configure(url: nil)
        }
        sourceName.stringValue = feed.source.name
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let width = bounds.width
        var y: CGFloat = 0

        if !imageView.isHidden {
            let ratio = max(mediaAspectRatio ?? 1, 0.3)
            let imageHeight = width / ratio
            imageView.frame = CGRect(x: 0, y: 0, width: width, height: imageHeight)
            y += imageHeight
        } else {
            imageView.frame = .zero
            let textWidth = max(width - 16, 0)
            if !title.isHidden {
                let height = childHeight(of: title, for: textWidth)
                title.frame = CGRect(x: 8, y: y + 8, width: textWidth, height: height)
                y += height + 4
            } else {
                title.frame = .zero
            }
            if !body.isHidden {
                let height = childHeight(of: body, for: textWidth)
                body.frame = CGRect(x: 8, y: y + 8, width: textWidth, height: height)
                y += height + 8
            } else {
                body.frame = .zero
            }
        }

        if !sourceIcon.isHidden {
            sourceIcon.frame = CGRect(x: 8, y: y + 6, width: 20, height: 20)
        } else {
            sourceIcon.frame = .zero
        }
        let nameX: CGFloat = sourceIcon.isHidden ? 8 : 34
        sourceName.frame = CGRect(x: nameX, y: y + 6, width: max(width - nameX - 8, 0), height: childHeight(of: sourceName, for: max(width - nameX - 8, 0)))
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        var height: CGFloat = 0
        if !imageView.isHidden {
            let ratio = max(mediaAspectRatio ?? 1, 0.3)
            height += width / ratio
        } else {
            let textWidth = max(width - 16, 0)
            if !title.isHidden {
                height += childHeight(of: title, for: textWidth) + 4
            }
            if !body.isHidden {
                height += childHeight(of: body, for: textWidth) + 8
            }
            if height > 0 {
                height += 8
            }
        }
        height += 32
        return ceil(max(height, 100))
    }

    @objc private func handleRootClick() {
        guard let feed else { return }
        feed.onClicked(ClickContext(launcher: makeGalleryLauncher(onOpenURL)))
    }
}

private final class GalleryPlaceholderTileNSView: FlippedView, TimelineHeightProviding {
    private let imageBlock = FlippedView()
    private let avatarBlock = FlippedView()
    private let labelBlock = FlippedView()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.controlBackgroundColor.cgColor
        layer?.cornerRadius = 12
        layer?.masksToBounds = true
        [imageBlock, avatarBlock, labelBlock].forEach { block in
            block.wantsLayer = true
            block.layer?.backgroundColor = NSColor.placeholderTextColor.withAlphaComponent(0.18).cgColor
            addSubview(block)
        }
        avatarBlock.layer?.cornerRadius = 10
        labelBlock.layer?.cornerRadius = 3
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        let width = bounds.width
        imageBlock.frame = CGRect(x: 0, y: 0, width: width, height: width)
        avatarBlock.frame = CGRect(x: 8, y: width + 6, width: 20, height: 20)
        labelBlock.frame = CGRect(x: 34, y: width + 11, width: min(72, max(width - 42, 0)), height: 10)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        width + 40
    }
}

private final class GalleryLoadingFooterNSView: FlippedView {
    private let indicator = NSProgressIndicator()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        indicator.style = .spinning
        indicator.controlSize = .regular
        indicator.startAnimation(nil)
        addSubview(indicator)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        indicator.frame = CGRect(x: (bounds.width - 20) / 2, y: (bounds.height - 20) / 2, width: 20, height: 20)
    }
}

private final class GalleryEndFooterNSView: FlippedView {
    private let label = TimelineTextField(font: .preferredFont(forTextStyle: .footnote), color: .secondaryLabelColor, maximumLines: 1)

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        label.alignment = .center
        label.stringValue = LocalizedStrings.string("end_of_list", fallback: "End")
        addSubview(label)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        label.frame = bounds
    }
}

private extension UiMedia {
    var galleryPreviewURL: String? {
        switch onEnum(of: self) {
        case .image(let image):
            image.previewUrl.isEmpty ? image.url : image.previewUrl
        case .video(let video):
            video.thumbnailUrl.isEmpty ? video.url : video.thumbnailUrl
        case .gif(let gif):
            gif.previewUrl.isEmpty ? gif.url : gif.previewUrl
        case .audio(let audio):
            audio.previewUrl
        }
    }

    var galleryAspectRatio: CGFloat {
        switch onEnum(of: self) {
        case .image(let image):
            CGFloat(image.aspectRatio)
        case .video(let video):
            CGFloat(video.aspectRatio)
        case .gif(let gif):
            CGFloat(gif.aspectRatio)
        case .audio:
            1
        }
    }

    var galleryIsVideo: Bool {
        if case .video = onEnum(of: self) {
            return true
        }
        return false
    }
}

private func makeGalleryLauncher(_ onOpenURL: ((URL) -> Void)?) -> AppleUriLauncher {
    AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { url in
        onOpenURL?(url)
        return .handled
    })
}
