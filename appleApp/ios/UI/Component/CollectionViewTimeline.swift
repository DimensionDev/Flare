import SwiftUI
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import CHTCollectionViewWaterfallLayout
import GSPlayer
import AVFoundation

enum TimelineUIKitLayoutMetrics {
    static let horizontalInset: CGFloat = 16
    static let columnSpacing: CGFloat = 8
    static let rowSpacing: CGFloat = 2
    static let timelinePlaceholderCount = 5
}

// MARK: - SwiftUI Wrapper

struct UITimelineCollectionView: UIViewControllerRepresentable {
    private let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let topContentInset: CGFloat
    let columnCount: Int
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    let suppressInitialRefreshIndicator: Bool
    let onIsAtTopChanged: (Bool) -> Void
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.aiConfig) private var aiConfig
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.networkKind) private var networkKind
    @Environment(\.openURL) private var openURL
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        topContentInset: CGFloat = 0,
        columnCount: Int = 1,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = [],
        suppressInitialRefreshIndicator: Bool = false,
        onIsAtTopChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.topContentInset = topContentInset
        self.columnCount = max(columnCount, 1)
        self.accessoryItems = accessoryItems
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        self.onIsAtTopChanged = onIsAtTopChanged
    }

    func makeUIViewController(context: Context) -> UITimelineCollectionViewController {
        let controller = UITimelineCollectionViewController(detailStatusKey: detailStatusKey)
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.onIsAtTopChanged = onIsAtTopChanged
        controller.topContentInset = topContentInset
        controller.topScrollIndicatorInset = topContentInset
        controller.appearance = TimelineUIKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff,
            showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
        )
        controller.aiTldrEnabled = aiConfig.tldr
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.networkKind = networkKind
        controller.accessoryItems = accessoryItems
        controller.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        controller.update(data: data, columnCount: columnCount)
        return controller
    }

    func updateUIViewController(_ controller: UITimelineCollectionViewController, context: Context) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.onIsAtTopChanged = onIsAtTopChanged
        controller.topContentInset = topContentInset
        controller.topScrollIndicatorInset = topContentInset
        controller.appearance = TimelineUIKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff,
            showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
        )
        controller.aiTldrEnabled = aiConfig.tldr
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.networkKind = networkKind
        controller.accessoryItems = accessoryItems
        controller.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        controller.update(data: data, columnCount: columnCount)
    }
}

struct UITimelineCollectionViewAccessoryItem {
    let id: String
    let view: UIView
    let onVisibilityChanged: ((Bool) -> Void)?

    init(id: String, view: UIView, onVisibilityChanged: ((Bool) -> Void)? = nil) {
        self.id = id
        self.view = view
        self.onVisibilityChanged = onVisibilityChanged
    }
}

// MARK: - Controller

final class UITimelineCollectionViewController: UIViewController, UICollectionViewDelegate, UIScrollViewDelegate, CHTCollectionViewDelegateWaterfallLayout {

    // Use Int for section and String for item to avoid Sendable issues
    private static let sectionAccessories = 0
    private static let sectionMain = 1
    private static let sectionFooter = 2

    private enum ContentKind: Equatable {
        case timeline
        case profileMedia
    }

    private let detailStatusKey: MicroBlogKey?
    private var contentKind = ContentKind.timeline
    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?
    private var currentProfileMediaData: PagingState<ProfileMedia>?
    private var currentProfileMediaSuccess: PagingStateSuccess<ProfileMedia>?

    var refreshCallback: (() async -> Void)?
    var onIsAtTopChanged: ((Bool) -> Void)?
    var onContentOffsetChanged: ((CGFloat) -> Void)?
    var onScrollInteractionBegan: (() -> Void)?
    var openURL: ((URL) -> Void)?
    var suppressInitialRefreshIndicator = false
    var usesGroupedBackgroundOverride: Bool? {
        didSet {
            guard oldValue != usesGroupedBackgroundOverride, isViewLoaded else { return }
            updateBackgroundColors()
        }
    }
    var appearance = TimelineUIKitAppearance(timeline: TimelineAppearance.companion.Default) {
        didSet {
            guard isViewLoaded else { return }
            guard oldValue != appearance else {
                return
            }
            clearAllHeightCache()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
            handleAutoplayAvailabilityChanged()
            updateBackgroundColors()
        }
    }
    var aiTldrEnabled = false {
        didSet {
            guard oldValue != aiTldrEnabled, isViewLoaded else { return }
            clearAllHeightCache()
            reconfigureVisibleCells()
        }
    }
    var networkKind: NetworkKind = .cellular {
        didSet {
            guard oldValue != networkKind, isViewLoaded else { return }
            handleAutoplayAvailabilityChanged()
        }
    }
    var topContentInset: CGFloat = 0 {
        didSet {
            guard isViewLoaded else { return }
            updateContentInsets()
        }
    }
    var topScrollIndicatorInset: CGFloat = 0 {
        didSet {
            guard oldValue != topScrollIndicatorInset, isViewLoaded else { return }
            updateContentInsets()
        }
    }
    var minimumVerticalScrollDistance: CGFloat = 0 {
        didSet {
            guard isViewLoaded else { return }
            updateContentInsets()
        }
    }
    var extendsContentUnderTopBars: Bool = false {
        didSet {
            guard oldValue != extendsContentUnderTopBars, isViewLoaded else { return }
            updateContentInsets()
        }
    }
    var columnCount: Int = 1 {
        didSet {
            let clamped = max(columnCount, 1)
            if clamped != columnCount {
                columnCount = clamped
                return
            }
            guard oldValue != columnCount, isViewLoaded else { return }
            guard !isApplyingContentTransition else { return }
            clearAllHeightCache()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
            updateBackgroundColors()
        }
    }
    var accessoryItems: [UITimelineCollectionViewAccessoryItem] = [] {
        didSet {
            let oldIDs = oldValue.map { "\(Self.accessoryPrefix)\($0.id)" }
            let newIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
            accessoryItemMap = Dictionary(
                uniqueKeysWithValues: zip(newIDs, accessoryItems)
            )
            guard isViewLoaded else { return }
            if oldIDs.isEmpty != newIDs.isEmpty {
                collectionView.collectionViewLayout.invalidateLayout()
            }
            if oldIDs == newIDs {
                reconfigureItems(newIDs)
                return
            }
            lastAppliedSignature = nil
            applyCurrentSnapshot()
        }
    }

    var effectiveContentOffsetY: CGFloat {
        guard isViewLoaded else { return 0 }
        return collectionView.contentOffset.y + collectionView.adjustedContentInset.top
    }

    var maximumEffectiveContentOffsetY: CGFloat {
        guard isViewLoaded else { return 0 }
        let minimumOffsetY = -collectionView.adjustedContentInset.top
        let maximumOffsetY = max(
            minimumOffsetY,
            collectionView.contentSize.height - collectionView.bounds.height + collectionView.adjustedContentInset.bottom
        )
        return maximumOffsetY + collectionView.adjustedContentInset.top
    }

    var scrollDecelerationRate: UIScrollView.DecelerationRate {
        guard isViewLoaded else { return .normal }
        return collectionView.decelerationRate
    }

    func beginExternalScrollInteraction() {
        beginScrollInteraction()
    }

    func endExternalScrollInteraction() {
        endScrollInteraction()
    }

    func restoreEffectiveContentOffsetAfterNextSnapshot(_ offsetY: CGFloat) {
        guard isViewLoaded else { return }
        pendingEffectiveContentOffsetYAfterSnapshot = offsetY
    }

    func restoreContentOffset(_ offset: CGPoint, animated: Bool) {
        guard isViewLoaded else { return }
        view.layoutIfNeeded()
        collectionView.layoutIfNeeded()
        collectionView.setContentOffset(
            CGPoint(x: offset.x, y: clampedContentOffsetY(offset.y)),
            animated: animated
        )
    }

    func restoreEffectiveContentOffset(_ offsetY: CGFloat, animated: Bool) {
        guard isViewLoaded else { return }
        restoreContentOffset(
            CGPoint(
                x: collectionView.contentOffset.x,
                y: offsetY - collectionView.adjustedContentInset.top
            ),
            animated: animated
        )
    }

    func setEffectiveContentOffset(_ offsetY: CGFloat, animated: Bool) {
        guard isViewLoaded else { return }
        collectionView.setContentOffset(
            CGPoint(
                x: collectionView.contentOffset.x,
                y: clampedContentOffsetY(offsetY - collectionView.adjustedContentInset.top)
            ),
            animated: animated
        )
    }

    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var refreshControl = UIRefreshControl()
    private var isUserRefreshing = false
    private var shouldRevealRefreshControl = false
    private var hasCompletedInitialRefreshCycle = false
    private var scrollingState = IsScrollingState()
    private var lastReportedIsAtTop: Bool?
    private var lastAppliedSignature: SnapshotSignature?
    private var lastRenderHashMap: [String: Int32] = [:]
    private var lastLoadedItemIDs: Set<String> = []
    private let autoplayPlayerView = VideoPlayerView()
    private var autoplayPlayerObservation: NSKeyValueObservation?
    private var autoplaySelectionTask: Task<Void, Never>?
    private var autoplayCountdownTask: Task<Void, Never>?
    private var postRefreshPoolCleanupTask: Task<Void, Never>?
    private var deferredPoolCleanupTask: Task<Void, Never>?
    private let deferredPoolCleanupCells = NSHashTable<TimelineUIKitCollectionViewCell>.weakObjects()
    private weak var currentAutoplayHostView: UIView?
    private var currentAutoplayID: String?
    private var accessoryItemMap: [String: UITimelineCollectionViewAccessoryItem] = [:]
    private var pendingScrollAnchor: ScrollAnchor?
    private var pendingEffectiveContentOffsetYAfterSnapshot: CGFloat?
    private var isRestoringScrollAnchor = false
    private var isApplyingContentTransition = false
    private var snapshotPreparationGeneration = 0
    private var heightCachePruneGeneration = 0

    // Maps item identifier → paging index.
    private var itemIndexMap: [String: Int] = [:]

    private struct SnapshotSignature: Equatable, Sendable {
        let accessoryIDs: [String]
        let itemIDs: [String]
        let footerIDs: [String]
    }

    private struct SnapshotPlan: Sendable {
        let signature: SnapshotSignature
        let accessoryIDs: [String]
        let itemIDs: [String]
        let footerIDs: [String]
        let indexMap: [String: Int]
        let renderHashMap: [String: Int32]
        let loadedItemIDs: Set<String>
        let isRefreshing: Bool
        let isInitialLoading: Bool
    }

    private struct ScrollAnchor {
        let itemID: String
        let distanceFromViewportTop: CGFloat
    }

    // Item ID prefixes / constants
    private static let timelinePrefix = "t:"
    private static let placeholderPrefix = "p:"
    private static let profileMediaPrefix = "m:"
    private static let profileMediaPlaceholderPrefix = "mp:"
    private static let accessoryPrefix = "a:"
    private static let emptyID = "__empty__"
    private static let errorID = "__error__"
    private static let footerLoadingID = "__fl__"
    private static let footerErrorID = "__fe__"
    private static let footerEndID = "__fend__"

    private static func itemIdentityKey(for item: UiTimelineV2) -> String {
        if let itemKey = item.itemKey, !itemKey.isEmpty {
            return itemKey
        }
        return [
            item.itemType,
            String(describing: item.accountType),
            String(describing: item.statusKey),
        ].joined(separator: ":")
    }

    init(detailStatusKey: MicroBlogKey?) {
        self.detailStatusKey = detailStatusKey
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCollectionView()
        setupDataSource()
        setupRefreshControl()
        setupVideoAutoplay()
        updateContentInsets()
        updateBackgroundColors()
        updateProfileMediaColumnCount()
        syncRefreshControl(isRefreshing: currentPagingIsRefreshing)
        applyCurrentSnapshot()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        updateProfileMediaColumnCount()
        updateContentInsets()
        reportIsAtTop()
        if shouldRevealRefreshControl {
            revealRefreshControlIfNeeded()
        }
        scheduleAutoplaySelection()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        updateContentInsets()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
        detachAutoplayPlayer(pause: true)
        accessoryItems.forEach { $0.onVisibilityChanged?(false) }
    }

    deinit {
        autoplaySelectionTask?.cancel()
        autoplayCountdownTask?.cancel()
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
        autoplayPlayerObservation?.invalidate()
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Setup

    private func setupCollectionView() {
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: makeSingleColumnLayout())
        collectionView.delegate = self
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        applyLayoutForColumnCount()
    }

    private func makeSingleColumnLayout() -> UICollectionViewLayout {
        return UICollectionViewCompositionalLayout { sectionIndex, _ in
            let isAccessorySection = !self.accessoryItems.isEmpty && sectionIndex == 0
            let horizontalInset = isAccessorySection || self.appearance.isPlainTimelineDisplayMode
                ? 0
                : TimelineUIKitLayoutMetrics.horizontalInset
            let itemSize = NSCollectionLayoutSize(
                widthDimension: .fractionalWidth(1),
                heightDimension: .estimated(180)
            )
            let item = NSCollectionLayoutItem(layoutSize: itemSize)
            let group = NSCollectionLayoutGroup.vertical(layoutSize: itemSize, subitems: [item])
            let section = NSCollectionLayoutSection(group: group)
            section.interGroupSpacing = TimelineUIKitLayoutMetrics.rowSpacing
            section.contentInsets = NSDirectionalEdgeInsets(
                top: 0,
                leading: horizontalInset,
                bottom: 0,
                trailing: horizontalInset
            )
            return section
        }
    }

    private func makeWaterfallLayout(columns: Int) -> UICollectionViewLayout {
        let layout = CHTCollectionViewWaterfallLayout()
        layout.columnCount = columns
        layout.minimumColumnSpacing = TimelineUIKitLayoutMetrics.columnSpacing
        layout.minimumInteritemSpacing = 0
        layout.sectionInset = UIEdgeInsets(
            top: 0,
            left: TimelineUIKitLayoutMetrics.horizontalInset,
            bottom: 0,
            right: TimelineUIKitLayoutMetrics.horizontalInset
        )
        layout.itemRenderDirection = .shortestFirst
        return layout
    }

    private func updateProfileMediaColumnCount() {
        guard contentKind == .profileMedia else { return }
        let columns = resolvedProfileMediaColumnCount()
        if columnCount != columns {
            columnCount = columns
        }
    }

    private func resolvedProfileMediaColumnCount() -> Int {
        let width = collectionView?.bounds.width ?? viewIfLoaded?.bounds.width ?? 0
        let targetWidth: CGFloat = traitCollection.horizontalSizeClass == .regular ? 240 : 120
        let horizontalInsets = TimelineUIKitLayoutMetrics.horizontalInset * 2
        let availableWidth = max(width - horizontalInsets, 0)
        return width > 0
            ? max(
                Int(
                    (availableWidth + TimelineUIKitLayoutMetrics.columnSpacing) /
                        (targetWidth + TimelineUIKitLayoutMetrics.columnSpacing)
                ),
                2
            )
            : 2
    }

    // MARK: - Sizing (for waterfall)

    private lazy var sizingTimelineView = TimelineUIView()
    private lazy var sizingTimelineCard: AdaptiveTimelineCardUIView = {
        let card = AdaptiveTimelineCardUIView()
        card.isMultipleColumn = true
        card.setContent(UIView.padding(sizingTimelineView, insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)))
        return card
    }()
    private lazy var sizingPlaceholderCard: AdaptiveTimelineCardUIView = {
        let card = makeTimelinePlaceholderCardUIView()
        card.isMultipleColumn = true
        return card
    }()
    private var heightCache: [String: CGFloat] = [:]
    private var heightCacheKeysByItemID: [String: Set<String>] = [:]
    private var pendingHeightCorrections: [String: CGFloat] = [:]
    private var isHeightCorrectionFlushScheduled = false

    private func clearAllHeightCache() {
        heightCache.removeAll(keepingCapacity: true)
        heightCacheKeysByItemID.removeAll(keepingCapacity: true)
        pendingHeightCorrections.removeAll(keepingCapacity: true)
    }

    private func heightCacheWidthKey(for width: CGFloat) -> Int {
        Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
    }

    private func timelineHeightCacheKey(itemID: String, renderHash: Int32, width: CGFloat) -> String {
        "\(itemID):\(renderHash):\(heightCacheWidthKey(for: width))"
    }

    private func measuredCompressedCardHeight(
        _ card: UIView,
        width: CGFloat,
        heightPadding: CGFloat = 1
    ) -> CGFloat {
        card.bounds = CGRect(x: 0, y: 0, width: width, height: UIView.layoutFittingCompressedSize.height)
        card.setNeedsLayout()
        let size = card.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return size.height + heightPadding
    }

    private func sectionIdentifier(at index: Int) -> Int? {
        guard dataSource != nil else { return nil }
        let identifiers = dataSource.snapshot().sectionIdentifiers
        guard identifiers.indices.contains(index) else { return nil }
        return identifiers[index]
    }

    private func isFullWidthSection(at index: Int) -> Bool {
        guard let identifier = sectionIdentifier(at: index) else { return false }
        return identifier == Self.sectionAccessories ||
            identifier == Self.sectionFooter ||
            (identifier == Self.sectionMain && mainSectionUsesFullWidth)
    }

    private var mainSectionUsesFullWidth: Bool {
        switch contentKind {
        case .timeline:
            guard let currentData else { return false }
            switch onEnum(of: currentData) {
            case .empty, .error: return true
            default: return false
            }
        case .profileMedia:
            guard let currentProfileMediaData else { return false }
            switch onEnum(of: currentProfileMediaData) {
            case .empty, .error: return true
            default: return false
            }
        }
    }

    private func waterfallInsets(for section: Int, layout: CHTCollectionViewWaterfallLayout) -> UIEdgeInsets {
        guard contentKind == .profileMedia else { return layout.sectionInset }
        switch sectionIdentifier(at: section) {
        case Self.sectionAccessories:
            return .zero
        case Self.sectionMain:
            return UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)
        default:
            return UIEdgeInsets(top: 0, left: 16, bottom: 0, right: 16)
        }
    }

    private func pruneHeightCache(keepingItemIDs: Set<String>) {
        let existing = Set(heightCacheKeysByItemID.keys)
        let removed = existing.subtracting(keepingItemIDs)
        guard !removed.isEmpty else { return }
        for itemID in removed {
            guard let keys = heightCacheKeysByItemID.removeValue(forKey: itemID) else { continue }
            for key in keys {
                heightCache.removeValue(forKey: key)
            }
        }
    }

    private func scheduleHeightCachePrune(keepingItemIDs: Set<String>) {
        let existingItemIDs = Array(heightCacheKeysByItemID.keys)
        guard existingItemIDs.count > keepingItemIDs.count else { return }

        heightCachePruneGeneration += 1
        let generation = heightCachePruneGeneration
        DispatchQueue.global(qos: .utility).async { [existingItemIDs, keepingItemIDs] in
            let removed = existingItemIDs.filter { !keepingItemIDs.contains($0) }
            guard !removed.isEmpty else { return }
            DispatchQueue.main.async { [weak self] in
                guard let self, self.heightCachePruneGeneration == generation else { return }
                for itemID in removed where !keepingItemIDs.contains(itemID) {
                    guard let keys = self.heightCacheKeysByItemID.removeValue(forKey: itemID) else { continue }
                    for key in keys {
                        self.heightCache.removeValue(forKey: key)
                    }
                }
            }
        }
    }

    private func applyMeasuredHeightCorrection(
        itemID: String,
        renderHash: Int32,
        width: CGFloat,
        height: CGFloat
    ) {
        guard width > 1, height.isFinite else { return }
        let key = timelineHeightCacheKey(itemID: itemID, renderHash: renderHash, width: width)
        let correctedHeight = max(ceil(height), 1)
        if let cachedHeight = heightCache[key],
           abs(cachedHeight - correctedHeight) <= 1 {
            return
        }

        heightCache[key] = correctedHeight
        heightCacheKeysByItemID[itemID, default: []].insert(key)
        guard columnCount > 1 else { return }
        pendingHeightCorrections[key] = correctedHeight
        scheduleHeightCorrectionFlush()
    }

    private func scheduleHeightCorrectionFlush() {
        guard !isHeightCorrectionFlushScheduled else { return }
        isHeightCorrectionFlushScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.flushPendingHeightCorrections()
        }
    }

    private func flushPendingHeightCorrections() {
        isHeightCorrectionFlushScheduled = false
        guard isViewLoaded, !pendingHeightCorrections.isEmpty else {
            pendingHeightCorrections.removeAll(keepingCapacity: true)
            return
        }

        pendingHeightCorrections.removeAll(keepingCapacity: true)
        collectionView.collectionViewLayout.invalidateLayout()
        collectionView.performBatchUpdates(nil)
    }

    private func applyLayoutForColumnCount() {
        guard collectionView != nil else { return }
        let newLayout: UICollectionViewLayout = columnCount > 1
            ? makeWaterfallLayout(columns: columnCount)
            : makeSingleColumnLayout()
        collectionView.setCollectionViewLayout(newLayout, animated: false)
        collectionView.collectionViewLayout.invalidateLayout()
    }

    private func setupDataSource() {
        let timelineCellReg = UICollectionView.CellRegistration<TimelineUIKitCollectionViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            guard let index = self.itemIndexMap[itemID] else { return }
            self.configureTimelineCell(cell, itemID: itemID, index: index)
        }
        let placeholderCellReg = UICollectionView.CellRegistration<TimelinePlaceholderCollectionViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            let indexStr = itemID.dropFirst(Self.placeholderPrefix.count)
            let index = Int(indexStr) ?? 0
            self.configurePlaceholderCell(cell, index: index)
        }
        let profileMediaCellReg = UICollectionView.CellRegistration<ProfileMediaCollectionViewCell, String> {
            [weak self] cell, _, itemID in
            self?.configureProfileMediaCell(cell, itemID: itemID)
        }
        let hostedCellReg = UICollectionView.CellRegistration<TimelineHostedViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            self.configureHostedCell(cell, itemID: itemID)
        }

        dataSource = UICollectionViewDiffableDataSource<Int, String>(
            collectionView: collectionView
        ) { (collectionView: UICollectionView, indexPath: IndexPath, itemID: String) -> UICollectionViewCell? in
            if itemID.hasPrefix(Self.timelinePrefix) {
                return collectionView.dequeueConfiguredReusableCell(using: timelineCellReg, for: indexPath, item: itemID)
            }
            if itemID.hasPrefix(Self.placeholderPrefix) {
                return collectionView.dequeueConfiguredReusableCell(using: placeholderCellReg, for: indexPath, item: itemID)
            }
            if itemID.hasPrefix(Self.profileMediaPrefix) || itemID.hasPrefix(Self.profileMediaPlaceholderPrefix) {
                return collectionView.dequeueConfiguredReusableCell(using: profileMediaCellReg, for: indexPath, item: itemID)
            }
            return collectionView.dequeueConfiguredReusableCell(using: hostedCellReg, for: indexPath, item: itemID)
        }
    }

    private func setupRefreshControl() {
        refreshControl.addTarget(self, action: #selector(handleRefresh), for: .valueChanged)
        collectionView.refreshControl = refreshControl
    }

    private func setupVideoAutoplay() {
        autoplayPlayerView.isMuted = true
        autoplayPlayerView.isAutoReplay = true
        autoplayPlayerView.contentMode = .scaleAspectFill
        autoplayPlayerView.isUserInteractionEnabled = false
        autoplayPlayerView.stateDidChanged = { [weak self] state in
            Task { @MainActor in
                self?.handleAutoplayPlayerStateChanged(state)
            }
        }
        autoplayPlayerObservation =
            autoplayPlayerView.playerLayer.observe(\.player, options: [.initial, .new]) { [weak self] _, _ in
                self?.configureTimelineAutoplayPlayer()
            }
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleTimelineVideoAutoplayNeedsUpdate),
            name: .timelineVideoAutoplayNeedsUpdate,
            object: nil
        )
    }

    private func updateContentInsets() {
        guard collectionView != nil else { return }
        let oldAdjustedTopInset = collectionView.adjustedContentInset.top
        let wasPinnedToTop = abs(collectionView.contentOffset.y + oldAdjustedTopInset) < 1
        let automaticTopInset = max(0, oldAdjustedTopInset - collectionView.contentInset.top)
        let desiredTopInset = topContentInset - (extendsContentUnderTopBars ? automaticTopInset : 0)
        if abs(collectionView.contentInset.top - desiredTopInset) > 0.5 {
            collectionView.contentInset.top = desiredTopInset
        }
        collectionView.verticalScrollIndicatorInsets.top = topScrollIndicatorInset
        if wasPinnedToTop {
            let topOffset = -collectionView.adjustedContentInset.top
            if abs(collectionView.contentOffset.y - topOffset) > 0.5 {
                collectionView.setContentOffset(
                    CGPoint(x: collectionView.contentOffset.x, y: topOffset),
                    animated: false
                )
            }
        }
        updateMinimumScrollableBottomInset()
    }

    private func updateMinimumScrollableBottomInset() {
        guard minimumVerticalScrollDistance > 0, collectionView.bounds.height > 0 else {
            if collectionView.contentInset.bottom != 0 {
                collectionView.contentInset.bottom = 0
            }
            return
        }
        let automaticBottomInset = max(
            0,
            collectionView.adjustedContentInset.bottom - collectionView.contentInset.bottom
        )
        let maximumOffsetWithoutBottomInset =
            collectionView.contentSize.height - collectionView.bounds.height +
            automaticBottomInset + collectionView.adjustedContentInset.top
        let requiredBottomInset = max(
            0,
            minimumVerticalScrollDistance - maximumOffsetWithoutBottomInset
        )
        if abs(collectionView.contentInset.bottom - requiredBottomInset) > 0.5 {
            collectionView.contentInset.bottom = requiredBottomInset
        }
    }

    private func updateBackgroundColors() {
        let usesGroupedBackground = usesGroupedBackgroundOverride ?? (
            appearance.usesCardBackground ||
                (columnCount > 1 && contentKind != .profileMedia)
        )
        let backgroundColor: UIColor = usesGroupedBackground ? .systemGroupedBackground : .systemBackground
        view.backgroundColor = backgroundColor
        collectionView.backgroundColor = backgroundColor
    }

    @objc private func handleTimelineVideoAutoplayNeedsUpdate() {
        validateCurrentAutoplayVisibility()
        scheduleAutoplaySelection()
    }

    // MARK: - Cell Configuration

    private func configureHostedCell(_ cell: TimelineHostedViewCell, itemID: String) {
        if itemID.hasPrefix(Self.accessoryPrefix) {
            cell.setHostedView(accessoryItemMap[itemID]?.view, usesWaterfallLayout: columnCount > 1)
        } else if itemID == Self.emptyID {
            cell.setHostedView(CenteredCellContentView(content: ListEmptyUIView()), usesWaterfallLayout: columnCount > 1)
        } else if itemID == Self.errorID {
            configureErrorCell(cell)
        } else if itemID == Self.footerLoadingID {
            cell.setHostedView(makeLoadingFooterView(), usesWaterfallLayout: columnCount > 1)
        } else if itemID == Self.footerErrorID {
            configureFooterErrorCell(cell)
        } else if itemID == Self.footerEndID {
            cell.setHostedView(makeTextFooterView(text: String(localized: "end_of_list")), usesWaterfallLayout: columnCount > 1)
        }
    }

    private func configureTimelineCell(_ cell: TimelineUIKitCollectionViewCell, itemID: String, index: Int) {
        guard let success = currentSuccess else { return }
        let totalCount = Int(success.itemCount)
        let item = (index >= 0 && index < totalCount) ? success.peek(index: Int32(index)) : nil
        if let item {
            cell.cachedPreferredHeight = { [weak self] width in
                guard let self, self.columnCount == 1 else { return nil }
                let key = self.timelineHeightCacheKey(itemID: itemID, renderHash: item.renderHash, width: width)
                return self.heightCache[key]
            }
            cell.onPreferredHeightChanged = { [weak self] width, height in
                self?.applyMeasuredHeightCorrection(
                    itemID: itemID,
                    renderHash: item.renderHash,
                    width: width,
                    height: height
                )
            }
            cell.configureTimeline(
                data: item,
                index: index,
                totalCount: totalCount,
                appearance: appearance,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                isMultipleColumn: columnCount > 1,
                openURL: openURL
            )
        } else {
            cell.cachedPreferredHeight = nil
            cell.onPreferredHeightChanged = nil
            cell.configurePlaceholder(
                index: index,
                totalCount: totalCount,
                appearance: appearance,
                isMultipleColumn: columnCount > 1
            )
        }
    }

    private func configurePlaceholderCell(_ cell: TimelinePlaceholderCollectionViewCell, index: Int) {
        let totalCount: Int
        if let success = currentSuccess {
            totalCount = Int(success.itemCount)
        } else {
            totalCount = 5
        }
        cell.configurePlaceholder(
            index: index,
            totalCount: totalCount,
            appearance: appearance,
            isMultipleColumn: columnCount > 1
        )
    }

    private func configureProfileMediaCell(_ cell: ProfileMediaCollectionViewCell, itemID: String) {
        guard itemID.hasPrefix(Self.profileMediaPrefix),
              let index = itemIndexMap[itemID],
              let success = currentProfileMediaSuccess,
              index >= 0,
              index < Int(success.itemCount),
              let item = success.peek(index: Int32(index)) else {
            cell.configurePlaceholder()
            return
        }
        cell.configure(item: item, appearance: appearance.status) { [weak self] in
            self?.openProfileMedia(item)
        }
    }

    private func openProfileMedia(_ item: ProfileMedia) {
        guard let post = item.status.timelineContentPost else { return }
        IOSTimelineMediaActions.open(
            post: post,
            statusKey: item.statusKey,
            index: Int32(item.index),
            preview: item.media.mediaPreviewURL,
            openURL: { [weak self] url in
                self?.openURL?(url)
            }
        )
    }

    private func configureErrorCell(_ cell: TimelineHostedViewCell) {
        let errorView = ListErrorUIView()
        errorView.onOpenURL = openURL
        switch contentKind {
        case .timeline:
            guard let data = currentData, case .error(let errorState) = onEnum(of: data) else { return }
            errorView.configure(error: errorState.error) { errorState.onRetry() }
        case .profileMedia:
            guard let data = currentProfileMediaData, case .error(let errorState) = onEnum(of: data) else { return }
            errorView.configure(error: errorState.error) { errorState.onRetry() }
        }
        cell.setHostedView(CenteredCellContentView(content: errorView), usesWaterfallLayout: columnCount > 1)
    }

    private func configureFooterErrorCell(_ cell: TimelineHostedViewCell) {
        let errorView = ListErrorUIView()
        errorView.onOpenURL = openURL
        switch contentKind {
        case .timeline:
            guard let success = currentSuccess,
                  case .error(let error) = onEnum(of: success.appendState) else { return }
            errorView.configure(error: error.error) { success.retry() }
        case .profileMedia:
            guard let success = currentProfileMediaSuccess,
                  case .error(let error) = onEnum(of: success.appendState) else { return }
            errorView.configure(error: error.error) { success.retry() }
        }
        cell.setHostedView(
            UIView.padding(errorView, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)),
            usesWaterfallLayout: columnCount > 1
        )
    }

    private func makeLoadingFooterView() -> UIView {
        let progress = UIActivityIndicatorView(style: .medium)
        progress.startAnimating()
        return UIView.padding(progress, insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16))
    }

    private func makeTextFooterView(text: String) -> UIView {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: .footnote)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.adjustsFontForContentSizeCategory = true
        return UIView.padding(label, insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16))
    }

    // MARK: - Refresh

    @objc private func handleRefresh() {
        isUserRefreshing = true
        Task { @MainActor in
            if let refreshCallback {
                await refreshCallback()
            }
            isUserRefreshing = false
            if !currentPagingIsRefreshing {
                refreshControl.endRefreshing()
            }
        }
    }

    private func pagingIsRefreshing<Item: AnyObject>(_ data: PagingState<Item>) -> Bool {
        switch onEnum(of: data) {
        case .loading:
            return true
        case .success(let success):
            return success.isRefreshing
        default:
            return false
        }
    }

    private var currentPagingIsRefreshing: Bool {
        switch contentKind {
        case .timeline:
            currentData.map(pagingIsRefreshing) ?? false
        case .profileMedia:
            currentProfileMediaData.map(pagingIsRefreshing) ?? false
        }
    }

    func resetInitialRefreshIndicatorSuppression() {
        hasCompletedInitialRefreshCycle = false
        shouldRevealRefreshControl = false
        guard isViewLoaded,
              suppressInitialRefreshIndicator,
              refreshControl.isRefreshing,
              !isUserRefreshing else {
            return
        }
        refreshControl.endRefreshing()
    }

    // MARK: - State Update

    func update(data: PagingState<UiTimelineV2>, columnCount requestedColumnCount: Int) {
        let wasRefreshing = contentKind == .timeline && currentPagingIsRefreshing
        let isRefreshing = pagingIsRefreshing(data)
        let nextSuccess: PagingStateSuccess<UiTimelineV2>?
        if case .success(let success) = onEnum(of: data) {
            nextSuccess = success
        } else {
            nextSuccess = nil
        }
        let targetColumnCount = max(requestedColumnCount, 1)

        guard isViewLoaded else {
            setContentKind(.timeline)
            columnCount = targetColumnCount
            currentData = data
            currentSuccess = nextSuccess
            return
        }

        if contentKind != .timeline || columnCount != targetColumnCount {
            let plan = makeSnapshotPlan(data: data, columnCount: targetColumnCount)
            let snapshot = Self.makeSnapshot(from: plan)
            applyPreparedContentTransition(
                to: .timeline,
                columnCount: targetColumnCount,
                snapshot: snapshot,
                plan: plan
            ) {
                self.currentData = data
                self.currentSuccess = nextSuccess
            }
        } else {
            currentData = data
            currentSuccess = nextSuccess
            syncRefreshControl(isRefreshing: isRefreshing)
            applySnapshot(data: data)
        }
        if currentSuccess == nil {
            detachAutoplayPlayer(pause: true)
        } else {
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
        }
        if wasRefreshing && !isRefreshing {
            schedulePostRefreshPoolCleanup()
        }
    }

    func update(profileMediaData data: PagingState<ProfileMedia>) {
        let isRefreshing = pagingIsRefreshing(data)
        let nextSuccess: PagingStateSuccess<ProfileMedia>?
        if case .success(let success) = onEnum(of: data) {
            nextSuccess = success
        } else {
            nextSuccess = nil
        }
        let targetColumnCount = resolvedProfileMediaColumnCount()

        guard isViewLoaded else {
            setContentKind(.profileMedia)
            columnCount = targetColumnCount
            currentProfileMediaData = data
            currentProfileMediaSuccess = nextSuccess
            return
        }

        if contentKind != .profileMedia || columnCount != targetColumnCount {
            let plan = makeSnapshotPlan(profileMediaData: data, columnCount: targetColumnCount)
            let snapshot = Self.makeSnapshot(from: plan)
            applyPreparedContentTransition(
                to: .profileMedia,
                columnCount: targetColumnCount,
                snapshot: snapshot,
                plan: plan
            ) {
                self.currentProfileMediaData = data
                self.currentProfileMediaSuccess = nextSuccess
            }
        } else {
            currentProfileMediaData = data
            currentProfileMediaSuccess = nextSuccess
            syncRefreshControl(isRefreshing: isRefreshing)
            applySnapshot(profileMediaData: data)
        }
    }

    private func setContentKind(_ newKind: ContentKind) {
        guard contentKind != newKind else { return }
        contentKind = newKind
        currentData = nil
        currentSuccess = nil
        currentProfileMediaData = nil
        currentProfileMediaSuccess = nil
        pendingScrollAnchor = nil
    }

    private func syncRefreshControl(isRefreshing: Bool) {
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
                if refreshControl.isRefreshing {
                    refreshControl.endRefreshing()
                }
                return
            }
            if !refreshControl.isRefreshing {
                refreshControl.beginRefreshing()
                shouldRevealRefreshControl = !isUserRefreshing
                revealRefreshControlIfNeeded()
            }
        } else if !isUserRefreshing {
            shouldRevealRefreshControl = false
            if refreshControl.isRefreshing {
                refreshControl.endRefreshing()
            }
        }
    }

    private func revealRefreshControlIfNeeded() {
        guard shouldRevealRefreshControl, refreshControl.isRefreshing else { return }

        // `beginRefreshing()` alone does not make the indicator visible.
        // Pull the collection view down far enough so the refresh control is revealed.
        let refreshHeight = max(refreshControl.bounds.height, 60)
        let targetOffsetY = -(collectionView.adjustedContentInset.top + refreshHeight)

        guard collectionView.contentOffset.y > targetOffsetY else {
            shouldRevealRefreshControl = false
            return
        }

        collectionView.setContentOffset(
            CGPoint(x: collectionView.contentOffset.x, y: targetOffsetY),
            animated: false
        )
        shouldRevealRefreshControl = false
    }

    private func reportIsAtTop() {
        let isAtTop = effectiveContentOffsetY <= 1
        guard lastReportedIsAtTop != isAtTop else { return }
        lastReportedIsAtTop = isAtTop
        onIsAtTopChanged?(isAtTop)
    }

    private var allowsScrollAnchorRestoration: Bool {
        !collectionView.isTracking &&
            !collectionView.isDragging &&
            !collectionView.isDecelerating &&
            !scrollingState.isScrolling
    }

    private func clampedContentOffsetY(_ offsetY: CGFloat) -> CGFloat {
        let minY = -collectionView.adjustedContentInset.top
        let maxY = max(
            minY,
            collectionView.contentSize.height - collectionView.bounds.height + collectionView.adjustedContentInset.bottom
        )
        return min(max(offsetY, minY), maxY)
    }

    private func isDataItemID(_ itemID: String) -> Bool {
        itemID.hasPrefix(Self.timelinePrefix) || itemID.hasPrefix(Self.profileMediaPrefix)
    }

    private func captureScrollAnchor() -> ScrollAnchor? {
        guard isViewLoaded,
              currentSuccess != nil || currentProfileMediaSuccess != nil,
              allowsScrollAnchorRestoration,
              collectionView.bounds.height > 1 else {
            return nil
        }

        let viewportTop = effectiveContentOffsetY
        let viewportBottom = collectionView.contentOffset.y + collectionView.bounds.height - collectionView.adjustedContentInset.bottom
        return collectionView.indexPathsForVisibleItems
            .compactMap { indexPath -> (itemID: String, frame: CGRect)? in
                guard let itemID = dataSource.itemIdentifier(for: indexPath),
                      isDataItemID(itemID) else {
                    return nil
                }
                let frame = collectionView.layoutAttributesForItem(at: indexPath)?.frame
                    ?? collectionView.cellForItem(at: indexPath)?.frame
                    ?? .null
                guard !frame.isNull,
                      frame.maxY > viewportTop,
                      frame.minY < viewportBottom else {
                    return nil
                }
                return (itemID, frame)
            }
            .min { lhs, rhs in
                if abs(lhs.frame.minY - rhs.frame.minY) > 0.5 {
                    return lhs.frame.minY < rhs.frame.minY
                }
                return lhs.frame.minX < rhs.frame.minX
            }
            .map {
                ScrollAnchor(
                    itemID: $0.itemID,
                    distanceFromViewportTop: $0.frame.minY - viewportTop
                )
            }
    }

    @discardableResult
    private func restoreScrollAnchorIfNeeded(_ anchor: ScrollAnchor?) -> Bool {
        guard let anchor,
              isViewLoaded,
              allowsScrollAnchorRestoration,
              let indexPath = dataSource.indexPath(for: anchor.itemID) else {
            return false
        }

        view.layoutIfNeeded()
        collectionView.layoutIfNeeded()

        guard let attributes = collectionView.layoutAttributesForItem(at: indexPath) else {
            return false
        }

        let targetOffsetY = attributes.frame.minY - anchor.distanceFromViewportTop - collectionView.adjustedContentInset.top
        let targetOffset = CGPoint(x: collectionView.contentOffset.x, y: clampedContentOffsetY(targetOffsetY))
        if abs(collectionView.contentOffset.y - targetOffset.y) > 0.5 {
            isRestoringScrollAnchor = true
            collectionView.setContentOffset(targetOffset, animated: false)
            isRestoringScrollAnchor = false
        }
        return true
    }

    private func restorePendingContentOffsetIfNeeded(finalize: Bool) {
        guard let offsetY = pendingEffectiveContentOffsetYAfterSnapshot else { return }
        restoreEffectiveContentOffset(offsetY, animated: false)
        if finalize {
            pendingEffectiveContentOffsetYAfterSnapshot = nil
        }
        collectionView.layer.removeAllAnimations()
    }

    private func applyPreparedContentTransition(
        to newKind: ContentKind,
        columnCount newColumnCount: Int,
        snapshot: NSDiffableDataSourceSnapshot<Int, String>,
        plan: SnapshotPlan,
        updateState: () -> Void
    ) {
        // Invalidate any in-flight snapshot for the previous tab before replacing
        // both its data and layout in the same non-animated transaction.
        snapshotPreparationGeneration += 1
        isApplyingContentTransition = true
        defer { isApplyingContentTransition = false }

        UIView.performWithoutAnimation {
            CATransaction.begin()
            CATransaction.setDisableActions(true)

            setContentKind(newKind)
            columnCount = max(newColumnCount, 1)
            updateState()
            clearAllHeightCache()
            detachAutoplayPlayer(pause: true)
            pendingScrollAnchor = nil
            itemIndexMap = plan.indexMap

            // Keep stable accessory and state cells attached, but refresh their
            // callbacks against the newly selected paging source.
            var transitionSnapshot = snapshot
            let existingIDs = Set(dataSource.snapshot().itemIdentifiers)
            transitionSnapshot.reconfigureItems(
                transitionSnapshot.itemIdentifiers.filter(existingIDs.contains)
            )
            dataSource.apply(transitionSnapshot, animatingDifferences: false)
            applyLayoutForColumnCount()
            updateBackgroundColors()
            syncRefreshControl(isRefreshing: plan.isRefreshing)
            collectionView.layoutIfNeeded()
            restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
            collectionView.layer.removeAllAnimations()

            lastAppliedSignature = plan.signature
            lastRenderHashMap = plan.renderHashMap
            lastLoadedItemIDs = plan.loadedItemIDs
            CATransaction.commit()
        }
    }

    private func applyCurrentSnapshot() {
        switch contentKind {
        case .timeline:
            if let currentData {
                applySnapshot(data: currentData)
            }
        case .profileMedia:
            if let currentProfileMediaData {
                applySnapshot(profileMediaData: currentProfileMediaData)
            }
        }
    }

    private func applySnapshot(data: PagingState<UiTimelineV2>) {
        applySnapshot(plan: makeSnapshotPlan(data: data, columnCount: columnCount))
    }

    private func applySnapshot(profileMediaData data: PagingState<ProfileMedia>) {
        applySnapshot(plan: makeSnapshotPlan(profileMediaData: data, columnCount: columnCount))
    }

    private func applySnapshot(plan: SnapshotPlan) {
        snapshotPreparationGeneration += 1
        let generation = snapshotPreparationGeneration
        DispatchQueue.global(qos: .userInitiated).async { [plan] in
            let snapshot = Self.makeSnapshot(from: plan)
            DispatchQueue.main.async { [weak self] in
                guard let self, self.snapshotPreparationGeneration == generation else { return }
                self.applyPreparedSnapshot(snapshot, plan: plan)
            }
        }
    }

    private func makeSnapshotPlan(
        data: PagingState<UiTimelineV2>,
        columnCount: Int
    ) -> SnapshotPlan {
        makeSnapshotPlan(
            data: data,
            loadingItemCount: loadingPlaceholderCount(
                minimum: TimelineUIKitLayoutMetrics.timelinePlaceholderCount,
                columnCount: columnCount,
                estimatedPlaceholderHeight: 120
            ),
            placeholderPrefix: Self.placeholderPrefix,
            itemID: { "\(Self.timelinePrefix)\(Self.itemIdentityKey(for: $0))" },
            renderHash: { $0.renderHash }
        )
    }

    private func makeSnapshotPlan(
        profileMediaData data: PagingState<ProfileMedia>,
        columnCount: Int
    ) -> SnapshotPlan {
        makeSnapshotPlan(
            data: data,
            loadingItemCount: loadingPlaceholderCount(
                minimum: 8,
                columnCount: columnCount,
                estimatedPlaceholderHeight: profileMediaPlaceholderHeight(columnCount: columnCount)
            ),
            placeholderPrefix: Self.profileMediaPlaceholderPrefix,
            itemID: { "\(Self.profileMediaPrefix)\($0.key)" },
            renderHash: { $0.status.renderHash }
        )
    }

    private func makeSnapshotPlan<Item: AnyObject>(
        data: PagingState<Item>,
        loadingItemCount: Int,
        placeholderPrefix: String,
        itemID: (Item) -> String,
        renderHash: (Item) -> Int32
    ) -> SnapshotPlan {
        var newIndexMap: [String: Int] = [:]
        var newRenderHashMap: [String: Int32] = [:]
        var newLoadedItemIDs = Set<String>()
        let accessoryIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
        var itemIDs: [String] = []
        var footerIDs: [String] = []
        var isInitialLoading = false

        switch onEnum(of: data) {
        case .loading:
            isInitialLoading = true
            itemIDs = (0..<loadingItemCount).map { "\(placeholderPrefix)\($0)" }
        case .error:
            itemIDs = [Self.errorID]
        case .empty:
            itemIDs = [Self.emptyID]
        case .success(let success):
            let itemCount = Int(success.itemCount)
            var loadedIDsByIndex: [Int: String] = [:]
            var loadedRenderHashByItemID: [String: Int32] = [:]

            for index in 0..<itemCount {
                guard let item = success.peek(index: Int32(index)) else { continue }
                let id = itemID(item)
                loadedIDsByIndex[index] = id
                loadedRenderHashByItemID[id] = renderHash(item)
                newLoadedItemIDs.insert(id)
            }

            itemIDs.reserveCapacity(itemCount)
            for index in 0..<itemCount {
                let id: String
                if let loadedID = loadedIDsByIndex[index] {
                    id = loadedID
                    newRenderHashMap[id] = loadedRenderHashByItemID[id]
                } else {
                    id = "\(placeholderPrefix)\(index)"
                }
                itemIDs.append(id)
                newIndexMap[id] = index
            }
            footerIDs = footerItemIDs(for: success)
        }

        let signature = SnapshotSignature(
            accessoryIDs: accessoryIDs,
            itemIDs: itemIDs,
            footerIDs: footerIDs
        )
        return SnapshotPlan(
            signature: signature,
            accessoryIDs: accessoryIDs,
            itemIDs: itemIDs,
            footerIDs: footerIDs,
            indexMap: newIndexMap,
            renderHashMap: newRenderHashMap,
            loadedItemIDs: newLoadedItemIDs,
            isRefreshing: pagingIsRefreshing(data),
            isInitialLoading: isInitialLoading
        )
    }

    private func loadingPlaceholderCount(
        minimum: Int,
        columnCount: Int,
        estimatedPlaceholderHeight: CGFloat
    ) -> Int {
        guard let targetOffsetY = pendingEffectiveContentOffsetYAfterSnapshot, isViewLoaded else {
            return minimum
        }
        let viewportHeight = max(collectionView.bounds.height, 1)
        let targetContentHeight = max(
            targetOffsetY + viewportHeight,
            viewportHeight
        )
        let rows = max(Int(ceil(targetContentHeight / max(estimatedPlaceholderHeight, 1))) + 1, 1)
        return max(minimum, rows * max(columnCount, 1))
    }

    private func profileMediaPlaceholderHeight(columnCount: Int) -> CGFloat {
        let width = collectionView.bounds.width
        let horizontalInsets = TimelineUIKitLayoutMetrics.horizontalInset * 2
        guard width > horizontalInsets else { return 120 }
        let columns = max(columnCount, 1)
        let availableWidth = width - horizontalInsets -
            CGFloat(columns - 1) * TimelineUIKitLayoutMetrics.columnSpacing
        return max(availableWidth / CGFloat(columns), 1)
    }

    nonisolated private static func makeSnapshot(from plan: SnapshotPlan) -> NSDiffableDataSourceSnapshot<Int, String> {
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        if !plan.accessoryIDs.isEmpty {
            snapshot.appendSections([Self.sectionAccessories])
            snapshot.appendItems(plan.accessoryIDs, toSection: Self.sectionAccessories)
        }
        snapshot.appendSections([Self.sectionMain])
        snapshot.appendItems(plan.itemIDs, toSection: Self.sectionMain)
        if !plan.footerIDs.isEmpty {
            snapshot.appendSections([Self.sectionFooter])
            snapshot.appendItems(plan.footerIDs, toSection: Self.sectionFooter)
        }
        return snapshot
    }

    private func applyPreparedSnapshot(
        _ preparedSnapshot: NSDiffableDataSourceSnapshot<Int, String>,
        plan: SnapshotPlan
    ) {
        var snapshot = preparedSnapshot
        let newSignature = plan.signature
        let previousSignature = lastAppliedSignature
        let scrollAnchor = pendingEffectiveContentOffsetYAfterSnapshot == nil &&
            previousSignature != nil &&
            previousSignature?.itemIDs != newSignature.itemIDs &&
            allowsScrollAnchorRestoration
            ? captureScrollAnchor()
            : nil

        itemIndexMap = plan.indexMap
        scheduleHeightCachePrune(keepingItemIDs: Set(plan.indexMap.keys))

        if previousSignature?.accessoryIDs == newSignature.accessoryIDs,
           previousSignature?.itemIDs == newSignature.itemIDs,
           previousSignature?.footerIDs == newSignature.footerIDs {
            let changedIDs = changedItemIDs(
                in: plan.itemIDs,
                newRenderHashMap: plan.renderHashMap,
                newLoadedItemIDs: plan.loadedItemIDs
            )
            lastRenderHashMap = plan.renderHashMap
            lastLoadedItemIDs = plan.loadedItemIDs
            reconfigureItems(changedIDs)
            restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        if previousSignature?.accessoryIDs == newSignature.accessoryIDs,
           previousSignature?.itemIDs == newSignature.itemIDs {
            let changedIDs = changedItemIDs(
                in: plan.itemIDs,
                newRenderHashMap: plan.renderHashMap,
                newLoadedItemIDs: plan.loadedItemIDs
            )
            applyFooterSnapshot(footerIDs: plan.footerIDs, reconfigureIDs: changedIDs, isRefreshing: plan.isRefreshing)
            restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
            lastAppliedSignature = newSignature
            lastRenderHashMap = plan.renderHashMap
            lastLoadedItemIDs = plan.loadedItemIDs
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        // Reconfigure only existing timeline items whose render payload or loaded state changed.
        let existing = Set(dataSource.snapshot().itemIdentifiers)
        let toReconfigure = plan.itemIDs.filter {
            existing.contains($0) && itemNeedsReconfigure(
                $0,
                newRenderHashMap: plan.renderHashMap,
                newLoadedItemIDs: plan.loadedItemIDs
            )
        }
        if !toReconfigure.isEmpty {
            snapshot.reconfigureItems(toReconfigure)
        }

        let shouldAnimateDifferences =
                    !plan.isRefreshing &&
                    !refreshControl.isRefreshing &&
                    pendingEffectiveContentOffsetYAfterSnapshot == nil &&
                    scrollAnchor == nil &&
                    !collectionView.isDragging &&
                    !collectionView.isDecelerating

        if let scrollAnchor {
            pendingScrollAnchor = scrollAnchor
            UIView.performWithoutAnimation {
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                dataSource.apply(snapshot, animatingDifferences: false) { [weak self] in
                    guard let self else { return }
                    self.restoreScrollAnchorIfNeeded(scrollAnchor)
                    self.restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
                    self.pendingScrollAnchor = nil
                    self.validateCurrentAutoplayVisibility()
                    self.scheduleAutoplaySelection()
                }
                restoreScrollAnchorIfNeeded(scrollAnchor)
                collectionView.layer.removeAllAnimations()
                CATransaction.commit()
            }
        } else {
            dataSource.apply(snapshot, animatingDifferences: shouldAnimateDifferences) { [weak self] in
                guard let self else { return }
                self.restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
                self.validateCurrentAutoplayVisibility()
                self.scheduleAutoplaySelection()
            }
        }
        lastAppliedSignature = newSignature
        lastRenderHashMap = plan.renderHashMap
        lastLoadedItemIDs = plan.loadedItemIDs
    }

    private func changedItemIDs(
        in itemIDs: [String],
        newRenderHashMap: [String: Int32],
        newLoadedItemIDs: Set<String>
    ) -> [String] {
        itemIDs.filter {
            itemNeedsReconfigure(
                $0,
                newRenderHashMap: newRenderHashMap,
                newLoadedItemIDs: newLoadedItemIDs
            )
        }
    }

    private func itemNeedsReconfigure(
        _ itemID: String,
        newRenderHashMap: [String: Int32],
        newLoadedItemIDs: Set<String>
    ) -> Bool {
        lastRenderHashMap[itemID] != newRenderHashMap[itemID] ||
            lastLoadedItemIDs.contains(itemID) != newLoadedItemIDs.contains(itemID)
    }

    private func restorePendingScrollAnchorIfNeeded() {
        guard !isRestoringScrollAnchor,
              allowsScrollAnchorRestoration,
              let pendingScrollAnchor else {
            return
        }
        if restoreScrollAnchorIfNeeded(pendingScrollAnchor) {
            collectionView.layer.removeAllAnimations()
        }
    }

    private func reconfigureItems(_ itemIDs: [String]) {
        guard !itemIDs.isEmpty else { return }
        var snapshot = dataSource.snapshot()
        let existingItems = Set(snapshot.itemIdentifiers)
        let reconfigureIDs = itemIDs.filter { existingItems.contains($0) }
        guard !reconfigureIDs.isEmpty else { return }
        snapshot.reconfigureItems(reconfigureIDs)
        dataSource.apply(snapshot, animatingDifferences: false)
    }

    private func reconfigureVisibleCells() {
        let visibleIDs = collectionView.indexPathsForVisibleItems.compactMap {
            dataSource.itemIdentifier(for: $0)
        }
        reconfigureItems(visibleIDs)
    }

    private func applyFooterSnapshot(footerIDs: [String], reconfigureIDs: [String], isRefreshing: Bool) {
        var snapshot = dataSource.snapshot()
        let hasFooterSection = snapshot.sectionIdentifiers.contains(Self.sectionFooter)

        if hasFooterSection {
            snapshot.deleteSections([Self.sectionFooter])
        }

        if !footerIDs.isEmpty {
            snapshot.appendSections([Self.sectionFooter])
            snapshot.appendItems(footerIDs, toSection: Self.sectionFooter)
        }

        let existingItems = Set(snapshot.itemIdentifiers)
        let intersectedReconfigureIDs = reconfigureIDs.filter { existingItems.contains($0) }
        if !intersectedReconfigureIDs.isEmpty {
            snapshot.reconfigureItems(intersectedReconfigureIDs)
        }

        let shouldAnimateDifferences =
                    !isRefreshing &&
                    !refreshControl.isRefreshing &&
                    !collectionView.isDragging &&
                    !collectionView.isDecelerating
        dataSource.apply(snapshot, animatingDifferences: shouldAnimateDifferences)
    }

    private func footerItemIDs<Item: AnyObject>(for success: PagingStateSuccess<Item>) -> [String] {
        switch onEnum(of: success.appendState) {
        case .error:
            return [Self.footerErrorID]
        case .loading:
            return [Self.footerLoadingID]
        case .notLoading(let notLoading):
            if notLoading.endOfPaginationReached {
                return [Self.footerEndID]
            }
            return []
        }
    }

    // MARK: - Video Autoplay

    private var isVideoAutoplayAllowed: Bool {
        switch appearance.videoAutoplay {
        case .never:
            return false
        case .wifi:
            return networkKind == .wifi
        case .always:
            return true
        default:
            return false
        }
    }

    private func handleAutoplayAvailabilityChanged() {
        validateCurrentAutoplayVisibility()
        guard isVideoAutoplayAllowed else {
            detachAutoplayPlayer(pause: true)
            return
        }
        scheduleAutoplaySelection()
    }

    private func scheduleAutoplaySelection(delayNanoseconds: UInt64 = 300_000_000) {
        autoplaySelectionTask?.cancel()
        guard isViewLoaded, currentSuccess != nil, isVideoAutoplayAllowed else { return }
        autoplaySelectionTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: delayNanoseconds)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            self.selectAutoplayCandidateIfStable()
        }
    }

    private func schedulePostRefreshPoolCleanup() {
        postRefreshPoolCleanupTask?.cancel()
        guard isViewLoaded else { return }
        postRefreshPoolCleanupTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: 300_000_000)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            self.performLightweightPoolCleanupIfStable()
        }
    }

    private func performLightweightPoolCleanupIfStable() {
        guard !collectionView.isDragging,
              !collectionView.isDecelerating,
              !scrollingState.isScrolling else {
            return
        }

        for cell in collectionView.visibleCells {
            (cell as? TimelineUIKitCollectionViewCell)?.performLightweightPoolCleanup()
        }
    }

    private func scheduleDeferredPoolCleanup() {
        deferredPoolCleanupTask?.cancel()
        guard isViewLoaded else { return }
        deferredPoolCleanupTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: 10_000_000_000)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            self.performDeferredPoolCleanupIfStable()
        }
    }

    private func performDeferredPoolCleanupIfStable() {
        guard !collectionView.isDragging,
              !collectionView.isDecelerating,
              !scrollingState.isScrolling else {
            return
        }

        var seen = Set<ObjectIdentifier>()
        var cells: [TimelineUIKitCollectionViewCell] = []

        func append(_ cell: TimelineUIKitCollectionViewCell) {
            let id = ObjectIdentifier(cell)
            guard !seen.contains(id) else { return }
            seen.insert(id)
            cells.append(cell)
        }

        for cell in collectionView.visibleCells {
            if let timelineCell = cell as? TimelineUIKitCollectionViewCell {
                append(timelineCell)
            }
        }
        for cell in deferredPoolCleanupCells.allObjects {
            append(cell)
        }

        for cell in cells {
            cell.performDeferredPoolCleanup()
        }
        deferredPoolCleanupCells.removeAllObjects()
    }

    private func selectAutoplayCandidateIfStable() {
        guard isVideoAutoplayAllowed, currentSuccess != nil else {
            detachAutoplayPlayer(pause: true)
            return
        }
        guard !collectionView.isDragging, !collectionView.isDecelerating, !scrollingState.isScrolling else { return }
        guard let candidate = bestAutoplayCandidate() else {
            detachAutoplayPlayer(pause: true)
            return
        }
        playAutoplayCandidate(candidate)
    }

    private func bestAutoplayCandidate() -> TimelineVideoAutoplayCandidate? {
        let viewportRect = collectionView.bounds
        guard viewportRect.width > 0, viewportRect.height > 0 else { return nil }
        let visibleCenter = CGPoint(x: viewportRect.midX, y: viewportRect.midY)

        return visibleAutoplayCandidates()
            .compactMap { candidate -> (candidate: TimelineVideoAutoplayCandidate, distance: CGFloat)? in
                guard let candidateRect = visibleRect(for: candidate.hostView, in: collectionView) else { return nil }
                let candidateCenter = CGPoint(x: candidateRect.midX, y: candidateRect.midY)
                let dx = candidateCenter.x - visibleCenter.x
                let dy = candidateCenter.y - visibleCenter.y
                return (candidate, dx * dx + dy * dy)
            }
            .min { lhs, rhs in lhs.distance < rhs.distance }?
            .candidate
    }

    private func visibleAutoplayCandidates() -> [TimelineVideoAutoplayCandidate] {
        collectionView.indexPathsForVisibleItems.flatMap { indexPath -> [TimelineVideoAutoplayCandidate] in
            guard let cell = collectionView.cellForItem(at: indexPath) as? TimelineUIKitCollectionViewCell,
                  let itemID = dataSource.itemIdentifier(for: indexPath),
                  itemID.hasPrefix(Self.timelinePrefix) else {
                return []
            }
            return cell.autoplayCandidates(prefix: itemID)
        }
    }

    private func playAutoplayCandidate(_ candidate: TimelineVideoAutoplayCandidate) {
        guard currentAutoplayID != candidate.id || currentAutoplayHostView !== candidate.hostView else {
            return
        }
        guard let newHost = candidate.hostView as? MediaUIView else { return }

        if let oldHost = currentAutoplayHostView as? MediaUIView, oldHost !== candidate.hostView {
            oldHost.detachAutoplayPlayer()
        } else if autoplayPlayerView.superview !== candidate.hostView {
            autoplayPlayerView.removeFromSuperview()
        }

        newHost.attachAutoplayPlayer(autoplayPlayerView)
        newHost.setAutoplayOverlay(.loading)

        currentAutoplayID = candidate.id
        currentAutoplayHostView = candidate.hostView
        autoplayPlayerView.play(for: candidate.url)
        configureTimelineAutoplayPlayer()
        autoplayPlayerView.isMuted = true
        autoplayPlayerView.isAutoReplay = true
        startAutoplayCountdownUpdates()
    }

    private func configureTimelineAutoplayPlayer() {
        autoplayPlayerView.player?.preventsDisplaySleepDuringVideoPlayback = false
    }

    private func handleAutoplayPlayerStateChanged(_ state: VideoPlayerView.State) {
        guard let host = currentAutoplayHostView as? MediaUIView else { return }
        switch state {
        case .none:
            stopAutoplayCountdownUpdates()
            host.setAutoplayOverlay(.idle)
        case .loading:
            stopAutoplayCountdownUpdates()
            host.setAutoplayOverlay(.loading)
        case .playing:
            startAutoplayCountdownUpdates()
            updateAutoplayCountdown()
        case .paused:
            stopAutoplayCountdownUpdates()
            host.setAutoplayOverlay(.idle)
        case .error:
            stopAutoplayCountdownUpdates()
            host.setAutoplayOverlay(.error)
        }
    }

    private func startAutoplayCountdownUpdates() {
        autoplayCountdownTask?.cancel()
        autoplayCountdownTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                self?.updateAutoplayCountdown()
                do {
                    try await Task.sleep(nanoseconds: 500_000_000)
                } catch {
                    return
                }
            }
        }
    }

    private func stopAutoplayCountdownUpdates() {
        autoplayCountdownTask?.cancel()
        autoplayCountdownTask = nil
    }

    private func updateAutoplayCountdown() {
        guard let host = currentAutoplayHostView as? MediaUIView else { return }
        let remaining = max(autoplayPlayerView.totalDuration - autoplayPlayerView.currentDuration, 0)
        host.setAutoplayOverlay(.playing(remaining: remaining))
    }

    private func validateCurrentAutoplayVisibility() {
        guard currentAutoplayHostView != nil else { return }
        guard isVideoAutoplayAllowed,
              let host = currentAutoplayHostView,
              let currentID = currentAutoplayID,
              host.window != nil,
              visibleRect(for: host, in: collectionView) != nil else {
            detachAutoplayPlayer(pause: true)
            return
        }
        let stillValid = visibleAutoplayCandidates().contains { candidate in
            candidate.id == currentID && candidate.hostView === host
        }
        if !stillValid {
            detachAutoplayPlayer(pause: true)
        }
    }

    private func detachAutoplayPlayer(pause: Bool) {
        autoplaySelectionTask?.cancel()
        stopAutoplayCountdownUpdates()
        if pause {
            autoplayPlayerView.pause(reason: .hidden)
        }
        if let host = currentAutoplayHostView as? MediaUIView {
            host.detachAutoplayPlayer()
        } else {
            autoplayPlayerView.removeFromSuperview()
        }
        currentAutoplayID = nil
        currentAutoplayHostView = nil
    }

    private func visibleRect(for hostView: UIView, in collectionView: UICollectionView) -> CGRect? {
        guard !hostView.isHidden,
              hostView.alpha > 0.01,
              hostView.window != nil,
              hostView.bounds.width > 1,
              hostView.bounds.height > 1 else {
            return nil
        }
        let rect = hostView.convert(hostView.bounds, to: collectionView)
        let visibleRect = rect.intersection(collectionView.bounds)
        guard !visibleRect.isNull, visibleRect.width > 1, visibleRect.height > 1 else { return nil }
        return visibleRect
    }

    // MARK: - CHTCollectionViewDelegateWaterfallLayout

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> CGSize {
        guard let layout = collectionViewLayout as? CHTCollectionViewWaterfallLayout else {
            return CGSize(width: collectionView.bounds.width, height: 200)
        }
        let section = indexPath.section
        let columns = isFullWidthSection(at: section) ? 1 : max(columnCount, 1)
        let insets = waterfallInsets(for: section, layout: layout)
        let available = collectionView.bounds.width - insets.left - insets.right
        let totalSpacing = CGFloat(columns - 1) * layout.minimumColumnSpacing
        let width = max((available - totalSpacing) / CGFloat(columns), 1)

        guard let itemID = dataSource.itemIdentifier(for: indexPath) else {
            return CGSize(width: width, height: 200)
        }

        if itemID.hasPrefix(Self.accessoryPrefix),
           let accessory = accessoryItemMap[itemID] {
            // Match the fractional self-sizing used by the compositional layout
            // so switching to the waterfall layout does not move profile tabs.
            let height = max(
                measuredCompressedCardHeight(accessory.view, width: width, heightPadding: 0),
                1
            )
            return CGSize(width: width, height: height)
        }

        switch itemID {
        case Self.emptyID, Self.errorID:
            return CGSize(width: width, height: 240)
        case Self.footerLoadingID,
             Self.footerErrorID,
             Self.footerEndID:
            return CGSize(width: width, height: 60)
        default:
            break
        }

        if itemID.hasPrefix(Self.placeholderPrefix) {
            let key = "__placeholder__:\(heightCacheWidthKey(for: width))"
            if let cached = heightCache[key] { return CGSize(width: width, height: cached) }
            let totalCount = currentSuccess.map { Int($0.itemCount) } ?? 5
            sizingPlaceholderCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
            sizingPlaceholderCard.isMultipleColumn = true
            sizingPlaceholderCard.configure(index: 0, totalCount: totalCount)
            let height = max(ceil(measuredCompressedCardHeight(sizingPlaceholderCard, width: width)), 120)
            heightCache[key] = height
            return CGSize(width: width, height: height)
        }

        if itemID.hasPrefix(Self.profileMediaPlaceholderPrefix) {
            return CGSize(width: width, height: width)
        }

        if itemID.hasPrefix(Self.profileMediaPrefix),
           let index = itemIndexMap[itemID],
           let success = currentProfileMediaSuccess,
           index >= 0,
           index < Int(success.itemCount),
           let item = success.peek(index: Int32(index)) {
            let rawRatio = item.media.aspectRatio ?? 1
            let ratio = rawRatio.isFinite && rawRatio > 0
                ? max(9.0 / 21.0, rawRatio)
                : 1
            return CGSize(width: width, height: max(ceil(width / ratio), 1))
        }

        if itemID.hasPrefix(Self.timelinePrefix),
           let index = itemIndexMap[itemID],
           let success = currentSuccess,
           index >= 0,
           index < Int(success.itemCount),
           let item = success.peek(index: Int32(index)) {
            let key = timelineHeightCacheKey(itemID: itemID, renderHash: item.renderHash, width: width)
            if let cached = heightCache[key] { return CGSize(width: width, height: cached) }
            sizingTimelineCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
            sizingTimelineCard.isMultipleColumn = true
            sizingTimelineCard.configure(index: index, totalCount: Int(success.itemCount))
            sizingTimelineView.configure(
                data: item,
                appearance: appearance.status,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                onOpenURL: nil
            )
            // Compose applies the multi-column card wrapper outside the row:
            // 2pt horizontally, 6pt vertically.
            let contentWidth = max(width - 4 - 32, 1)
            sizingTimelineView.prepareForFitting(width: contentWidth)
            let measuredHeight: CGFloat
            if let contentHeight = sizingTimelineView.estimatedHeightForFitting(width: contentWidth) {
                measuredHeight = ceil(contentHeight + 16 + 12) + 1
            } else {
                measuredHeight = ceil(measuredCompressedCardHeight(sizingTimelineCard, width: width))
            }
            let height = max(measuredHeight, 120)
            heightCache[key] = height
            heightCacheKeysByItemID[itemID, default: []].insert(key)
            return CGSize(width: width, height: height)
        }

        return CGSize(width: width, height: 200)
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        columnCountFor section: Int
    ) -> Int {
        isFullWidthSection(at: section) ? 1 : max(columnCount, 1)
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        insetsFor section: Int
    ) -> UIEdgeInsets {
        guard let layout = collectionViewLayout as? CHTCollectionViewWaterfallLayout else { return .zero }
        return waterfallInsets(for: section, layout: layout)
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        minimumInteritemSpacingFor section: Int
    ) -> CGFloat {
        guard contentKind == .profileMedia else { return 0 }
        switch sectionIdentifier(at: section) {
        case Self.sectionAccessories: return 2
        case Self.sectionMain: return 8
        default: return 0
        }
    }

    // MARK: - UICollectionViewDelegate

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        if let itemID = dataSource.itemIdentifier(for: indexPath),
           let accessory = accessoryItemMap[itemID] {
            accessory.onVisibilityChanged?(true)
            return
        }
        guard let itemID = dataSource.itemIdentifier(for: indexPath),
              let index = itemIndexMap[itemID] else { return }
        switch contentKind {
        case .timeline:
            if let success = currentSuccess,
               index >= 0,
               index < Int(success.itemCount) {
                _ = success.get(index: Int32(index))
            }
            scheduleAutoplaySelection()
        case .profileMedia:
            if let success = currentProfileMediaSuccess,
               index >= 0,
               index < Int(success.itemCount) {
                _ = success.get(index: Int32(index))
            }
        }
    }

    func collectionView(_ collectionView: UICollectionView, didEndDisplaying cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        if let itemID = dataSource.itemIdentifier(for: indexPath),
           let accessory = accessoryItemMap[itemID] {
            accessory.onVisibilityChanged?(false)
            return
        }
        if let timelineCell = cell as? TimelineUIKitCollectionViewCell {
            deferredPoolCleanupCells.add(timelineCell)
        }
        guard let host = currentAutoplayHostView,
              host.isDescendant(of: cell) else {
            return
        }
        detachAutoplayPlayer(pause: true)
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        onScrollInteractionBegan?()
        beginScrollInteraction()
    }

    private func beginScrollInteraction() {
        scrollingState.isScrolling = true
        pendingScrollAnchor = nil
        pendingEffectiveContentOffsetYAfterSnapshot = nil
        autoplaySelectionTask?.cancel()
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        restorePendingScrollAnchorIfNeeded()
        reportIsAtTop()
        onContentOffsetChanged?(effectiveContentOffsetY)
        validateCurrentAutoplayVisibility()
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            endScrollInteraction()
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        endScrollInteraction()
    }

    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
        endScrollInteraction()
    }

    private func endScrollInteraction() {
        scrollingState.isScrolling = false
        scheduleAutoplaySelection()
        scheduleDeferredPoolCleanup()
    }
}

private final class ProfileMediaCollectionViewCell: UICollectionViewCell {
    private let mediaView = StatusMediaContentUIView()
    private let placeholderView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
        contentView.clipsToBounds = true

        mediaView.translatesAutoresizingMaskIntoConstraints = false
        placeholderView.translatesAutoresizingMaskIntoConstraints = false
        placeholderView.backgroundColor = .tertiarySystemFill
        placeholderView.layer.cornerRadius = 12
        placeholderView.clipsToBounds = true
        contentView.addSubview(mediaView)
        contentView.addSubview(placeholderView)
        NSLayoutConstraint.activate([
            mediaView.topAnchor.constraint(equalTo: contentView.topAnchor),
            mediaView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            mediaView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            mediaView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            placeholderView.topAnchor.constraint(equalTo: contentView.topAnchor),
            placeholderView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            placeholderView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            placeholderView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        configurePlaceholder()
    }

    func configure(item: ProfileMedia, appearance: StatusUIKitAppearance, onTap: @escaping () -> Void) {
        placeholderView.isHidden = true
        mediaView.isHidden = false
        mediaView.configure(
            data: [item.media],
            sensitive: item.status.timelineContentPost?.sensitive ?? false,
            cornerRadius: 12,
            appearanceShowMedia: true,
            appearanceShowSensitive: appearance.showSensitiveContent,
            appearanceExpandMediaSize: true,
            appearanceLimitMediaGridToNine: true,
            appearanceMediaLayout: .grid,
            carouselLeadingPadding: 0,
            carouselTrailingPadding: 0
        )
        mediaView.onMediaClicked = { _, _ in onTap() }
    }

    func configurePlaceholder() {
        mediaView.onMediaClicked = nil
        mediaView.prepareForPoolRemoval()
        mediaView.isHidden = true
        placeholderView.isHidden = false
    }
}

private final class TimelineUIKitCollectionViewCell: UICollectionViewCell {
    var onPreferredHeightChanged: ((CGFloat, CGFloat) -> Void)?
    var cachedPreferredHeight: ((CGFloat) -> CGFloat?)?

    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private var timelineViewStorage: TimelineUIView?
    private var timelineCardStorage: AdaptiveTimelineCardUIView?
    private var placeholderCardStorage: AdaptiveTimelineCardUIView?

    // Rebuild-skip signature. When the incoming data + appearance + detail-key are
    // identical to the previous configure we short-circuit the expensive
    // `TimelineUIView.configure` → `StatusUIKitView.rebuild()` path.
    private var lastRenderHash: Int32?
    private var lastItemKey: String?
    private var lastAppearance: TimelineUIKitAppearance?
    private var lastDetailStatusKey: String?
    private var lastAiTldrEnabled: Bool?
    private var lastPreferredHeightReport: (widthKey: Int, height: CGFloat)?
    private var pendingFreshMeasurement = false
    private var usesWaterfallLayout = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        // Reset signature so a recycled cell always rebuilds for its new tenant,
        // even in the (unlikely) event that renderHash/itemKey collide.
        resetRenderSignature()
        onPreferredHeightChanged = nil
        cachedPreferredHeight = nil
        lastPreferredHeightReport = nil
        pendingFreshMeasurement = false
        usesWaterfallLayout = false
    }

    func autoplayCandidates(prefix: String) -> [TimelineVideoAutoplayCandidate] {
        guard hostedView === timelineCardStorage else {
            return []
        }
        return timelineViewStorage?.autoplayCandidates(prefix: prefix) ?? []
    }

    func performDeferredPoolCleanup() {
        guard let timelineView = timelineViewStorage else { return }
        if window == nil || hostedView !== timelineCardStorage {
            resetRenderSignature()
            timelineView.prepareForDeferredReuseCleanup()
        } else {
            timelineView.performDeferredPoolCleanup()
        }
    }

    func performLightweightPoolCleanup() {
        guard window != nil,
              hostedView === timelineCardStorage,
              let timelineView = timelineViewStorage else { return }
        timelineView.performLightweightPoolCleanup()
    }

    private func resetRenderSignature() {
        lastRenderHash = nil
        lastItemKey = nil
        lastAppearance = nil
        lastDetailStatusKey = nil
        lastAiTldrEnabled = nil
    }

    func configureTimeline(
        data: UiTimelineV2,
        index: Int,
        totalCount: Int,
        appearance: TimelineUIKitAppearance,
        detailStatusKey: MicroBlogKey?,
        aiTldrEnabled: Bool,
        isMultipleColumn: Bool,
        openURL: ((URL) -> Void)?
    ) {
        let timelineView = resolvedTimelineView()
        let timelineCard = resolvedTimelineCard()
        timelineView.onLocalHeightInvalidated = { [weak self] in
            self?.handleLocalTimelineHeightInvalidated()
        }
        // Card styling is cheap; always reapply so index/totalCount changes
        // (affecting the card's outer rounded corners) are picked up.
        timelineCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        timelineCard.isMultipleColumn = isMultipleColumn
        timelineCard.configure(index: index, totalCount: totalCount)

        let itemKey = data.itemKey ?? ""
        let detailKeyStr = detailStatusKey.map { String(describing: $0) } ?? ""
        let dataUnchanged =
            lastRenderHash == data.renderHash &&
            lastItemKey == itemKey &&
            lastAppearance == appearance &&
            lastDetailStatusKey == detailKeyStr &&
            lastAiTldrEnabled == aiTldrEnabled

        if !dataUnchanged {
            lastRenderHash = data.renderHash
            lastItemKey = itemKey
            lastAppearance = appearance
            lastDetailStatusKey = detailKeyStr
            lastAiTldrEnabled = aiTldrEnabled
            timelineView.configure(
                data: data,
                appearance: appearance.status,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                onOpenURL: openURL
            )
        } else {
            // Same render state — just refresh the click callback in case the
            // parent routed a new openURL handler through.
            timelineView.onOpenURL = openURL
        }
        setHostedView(timelineCard, usesWaterfallLayout: isMultipleColumn)
    }

    func configurePlaceholder(
        index: Int,
        totalCount: Int,
        appearance: TimelineUIKitAppearance,
        isMultipleColumn: Bool
    ) {
        let placeholderCard = resolvedPlaceholderCard()
        placeholderCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        placeholderCard.isMultipleColumn = isMultipleColumn
        placeholderCard.configure(index: index, totalCount: totalCount)
        setHostedView(placeholderCard, usesWaterfallLayout: isMultipleColumn)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
        reportPreferredHeightIfNeeded()
    }

    override func preferredLayoutAttributesFitting(_ layoutAttributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        guard !usesWaterfallLayout else {
            return layoutAttributes
        }
        guard hostedView != nil else {
            return super.preferredLayoutAttributesFitting(layoutAttributes)
        }

        let fitted = layoutAttributes.copy() as! UICollectionViewLayoutAttributes
        let width = fitted.size.width > 1 ? fitted.size.width : contentView.bounds.width
        guard width > 1, width.isFinite else {
            return super.preferredLayoutAttributesFitting(layoutAttributes)
        }

        fitted.size = CGSize(width: width, height: measuredHostedHeight(width: width))
        return fitted
    }

    func setHostedView(_ view: UIView?, usesWaterfallLayout: Bool = false) {
        self.usesWaterfallLayout = usesWaterfallLayout
        contentConfiguration = nil
        backgroundConfiguration = .clear()
        if hostedView === view {
            view?.invalidateIntrinsicContentSize()
            view?.setNeedsLayout()
            contentView.setNeedsLayout()
            setNeedsLayout()
            return
        }
        NSLayoutConstraint.deactivate(hostedConstraints)
        hostedConstraints = []
        hostedBottomConstraint = nil
        hostedView?.removeFromSuperview()
        hostedView = view

        guard let view else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(view)
        let bottomConstraint = view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bottomConstraint.priority = .init(999)
        hostedConstraints = [
            view.topAnchor.constraint(equalTo: contentView.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottomConstraint,
        ]
        hostedBottomConstraint = bottomConstraint
        NSLayoutConstraint.activate(hostedConstraints)
        lastPreferredHeightReport = nil
    }

    private func measuredHostedHeight(width: CGFloat) -> CGFloat {
        guard let hostedView else { return 0 }

        if !pendingFreshMeasurement,
           hostedView === timelineCardStorage,
           let cachedHeight = cachedPreferredHeight?(width),
           cachedHeight > 0,
           cachedHeight.isFinite {
            return cachedHeight
        }

        if hostedView === timelineCardStorage {
            let cardWrapperWidth: CGFloat = usesWaterfallLayout ? 4 : 0
            timelineViewStorage?.prepareForFitting(width: max(width - cardWrapperWidth - 32, 1))
        }

        contentView.bounds = CGRect(x: 0, y: 0, width: width, height: contentView.bounds.height)
        hostedView.bounds = CGRect(x: 0, y: 0, width: width, height: hostedView.bounds.height)
        hostedView.setNeedsLayout()

        let height = childHeight(of: hostedView, for: width)
        let preferredHeight = max(ceil(height) + 1, 1)
        if hostedView === timelineCardStorage {
            pendingFreshMeasurement = false
            onPreferredHeightChanged?(width, preferredHeight)
        }
        return preferredHeight
    }

    private func reportPreferredHeightIfNeeded() {
        guard hostedView === timelineCardStorage,
              let onPreferredHeightChanged,
              contentView.bounds.width > 1 else {
            return
        }

        let width = contentView.bounds.width
        let preferredHeight = measuredHostedHeight(width: width)
        guard abs(preferredHeight - contentView.bounds.height) > 1 else { return }

        let widthKey = Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
        if let lastPreferredHeightReport,
           lastPreferredHeightReport.widthKey == widthKey,
           abs(lastPreferredHeightReport.height - preferredHeight) < 0.5 {
            return
        }
        lastPreferredHeightReport = (widthKey, preferredHeight)
        onPreferredHeightChanged(width, preferredHeight)
    }

    private func handleLocalTimelineHeightInvalidated() {
        // Cache is keyed by item+width only; we must skip the cached lookup once
        // so the next measurement reflects the new local UI state (expanded
        // content warning, show-more, summary) before refreshing the cache.
        pendingFreshMeasurement = true
        lastPreferredHeightReport = nil
        contentView.invalidateIntrinsicContentSize()
        contentView.setNeedsLayout()
        setNeedsLayout()
    }

    private func resolvedTimelineView() -> TimelineUIView {
        if let timelineViewStorage {
            return timelineViewStorage
        }
        let view = TimelineUIView()
        timelineViewStorage = view
        return view
    }

    private func resolvedTimelineCard() -> AdaptiveTimelineCardUIView {
        if let timelineCardStorage {
            return timelineCardStorage
        }
        let card = AdaptiveTimelineCardUIView()
        card.setContent(UIView.padding(resolvedTimelineView(), insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)))
        timelineCardStorage = card
        return card
    }

    private func resolvedPlaceholderCard() -> AdaptiveTimelineCardUIView {
        if let placeholderCardStorage {
            return placeholderCardStorage
        }
        let card = makeTimelinePlaceholderCardUIView()
        placeholderCardStorage = card
        return card
    }
}

private final class TimelinePlaceholderCollectionViewCell: UICollectionViewCell {
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private var placeholderCardStorage: AdaptiveTimelineCardUIView?
    private var usesWaterfallLayout = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configurePlaceholder(index: Int, totalCount: Int, appearance: TimelineUIKitAppearance, isMultipleColumn: Bool) {
        let placeholderCard = resolvedPlaceholderCard()
        placeholderCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        placeholderCard.isMultipleColumn = isMultipleColumn
        placeholderCard.configure(index: index, totalCount: totalCount)
        setHostedView(placeholderCard, usesWaterfallLayout: isMultipleColumn)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
    }

    override func preferredLayoutAttributesFitting(_ layoutAttributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        guard !usesWaterfallLayout else {
            return layoutAttributes
        }
        return super.preferredLayoutAttributesFitting(layoutAttributes)
    }

    private func setHostedView(_ view: UIView?, usesWaterfallLayout: Bool = false) {
        self.usesWaterfallLayout = usesWaterfallLayout
        contentConfiguration = nil
        backgroundConfiguration = .clear()
        if hostedView === view {
            view?.invalidateIntrinsicContentSize()
            view?.setNeedsLayout()
            contentView.setNeedsLayout()
            setNeedsLayout()
            return
        }
        NSLayoutConstraint.deactivate(hostedConstraints)
        hostedConstraints = []
        hostedBottomConstraint = nil
        hostedView?.removeFromSuperview()
        hostedView = view

        guard let view else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(view)
        let bottomConstraint = view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bottomConstraint.priority = .init(999)
        hostedConstraints = [
            view.topAnchor.constraint(equalTo: contentView.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottomConstraint,
        ]
        hostedBottomConstraint = bottomConstraint
        NSLayoutConstraint.activate(hostedConstraints)
    }

    private func resolvedPlaceholderCard() -> AdaptiveTimelineCardUIView {
        if let placeholderCardStorage {
            return placeholderCardStorage
        }
        let card = makeTimelinePlaceholderCardUIView()
        placeholderCardStorage = card
        return card
    }
}

private final class TimelineHostedViewCell: UICollectionViewCell {
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private var usesWaterfallLayout = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        setHostedView(nil)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
    }

    override func preferredLayoutAttributesFitting(_ layoutAttributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        guard !usesWaterfallLayout else {
            return layoutAttributes
        }
        return super.preferredLayoutAttributesFitting(layoutAttributes)
    }

    func setHostedView(_ view: UIView?, usesWaterfallLayout: Bool = false) {
        self.usesWaterfallLayout = usesWaterfallLayout
        contentConfiguration = nil
        backgroundConfiguration = .clear()
        if hostedView === view {
            view?.invalidateIntrinsicContentSize()
            view?.setNeedsLayout()
            contentView.setNeedsLayout()
            setNeedsLayout()
            return
        }
        NSLayoutConstraint.deactivate(hostedConstraints)
        hostedConstraints = []
        hostedBottomConstraint = nil
        // The view may already have moved to another reusable cell. Only the
        // cell that still owns it should detach it during reuse.
        if hostedView?.superview === contentView {
            hostedView?.removeFromSuperview()
        }
        hostedView = view

        guard let view else { return }
        view.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(view)
        let bottomConstraint = view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bottomConstraint.priority = .init(999)
        hostedConstraints = [
            view.topAnchor.constraint(equalTo: contentView.topAnchor),
            view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottomConstraint,
        ]
        hostedBottomConstraint = bottomConstraint
        NSLayoutConstraint.activate(hostedConstraints)
    }
}

private final class CenteredCellContentView: UIView {
    init(content: UIView) {
        super.init(frame: .zero)
        content.translatesAutoresizingMaskIntoConstraints = false
        addSubview(content)
        NSLayoutConstraint.activate([
            content.centerXAnchor.constraint(equalTo: centerXAnchor),
            content.centerYAnchor.constraint(equalTo: centerYAnchor),
            content.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 16),
            content.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -16),
            content.topAnchor.constraint(greaterThanOrEqualTo: topAnchor, constant: 16),
            content.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor, constant: -16),
            heightAnchor.constraint(greaterThanOrEqualToConstant: 160),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }
}
