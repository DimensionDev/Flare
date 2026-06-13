import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct CollectionViewTimelineView: NSViewControllerRepresentable {
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let topContentInset: CGFloat
    let columnCount: Int
    let accessoryItems: [CollectionViewTimelineAccessoryItem]
    let suppressInitialRefreshIndicator: Bool

    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.aiConfig) private var aiConfig
    @Environment(\.openURL) private var openURL
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        topContentInset: CGFloat = 0,
        columnCount: Int = 1,
        accessoryItems: [CollectionViewTimelineAccessoryItem] = [],
        suppressInitialRefreshIndicator: Bool = false
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.topContentInset = topContentInset
        self.columnCount = max(columnCount, 1)
        self.accessoryItems = accessoryItems
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
    }

    func makeNSViewController(context: Context) -> CollectionViewTimelineController {
        let controller = CollectionViewTimelineController(detailStatusKey: detailStatusKey)
        apply(to: controller)
        return controller
    }

    func updateNSViewController(_ controller: CollectionViewTimelineController, context: Context) {
        apply(to: controller)
    }

    private func apply(to controller: CollectionViewTimelineController) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.appearance = TimelineAppKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff
        )
        controller.aiTldrEnabled = aiConfig.tldr
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.columnCount = columnCount
        controller.accessoryItems = accessoryItems
        controller.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        controller.update(data: data)
    }
}

struct CollectionViewTimelineAccessoryItem {
    let id: String
    let view: NSView
    let onVisibilityChanged: ((Bool) -> Void)?

    init(id: String, view: NSView, onVisibilityChanged: ((Bool) -> Void)? = nil) {
        self.id = id
        self.view = view
        self.onVisibilityChanged = onVisibilityChanged
    }
}

final class AppKitWaterfallLayout: NSCollectionViewLayout {
    var columnCount: Int = 1 {
        didSet {
            columnCount = max(columnCount, 1)
            if oldValue != columnCount {
                invalidateLayout()
            }
        }
    }
    var minimumColumnSpacing: CGFloat = 0 {
        didSet { invalidateLayout() }
    }
    var minimumInteritemSpacing: CGFloat = 0 {
        didSet { invalidateLayout() }
    }
    var sectionInset = NSEdgeInsetsZero {
        didSet { invalidateLayout() }
    }

    private var cachedAttributes: [IndexPath: NSCollectionViewLayoutAttributes] = [:]
    private var orderedAttributes: [NSCollectionViewLayoutAttributes] = []
    private var contentSize: CGSize = .zero
    private var preparedWidth: CGFloat = -1

    override func prepare() {
        super.prepare()
        guard let collectionView else { return }
        let width = collectionView.bounds.width
        guard width > 0 else {
            cachedAttributes.removeAll()
            orderedAttributes.removeAll()
            contentSize = .zero
            preparedWidth = width
            return
        }
        guard preparedWidth != width || cachedAttributes.isEmpty else { return }

        cachedAttributes.removeAll(keepingCapacity: true)
        orderedAttributes.removeAll(keepingCapacity: true)
        preparedWidth = width

        let availableWidth = max(width - sectionInset.left - sectionInset.right, 1)
        let columns = max(columnCount, 1)
        let totalColumnSpacing = CGFloat(columns - 1) * minimumColumnSpacing
        let columnWidth = max((availableWidth - totalColumnSpacing) / CGFloat(columns), 1)
        var columnHeights = Array(repeating: sectionInset.top, count: columns)

        for section in 0..<collectionView.numberOfSections {
            for item in 0..<collectionView.numberOfItems(inSection: section) {
                let indexPath = IndexPath(item: item, section: section)
                let requestedSize = requestedItemSize(collectionView: collectionView, indexPath: indexPath)
                let itemHeight = max(ceil(requestedSize.height), 1)
                let isFullWidth = requestedSize.width >= availableWidth - 0.5 || columns == 1
                let frame: CGRect

                if isFullWidth {
                    let y = columnHeights.max() ?? sectionInset.top
                    frame = CGRect(
                        x: sectionInset.left,
                        y: y,
                        width: availableWidth,
                        height: itemHeight
                    )
                    let nextY = frame.maxY + minimumInteritemSpacing
                    for index in columnHeights.indices {
                        columnHeights[index] = nextY
                    }
                } else {
                    let column = columnHeights.enumerated().min(by: { $0.element < $1.element })?.offset ?? 0
                    let itemWidth = min(max(requestedSize.width, 1), columnWidth)
                    frame = CGRect(
                        x: sectionInset.left + CGFloat(column) * (columnWidth + minimumColumnSpacing),
                        y: columnHeights[column],
                        width: itemWidth,
                        height: itemHeight
                    )
                    columnHeights[column] = frame.maxY + minimumInteritemSpacing
                }

                let attributes = NSCollectionViewLayoutAttributes(forItemWith: indexPath)
                attributes.frame = frame.integral
                cachedAttributes[indexPath] = attributes
                orderedAttributes.append(attributes)
            }
        }

        let rawHeight = (columnHeights.max() ?? 0) - minimumInteritemSpacing + sectionInset.bottom
        contentSize = CGSize(width: width, height: max(ceil(rawHeight), 0))
    }

    override var collectionViewContentSize: NSSize {
        contentSize
    }

    override func layoutAttributesForElements(in rect: NSRect) -> [NSCollectionViewLayoutAttributes] {
        orderedAttributes.filter { $0.frame.intersects(rect) }
    }

    override func layoutAttributesForItem(at indexPath: IndexPath) -> NSCollectionViewLayoutAttributes? {
        cachedAttributes[indexPath]
    }

    override func shouldInvalidateLayout(forBoundsChange newBounds: NSRect) -> Bool {
        guard let collectionView else { return true }
        return abs(collectionView.bounds.width - newBounds.width) > 0.5
    }

    override func invalidateLayout() {
        super.invalidateLayout()
        preparedWidth = -1
        cachedAttributes.removeAll(keepingCapacity: true)
        orderedAttributes.removeAll(keepingCapacity: true)
    }

    private func requestedItemSize(collectionView: NSCollectionView, indexPath: IndexPath) -> CGSize {
        if let delegate = collectionView.delegate as? NSCollectionViewDelegateFlowLayout,
           let size = delegate.collectionView?(collectionView, layout: self, sizeForItemAt: indexPath) {
            return size
        }
        return CGSize(width: collectionView.bounds.width, height: 44)
    }
}

final class CollectionViewTimelineController: NSViewController, NSCollectionViewDataSource, NSCollectionViewDelegateFlowLayout {
    private enum Item: Hashable {
        case accessory(String)
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

    private let detailStatusKey: MicroBlogKey?
    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?
    private var items: [Item] = []
    private var heightCache: [String: CGFloat] = [:]
    private var refreshControllerObject: AnyObject?
    private var isUserRefreshing = false
    private var shouldRevealRefreshControl = false
    private var hasCompletedInitialRefreshCycle = false
    private var pendingScrollAnchor: ScrollAnchor?
    private var isRestoringScrollAnchor = false
    private let scrollView = NSScrollView()
    private let collectionView = NSCollectionView()
    private let layout = AppKitWaterfallLayout()
    private let sizingTimelineView = TimelineUIView()
    private let sizingPlaceholder = TimelinePlaceholderUIView()
    private let sizingCard = AdaptiveTimelineCardNSView()
    private let sizingPlaceholderCard = AdaptiveTimelineCardNSView()

    var refreshCallback: (() async -> Void)?
    var openURL: ((URL) -> Void)?
    var suppressInitialRefreshIndicator = false
    var topContentInset: CGFloat = 0 {
        didSet { updateInsets() }
    }
    var appearance = TimelineAppKitAppearance(timeline: TimelineAppearance.companion.Default) {
        didSet {
            guard oldValue != appearance else { return }
            heightCache.removeAll()
            updateInsets()
            updateBackgroundColors()
            reloadPreservingScrollAnchor()
        }
    }
    var aiTldrEnabled = false {
        didSet {
            guard oldValue != aiTldrEnabled else { return }
            heightCache.removeAll()
            reloadPreservingScrollAnchor()
        }
    }
    var columnCount = 1 {
        didSet {
            columnCount = max(columnCount, 1)
            guard oldValue != columnCount else { return }
            layout.columnCount = columnCount
            heightCache.removeAll()
            updateInsets()
            updateBackgroundColors()
            collectionView.collectionViewLayout?.invalidateLayout()
            reloadPreservingScrollAnchor()
        }
    }
    var accessoryItems: [CollectionViewTimelineAccessoryItem] = [] {
        didSet { rebuildItems() }
    }

    init(detailStatusKey: MicroBlogKey?) {
        self.detailStatusKey = detailStatusKey
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func loadView() {
        view = FlippedView()
        view.wantsLayer = true
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        layout.minimumColumnSpacing = 12
        layout.minimumInteritemSpacing = 12
        updateLayoutSectionInset()
        layout.columnCount = columnCount

        collectionView.collectionViewLayout = layout
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.isSelectable = false
        collectionView.backgroundColors = [.clear]
        collectionView.register(TimelineCollectionItem.self, forItemWithIdentifier: TimelineCollectionItem.identifier)

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
        updateInsets()
        updateBackgroundColors()
    }

    override func viewDidLayout() {
        super.viewDidLayout()
        collectionView.collectionViewLayout?.invalidateLayout()
        restorePendingScrollAnchorIfNeeded()
    }

    func update(data: PagingState<UiTimelineV2>) {
        let wasRefreshing = currentData.map(pagingIsRefreshing) ?? false
        let isRefreshing = pagingIsRefreshing(data)
        currentData = data
        if case .success(let success) = onEnum(of: data) {
            currentSuccess = success
        } else {
            currentSuccess = nil
        }
        syncRefreshController(data: data)
        rebuildItems()
        if wasRefreshing && !isRefreshing {
            collectionView.reloadData()
        }
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
        let item = collectionView.makeItem(withIdentifier: TimelineCollectionItem.identifier, for: indexPath)
        guard let timelineItem = item as? TimelineCollectionItem else { return item }
        timelineItem.configure(
            content: content(at: indexPath.item),
            appearance: appearance,
            isMultipleColumn: columnCount > 1,
            detailStatusKey: detailStatusKey,
            aiTldrEnabled: aiTldrEnabled,
            onOpenURL: openURL,
            onHeightInvalidated: { [weak self] in
                self?.handleLocalHeightInvalidated(at: indexPath)
            },
            retry: { [weak self] in self?.retryForCurrentState() }
        )
        return timelineItem
    }

    func collectionView(
        _ collectionView: NSCollectionView,
        willDisplay item: NSCollectionViewItem,
        forRepresentedObjectAt indexPath: IndexPath
    ) {
        guard items.indices.contains(indexPath.item) else { return }
        switch items[indexPath.item] {
        case .timeline(_, let position):
            _ = currentSuccess?.get(index: Int32(position))
        case .accessory(let id):
            accessoryItems.first { $0.id == id }?.onVisibilityChanged?(true)
        default:
            break
        }
    }

    func collectionView(
        _ collectionView: NSCollectionView,
        didEndDisplaying item: NSCollectionViewItem,
        forRepresentedObjectAt indexPath: IndexPath
    ) {
        guard items.indices.contains(indexPath.item) else { return }
        if case .accessory(let id) = items[indexPath.item] {
            accessoryItems.first { $0.id == id }?.onVisibilityChanged?(false)
        }
    }

    func collectionView(
        _ collectionView: NSCollectionView,
        layout collectionViewLayout: NSCollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> NSSize {
        let width = itemWidth(for: collectionView.bounds.width)
        guard items.indices.contains(indexPath.item) else {
            return CGSize(width: width, height: 1)
        }
        let item = items[indexPath.item]
        let fullWidth = max(collectionView.bounds.width - layout.sectionInset.left - layout.sectionInset.right, 1)
        switch item {
        case .empty, .error:
            return CGSize(width: fullWidth, height: max(collectionView.bounds.height - layout.sectionInset.top - layout.sectionInset.bottom, 220))
        case .accessory(let id):
            let accessory = accessoryItems.first { $0.id == id }?.view
            let height = accessory.map { max(childHeight(of: $0, for: fullWidth), 1) } ?? 1
            return CGSize(width: fullWidth, height: height)
        case .footerLoading, .footerError, .footerEnd:
            return CGSize(width: fullWidth, height: 56)
        case .placeholder(let index):
            let key = "p:\(index):\(heightCacheWidthKey(for: width)):\(appearance.timelineDisplayModeID):\(columnCount)"
            return CGSize(width: width, height: cachedHeight(key: key) {
                configureCard(sizingPlaceholderCard, index: index, totalCount: items.count, content: padded(sizingPlaceholder))
                return sizingPlaceholderCard.timelineHeight(for: width) ?? 152
            })
        case .timeline(let id, let index):
            let renderHash = currentSuccess?.peek(index: Int32(index))?.renderHash ?? 0
            let key = "t:\(id):\(renderHash):\(heightCacheWidthKey(for: width)):\(appearance.status):\(columnCount)"
            return CGSize(width: width, height: cachedHeight(key: key) {
                guard let data = currentSuccess?.peek(index: Int32(index)) else { return 152 }
                sizingTimelineView.configure(
                    data: data,
                    appearance: appearance.status,
                    detailStatusKey: detailStatusKey,
                    aiTldrEnabled: aiTldrEnabled,
                    onOpenURL: nil
                )
                configureCard(sizingCard, index: index, totalCount: timelineItemCount, content: padded(sizingTimelineView))
                return sizingCard.timelineHeight(for: width) ?? 152
            })
        }
    }

    private var timelineItemCount: Int {
        guard let currentSuccess else { return items.count }
        return Int(currentSuccess.itemCount)
    }

    private func content(at index: Int) -> TimelineCollectionItem.Content {
        guard items.indices.contains(index) else { return .loading(index: 0, totalCount: 1) }
        switch items[index] {
        case .empty:
            return .empty
        case .error:
            if let currentData, case .error(let error) = onEnum(of: currentData) {
                return .error(error.error)
            }
            return .empty
        case .placeholder(let index):
            return .loading(index: index, totalCount: items.count)
        case .timeline(_, let position):
            if let data = currentSuccess?.peek(index: Int32(position)) {
                return .timeline(data, index: position, totalCount: timelineItemCount)
            } else {
                return .loading(index: position, totalCount: timelineItemCount)
            }
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
        case .accessory(let id):
            return .accessory(accessoryItems.first { $0.id == id }?.view ?? NSView())
        }
    }

    private func rebuildItems() {
        var nextItems: [Item] = accessoryItems.map { .accessory($0.id) }
        guard let currentData else {
            items = nextItems
            collectionView.reloadData()
            return
        }

        switch onEnum(of: currentData) {
        case .empty:
            nextItems.append(.empty)
        case .error(let error):
            nextItems.append(.error(error.error.message ?? "error"))
        case .loading:
            nextItems.append(contentsOf: (0..<5).map { .placeholder($0) })
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

    private func handleLocalHeightInvalidated(at indexPath: IndexPath) {
        guard items.indices.contains(indexPath.item) else { return }
        heightCache.removeAll(keepingCapacity: true)
        collectionView.collectionViewLayout?.invalidateLayout()
        collectionView.needsLayout = true
        scrollView.needsLayout = true
    }

    private func updateInsets() {
        let wasPinnedToTop = scrollView.contentView.bounds.minY <= 1
        scrollView.contentInsets.top = topContentInset
        scrollView.scrollerInsets.top = topContentInset
        updateLayoutSectionInset()
        collectionView.collectionViewLayout?.invalidateLayout()
        if wasPinnedToTop {
            setContentOffsetY(0)
        }
    }

    private func itemWidth(for totalWidth: CGFloat) -> CGFloat {
        let inset = layout.sectionInset.left + layout.sectionInset.right
        let spacing = layout.minimumColumnSpacing * CGFloat(max(columnCount - 1, 0))
        return floor(max(totalWidth - inset - spacing, 1) / CGFloat(max(columnCount, 1)))
    }

    private func updateLayoutSectionInset() {
        let horizontalInset: CGFloat = columnCount > 1 ? 12 : (appearance.isPlainTimelineDisplayMode ? 0 : 16)
        let topInset: CGFloat = columnCount > 1 ? 12 : 0
        let bottomInset: CGFloat = columnCount > 1 ? 12 : 0
        layout.sectionInset = NSEdgeInsets(top: topInset, left: horizontalInset, bottom: bottomInset, right: horizontalInset)
    }

    private func updateBackgroundColors() {
        let color: NSColor = appearance.usesCardBackground || columnCount > 1 ? .windowBackgroundColor : .textBackgroundColor
        view.layer?.backgroundColor = color.cgColor
        collectionView.backgroundColors = [color]
        scrollView.backgroundColor = color
    }

    private func heightCacheWidthKey(for width: CGFloat) -> Int {
        let scale = view.window?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2
        return Int((width * scale).rounded(.toNearestOrAwayFromZero))
    }

    private func cachedHeight(key: String, compute: () -> CGFloat) -> CGFloat {
        if let cached = heightCache[key] { return cached }
        let value = ceil(max(compute(), 1))
        heightCache[key] = value
        return value
    }

    private func configureCard(_ card: AdaptiveTimelineCardNSView, index: Int, totalCount: Int, content: NSView) {
        card.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        card.isMultipleColumn = columnCount > 1
        card.configure(index: index, totalCount: totalCount)
        card.setContent(content)
    }

    private func padded(_ content: NSView) -> NSView {
        TimelinePaddingView(content: content, insets: NSEdgeInsets(top: 12, left: 16, bottom: 12, right: 16))
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

    func resetInitialRefreshIndicatorSuppression() {
        hasCompletedInitialRefreshCycle = false
        shouldRevealRefreshControl = false
        guard suppressInitialRefreshIndicator,
              refreshControllerIsRefreshing,
              !isUserRefreshing else {
            return
        }
        endRefreshController()
    }

    private func syncRefreshController(data: PagingState<UiTimelineV2>) {
        let isRefreshing = pagingIsRefreshing(data)
        if !isRefreshing {
            hasCompletedInitialRefreshCycle = true
        }

        let shouldSuppressInitialRefreshIndicator =
            suppressInitialRefreshIndicator &&
            !hasCompletedInitialRefreshCycle &&
            !isUserRefreshing

        if isRefreshing {
            guard !shouldSuppressInitialRefreshIndicator else {
                shouldRevealRefreshControl = false
                if refreshControllerIsRefreshing {
                    endRefreshController()
                }
                return
            }
            if !refreshControllerIsRefreshing {
                beginRefreshController()
                shouldRevealRefreshControl = !isUserRefreshing
            }
        } else if !isUserRefreshing {
            shouldRevealRefreshControl = false
            if refreshControllerIsRefreshing {
                endRefreshController()
            }
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

    var currentContentOffset: CGPoint {
        scrollView.contentView.bounds.origin
    }

    func setContentOffset(_ offset: CGPoint, animated: Bool) {
        setContentOffsetY(offset.y)
    }

    func restoreContentOffset(_ offset: CGPoint, animated: Bool) {
        collectionView.layoutSubtreeIfNeeded()
        setContentOffsetY(clampedContentOffsetY(offset.y))
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

final class TimelineCollectionItem: NSCollectionViewItem {
    static let identifier = NSUserInterfaceItemIdentifier("TimelineCollectionItem")

    enum Content {
        case empty
        case error(KotlinThrowable)
        case loading(index: Int, totalCount: Int)
        case timeline(UiTimelineV2, index: Int, totalCount: Int)
        case footerLoading
        case footerError(KotlinThrowable)
        case footerEnd
        case accessory(NSView)
    }

    private let card = AdaptiveTimelineCardNSView()
    private let timeline = TimelineUIView()
    private let placeholder = TimelinePlaceholderUIView()
    private let empty = ListEmptyUIView()
    private let errorView = ListErrorUIView()
    private let footerLabel = TimelineTextField(font: .preferredFont(forTextStyle: .body), color: .secondaryLabelColor)
    private var accessoryHost: NSView?

    override func loadView() {
        view = FlippedView()
        view.wantsLayer = true
    }

    func configure(
        content: Content,
        appearance: TimelineAppKitAppearance,
        isMultipleColumn: Bool,
        detailStatusKey: MicroBlogKey?,
        aiTldrEnabled: Bool,
        onOpenURL: ((URL) -> Void)?,
        onHeightInvalidated: @escaping () -> Void,
        retry: @escaping () -> Void
    ) {
        view.subviews.forEach { $0.removeFromSuperview() }
        accessoryHost = nil

        switch content {
        case .empty:
            view.addSubview(empty)
            empty.frame = view.bounds
            empty.autoresizingMask = [.width, .height]
        case .error(let error):
            errorView.onOpenURL = onOpenURL
            errorView.configure(error: error, onRetry: retry)
            view.addSubview(errorView)
            errorView.frame = view.bounds
            errorView.autoresizingMask = [.width, .height]
        case .footerLoading:
            footerLabel.stringValue = LocalizedStrings.string("macos_loading", fallback: "Loading")
            view.addSubview(footerLabel)
            footerLabel.frame = view.bounds
            footerLabel.alignment = .center
            footerLabel.autoresizingMask = [.width, .height]
        case .footerError(let error):
            footerLabel.stringValue = error.message ?? LocalizedStrings.string("error_generic", fallback: "Something went wrong")
            view.addSubview(footerLabel)
            footerLabel.frame = view.bounds
            footerLabel.alignment = .center
            footerLabel.autoresizingMask = [.width, .height]
        case .footerEnd:
            footerLabel.stringValue = LocalizedStrings.string("end_of_list", fallback: "End")
            view.addSubview(footerLabel)
            footerLabel.frame = view.bounds
            footerLabel.alignment = .center
            footerLabel.autoresizingMask = [.width, .height]
        case .accessory(let accessory):
            accessoryHost = accessory
            view.addSubview(accessory)
            accessory.frame = view.bounds
            accessory.autoresizingMask = [.width, .height]
        case .loading(let index, let totalCount):
            configureCard(appearance: appearance, isMultipleColumn: isMultipleColumn, index: index, totalCount: totalCount)
            card.setContent(TimelinePaddingView(content: placeholder, insets: NSEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
            view.addSubview(card)
            card.frame = view.bounds
            card.autoresizingMask = [.width, .height]
        case .timeline(let data, let index, let totalCount):
            timeline.onLocalHeightInvalidated = onHeightInvalidated
            timeline.configure(
                data: data,
                appearance: appearance.status,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                onOpenURL: onOpenURL
            )
            configureCard(appearance: appearance, isMultipleColumn: isMultipleColumn, index: index, totalCount: totalCount)
            card.setContent(TimelinePaddingView(content: timeline, insets: NSEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
            view.addSubview(card)
            card.frame = view.bounds
            card.autoresizingMask = [.width, .height]
        }
    }

    private func configureCard(appearance: TimelineAppKitAppearance, isMultipleColumn: Bool, index: Int, totalCount: Int) {
        card.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        card.isMultipleColumn = isMultipleColumn
        card.configure(index: index, totalCount: totalCount)
    }
}

private final class TimelinePaddingView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let content: NSView
    private let insets: NSEdgeInsets

    init(content: NSView, insets: NSEdgeInsets) {
        self.content = content
        self.insets = insets
        super.init(frame: .zero)
        addSubview(content)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        content.frame = CGRect(
            x: insets.left,
            y: insets.top,
            width: max(bounds.width - insets.left - insets.right, 0),
            height: max(bounds.height - insets.top - insets.bottom, 0)
        )
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        childHeight(of: content, for: max(width - insets.left - insets.right, 0)) + insets.top + insets.bottom
    }
}
