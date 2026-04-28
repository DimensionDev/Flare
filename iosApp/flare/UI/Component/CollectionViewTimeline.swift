import SwiftUI
import KotlinSharedUI
import CHTCollectionViewWaterfallLayout
import GSPlayer

// MARK: - SwiftUI Wrapper

struct CollectionViewTimelineView: UIViewControllerRepresentable {
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let topContentInset: CGFloat
    let columnCount: Int
    let accessoryItems: [CollectionViewTimelineAccessoryItem]
    let suppressInitialRefreshIndicator: Bool
    @Environment(\.appearanceSettings) private var appearanceSettings
    @Environment(\.aiConfig) private var aiConfig
    @Environment(\.networkKind) private var networkKind
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

    func makeUIViewController(context: Context) -> CollectionViewTimelineController {
        let controller = CollectionViewTimelineController(detailStatusKey: detailStatusKey)
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.appearance = TimelineUIKitAppearance(settings: appearanceSettings)
        controller.aiTldrEnabled = aiConfig.tldr
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.networkKind = networkKind
        controller.columnCount = columnCount
        controller.accessoryItems = accessoryItems
        controller.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        controller.update(data: data)
        return controller
    }

    func updateUIViewController(_ controller: CollectionViewTimelineController, context: Context) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.appearance = TimelineUIKitAppearance(settings: appearanceSettings)
        controller.aiTldrEnabled = aiConfig.tldr
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.networkKind = networkKind
        controller.columnCount = columnCount
        controller.accessoryItems = accessoryItems
        controller.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        controller.update(data: data)
    }
}

struct CollectionViewTimelineAccessoryItem {
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

final class CollectionViewTimelineController: UIViewController, UICollectionViewDelegate, UIScrollViewDelegate, CHTCollectionViewDelegateWaterfallLayout {

    // Use Int for section and String for item to avoid Sendable issues
    private static let sectionAccessories = 0
    private static let sectionMain = 1
    private static let sectionFooter = 2

    private let detailStatusKey: MicroBlogKey?
    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?

    var refreshCallback: (() async -> Void)?
    var openURL: ((URL) -> Void)?
    var suppressInitialRefreshIndicator = false
    var appearance = TimelineUIKitAppearance(settings: AppearanceSettings.companion.Default) {
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
            clearAllHeightCache()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
            updateBackgroundColors()
        }
    }
    var accessoryItems: [CollectionViewTimelineAccessoryItem] = [] {
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
            guard let currentData else { return }
            lastAppliedSignature = nil
            applySnapshot(data: currentData)
        }
    }

    var currentContentOffset: CGPoint {
        collectionView?.contentOffset ?? .zero
    }

    func setContentOffset(_ offset: CGPoint, animated: Bool) {
        guard isViewLoaded else { return }
        collectionView.setContentOffset(offset, animated: animated)
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

    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var refreshControl = UIRefreshControl()
    private var isUserRefreshing = false
    private var shouldRevealRefreshControl = false
    private var hasCompletedInitialRefreshCycle = false
    private var scrollingState = IsScrollingState()
    private var lastAppliedSignature: SnapshotSignature?
    private var lastRenderHashMap: [String: Int32] = [:]
    private let autoplayPlayerView = VideoPlayerView()
    private var autoplaySelectionTask: Task<Void, Never>?
    private var autoplayCountdownTask: Task<Void, Never>?
    private var postRefreshPoolCleanupTask: Task<Void, Never>?
    private var deferredPoolCleanupTask: Task<Void, Never>?
    private let deferredPoolCleanupCells = NSHashTable<TimelineUIKitCollectionViewCell>.weakObjects()
    private weak var currentAutoplayHostView: UIView?
    private var currentAutoplayID: String?
    private var accessoryItemMap: [String: CollectionViewTimelineAccessoryItem] = [:]
    private var pendingScrollAnchor: ScrollAnchor?
    private var isRestoringScrollAnchor = false
    private var snapshotPreparationGeneration = 0
    private var heightCachePruneGeneration = 0

    // Maps item identifier → index for timeline cells
    private var itemIndexMap: [String: Int] = [:]
    private var stableTimelineItemIDs: Set<String> = []

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
        let stableTimelineItemIDs: Set<String>
        let isRefreshing: Bool
    }

    private struct ScrollAnchor {
        let itemID: String
        let distanceFromViewportTop: CGFloat
    }

    // Item ID prefixes / constants
    private static let timelinePrefix = "t:"
    private static let placeholderPrefix = "p:"
    private static let accessoryPrefix = "a:"
    private static let emptyID = "__empty__"
    private static let errorID = "__error__"
    private static let footerLoadingID = "__fl__"
    private static let footerErrorID = "__fe__"
    private static let footerEndID = "__fend__"

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
        if let data = currentData {
            syncRefreshControl(data: data)
            applySnapshot(data: data)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        updateContentInsets()
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
            let horizontalInset = isAccessorySection || self.appearance.isPlainTimelineDisplayMode ? 0 : 16
            let itemSize = NSCollectionLayoutSize(
                widthDimension: .fractionalWidth(1),
                heightDimension: .estimated(180)
            )
            let item = NSCollectionLayoutItem(layoutSize: itemSize)
            let group = NSCollectionLayoutGroup.vertical(layoutSize: itemSize, subitems: [item])
            let section = NSCollectionLayoutSection(group: group)
            section.interGroupSpacing = 2
            section.contentInsets = NSDirectionalEdgeInsets(
                top: 0,
                leading: CGFloat(horizontalInset),
                bottom: 0,
                trailing: CGFloat(horizontalInset)
            )
            return section
        }
    }

    private func makeWaterfallLayout(columns: Int) -> UICollectionViewLayout {
        let layout = CHTCollectionViewWaterfallLayout()
        layout.columnCount = columns
        layout.minimumColumnSpacing = 12
        layout.minimumInteritemSpacing = 12
        layout.sectionInset = UIEdgeInsets(
            top: 12,
            left: layout.minimumColumnSpacing,
            bottom: 12,
            right: layout.minimumColumnSpacing
        )
        layout.itemRenderDirection = .shortestFirst
        return layout
    }

    // MARK: - Sizing (for waterfall)

    private lazy var sizingTimelineView = TimelineUIView()
    private lazy var sizingTimelineCard: AdaptiveTimelineCardUIView = {
        let card = AdaptiveTimelineCardUIView()
        card.isMultipleColumn = true
        card.setContent(UIView.padding(sizingTimelineView, insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
        return card
    }()
    private lazy var sizingPlaceholderView = TimelinePlaceholderUIView()
    private lazy var sizingPlaceholderCard: AdaptiveTimelineCardUIView = {
        let card = AdaptiveTimelineCardUIView()
        card.isMultipleColumn = true
        card.setContent(UIView.padding(sizingPlaceholderView, insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
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

    private func measuredCompressedCardHeight(_ card: UIView, width: CGFloat) -> CGFloat {
        card.bounds = CGRect(x: 0, y: 0, width: width, height: UIView.layoutFittingCompressedSize.height)
        card.setNeedsLayout()
        let size = card.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return ceil(size.height) + 1
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
        collectionView.verticalScrollIndicatorInsets.top = topContentInset
        if wasPinnedToTop {
            let topOffset = -collectionView.adjustedContentInset.top
            if abs(collectionView.contentOffset.y - topOffset) > 0.5 {
                collectionView.setContentOffset(
                    CGPoint(x: collectionView.contentOffset.x, y: topOffset),
                    animated: false
                )
            }
        }
    }

    private func updateBackgroundColors() {
        let backgroundColor: UIColor = appearance.usesCardBackground || columnCount > 1 ? .systemGroupedBackground : .systemBackground
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
            cell.setHostedView(nil)
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

    private func configureErrorCell(_ cell: TimelineHostedViewCell) {
        guard let data = currentData, case .error(let errorState) = onEnum(of: data) else { return }
        let errorView = ListErrorUIView()
        errorView.onOpenURL = openURL
        errorView.configure(error: errorState.error) {
            errorState.onRetry()
        }
        cell.setHostedView(CenteredCellContentView(content: errorView), usesWaterfallLayout: columnCount > 1)
    }

    private func configureFooterErrorCell(_ cell: TimelineHostedViewCell) {
        guard let success = currentSuccess else { return }
        if case .error(let error) = onEnum(of: success.appendState) {
            let errorView = ListErrorUIView()
            errorView.onOpenURL = openURL
            errorView.configure(error: error.error) {
                success.retry()
            }
            cell.setHostedView(
                UIView.padding(errorView, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)),
                usesWaterfallLayout: columnCount > 1
            )
        }
    }

    private func makeLoadingFooterView() -> UIView {
        let progress = UIActivityIndicatorView(style: .medium)
        progress.startAnimating()
        return UIView.padding(progress, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
    }

    private func makeTextFooterView(text: String) -> UIView {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: .footnote)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.adjustsFontForContentSizeCategory = true
        return UIView.padding(label, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
    }

    // MARK: - Refresh

    @objc private func handleRefresh() {
        isUserRefreshing = true
        Task { @MainActor in
            if let refreshCallback {
                await refreshCallback()
            }
            isUserRefreshing = false
            if let data = currentData, !pagingIsRefreshing(data) {
                refreshControl.endRefreshing()
            }
        }
    }

    private func pagingIsRefreshing(_ data: PagingState<UiTimelineV2>) -> Bool {
        switch onEnum(of: data) {
        case .loading:
            return true
        case .success(let success):
            return success.isRefreshing
        default:
            return false
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

    func update(data: PagingState<UiTimelineV2>) {
        let wasRefreshing = currentData.map(pagingIsRefreshing) ?? false
        let isRefreshing = pagingIsRefreshing(data)
        currentData = data
        if case .success(let success) = onEnum(of: data) {
            currentSuccess = success
        } else {
            currentSuccess = nil
        }

        guard isViewLoaded else { return }

        syncRefreshControl(data: data)
        applySnapshot(data: data)
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

    private func syncRefreshControl(data: PagingState<UiTimelineV2>) {
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

    private var effectiveViewportTop: CGFloat {
        collectionView.contentOffset.y + collectionView.adjustedContentInset.top
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

    private func isStableTimelineItemID(_ itemID: String) -> Bool {
        itemID.hasPrefix(Self.timelinePrefix) && stableTimelineItemIDs.contains(itemID)
    }

    private func captureScrollAnchor() -> ScrollAnchor? {
        guard isViewLoaded,
              currentSuccess != nil,
              allowsScrollAnchorRestoration,
              collectionView.bounds.height > 1 else {
            return nil
        }

        let viewportTop = effectiveViewportTop
        let viewportBottom = collectionView.contentOffset.y + collectionView.bounds.height - collectionView.adjustedContentInset.bottom
        return collectionView.indexPathsForVisibleItems
            .compactMap { indexPath -> (itemID: String, frame: CGRect)? in
                guard let itemID = dataSource.itemIdentifier(for: indexPath),
                      isStableTimelineItemID(itemID) else {
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

    private func applySnapshot(data: PagingState<UiTimelineV2>) {
        let plan = makeSnapshotPlan(data: data)
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

    private func makeSnapshotPlan(data: PagingState<UiTimelineV2>) -> SnapshotPlan {
        var newIndexMap: [String: Int] = [:]
        var newRenderHashMap: [String: Int32] = [:]
        var newStableTimelineItemIDs = Set<String>()
        let accessoryIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
        var itemIDs: [String] = []
        var footerIDs: [String] = []


        switch onEnum(of: data) {
        case .loading:
            let items = (0..<5).map { "\(Self.placeholderPrefix)\($0)" }
            itemIDs = items

        case .error:
            itemIDs = [Self.errorID]

        case .empty:
            itemIDs = [Self.emptyID]

        case .success(let success):
            let itemCount = Int(success.itemCount)
            var items: [String] = []
            items.reserveCapacity(itemCount)
            for i in 0..<itemCount {
                let peeked = success.peek(index: Int32(i))
                let itemKey = peeked?.itemKey
                let id: String
                if let itemKey, !itemKey.isEmpty {
                    id = "\(Self.timelinePrefix)\(itemKey)"
                    newStableTimelineItemIDs.insert(id)
                } else {
                    id = "\(Self.timelinePrefix)idx_\(i)"
                }
                items.append(id)
                newIndexMap[id] = i
                if let peeked {
                    newRenderHashMap[id] = peeked.renderHash
                }
            }
            itemIDs = items

            // Footer
            let footer = footerItemIDs(for: success)
            footerIDs = footer
        }

        let newSignature = SnapshotSignature(accessoryIDs: accessoryIDs, itemIDs: itemIDs, footerIDs: footerIDs)
        return SnapshotPlan(
            signature: newSignature,
            accessoryIDs: accessoryIDs,
            itemIDs: itemIDs,
            footerIDs: footerIDs,
            indexMap: newIndexMap,
            renderHashMap: newRenderHashMap,
            stableTimelineItemIDs: newStableTimelineItemIDs,
            isRefreshing: pagingIsRefreshing(data)
        )
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
        let scrollAnchor = previousSignature != nil &&
            previousSignature?.itemIDs != newSignature.itemIDs &&
            allowsScrollAnchorRestoration
            ? captureScrollAnchor()
            : nil

        itemIndexMap = plan.indexMap
        stableTimelineItemIDs = plan.stableTimelineItemIDs
        scheduleHeightCachePrune(keepingItemIDs: Set(plan.indexMap.keys))

        if previousSignature?.accessoryIDs == newSignature.accessoryIDs,
           previousSignature?.itemIDs == newSignature.itemIDs,
           previousSignature?.footerIDs == newSignature.footerIDs {
            let changedIDs = plan.itemIDs.filter { lastRenderHashMap[$0] != plan.renderHashMap[$0] }
            lastRenderHashMap = plan.renderHashMap
            reconfigureItems(changedIDs)
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        if previousSignature?.accessoryIDs == newSignature.accessoryIDs,
           previousSignature?.itemIDs == newSignature.itemIDs {
            let changedIDs = plan.itemIDs.filter { lastRenderHashMap[$0] != plan.renderHashMap[$0] }
            applyFooterSnapshot(footerIDs: plan.footerIDs, reconfigureIDs: changedIDs, isRefreshing: plan.isRefreshing)
            lastAppliedSignature = newSignature
            lastRenderHashMap = plan.renderHashMap
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        // Reconfigure only existing timeline items whose render payload changed.
        let existing = Set(dataSource.snapshot().itemIdentifiers)
        let toReconfigure = plan.itemIDs.filter {
            existing.contains($0) && lastRenderHashMap[$0] != plan.renderHashMap[$0]
        }
        if !toReconfigure.isEmpty {
            snapshot.reconfigureItems(toReconfigure)
        }
        
        let shouldAnimateDifferences =
                    !plan.isRefreshing &&
                    !refreshControl.isRefreshing &&
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
                self.validateCurrentAutoplayVisibility()
                self.scheduleAutoplaySelection()
            }
        }
        lastAppliedSignature = newSignature
        lastRenderHashMap = plan.renderHashMap
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

    private func footerItemIDs(for success: PagingStateSuccess<UiTimelineV2>) -> [String] {
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
        autoplayPlayerView.isMuted = true
        autoplayPlayerView.isAutoReplay = true
        startAutoplayCountdownUpdates()
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
        let columns = section == Self.sectionFooter ? 1 : max(columnCount, 1)
        let insets = layout.sectionInset
        let available = collectionView.bounds.width - insets.left - insets.right
        let totalSpacing = CGFloat(columns - 1) * layout.minimumColumnSpacing
        let width = max((available - totalSpacing) / CGFloat(columns), 1)

        guard let itemID = dataSource.itemIdentifier(for: indexPath) else {
            return CGSize(width: width, height: 200)
        }

        switch itemID {
        case Self.emptyID, Self.errorID:
            return CGSize(width: width, height: 240)
        case Self.footerLoadingID, Self.footerErrorID, Self.footerEndID:
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
            let height = max(measuredCompressedCardHeight(sizingPlaceholderCard, width: width), 120)
            heightCache[key] = height
            return CGSize(width: width, height: height)
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
            let contentWidth = max(width - 32, 1)
            sizingTimelineView.prepareForFitting(width: contentWidth)
            let measuredHeight: CGFloat
            if let contentHeight = sizingTimelineView.estimatedHeightForFitting(width: contentWidth) {
                measuredHeight = ceil(contentHeight + 24) + 1
            } else {
                measuredHeight = measuredCompressedCardHeight(sizingTimelineCard, width: width)
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
        section == Self.sectionFooter ? 1 : max(columnCount, 1)
    }

    // MARK: - UICollectionViewDelegate

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        if let itemID = dataSource.itemIdentifier(for: indexPath),
           let accessory = accessoryItemMap[itemID] {
            accessory.onVisibilityChanged?(true)
            return
        }
        guard let success = currentSuccess else { return }
        if let itemID = dataSource.itemIdentifier(for: indexPath),
           let index = itemIndexMap[itemID],
           index >= 0,
           index < Int(success.itemCount) {
            _ = success.get(index: Int32(index))
        }
        scheduleAutoplaySelection()
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
        scrollingState.isScrolling = true
        pendingScrollAnchor = nil
        autoplaySelectionTask?.cancel()
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        restorePendingScrollAnchorIfNeeded()
        validateCurrentAutoplayVisibility()
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            scrollingState.isScrolling = false
            scheduleAutoplaySelection()
            scheduleDeferredPoolCleanup()
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = false
        scheduleAutoplaySelection()
        scheduleDeferredPoolCleanup()
    }

    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = false
        scheduleAutoplaySelection()
        scheduleDeferredPoolCleanup()
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
        timelineViewStorage?.autoplayCandidates(prefix: prefix) ?? []
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
            timelineViewStorage?.prepareForFitting(width: max(width - 32, 1))
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
        card.setContent(UIView.padding(resolvedTimelineView(), insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
        timelineCardStorage = card
        return card
    }
}

private final class TimelinePlaceholderCollectionViewCell: UICollectionViewCell {
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private var placeholderViewStorage: TimelinePlaceholderUIView?
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

    private func resolvedPlaceholderView() -> TimelinePlaceholderUIView {
        if let placeholderViewStorage {
            return placeholderViewStorage
        }
        let view = TimelinePlaceholderUIView()
        placeholderViewStorage = view
        return view
    }

    private func resolvedPlaceholderCard() -> AdaptiveTimelineCardUIView {
        if let placeholderCardStorage {
            return placeholderCardStorage
        }
        let card = AdaptiveTimelineCardUIView()
        card.setContent(UIView.padding(resolvedPlaceholderView(), insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
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
