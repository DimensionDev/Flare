import SwiftUI
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import CHTCollectionViewWaterfallLayout

enum TimelineUIKitLayoutMetrics {
    static let horizontalInset: CGFloat = 16
    static let columnSpacing: CGFloat = 8
    static let rowSpacing: CGFloat = 2
    static let timelinePlaceholderCount = 5
}

// MARK: - SwiftUI Wrapper

struct UITimelineCollectionView: UIViewControllerRepresentable {
    private let data: PagingState<UiTimelineV2>?
    private let headerState: UiState<UiTimelineV2>?
    private let userData: PagingState<UiProfile>?
    let detailStatusKey: MicroBlogKey?
    let topContentInset: CGFloat
    let columnCount: Int
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    let suppressInitialRefreshIndicator: Bool
    // Changing a non-nil key replaces the list while retaining its scroll position.
    let contentKey: AnyHashable?
    let onIsAtTopChanged: (Bool) -> Void
    let readingState: TimelineReadingState?
    @State private var localPositions = TimelinePagePositions()
    @Environment(\.timelineAccountScope) private var accountScope
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.aiConfig) private var aiConfig
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.networkKind) private var networkKind
    @Environment(\.openURL) private var openURL
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    init(
        data: PagingState<UiTimelineV2>? = nil,
        detailStatusKey: MicroBlogKey?,
        headerState: UiState<UiTimelineV2>? = nil,
        userData: PagingState<UiProfile>? = nil,
        topContentInset: CGFloat = 0,
        columnCount: Int = 1,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = [],
        suppressInitialRefreshIndicator: Bool = false,
        contentKey: AnyHashable? = nil,
        readingState: TimelineReadingState? = nil,
        onIsAtTopChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.data = data
        self.headerState = headerState
        self.userData = userData
        self.detailStatusKey = detailStatusKey
        self.topContentInset = topContentInset
        self.columnCount = max(columnCount, 1)
        self.accessoryItems = accessoryItems
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        self.contentKey = contentKey
        self.readingState = readingState
        self.onIsAtTopChanged = onIsAtTopChanged
    }

    func makeUIViewController(context: Context) -> UITimelineCollectionViewController {
        let controller = UITimelineCollectionViewController(detailStatusKey: detailStatusKey)
        configure(controller)
        return controller
    }

    func updateUIViewController(_ controller: UITimelineCollectionViewController, context: Context) {
        configure(controller)
    }

    private func configure(_ controller: UITimelineCollectionViewController) {
        controller.setReadingState(readingState ?? localPositions.state(for: "page", scope: accountScope))
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
        if let userData {
            controller.update(userData: userData, columnCount: columnCount)
        } else {
            controller.update(data: data, columnCount: columnCount, headerState: headerState, contentKey: contentKey)
        }
    }

    static func dismantleUIViewController(_ controller: UITimelineCollectionViewController, coordinator: ()) {
        controller.saveReadingPosition()
    }
}

struct UITimelineCollectionViewAccessoryItem {
    let id: String
    let view: UIView
    let onVisibilityChanged: ((Bool) -> Void)?
    let pinnedView: UIView?

    init(id: String, view: UIView, onVisibilityChanged: ((Bool) -> Void)? = nil, pinnedView: UIView? = nil) {
        self.id = id
        self.view = view
        self.onVisibilityChanged = onVisibilityChanged
        self.pinnedView = pinnedView
    }
}

// MARK: - Controller

final class UITimelineCollectionViewController: UIViewController, UICollectionViewDelegate, UIScrollViewDelegate, CHTCollectionViewDelegateWaterfallLayout {

    // Use Int for section and String for item to avoid Sendable issues
    private static let sectionAccessories = 0
    private static let sectionMain = 1
    private static let sectionFooter = 2
    nonisolated private static let sectionHeader = 3

    private let detailStatusKey: MicroBlogKey?
    private var content = TimelineContent.timeline(nil, header: nil, key: nil)
    private var contentKind: TimelineContent.Kind { content.kind }
    private var contentKey: AnyHashable? { content.key }
    private var headerState: UiState<UiTimelineV2>? { content.header }

    private var readingState: TimelineReadingState?
    private var pendingSavedPosition: TimelineCollectionView.ReadingPosition?
    private var isSnapshotReadyForReadingPosition = false

    func setReadingState(_ state: TimelineReadingState) {
        guard readingState !== state else { return }
        saveReadingPosition()
        if let collectionView, collectionView.isProgrammaticScrolling {
            collectionView.setContentOffset(collectionView.contentOffset, animated: false)
        }
        resetInitialRefreshIndicatorSuppression()
        readingState = state
        pendingSavedPosition = state.position ?? .top
        isSnapshotReadyForReadingPosition = false
        collectionView?.resetReadingPosition()
    }

    var hasPendingReadingPosition: Bool { pendingSavedPosition != nil }
    var hasSavedReadingPosition: Bool { readingState?.position != nil }

    func saveReadingPosition() {
        guard pendingSavedPosition == nil, isViewLoaded, !currentPagingIsInitialLoading,
              let position = collectionView.captureReadingPosition() else { return }
        readingState?.position = position
    }

    private func restoreSavedPositionIfReady() {
        guard let position = pendingSavedPosition, isViewLoaded,
              isSnapshotReadyForReadingPosition,
              !currentPagingIsInitialLoading, collectionView.bounds.width > 1 else { return }
        pendingSavedPosition = nil
        // Resolve against the data already held by this page. Never page backwards
        // or load more solely to recover a bookmark whose data has been released.
        collectionView.restoreReadingPosition(position)
        collectionView.setNeedsLayout()
    }

    var refreshCallback: (() async -> Void)?
    var onIsAtTopChanged: ((Bool) -> Void)?
    var onContentOffsetChanged: ((CGFloat) -> Void)?
    var onScrollInteractionBegan: (() -> Void)?
    var openURL: ((URL) -> Void)?
    var suppressInitialRefreshIndicator = false
    var restoresScrollAnchorOnSnapshotChanges = true
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
            collectionView.prepareForLayoutChange()
            clearHeightCache()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
            updateAutoplayConfiguration()
            updateBackgroundColors()
        }
    }
    var aiTldrEnabled = false {
        didSet {
            guard oldValue != aiTldrEnabled, isViewLoaded else { return }
            clearHeightCache()
            reconfigureVisibleCells()
        }
    }
    var networkKind: NetworkKind = .cellular {
        didSet {
            guard oldValue != networkKind, isViewLoaded else { return }
            updateAutoplayConfiguration()
        }
    }
    var topContentInset: CGFloat = 0 {
        didSet {
            guard oldValue != topContentInset, isViewLoaded else { return }
            collectionView.prepareForLayoutChange()
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
    private(set) var columnCount = 1
    var accessoryItems: [UITimelineCollectionViewAccessoryItem] = [] {
        didSet {
            let oldIDs = oldValue.map { "\(Self.accessoryPrefix)\($0.id)" }
            let newIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
            guard !oldIDs.isEmpty || !newIDs.isEmpty else { return }
            for accessory in accessoryItems {
                (accessory.view as? TimelineHostedAccessoryView)?.onHeightChanged = { [weak self] in
                    guard let self, self.isViewLoaded else { return }
                    self.collectionView.prepareForLayoutChange()
                    self.collectionView.collectionViewLayout.invalidateLayout()
                }
            }
            guard isViewLoaded else { return }
            pendingReconfigureIDs.formUnion(newIDs)
            applyCurrentSnapshot()
        }
    }

    var effectiveContentOffsetY: CGFloat {
        guard isViewLoaded else { return 0 }
        return collectionView.contentOffset.y + collectionView.restingAdjustedTopInset
    }

    var maximumEffectiveContentOffsetY: CGFloat {
        guard isViewLoaded else { return 0 }
        let minimumOffsetY = -collectionView.restingAdjustedTopInset
        let maximumOffsetY = max(
            minimumOffsetY,
            collectionView.contentSize.height - collectionView.bounds.height + collectionView.adjustedContentInset.bottom
        )
        return maximumOffsetY + collectionView.restingAdjustedTopInset
    }

    var scrollDecelerationRate: UIScrollView.DecelerationRate {
        guard isViewLoaded else { return .normal }
        return collectionView.decelerationRate
    }

    func beginExternalScrollInteraction() {
        collectionView.beginExternalScrollInteraction()
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
        pendingSavedPosition = nil
        if collectionView.isPresentingRefresh { collectionView.cancelRefresh() }
        collectionView.resetReadingPosition()
        view.layoutIfNeeded()
        collectionView.layoutIfNeeded()
        applyExplicitContentOffset(
            CGPoint(x: offset.x, y: clampedContentOffsetY(offset.y)),
            animated: animated
        )
    }

    func restoreEffectiveContentOffset(_ offsetY: CGFloat, animated: Bool) {
        guard isViewLoaded else { return }
        restoreContentOffset(
            CGPoint(
                x: collectionView.contentOffset.x,
                y: offsetY - collectionView.restingAdjustedTopInset
            ),
            animated: animated
        )
    }

    func setEffectiveContentOffset(_ offsetY: CGFloat, animated: Bool) {
        pendingSavedPosition = nil
        guard isViewLoaded else { return }
        if collectionView.isPresentingRefresh { collectionView.cancelRefresh() }
        collectionView.resetReadingPosition()
        applyExplicitContentOffset(
            CGPoint(
                x: collectionView.contentOffset.x,
                y: clampedContentOffsetY(offsetY - collectionView.restingAdjustedTopInset)
            ),
            animated: animated
        )
    }

    private func applyExplicitContentOffset(_ offset: CGPoint, animated: Bool) {
        collectionView.setContentOffset(offset, animated: animated)
    }

    private var collectionView: TimelineCollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var refreshControl = UIRefreshControl()
    private var isUserRefreshing = false
    private var pendingRefreshEnd = false
    private var refreshRequestGeneration = 0
    private var pendingRefreshControlOffsetY: CGFloat?
    private var hasCompletedInitialRefreshCycle = false
    private var lastReportedIsAtTop: Bool?
    private var renderedPlan: SnapshotPlan?
    private var lastRenderHashMap: [String: Int32] { renderedPlan?.renderHashMap ?? [:] }
    private struct Input {
        let content: () -> TimelineContent
        let columns: Int
        var retainedOffset: CGFloat? = nil
    }
    private var pendingInput: Input?
    private var pendingReconfigureIDs = Set<String>()
    private var isApplyingSnapshot = false
    private var isSubmissionScheduled = false
    private var autoplay: TimelineAutoplay!
    let mediaSelections = TimelineMediaSelections()
    private var postRefreshPoolCleanupTask: Task<Void, Never>?
    private var deferredPoolCleanupTask: Task<Void, Never>?
    private let deferredPoolCleanupCells = NSHashTable<TimelineUIKitCollectionViewCell>.weakObjects()
    private weak var pinnedAccessoryView: UIView?
    private var accessoryItemMap: [String: UITimelineCollectionViewAccessoryItem] = [:]
    private var lastProfileMediaScrollAnchor: ScrollAnchor?
    private var profileMediaGeometryTransition: (anchor: ScrollAnchor, originColumnCount: Int)?
    private var pendingEffectiveContentOffsetYAfterSnapshot: CGFloat?
    private var isRestoringScrollAnchor = false


    // Maps item identifier → paging index.
    private var itemIndexMap: [String: Int] { renderedPlan?.indexMap ?? [:] }

    private struct SnapshotSignature: Equatable {
        let headerIDs: [String]
        let accessoryIDs: [String]
        let itemIDs: [String]
        let footerIDs: [String]
    }

    private struct SnapshotPlan {
        let signature: SnapshotSignature
        var headerIDs: [String] { signature.headerIDs }
        var accessoryIDs: [String] { signature.accessoryIDs }
        var itemIDs: [String] { signature.itemIDs }
        var footerIDs: [String] { signature.footerIDs }
        let indexMap: [String: Int]
        let renderHashMap: [String: Int32]
        let isRefreshing: Bool
        let isInitialLoading: Bool
    }

    private struct ScrollAnchor {
        let itemID: String
        let distanceFromViewportTop: CGFloat
    }

    // Item ID prefixes / constants
    private static let userPrefix = "u:"
    private static let timelinePrefix = "t:"
    private static let placeholderPrefix = "p:"
    private static let profileMediaPrefix = "m:"
    private static let profileMediaPlaceholderPrefix = "mp:"
    private static let accessoryPrefix = "a:"
    private static let headerTimelineID = "t:__header__"
    private static let headerPlaceholderID = "p:__header__"
    private static let headerErrorID = "__header_error__"
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
        autoplay = TimelineAutoplay(collectionView: collectionView, mediaSelections: mediaSelections) { [weak self] in
            self?.dataSource.itemIdentifier(for: $0)
        }
        updateAutoplayConfiguration()
        updateContentInsets()
        updateBackgroundColors()
        updateProfileMediaColumnCount()
        applyCurrentSnapshot()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        finishPendingRefreshIfReady()
        autoplay.setVisible(true)
        reconfigureVisibleCells()
        autoplay.reconsider()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        updateProfileMediaColumnCount()
        updateContentInsets()
        updatePinnedHeader()
        restoreSavedPositionIfReady()
        if profileMediaGeometryTransition?.originColumnCount == columnCount {
            restoreProfileMediaGeometryTransition(finalize: false)
        } else {
            rememberProfileMediaScrollAnchor()
        }
        reportIsAtTop()
        revealRefreshControlIfNeeded()
        autoplay.reconsider()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        updateContentInsets()
    }

    override func viewWillTransition(
        to size: CGSize,
        with coordinator: UIViewControllerTransitionCoordinator
    ) {
        super.viewWillTransition(to: size, with: coordinator)
        guard contentKind == .profileMedia else { return }
        if profileMediaGeometryTransition == nil,
           let anchor = captureScrollAnchor(requiringStableInteraction: false)
                ?? lastProfileMediaScrollAnchor {
            profileMediaGeometryTransition = (anchor, columnCount)
        }
        guard profileMediaGeometryTransition != nil else { return }
        coordinator.animate(alongsideTransition: nil) { [weak self] _ in
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.view.layoutIfNeeded()
                self.collectionView.layoutIfNeeded()
                self.restoreProfileMediaGeometryTransition(finalize: true)
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        saveReadingPosition()
        super.viewWillDisappear(animated)
        autoplay.setVisible(false)
        collectionView.endScrollInteraction()
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
        accessoryItemMap.values.forEach { $0.onVisibilityChanged?(false) }
    }

    deinit {
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
    }

    // MARK: - Setup

    private func setupCollectionView() {
        collectionView = TimelineCollectionView(frame: .zero, collectionViewLayout: makeWaterfallLayout(columns: columnCount))
        collectionView.preservesReadingPosition = contentKind != .profileMedia
        collectionView.readingItemID = { [weak self] indexPath in
            guard let self, let id = self.dataSource?.itemIdentifier(for: indexPath),
                  self.isDataItemID(id) else { return nil }
            return id
        }
        collectionView.readingIndexPath = { [weak self] id in
            self?.dataSource?.indexPath(for: id)
        }
        collectionView.readingItemIDs = { [weak self] in
            guard let self else { return [] }
            return self.dataSource?.snapshot().itemIdentifiers.filter(self.isDataItemID) ?? []
        }
        collectionView.readingTopInset = { [weak self] in
            guard let self else { return 0 }
            if self.extendsContentUnderTopBars {
                return min(self.collectionView.restingAdjustedTopInset,
                    self.collectionView.safeAreaInsets.top + max(self.topContentInset - self.minimumVerticalScrollDistance, 0))
            }
            return self.collectionView.restingAdjustedTopInset
        }
        collectionView.readingTopOcclusion = { [weak self] in
            guard let self, let (_, frame) = self.pinnedHeaderGeometry() else { return 0 }
            let top = self.collectionView.contentOffset.y + self.collectionView.restingAdjustedTopInset
            return max(frame.maxY - top, 0)
        }
        collectionView.isReadingLayoutReady = { [weak self] indexPath in
            self?.isReadingLayoutReady(at: indexPath) == true
        }
        collectionView.onProgrammaticScrollBegan = { [weak self] in
            guard let self else { return }
            self.beginScrollInteraction()
        }
        collectionView.onProgrammaticScrollEnded = { [weak self] in
            self?.endScrollInteraction()
        }
        collectionView.keyboardDismissMode = .interactive
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
            if let pendingInput {
                self.pendingInput = Input(content: pendingInput.content, columns: columns, retainedOffset: pendingInput.retainedOffset)
                scheduleSubmission()
            } else {
                submit(content, columns: columns)
            }
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

    private lazy var sizingPlaceholderCard: AdaptiveTimelineCardUIView = {
        let card = makeTimelinePlaceholderCardUIView()
        card.isMultipleColumn = true
        return card
    }()
    private var heightCache: [String: CGFloat] = [:]
    private var itemHeightCache = TimelineItemHeightCache()
    private var hasPendingHeightCorrections = false
    private var isHeightCorrectionFlushScheduled = false

    private func clearHeightCache(keepingItemMeasurements: Bool = false) {
        heightCache.removeAll(keepingCapacity: true)
        if !keepingItemMeasurements {
            itemHeightCache.removeAll()
            hasPendingHeightCorrections = false
        }
    }

    private func isReadingLayoutReady(at indexPath: IndexPath?) -> Bool {
        guard isSnapshotReadyForReadingPosition, !hasPendingHeightCorrections else { return false }
        guard let indexPath else { return true }
        // Only the target and already visible cells participate. Never measure the
        // offscreen feed merely to finish a rotation or a prepend.
        let paths = Set(collectionView.indexPathsForVisibleItems + [indexPath])
        for path in paths {
            guard let id = dataSource.itemIdentifier(for: path),
                  id.hasPrefix(Self.timelinePrefix) || id.hasPrefix(Self.userPrefix) else { continue }
            guard let frame = collectionView.layoutAttributesForItem(at: path)?.frame,
                  let renderHash = lastRenderHashMap[id],
                  itemHeightCache.height(for: id, geometry: heightGeometry(itemID: id, width: frame.width),
                                         renderHash: renderHash) != nil else { return false }
            if let cell = collectionView.cellForItem(at: path) as? TimelineUIKitCollectionViewCell,
               !cell.hasMeasuredHeight(for: frame.width) { return false }
        }
        return true
    }

    private func heightCacheWidthKey(for width: CGFloat) -> Int {
        Int((width * max(traitCollection.displayScale, 1)).rounded(.toNearestOrAwayFromZero))
    }

    private func heightGeometry(itemID: String, width: CGFloat) -> TimelineItemHeightCache.Geometry {
        .init(widthInPixels: heightCacheWidthKey(for: width),
              multipleColumns: columnCount > 1 && itemID != Self.headerTimelineID)
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
            identifier == Self.sectionHeader ||
            identifier == Self.sectionFooter ||
            (identifier == Self.sectionMain && mainSectionUsesFullWidth)
    }

    private var mainSectionUsesFullWidth: Bool { content.state == .empty || content.state == .error }

    private func waterfallInsets(for section: Int, layout: CHTCollectionViewWaterfallLayout) -> UIEdgeInsets {
        guard contentKind == .profileMedia else {
            if sectionIdentifier(at: section) == Self.sectionAccessories { return .zero }
            if sectionIdentifier(at: section) == Self.sectionHeader {
                let inset = max(TimelineUIKitLayoutMetrics.horizontalInset, (collectionView.bounds.width - 600) / 2)
                return UIEdgeInsets(top: 0, left: inset, bottom: 0, right: inset)
            }
            return columnCount == 1 && appearance.isPlainTimelineDisplayMode ? .zero : layout.sectionInset
        }
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
        itemHeightCache.keep(keepingItemIDs)
    }

    private func applyMeasuredHeightCorrection(
        itemID: String,
        renderHash: Int32,
        width: CGFloat,
        height: CGFloat
    ) {
        guard width > 1, height.isFinite else { return }
        let correctedHeight = max(ceil(height), 1)
        // An unchanged measured height can still replace an estimate/stale render
        // and complete a pending restore, without requiring a layout invalidation.
        if collectionView.hasReadingPosition { collectionView.setNeedsLayout() }
        guard itemHeightCache.store(correctedHeight, for: itemID,
                                    geometry: heightGeometry(itemID: itemID, width: width),
                                    renderHash: renderHash) else { return }
        hasPendingHeightCorrections = true
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
        guard isViewLoaded, hasPendingHeightCorrections else {
            hasPendingHeightCorrections = false
            return
        }

        hasPendingHeightCorrections = false
        collectionView.invalidateMeasuredHeights()
    }

    private func applyLayoutForColumnCount() {
        guard collectionView != nil else { return }
        if let layout = collectionView.collectionViewLayout as? CHTCollectionViewWaterfallLayout {
            layout.columnCount = columnCount
        } else {
            collectionView.setCollectionViewLayout(makeWaterfallLayout(columns: columnCount), animated: false)
        }
        collectionView.collectionViewLayout.invalidateLayout()
    }

    private func setupDataSource() {
        let timelineCellReg = UICollectionView.CellRegistration<TimelineUIKitCollectionViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            self.configureTimelineCell(cell, itemID: itemID)
        }
        let placeholderCellReg = UICollectionView.CellRegistration<TimelinePlaceholderCollectionViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            let indexStr = itemID.dropFirst(Self.placeholderPrefix.count)
            let index = Int(indexStr) ?? 0
            self.configurePlaceholderCell(cell, index: index, isHeader: itemID == Self.headerPlaceholderID)
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

    private func updateAutoplayConfiguration() {
        autoplay?.configure(videoAutoplay: appearance.videoAutoplay, networkKind: networkKind,
                            hasPosts: content.hasPosts, multipleColumns: columnCount > 1)
    }

    private func updateContentInsets() {
        guard collectionView != nil else { return }
        let oldAdjustedTopInset = collectionView.restingAdjustedTopInset
        let wasPinnedToTop = abs(collectionView.contentOffset.y + oldAdjustedTopInset) < 1
        let automaticTopInset = max(0, collectionView.adjustedContentInset.top - collectionView.contentInset.top)
        let desiredTopInset = topContentInset - (extendsContentUnderTopBars ? automaticTopInset : 0)
        collectionView.setTopContentInset(desiredTopInset)
        collectionView.verticalScrollIndicatorInsets.top = topScrollIndicatorInset
        if wasPinnedToTop, !collectionView.isPresentingRefresh, !refreshControl.isRefreshing {
            let topOffset = -collectionView.restingAdjustedTopInset
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
            automaticBottomInset + collectionView.restingAdjustedTopInset
        let requiredBottomInset = max(
            0,
            minimumVerticalScrollDistance - maximumOffsetWithoutBottomInset
        )
        if abs(collectionView.contentInset.bottom - requiredBottomInset) > 0.5 {
            collectionView.contentInset.bottom = requiredBottomInset
        }
    }

    private func updateBackgroundColors() {
        let backgroundColor = TimelineUIKitAppearance.backgroundColor(
            displayMode: appearance.timelineDisplayMode,
            isMultipleColumn: columnCount > 1 && contentKind != .profileMedia,
            usesGroupedBackgroundOverride: usesGroupedBackgroundOverride
        )
        view.backgroundColor = backgroundColor
        collectionView.backgroundColor = backgroundColor
    }

    // MARK: - Cell Configuration

    private func userCard(for itemID: String) -> UIView? {
        guard let index = itemIndexMap[itemID], content.items.indices.contains(index),
              let user = content.items[index]?.user else { return nil }
        let view = UserCompatUIView()
        view.configure(data: user, trailing: nil) { [weak self] in
            user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: OpenURLAction { url in
                self?.openURL?(url)
                return .handled
            })))
        }
        let card = AdaptiveTimelineCardUIView()
        card.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
        card.isMultipleColumn = columnCount > 1
        card.setContent(UIView.padding(view, insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)))
        card.configure(index: index, totalCount: content.items.count)
        return card
    }

    private func configureHostedCell(_ cell: TimelineHostedViewCell, itemID: String) {
        cell.onPreferredHeightChanged = nil
        if itemID.hasPrefix(Self.userPrefix) {
            if let index = itemIndexMap[itemID], content.items.indices.contains(index),
               let user = content.items[index]?.user {
                let renderHash = Int32(truncatingIfNeeded: user.hash)
                cell.onPreferredHeightChanged = { [weak self] width, height in
                    self?.applyMeasuredHeightCorrection(itemID: itemID, renderHash: renderHash, width: width, height: height)
                }
            }
            cell.setHostedView(userCard(for: itemID))
        } else if itemID.hasPrefix(Self.accessoryPrefix) {
            cell.setHostedView(accessoryItemMap[itemID]?.view)
        } else if itemID == Self.emptyID {
            cell.setHostedView(CenteredCellContentView(content: ListEmptyUIView()))
        } else if itemID == Self.errorID {
            configureErrorCell(cell)
        } else if itemID == Self.headerErrorID,
                  let headerState, case .error(let error) = onEnum(of: headerState) {
            let errorView = ListErrorUIView()
            errorView.onOpenURL = openURL
            errorView.configure(error: error.throwable, onRetry: {})
            cell.setHostedView(CenteredCellContentView(content: errorView))
        } else if itemID == Self.footerLoadingID {
            cell.setHostedView(makeLoadingFooterView())
        } else if itemID == Self.footerErrorID {
            configureFooterErrorCell(cell)
        } else if itemID == Self.footerEndID {
            cell.setHostedView(makeTextFooterView(text: String(localized: "end_of_list")))
        }
    }

    private var headerItem: UiTimelineV2? {
        guard let headerState, case .success(let success) = onEnum(of: headerState) else { return nil }
        return success.data
    }

    private func timelineItem(for itemID: String) -> (data: UiTimelineV2, index: Int, totalCount: Int)? {
        if itemID == Self.headerTimelineID {
            return headerItem.map { ($0, 0, 1) }
        }
        guard let index = itemIndexMap[itemID], content.items.indices.contains(index),
              let item = content.items[index]?.post else { return nil }
        return (item, index, content.items.count)
    }

    private func configureTimelineCell(_ cell: TimelineUIKitCollectionViewCell, itemID: String) {
        if let row = timelineItem(for: itemID) {
            let item = row.data
            cell.cachedPreferredHeight = { [weak self] width in
                guard let self else { return nil }
                return self.itemHeightCache.height(for: itemID,
                    geometry: self.heightGeometry(itemID: itemID, width: width), renderHash: item.renderHash)
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
                index: row.index,
                totalCount: row.totalCount,
                appearance: appearance,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                isMultipleColumn: columnCount > 1 && itemID != Self.headerTimelineID,
                openURL: openURL
            )
        } else {
            cell.cachedPreferredHeight = nil
            cell.onPreferredHeightChanged = nil
            cell.configurePlaceholder(
                index: itemIndexMap[itemID] ?? 0,
                totalCount: max(content.items.count, 1),
                appearance: appearance,
                isMultipleColumn: columnCount > 1
            )
        }
    }

    private func configurePlaceholderCell(_ cell: TimelinePlaceholderCollectionViewCell, index: Int, isHeader: Bool) {
        let totalCount: Int
        if isHeader {
            totalCount = 1
        } else if !content.items.isEmpty {
            totalCount = content.items.count
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
              content.items.indices.contains(index),
              let item = content.items[index]?.media else {
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
        guard let failure = content.failure else { return }
        errorView.configure(error: failure.error, onRetry: failure.retry)
        cell.setHostedView(CenteredCellContentView(content: errorView))
    }

    private func configureFooterErrorCell(_ cell: TimelineHostedViewCell) {
        let errorView = ListErrorUIView()
        errorView.onOpenURL = openURL
        guard let failure = content.appendFailure else { return }
        errorView.configure(error: failure.error, onRetry: failure.retry)
        cell.setHostedView(
            UIView.padding(errorView, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
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
        if collectionView.preservesReadingPosition {
            collectionView.beginRefreshing(revealingIndicator: false)
        }
        let generation = refreshRequestGeneration
        Task { @MainActor [refreshCallback] in
            guard generation == refreshRequestGeneration else { return }
            if let refreshCallback {
                await refreshCallback()
            }
            guard generation == refreshRequestGeneration else { return }
            isUserRefreshing = false
            if !currentPagingIsRefreshing {
                if collectionView.preservesReadingPosition {
                    pendingRefreshEnd = true
                    finishPendingRefreshIfReady()
                } else {
                    refreshControl.endRefreshing()
                }
            }
        }
    }

    private var currentPagingIsRefreshing: Bool { content.isRefreshing }
    private var currentPagingIsInitialLoading: Bool { content.isInitialLoading }

    func resetInitialRefreshIndicatorSuppression() {
        refreshRequestGeneration += 1
        pendingRefreshEnd = false
        isUserRefreshing = false
        collectionView?.cancelRefresh()
        hasCompletedInitialRefreshCycle = false
        pendingRefreshControlOffsetY = nil
    }

    // MARK: - State Update

    func update(
        data: PagingState<UiTimelineV2>?,
        columnCount: Int,
        headerState: UiState<UiTimelineV2>? = nil,
        contentKey: AnyHashable? = nil
    ) {
        let switchedContent = self.contentKey != nil && contentKey != nil && self.contentKey != contentKey
        let retainedOffset = switchedContent && isViewLoaded ? max(effectiveContentOffsetY, 0) : nil
        enqueue(columns: columnCount, isRefreshing: data?.isRefreshing_ == true, retainedOffset: retainedOffset) {
            .timeline(data, header: headerState, key: contentKey)
        }
    }

    func update(profileMediaData data: PagingState<ProfileMedia>) {
        enqueue(columns: resolvedProfileMediaColumnCount(), isRefreshing: data.isRefreshing_) { .profileMedia(data) }
    }

    func update(userData data: PagingState<UiProfile>, columnCount: Int) {
        enqueue(columns: columnCount, isRefreshing: data.isRefreshing_) { .users(data) }
    }

    func submit(_ input: TimelineContent, columns: Int) {
        enqueue(columns: columns, isRefreshing: input.isRefreshing) { input }
    }

    private func enqueue(columns: Int, isRefreshing: Bool, retainedOffset: CGFloat? = nil, content: @escaping () -> TimelineContent) {
        pendingInput = Input(content: content, columns: max(columns, 1), retainedOffset: retainedOffset)
        guard isViewLoaded else {
            columnCount = max(columns, 1)
            return
        }
        // Refresh begin is a presentation event, not a snapshot. A fast refresh
        // must not disappear when its intermediate data input is coalesced away.
        // Ending still waits for the resulting snapshot and its measurements.
        if isRefreshing { syncRefreshControl(isRefreshing: true) }
        scheduleSubmission()
    }

    private func applyCurrentSnapshot() {
        if pendingInput == nil {
            let current = content
            pendingInput = Input(content: { current }, columns: columnCount)
        }
        scheduleSubmission()
    }

    private func scheduleSubmission() {
        guard isViewLoaded, !isApplyingSnapshot, !isSubmissionScheduled else { return }
        isSubmissionScheduled = true
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.isSubmissionScheduled = false
            self.applyPendingInput()
        }
    }

    /// All data, appearance, accessory and layout changes reach diffable here.
    /// The source, index map and render hashes are published before UIKit reads
    /// any cells. Another input can only be installed after this apply completes.
    private func applyPendingInput() {
        guard !isApplyingSnapshot else { return }
        guard pendingInput != nil || !pendingReconfigureIDs.isEmpty else { return }
        let input = (content: pendingInput?.content() ?? content, columns: pendingInput?.columns ?? columnCount,
                     retainedOffset: pendingInput?.retainedOffset)
        pendingInput = nil
        isApplyingSnapshot = true
        isSnapshotReadyForReadingPosition = false
        let previousPlan = renderedPlan
        let previousKind = contentKind
        let kindChanged = previousKind != input.content.kind
        let columnsChanged = columnCount != input.columns
        let switchedContent = contentKey != nil && input.content.key != nil && contentKey != input.content.key
        let wasRefreshing = currentPagingIsRefreshing
        let restoringState = readingState

        if switchedContent {
            let offsetY = input.retainedOffset ?? max(effectiveContentOffsetY, 0)
            collectionView.resetReadingPosition()
            minimumVerticalScrollDistance = offsetY
            pendingEffectiveContentOffsetYAfterSnapshot = offsetY
            resetInitialRefreshIndicatorSuppression()
        } else if contentKey != nil, minimumVerticalScrollDistance > 0,
                  pendingEffectiveContentOffsetYAfterSnapshot == nil, allowsScrollAnchorRestoration {
            pendingEffectiveContentOffsetYAfterSnapshot = max(effectiveContentOffsetY, 0)
        }
        if columnsChanged, !kindChanged, pendingEffectiveContentOffsetYAfterSnapshot == nil {
            collectionView.prepareForGeometryChange()
        }
        // Profile media retains its existing numeric/layout behavior. Other lists
        // have exactly one bookmark owner: TimelineCollectionView.
        let mediaAnchor = previousKind == .profileMedia && !kindChanged &&
            restoresScrollAnchorOnSnapshotChanges && allowsScrollAnchorRestoration
            ? captureScrollAnchor() ?? lastProfileMediaScrollAnchor : nil

        content = input.content
        columnCount = input.columns
        if kindChanged {
            collectionView.preservesReadingPosition = contentKind != .profileMedia
            collectionView.resetReadingPosition()
            lastProfileMediaScrollAnchor = nil
            profileMediaGeometryTransition = nil
            autoplay.stop()
        }
        let plan = makeCurrentSnapshotPlan()
        let structureChanged = previousPlan?.signature != plan.signature
        if structureChanged, previousPlan != nil, pendingSavedPosition == nil,
           pendingEffectiveContentOffsetYAfterSnapshot == nil, restoresScrollAnchorOnSnapshotChanges {
            collectionView.prepareForSnapshotChange()
        }
        if plan.isInitialLoading, previousPlan != nil, pendingSavedPosition == nil,
           pendingEffectiveContentOffsetYAfterSnapshot == nil, restoresScrollAnchorOnSnapshotChanges {
            pendingSavedPosition = collectionView.captureReadingPosition()
        }
        let keepsReadingPosition = previousPlan != nil && !kindChanged && !switchedContent &&
            pendingSavedPosition == nil && pendingEffectiveContentOffsetYAfterSnapshot == nil &&
            restoresScrollAnchorOnSnapshotChanges
        let survivingIDs = keepsReadingPosition ? Set(plan.headerIDs + plan.accessoryIDs + plan.itemIDs) : []
        collectionView.performUpdatesPreservingReadingPosition(keepingItemIDs: survivingIDs) {
            if columnsChanged || kindChanged {
                clearHeightCache(keepingItemMeasurements: !kindChanged && contentKind != .profileMedia)
            }
            if previousPlan?.signature.itemIDs != plan.itemIDs || previousPlan?.signature.headerIDs != plan.headerIDs {
                pruneHeightCache(keepingItemIDs: Set(plan.indexMap.keys).union(plan.headerIDs))
            }

            let existing = Set(dataSource.snapshot().itemIdentifiers)
            if columnsChanged || kindChanged {
                pendingReconfigureIDs.formUnion(existing)
            }
            // Error cells carry retry callbacks for the current paging source, even
            // when their stable identifiers have not changed.
            pendingReconfigureIDs.formUnion([Self.errorID, Self.footerErrorID, Self.headerErrorID])
            let changedIDs = (plan.headerIDs + plan.accessoryIDs + plan.itemIDs + plan.footerIDs).filter {
                existing.contains($0) && (pendingReconfigureIDs.contains($0) ||
                    previousPlan?.renderHashMap[$0] != plan.renderHashMap[$0])
            }
            pendingReconfigureIDs.removeAll()
            renderedPlan = plan
            accessoryItemMap = Dictionary(uniqueKeysWithValues: accessoryItems.map { ("\(Self.accessoryPrefix)\($0.id)", $0) })
            if columnsChanged || kindChanged {
                applyLayoutForColumnCount()
                updateBackgroundColors()
            }
            syncRefreshControl(isRefreshing: plan.isRefreshing)

            let completion = { [weak self] in
                guard let self else { return }
                if self.readingState === restoringState {
                    if let mediaAnchor { self.restoreScrollAnchorIfNeeded(mediaAnchor) }
                    self.restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
                }
                self.accessVisiblePagingItems()
                self.updateAutoplayConfiguration()
                if wasRefreshing && !plan.isRefreshing { self.schedulePostRefreshPoolCleanup() }
                self.isApplyingSnapshot = false
                if self.pendingInput != nil || !self.pendingReconfigureIDs.isEmpty { self.scheduleSubmission() }
            }
            guard structureChanged || !changedIDs.isEmpty else {
                completion()
                return
            }
            // No full snapshot construction for no-op/like-only updates. Footer and
            // column changes use the same completion and readiness rules as all others.
            var snapshot = structureChanged ? Self.makeSnapshot(from: plan) : dataSource.snapshot()
            snapshot.reconfigureItems(changedIDs)
            // Placeholder animations would delay queued content until their completion.
            let animate = structureChanged && !plan.isInitialLoading && !columnsChanged && !kindChanged &&
                !plan.isRefreshing && !refreshControl.isRefreshing &&
                pendingEffectiveContentOffsetYAfterSnapshot == nil && mediaAnchor == nil &&
                !collectionView.hasReadingPosition && allowsScrollAnchorRestoration
            dataSource.apply(snapshot, animatingDifferences: animate, completion: completion)
            restorePendingContentOffsetIfNeeded(finalize: false)
        }
    }

    private func syncRefreshControl(isRefreshing: Bool) {
        // Loading and an unbound data source are not completed refresh cycles.
        if !isRefreshing && content.state != .unbound && !currentPagingIsInitialLoading {
            hasCompletedInitialRefreshCycle = true
        }

        let shouldSuppressInitialRefreshIndicator =
            suppressInitialRefreshIndicator &&
            !hasCompletedInitialRefreshCycle &&
            !isUserRefreshing

        if isRefreshing {
            guard !shouldSuppressInitialRefreshIndicator else {
                pendingRefreshControlOffsetY = nil
                pendingRefreshEnd = false
                if collectionView.preservesReadingPosition {
                    collectionView.endRefreshing()
                } else if refreshControl.isRefreshing {
                    refreshControl.endRefreshing()
                }
                return
            }
            if collectionView.preservesReadingPosition {
                pendingRefreshEnd = false
                collectionView.beginRefreshing(
                    revealingIndicator: !isUserRefreshing && pendingSavedPosition?.itemID == nil
                )
                return
            }
            if !refreshControl.isRefreshing {
                // Capture the resting inset before UIKit adds space for the refresh control.
                // Reading it after beginRefreshing() can count the indicator height twice.
                pendingRefreshControlOffsetY = isUserRefreshing
                    ? nil
                    : -(collectionView.adjustedContentInset.top + max(refreshControl.bounds.height, 60))
                refreshControl.beginRefreshing()
                revealRefreshControlIfNeeded()
            }
        } else if !isUserRefreshing {
            pendingRefreshControlOffsetY = nil
            if collectionView.preservesReadingPosition {
                // The state can finish before its prepared snapshot reaches UIKit.
                pendingRefreshEnd = refreshControl.isRefreshing || collectionView.isPresentingRefresh
                return
            }
            if refreshControl.isRefreshing {
                refreshControl.endRefreshing()
            }
        }
    }

    private func finishPendingRefreshIfReady() {
        guard pendingRefreshEnd, isSnapshotReadyForReadingPosition, !isUserRefreshing,
              allowsScrollAnchorRestoration else { return }
        pendingRefreshEnd = false
        collectionView.endRefreshing()
    }

    private func revealRefreshControlIfNeeded() {
        guard let targetOffsetY = pendingRefreshControlOffsetY,
              refreshControl.isRefreshing else { return }

        // Consume the request before scrolling, which can trigger another layout pass.
        pendingRefreshControlOffsetY = nil
        guard collectionView.contentOffset.y > targetOffsetY else { return }

        collectionView.resetReadingPosition()
        collectionView.setContentOffset(
            CGPoint(x: collectionView.contentOffset.x, y: targetOffsetY),
            animated: true
        )
    }

    private func pinnedHeaderGeometry() -> (UIView, CGRect)? {
        guard isViewLoaded, collectionView != nil, dataSource != nil else { return nil }
        let top = collectionView.contentOffset.y + collectionView.restingAdjustedTopInset
        let titles = (renderedPlan?.accessoryIDs ?? []).compactMap { id -> (UIView, CGRect)? in
            guard let view = accessoryItemMap[id]?.pinnedView,
                  let path = dataSource.indexPath(for: id),
                  let frame = collectionView.layoutAttributesForItem(at: path)?.frame else { return nil }
            return (view, frame)
        }
        guard let index = titles.lastIndex(where: { $0.1.minY <= top }) else { return nil }
        let (view, frame) = titles[index]
        let nextTop = titles.indices.contains(index + 1) ? titles[index + 1].1.minY : .greatestFiniteMagnitude
        return (view, CGRect(x: frame.minX, y: min(top, nextTop - frame.height), width: frame.width, height: frame.height))
    }

    private func updatePinnedHeader() {
        guard let (view, frame) = pinnedHeaderGeometry() else {
            pinnedAccessoryView?.removeFromSuperview()
            pinnedAccessoryView = nil
            return
        }
        if pinnedAccessoryView !== view {
            pinnedAccessoryView?.removeFromSuperview()
            pinnedAccessoryView = view
            collectionView.addSubview(view)
        }
        view.frame = frame
        collectionView.bringSubviewToFront(view)
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
            !collectionView.isScrollInteractionActive
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
        itemID.hasPrefix(Self.timelinePrefix) || itemID.hasPrefix(Self.userPrefix) || itemID.hasPrefix(Self.profileMediaPrefix) || itemID.hasPrefix(Self.accessoryPrefix)
    }

    private func captureScrollAnchor(requiringStableInteraction: Bool = true) -> ScrollAnchor? {
        guard isViewLoaded,
              content.state == .loaded || headerItem != nil,
              !requiringStableInteraction || allowsScrollAnchorRestoration,
              collectionView.bounds.height > 1 else {
            return nil
        }

        let viewportTop = collectionView.contentOffset.y
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
        guard !isRestoringScrollAnchor,
              let anchor,
              isViewLoaded,
              allowsScrollAnchorRestoration,
              let indexPath = dataSource.indexPath(for: anchor.itemID) else {
            return false
        }

        // Layout can synchronously trigger scrollViewDidScroll and another restoration.
        isRestoringScrollAnchor = true
        defer { isRestoringScrollAnchor = false }
        view.layoutIfNeeded()
        collectionView.layoutIfNeeded()

        guard let attributes = collectionView.layoutAttributesForItem(at: indexPath) else {
            return false
        }

        let targetOffsetY = attributes.frame.minY - anchor.distanceFromViewportTop
        let targetOffset = CGPoint(x: collectionView.contentOffset.x, y: clampedContentOffsetY(targetOffsetY))
        if abs(collectionView.contentOffset.y - targetOffset.y) > 0.5 {
            collectionView.setContentOffset(targetOffset, animated: false)
        }
        return true
    }

    private func restorePendingContentOffsetIfNeeded(finalize: Bool) {
        if finalize {
            isSnapshotReadyForReadingPosition = true
            if collectionView.hasReadingPosition { collectionView.setNeedsLayout() }
        }
        defer { if finalize { finishPendingRefreshIfReady() } }
        restoreSavedPositionIfReady()
        if minimumVerticalScrollDistance > 0 {
            collectionView.layoutIfNeeded()
            updateMinimumScrollableBottomInset()
        }
        guard let offsetY = pendingEffectiveContentOffsetYAfterSnapshot else { return }
        restoreEffectiveContentOffset(offsetY, animated: false)
        if finalize {
            pendingEffectiveContentOffsetYAfterSnapshot = nil
        }
        collectionView.layer.removeAllAnimations()
    }

    private func rememberProfileMediaScrollAnchor() {
        guard contentKind == .profileMedia,
              let anchor = captureScrollAnchor(requiringStableInteraction: false) else {
            return
        }
        lastProfileMediaScrollAnchor = anchor
    }

    private func restoreProfileMediaGeometryTransition(finalize: Bool) {
        guard let transition = profileMediaGeometryTransition,
              restoreScrollAnchorIfNeeded(transition.anchor) else {
            return
        }
        collectionView.layer.removeAllAnimations()
        rememberProfileMediaScrollAnchor()
        if finalize, columnCount == transition.originColumnCount {
            profileMediaGeometryTransition = nil
        }
    }

    private func makeCurrentSnapshotPlan() -> SnapshotPlan {
        var indexMap: [String: Int] = [:]
        var renderHashMap: [String: Int32] = [:]
        var headerIDs: [String] = []
        let accessoryIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
        var itemIDs: [String] = []
        let footerIDs: [String]
        let placeholderPrefix = contentKind == .profileMedia ? Self.profileMediaPlaceholderPrefix : Self.placeholderPrefix

        if let headerState {
            switch onEnum(of: headerState) {
            case .success(let success):
                headerIDs = [Self.headerTimelineID]
                renderHashMap[Self.headerTimelineID] = success.data.renderHash
            case .loading: headerIDs = [Self.headerPlaceholderID]
            case .error(let error):
                headerIDs = [Self.headerErrorID]
                renderHashMap[Self.headerErrorID] = Int32(truncatingIfNeeded: error.throwable.hash)
            }
        }
        switch content.state {
        case .unbound: break
        case .loading:
            let count = loadingPlaceholderCount(
                minimum: contentKind == .timeline ? TimelineUIKitLayoutMetrics.timelinePlaceholderCount : 8,
                columnCount: columnCount,
                estimatedPlaceholderHeight: contentKind == .profileMedia ? profileMediaPlaceholderHeight(columnCount: columnCount) : 120
            )
            itemIDs = (0..<count).map { "\(placeholderPrefix)\($0)" }
        case .error: itemIDs = [Self.errorID]
        case .empty: itemIDs = [Self.emptyID]
        case .loaded:
            itemIDs.reserveCapacity(content.items.count)
            for (index, item) in content.items.enumerated() {
                let id = item?.id ?? "\(placeholderPrefix)\(index)"
                // Page overlap can repeat an ID. Keep its first occurrence and
                // paging index together; UIKit requires unique identifiers.
                guard indexMap[id] == nil else { continue }
                itemIDs.append(id)
                indexMap[id] = index
                renderHashMap[id] = item?.renderHash
            }
        }
        switch content.footer {
        case .none: footerIDs = []
        case .loading: footerIDs = [Self.footerLoadingID]
        case .error: footerIDs = [Self.footerErrorID]
        case .end: footerIDs = [Self.footerEndID]
        }
        return SnapshotPlan(
            signature: SnapshotSignature(headerIDs: headerIDs, accessoryIDs: accessoryIDs, itemIDs: itemIDs, footerIDs: footerIDs),
            indexMap: indexMap,
            renderHashMap: renderHashMap,
            isRefreshing: content.isRefreshing,
            isInitialLoading: content.isInitialLoading
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

    private static func makeSnapshot(from plan: SnapshotPlan) -> NSDiffableDataSourceSnapshot<Int, String> {
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        if !plan.headerIDs.isEmpty {
            snapshot.appendSections([Self.sectionHeader])
            snapshot.appendItems(plan.headerIDs, toSection: Self.sectionHeader)
        }
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

    private func reconfigureItems(_ itemIDs: [String]) {
        guard !itemIDs.isEmpty else { return }
        pendingReconfigureIDs.formUnion(itemIDs)
        scheduleSubmission()
    }

    private func reconfigureVisibleCells() {
        let visibleIDs = collectionView.indexPathsForVisibleItems.compactMap {
            dataSource.itemIdentifier(for: $0)
        }
        reconfigureItems(visibleIDs)
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
              !collectionView.isScrollInteractionActive else {
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
              !collectionView.isScrollInteractionActive else {
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
        // Match the pixel-aligned cell width used when storing measured heights.
        let width = max(layout.itemWidth(inSection: section), 1)

        guard let itemID = dataSource.itemIdentifier(for: indexPath) else {
            return CGSize(width: width, height: 200)
        }

        if itemID.hasPrefix(Self.userPrefix) {
            return CGSize(width: width, height: itemHeightCache.height(for: itemID,
                geometry: heightGeometry(itemID: itemID, width: width)) ?? 96)
        }

        if itemID.hasPrefix(Self.accessoryPrefix),
           let accessory = accessoryItemMap[itemID] {
            // Profile tabs use fractional heights; rounding would shift their position.
            let height = max(
                measuredCompressedCardHeight(accessory.view, width: width, heightPadding: 0),
                1
            )
            return CGSize(width: width, height: height)
        }

        switch itemID {
        case Self.emptyID, Self.errorID, Self.headerErrorID:
            return CGSize(width: width, height: 240)
        case Self.footerLoadingID,
             Self.footerErrorID,
             Self.footerEndID:
            return CGSize(width: width, height: 60)
        default:
            break
        }

        if itemID.hasPrefix(Self.placeholderPrefix) {
            let key = "__placeholder__:\(columnCount > 1):\(heightCacheWidthKey(for: width))"
            if let cached = heightCache[key] { return CGSize(width: width, height: cached) }
            let totalCount = max(content.items.count, 5)
            sizingPlaceholderCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
            sizingPlaceholderCard.isMultipleColumn = columnCount > 1
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
           content.items.indices.contains(index),
           let item = content.items[index]?.media {
            let rawRatio = item.media.aspectRatio ?? 1
            let ratio = rawRatio.isFinite && rawRatio > 0
                ? max(9.0 / 21.0, rawRatio)
                : 1
            return CGSize(width: width, height: max(ceil(width / ratio), 1))
        }

        if itemID.hasPrefix(Self.timelinePrefix),
           timelineItem(for: itemID) != nil {
            // A render-only update keeps its previous measured geometry while the
            // visible cell validates the new payload. Offscreen rows stay cheap.
            return CGSize(width: width, height: itemHeightCache.height(for: itemID,
                geometry: heightGeometry(itemID: itemID, width: width)) ?? 240)
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
        guard contentKind == .profileMedia else { return columnCount == 1 ? TimelineUIKitLayoutMetrics.rowSpacing : 0 }
        switch sectionIdentifier(at: section) {
        case Self.sectionAccessories: return 2
        case Self.sectionMain: return 8
        default: return 0
        }
    }

    // MARK: - UICollectionViewDelegate

    private func accessVisiblePagingItems() {
        for path in collectionView.indexPathsForVisibleItems {
            guard let id = dataSource.itemIdentifier(for: path), let index = itemIndexMap[id] else { continue }
            content.access(index)
        }
    }

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        guard let itemID = dataSource.itemIdentifier(for: indexPath) else { return }
        if let accessory = accessoryItemMap[itemID] {
            accessory.onVisibilityChanged?(true)
        } else {
            if let index = itemIndexMap[itemID] { content.access(index) }
            autoplay.reconsider()
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
        autoplay.didEndDisplaying(cell)
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        collectionView.endProgrammaticScrolling()
        onScrollInteractionBegan?()
        beginScrollInteraction()
    }

    private func beginScrollInteraction() {
        pendingInput?.retainedOffset = nil
        pendingSavedPosition = nil
        collectionView.interruptRefreshForScrolling()
        autoplay.scrollBegan()
        if contentKind == .profileMedia {
            profileMediaGeometryTransition = nil
        }
        pendingEffectiveContentOffsetYAfterSnapshot = nil
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        updatePinnedHeader()
        if contentKey != nil, collectionView.isScrollInteractionActive {
            let requiredDistance = min(minimumVerticalScrollDistance, max(effectiveContentOffsetY, 0))
            if minimumVerticalScrollDistance - requiredDistance > 0.5 {
                minimumVerticalScrollDistance = requiredDistance
            }
        }
        if allowsScrollAnchorRestoration {
            rememberProfileMediaScrollAnchor()
        }
        reportIsAtTop()
        onContentOffsetChanged?(effectiveContentOffsetY)
        autoplay.didScroll()
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

    func scrollViewShouldScrollToTop(_ scrollView: UIScrollView) -> Bool {
        if abs(scrollView.contentOffset.y + scrollView.adjustedContentInset.top) > 0.5 {
            collectionView.beginProgrammaticScrolling()
        } else {
            endScrollInteraction()
        }
        return true
    }

    func scrollViewDidScrollToTop(_ scrollView: UIScrollView) {
        endScrollInteraction()
    }

    private func endScrollInteraction() {
        collectionView.endScrollInteraction()
        finishPendingRefreshIfReady()
        rememberProfileMediaScrollAnchor()
        autoplay.reconsider()
        scheduleDeferredPoolCleanup()
        saveReadingPosition()
    }
}
