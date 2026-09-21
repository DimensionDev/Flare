import SwiftUI
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import CHTCollectionViewWaterfallLayout
import AVFoundation
import Combine

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
    let readingKey: String?
    @Environment(\.timelineScrollPositions) private var scrollPositions
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
        readingKey: String? = nil,
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
        self.readingKey = readingKey
        self.onIsAtTopChanged = onIsAtTopChanged
    }

    func makeUIViewController(context: Context) -> UITimelineCollectionViewController {
        let controller = UITimelineCollectionViewController(detailStatusKey: detailStatusKey)
        controller.setReadingContext(key: readingKey.map { accountScope + ":" + $0 }, store: scrollPositions)
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
        return controller
    }

    func updateUIViewController(_ controller: UITimelineCollectionViewController, context: Context) {
        controller.setReadingContext(key: readingKey.map { accountScope + ":" + $0 }, store: scrollPositions)
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

    private enum ContentKind: Equatable {
        case timeline
        case profileMedia
        case users
    }

    private let detailStatusKey: MicroBlogKey?
    private var contentKind = ContentKind.timeline
    private var contentKey: AnyHashable?
    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?
    private var headerState: UiState<UiTimelineV2>?
    private var currentUserData: PagingState<UiProfile>?
    private var currentUserSuccess: PagingStateSuccess<UiProfile>?
    private var currentProfileMediaData: PagingState<ProfileMedia>?
    private var currentProfileMediaSuccess: PagingStateSuccess<ProfileMedia>?
    private var readingKey: String?
    private var scrollPositions: TimelineScrollPositionStore?
    private var pendingSavedPosition: TimelineCollectionView.ReadingPosition?
    private var lastRestorationLoadCount: Int?
    private var isSnapshotReadyForReadingPosition = false

    func setReadingContext(key: String?, store: TimelineScrollPositionStore?) {
        guard readingKey != key || scrollPositions !== store else { return }
        saveReadingPosition()
        readingKey = key
        scrollPositions = store
        pendingSavedPosition = key.map { store?[$0] ?? .top }
        lastRestorationLoadCount = nil
        collectionView?.resetReadingPosition()
    }

    var hasSavedReadingPosition: Bool {
        guard let readingKey else { return false }
        return scrollPositions?[readingKey] != nil
    }

    func saveReadingPosition() {
        guard let readingKey, pendingSavedPosition == nil, isViewLoaded,
              !currentPagingIsInitialLoading,
              let position = collectionView.captureReadingPosition() else { return }
        scrollPositions?[readingKey] = position
    }

    private func restoreSavedPositionIfReady() {
        guard let position = pendingSavedPosition, isViewLoaded,
              isSnapshotReadyForReadingPosition,
              !currentPagingIsInitialLoading, collectionView.bounds.width > 1 else { return }
        if case .item(let id, _, let oldOrder) = position,
           (id.hasPrefix(Self.timelinePrefix) || id.hasPrefix(Self.userPrefix)),
           dataSource.indexPath(for: id) == nil {
            let oldIndex = oldOrder.firstIndex(of: id) ?? 0
            let currentIDs = Set(dataSource.snapshot().itemIdentifiers)
            let hasFollowingItem = oldOrder.dropFirst(oldIndex + 1).contains { currentIDs.contains($0) }
            if !hasFollowingItem && (loadMoreForRestoration(currentSuccess) || loadMoreForRestoration(currentUserSuccess)) {
                return
            }
        }
        pendingSavedPosition = nil
        collectionView.restoreReadingPosition(position)
        collectionView.layoutIfNeeded()
    }

    private func loadMoreForRestoration<Item: AnyObject>(_ success: PagingStateSuccess<Item>?) -> Bool {
        guard let success, success.itemCount > 0 else { return false }
        switch onEnum(of: success.appendState) {
        case .loading: return true
        case .notLoading(let state) where !state.endOfPaginationReached:
            if lastRestorationLoadCount != Int(success.itemCount) {
                lastRestorationLoadCount = Int(success.itemCount)
                _ = success.get(index: success.itemCount - 1)
            }
            return true
        default: return false
        }
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
            if oldValue != topContentInset {
                collectionView.prepareForLayoutChange()
            }
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
            collectionView.prepareForLayoutChange()
            let scrollAnchor: ScrollAnchor?
            if contentKind == .profileMedia {
                if let transition = profileMediaGeometryTransition,
                   transition.originColumnCount == columnCount {
                    scrollAnchor = transition.anchor
                } else {
                    scrollAnchor = captureScrollAnchor()
                        ?? profileMediaGeometryTransition?.anchor
                        ?? lastProfileMediaScrollAnchor
                }
            } else {
                scrollAnchor = nil
            }
            clearAllHeightCache()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
            updateBackgroundColors()
            if restoreScrollAnchorIfNeeded(scrollAnchor) {
                collectionView.layer.removeAllAnimations()
                rememberProfileMediaScrollAnchor()
            }
        }
    }
    var accessoryItems: [UITimelineCollectionViewAccessoryItem] = [] {
        didSet {
            let oldIDs = oldValue.map { "\(Self.accessoryPrefix)\($0.id)" }
            let newIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
            accessoryItemMap = Dictionary(
                uniqueKeysWithValues: zip(newIDs, accessoryItems)
            )
            for accessory in accessoryItems {
                (accessory.view as? TimelineHostedAccessoryView)?.onHeightChanged = { [weak self] in
                    guard let self, self.isViewLoaded else { return }
                    self.collectionView.prepareForLayoutChange()
                    self.collectionView.collectionViewLayout.invalidateLayout()
                }
            }
            guard isViewLoaded else { return }
            if restoresScrollAnchorOnSnapshotChanges && pendingSavedPosition == nil {
                collectionView.prepareForLayoutChange()
            }
            collectionView.collectionViewLayout.invalidateLayout()
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
        pendingSavedPosition = nil
        collectionView.resetReadingPosition()
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
        pendingSavedPosition = nil
        guard isViewLoaded else { return }
        collectionView.resetReadingPosition()
        collectionView.setContentOffset(
            CGPoint(
                x: collectionView.contentOffset.x,
                y: clampedContentOffsetY(offsetY - collectionView.adjustedContentInset.top)
            ),
            animated: animated
        )
    }

    private var collectionView: TimelineCollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var refreshControl = UIRefreshControl()
    private var isUserRefreshing = false
    private var pendingRefreshControlOffsetY: CGFloat?
    private var hasCompletedInitialRefreshCycle = false
    private var scrollingState = IsScrollingState()
    private var lastReportedIsAtTop: Bool?
    private var lastAppliedSignature: SnapshotSignature?
    private var lastRenderHashMap: [String: Int32] = [:]
    private var lastLoadedItemIDs: Set<String> = []
    private let autoplayPlayerView = VideoPlaybackSurfaceView()
    private let autoplaySession = VideoPlaybackSession()
    private var autoplayReadinessSubscription: AnyCancellable?
    private var autoplayLifecycleSubscription: AnyCancellable?
    private var autoplaySelectionTask: Task<Void, Never>?
    private var autoplayCountdownTask: Task<Void, Never>?
    private var postRefreshPoolCleanupTask: Task<Void, Never>?
    private var deferredPoolCleanupTask: Task<Void, Never>?
    private let deferredPoolCleanupCells = NSHashTable<TimelineUIKitCollectionViewCell>.weakObjects()
    private weak var currentAutoplayHostView: UIView?
    private var isAutoplayViewVisible = false
    private var isAutoplayViewportMoving = false
    private var autoplayImmediateReturn = false
    private var currentAutoplayID: String?
    private var currentAutoplayURL: URL?
    private var autoplayPolicy = TimelineAutoplayPolicy()
    let mediaSelections = TimelineMediaSelections()
    private let autoplayCarousels = NSHashTable<StatusMediaUIView>.weakObjects()
    private weak var pinnedAccessoryView: UIView?
    private var accessoryItemMap: [String: UITimelineCollectionViewAccessoryItem] = [:]
    private var pendingScrollAnchor: ScrollAnchor?
    private var lastProfileMediaScrollAnchor: ScrollAnchor?
    private var profileMediaGeometryTransition: (anchor: ScrollAnchor, originColumnCount: Int)?
    private var pendingEffectiveContentOffsetYAfterSnapshot: CGFloat?
    private var isRestoringScrollAnchor = false
    private var isApplyingContentTransition = false
    private var snapshotPreparationGeneration = 0
    private var heightCachePruneGeneration = 0

    // Maps item identifier → paging index.
    private var itemIndexMap: [String: Int] = [:]

    private struct SnapshotSignature: Equatable, Sendable {
        let headerIDs: [String]
        let accessoryIDs: [String]
        let itemIDs: [String]
        let footerIDs: [String]
    }

    private struct SnapshotPlan: Sendable {
        let signature: SnapshotSignature
        let headerIDs: [String]
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

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        isAutoplayViewVisible = true
        reconfigureVisibleCells()
        scheduleAutoplaySelection()
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
        scheduleAutoplaySelection()
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
        isAutoplayViewVisible = false
        isAutoplayViewportMoving = false
        scrollingState.isScrolling = false
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
        detachAutoplayPlayer()
        VideoPlaybackArbiter.shared.withdraw(self)
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
                return min(self.collectionView.adjustedContentInset.top,
                    self.collectionView.safeAreaInsets.top + max(self.topContentInset - self.minimumVerticalScrollDistance, 0))
            }
            return self.collectionView.adjustedContentInset.top
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
        "\(itemID):\(renderHash):\(columnCount > 1):\(heightCacheWidthKey(for: width))"
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

    private var mainSectionUsesFullWidth: Bool {
        switch contentKind {
        case .timeline:
            guard let currentData else { return false }
            switch onEnum(of: currentData) {
            case .empty, .error: return true
            default: return false
            }
        case .users:
            guard let currentUserData else { return false }
            switch onEnum(of: currentUserData) {
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
        collectionView.prepareForLayoutChange()
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

    private func setupVideoAutoplay() {
        autoplayLifecycleSubscription = NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
            .merge(with: NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification))
            .sink { [weak self] _ in self?.handleAutoplayAvailabilityChanged() }
        autoplayReadinessSubscription = autoplaySession.updates.sink { [weak self] in
            guard let self else { return }
            self.autoplayPlayerView.canDisplayFrame = self.autoplaySession.hasRestoredPosition
        }
        autoplayPlayerView.playerLayer.videoGravity = .resizeAspectFill
        autoplayPlayerView.isUserInteractionEnabled = false
        VideoPlaybackArbiter.shared.register(self, stop: { [weak self] in
            self?.detachAutoplayPlayer()
        }, reconsider: { [weak self] in
            self?.scheduleAutoplaySelection()
        }, mediaReturned: { [weak self] urls, selected in
            self?.mediaSelections.returned(urls: urls, selectedURL: selected)
        }, resume: { [weak self] in
            self?.autoplayImmediateReturn = true
            self?.scheduleAutoplaySelection()
        }, willHandoff: { VideoPlaybackSession.continuePlayback(to: $0) })
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

    @objc private func handleTimelineVideoAutoplayNeedsUpdate(_ notification: Notification) {
        guard let media = notification.object as? StatusMediaUIView,
              media.isDescendant(of: collectionView) else { return }
        autoplayCarousels.add(media)
        if notification.userInfo?["carouselInteraction"] as? Bool == true {
            autoplayImmediateReturn = false
            VideoPlaybackArbiter.shared.interacted(self)
            autoplayPolicy.interactedWithCarousel(media.autoplayGroupID)
        }
        if let url = notification.userInfo?["selectedMediaURL"] as? String {
            if notification.userInfo?["mediaClicked"] as? Bool == true {
                VideoPlaybackArbiter.shared.interacted(self)
            }
            autoplayPolicy.returnedToMedia(groupID: media.autoplayGroupID, mediaURL: url)
        }
        validateCurrentAutoplayVisibility()
        scheduleAutoplaySelection()
    }

    // MARK: - Cell Configuration

    private func userCard(for itemID: String) -> UIView? {
        guard let index = itemIndexMap[itemID], let success = currentUserSuccess,
              index < Int(success.itemCount), let user = success.peek(index: Int32(index)) else { return nil }
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
        card.configure(index: index, totalCount: Int(success.itemCount))
        return card
    }

    private func configureHostedCell(_ cell: TimelineHostedViewCell, itemID: String) {
        if itemID.hasPrefix(Self.userPrefix) {
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
        guard let index = itemIndexMap[itemID],
              let success = currentSuccess,
              index >= 0, index < Int(success.itemCount),
              let item = success.peek(index: Int32(index)) else { return nil }
        return (item, index, Int(success.itemCount))
    }

    private func configureTimelineCell(_ cell: TimelineUIKitCollectionViewCell, itemID: String) {
        if let row = timelineItem(for: itemID) {
            let item = row.data
            cell.cachedPreferredHeight = { [weak self] width in
                guard let self else { return nil }
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
                totalCount: currentSuccess.map { Int($0.itemCount) } ?? 1,
                appearance: appearance,
                isMultipleColumn: columnCount > 1
            )
        }
    }

    private func configurePlaceholderCell(_ cell: TimelinePlaceholderCollectionViewCell, index: Int, isHeader: Bool) {
        let totalCount: Int
        if isHeader {
            totalCount = 1
        } else if let success = currentSuccess {
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
        case .users:
            guard let data = currentUserData, case .error(let errorState) = onEnum(of: data) else { return }
            errorView.configure(error: errorState.error) { errorState.onRetry() }
        case .profileMedia:
            guard let data = currentProfileMediaData, case .error(let errorState) = onEnum(of: data) else { return }
            errorView.configure(error: errorState.error) { errorState.onRetry() }
        }
        cell.setHostedView(CenteredCellContentView(content: errorView))
    }

    private func configureFooterErrorCell(_ cell: TimelineHostedViewCell) {
        let errorView = ListErrorUIView()
        errorView.onOpenURL = openURL
        switch contentKind {
        case .timeline:
            guard let success = currentSuccess,
                  case .error(let error) = onEnum(of: success.appendState) else { return }
            errorView.configure(error: error.error) { success.retry() }
        case .users:
            guard let success = currentUserSuccess,
                  case .error(let error) = onEnum(of: success.appendState) else { return }
            errorView.configure(error: error.error) { success.retry() }
        case .profileMedia:
            guard let success = currentProfileMediaSuccess,
                  case .error(let error) = onEnum(of: success.appendState) else { return }
            errorView.configure(error: error.error) { success.retry() }
        }
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
        data.isRefreshing_
    }

    private var currentPagingIsRefreshing: Bool {
        switch contentKind {
        case .timeline:
            currentData.map(pagingIsRefreshing) ?? false
        case .users:
            currentUserData.map(pagingIsRefreshing) ?? false
        case .profileMedia:
            currentProfileMediaData.map(pagingIsRefreshing) ?? false
        }
    }

    private func pagingIsInitialLoading<Item: AnyObject>(_ data: PagingState<Item>?) -> Bool {
        guard let data else { return true }
        if case .loading = onEnum(of: data) {
            return true
        }
        return false
    }

    private var currentPagingIsInitialLoading: Bool {
        switch contentKind {
        case .timeline:
            currentData.map(pagingIsInitialLoading) ?? (headerState.map { if case .loading = onEnum(of: $0) { true } else { false } } ?? false)
        case .users:
            pagingIsInitialLoading(currentUserData)
        case .profileMedia:
            pagingIsInitialLoading(currentProfileMediaData)
        }
    }

    func resetInitialRefreshIndicatorSuppression() {
        hasCompletedInitialRefreshCycle = false
        pendingRefreshControlOffsetY = nil
        guard isViewLoaded,
              suppressInitialRefreshIndicator,
              refreshControl.isRefreshing,
              !isUserRefreshing else {
            return
        }
        refreshControl.endRefreshing()
    }

    // MARK: - State Update

    func update(
        data: PagingState<UiTimelineV2>?,
        columnCount requestedColumnCount: Int,
        headerState: UiState<UiTimelineV2>? = nil,
        contentKey: AnyHashable? = nil
    ) {
        isSnapshotReadyForReadingPosition = false
        let switchedContent = self.contentKey != nil && contentKey != nil && self.contentKey != contentKey
        self.contentKey = contentKey
        if switchedContent, isViewLoaded {
            collectionView.resetReadingPosition()
            let offsetY = max(effectiveContentOffsetY, 0)
            // A shorter tab must remain scrollable to the current position,
            // including while its loading and empty states are displayed.
            pendingScrollAnchor = nil
            minimumVerticalScrollDistance = offsetY
            restoreEffectiveContentOffsetAfterNextSnapshot(offsetY)
            resetInitialRefreshIndicatorSuppression()
        }
        let wasRefreshing = contentKind == .timeline && currentPagingIsRefreshing
        self.headerState = headerState
        let isRefreshing = data.map(pagingIsRefreshing) ?? false
        let nextSuccess: PagingStateSuccess<UiTimelineV2>?
        if let data, case .success(let success) = onEnum(of: data) {
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
        if switchedContent {
            // State cells share IDs across tabs; bind retries to the selected source.
            reconfigureItems([Self.errorID, Self.footerErrorID])
        }
        if currentSuccess == nil && headerItem == nil {
            detachAutoplayPlayer()
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

    func update(userData data: PagingState<UiProfile>, columnCount requestedColumnCount: Int) {
        isSnapshotReadyForReadingPosition = false
        let isRefreshing = pagingIsRefreshing(data)
        let nextSuccess: PagingStateSuccess<UiProfile>?
        if case .success(let success) = onEnum(of: data) {
            nextSuccess = success
        } else {
            nextSuccess = nil
        }
        let targetColumnCount = max(requestedColumnCount, 1)

        guard isViewLoaded else {
            setContentKind(.users)
            columnCount = targetColumnCount
            currentUserData = data
            currentUserSuccess = nextSuccess
            return
        }

        if contentKind != .users || columnCount != targetColumnCount {
            let plan = makeSnapshotPlan(userData: data, columnCount: targetColumnCount)
            let snapshot = Self.makeSnapshot(from: plan)
            applyPreparedContentTransition(
                to: .users,
                columnCount: targetColumnCount,
                snapshot: snapshot,
                plan: plan
            ) {
                self.currentUserData = data
                self.currentUserSuccess = nextSuccess
            }
        } else {
            currentUserData = data
            currentUserSuccess = nextSuccess
            syncRefreshControl(isRefreshing: isRefreshing)
            applySnapshot(userData: data)
        }
    }

    private func setContentKind(_ newKind: ContentKind) {
        guard contentKind != newKind else { return }
        contentKind = newKind
        collectionView?.preservesReadingPosition = newKind != .profileMedia
        collectionView?.resetReadingPosition()
        currentData = nil
        currentSuccess = nil
        currentUserData = nil
        currentUserSuccess = nil
        currentProfileMediaData = nil
        currentProfileMediaSuccess = nil
        if newKind == .profileMedia {
            headerState = nil
        }
        pendingScrollAnchor = nil
        lastProfileMediaScrollAnchor = nil
        profileMediaGeometryTransition = nil
    }

    private func syncRefreshControl(isRefreshing: Bool) {
        // Loading and an unbound data source are not completed refresh cycles.
        if !isRefreshing && !currentPagingIsInitialLoading {
            hasCompletedInitialRefreshCycle = true
        }

        let shouldSuppressInitialRefreshIndicator =
            suppressInitialRefreshIndicator &&
            !hasCompletedInitialRefreshCycle &&
            !isUserRefreshing

        if isRefreshing {
            guard !shouldSuppressInitialRefreshIndicator else {
                pendingRefreshControlOffsetY = nil
                if refreshControl.isRefreshing {
                    refreshControl.endRefreshing()
                }
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
            if refreshControl.isRefreshing {
                refreshControl.endRefreshing()
            }
        }
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

    private func updatePinnedHeader() {
        guard isViewLoaded, collectionView != nil, dataSource != nil else { return }
        let top = collectionView.contentOffset.y + collectionView.adjustedContentInset.top
        let titles = accessoryItems.compactMap { item -> (UIView, CGRect)? in
            guard let view = item.pinnedView,
                  let path = dataSource.indexPath(for: Self.accessoryPrefix + item.id),
                  let frame = collectionView.layoutAttributesForItem(at: path)?.frame else { return nil }
            return (view, frame)
        }
        guard let index = titles.lastIndex(where: { $0.1.minY <= top }) else {
            pinnedAccessoryView?.removeFromSuperview()
            pinnedAccessoryView = nil
            return
        }
        let (view, frame) = titles[index]
        if pinnedAccessoryView !== view {
            pinnedAccessoryView?.removeFromSuperview()
            pinnedAccessoryView = view
            collectionView.addSubview(view)
        }
        let nextTop = titles.indices.contains(index + 1) ? titles[index + 1].1.minY : .greatestFiniteMagnitude
        view.frame = CGRect(x: frame.minX, y: min(top, nextTop - frame.height), width: frame.width, height: frame.height)
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
        itemID.hasPrefix(Self.timelinePrefix) || itemID.hasPrefix(Self.userPrefix) || itemID.hasPrefix(Self.profileMediaPrefix) || itemID.hasPrefix(Self.accessoryPrefix)
    }

    private func captureScrollAnchor(requiringStableInteraction: Bool = true) -> ScrollAnchor? {
        guard isViewLoaded,
              currentSuccess != nil || currentUserSuccess != nil || headerItem != nil || currentProfileMediaSuccess != nil,
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

        let targetOffsetY = attributes.frame.minY - anchor.distanceFromViewportTop
        let targetOffset = CGPoint(x: collectionView.contentOffset.x, y: clampedContentOffsetY(targetOffsetY))
        if abs(collectionView.contentOffset.y - targetOffset.y) > 0.5 {
            isRestoringScrollAnchor = true
            collectionView.setContentOffset(targetOffset, animated: false)
            isRestoringScrollAnchor = false
        }
        return true
    }

    private func restorePendingContentOffsetIfNeeded(finalize: Bool) {
        isSnapshotReadyForReadingPosition = true
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

    private func applyPreparedContentTransition(
        to newKind: ContentKind,
        columnCount newColumnCount: Int,
        snapshot: NSDiffableDataSourceSnapshot<Int, String>,
        plan: SnapshotPlan,
        updateState: () -> Void
    ) {
        if contentKind == newKind, columnCount != newColumnCount,
           pendingEffectiveContentOffsetYAfterSnapshot == nil {
            collectionView.prepareForLayoutChange()
        }
        // Invalidate any in-flight snapshot for the previous tab before replacing
        // both its data and layout in the same non-animated transaction.
        snapshotPreparationGeneration += 1
        isSnapshotReadyForReadingPosition = false
        isApplyingContentTransition = true
        defer { isApplyingContentTransition = false }

        UIView.performWithoutAnimation {
            CATransaction.begin()
            CATransaction.setDisableActions(true)

            setContentKind(newKind)
            columnCount = max(newColumnCount, 1)
            updateState()
            clearAllHeightCache()
            detachAutoplayPlayer()
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
            accessVisiblePagingItems()
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
            applySnapshot(data: currentData)
        case .users:
            if let data = currentUserData { applySnapshot(userData: data) }
        case .profileMedia:
            if let currentProfileMediaData {
                applySnapshot(profileMediaData: currentProfileMediaData)
            }
        }
    }

    private func applySnapshot(data: PagingState<UiTimelineV2>?) {
        applySnapshot(plan: makeSnapshotPlan(data: data, columnCount: columnCount))
    }

    private func applySnapshot(userData data: PagingState<UiProfile>) {
        applySnapshot(plan: makeSnapshotPlan(userData: data, columnCount: columnCount))
    }

    private func applySnapshot(profileMediaData data: PagingState<ProfileMedia>) {
        applySnapshot(plan: makeSnapshotPlan(profileMediaData: data, columnCount: columnCount))
    }

    private func applySnapshot(plan: SnapshotPlan) {
        isSnapshotReadyForReadingPosition = false
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
        data: PagingState<UiTimelineV2>?,
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
            renderHash: { $0.renderHash },
            headerState: headerState
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

    private func makeSnapshotPlan(userData data: PagingState<UiProfile>, columnCount: Int) -> SnapshotPlan {
        makeSnapshotPlan(
            data: data,
            loadingItemCount: 8,
            placeholderPrefix: Self.placeholderPrefix,
            itemID: { "\(Self.userPrefix)\($0.key)" },
            renderHash: { Int32(truncatingIfNeeded: $0.hash) }
        )
    }

    private func makeSnapshotPlan<Item: AnyObject>(
        data: PagingState<Item>?,
        loadingItemCount: Int,
        placeholderPrefix: String,
        itemID: (Item) -> String,
        renderHash: (Item) -> Int32,
        headerState: UiState<UiTimelineV2>? = nil
    ) -> SnapshotPlan {
        var newIndexMap: [String: Int] = [:]
        var newRenderHashMap: [String: Int32] = [:]
        var newLoadedItemIDs = Set<String>()
        var headerIDs: [String] = []
        let accessoryIDs = accessoryItems.map { "\(Self.accessoryPrefix)\($0.id)" }
        var itemIDs: [String] = []
        var footerIDs: [String] = []
        var isInitialLoading = false

        if let headerState {
            switch onEnum(of: headerState) {
            case .success(let success):
                headerIDs = [Self.headerTimelineID]
                newRenderHashMap[Self.headerTimelineID] = success.data.renderHash
                newLoadedItemIDs.insert(Self.headerTimelineID)
            case .loading:
                headerIDs = [Self.headerPlaceholderID]
            case .error(let error):
                headerIDs = [Self.headerErrorID]
                newRenderHashMap[Self.headerErrorID] = Int32(truncatingIfNeeded: error.throwable.hash)
            }
        }

        switch data.map({ onEnum(of: $0) }) {
        case nil:
            break
        case .loading?:
            isInitialLoading = true
            itemIDs = (0..<loadingItemCount).map { "\(placeholderPrefix)\($0)" }
        case .error?:
            itemIDs = [Self.errorID]
        case .empty?:
            itemIDs = [Self.emptyID]
        case .success(let success)?:
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
            headerIDs: headerIDs,
            accessoryIDs: accessoryIDs,
            itemIDs: itemIDs,
            footerIDs: footerIDs
        )
        return SnapshotPlan(
            signature: signature,
            headerIDs: headerIDs,
            accessoryIDs: accessoryIDs,
            itemIDs: itemIDs,
            footerIDs: footerIDs,
            indexMap: newIndexMap,
            renderHashMap: newRenderHashMap,
            loadedItemIDs: newLoadedItemIDs,
            isRefreshing: data.map(pagingIsRefreshing) ?? false,
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

    private func applyPreparedSnapshot(
        _ preparedSnapshot: NSDiffableDataSourceSnapshot<Int, String>,
        plan: SnapshotPlan
    ) {
        var snapshot = preparedSnapshot
        let generation = snapshotPreparationGeneration
        if contentKey != nil,
           minimumVerticalScrollDistance > 0,
           pendingEffectiveContentOffsetYAfterSnapshot == nil,
           allowsScrollAnchorRestoration {
            // Loading may finish after the user has scrolled since the switch.
            // Keep that newer position when the shorter snapshot is installed.
            pendingEffectiveContentOffsetYAfterSnapshot = max(effectiveContentOffsetY, 0)
        }
        let newSignature = plan.signature
        let previousSignature = lastAppliedSignature
        let headerChanged = previousSignature?.headerIDs != newSignature.headerIDs
        let scrollAnchor = restoresScrollAnchorOnSnapshotChanges &&
            pendingSavedPosition == nil &&
            pendingEffectiveContentOffsetYAfterSnapshot == nil &&
            previousSignature != nil &&
            (headerChanged || previousSignature?.itemIDs != newSignature.itemIDs) &&
            (!headerChanged || effectiveContentOffsetY > 1) &&
            allowsScrollAnchorRestoration
            ? captureScrollAnchor()
            : nil

        if restoresScrollAnchorOnSnapshotChanges, pendingSavedPosition == nil,
           previousSignature != nil, previousSignature != newSignature,
           pendingEffectiveContentOffsetYAfterSnapshot == nil {
            collectionView.prepareForLayoutChange()
        }
        if plan.isInitialLoading, previousSignature != nil,
           pendingSavedPosition == nil, pendingEffectiveContentOffsetYAfterSnapshot == nil,
           restoresScrollAnchorOnSnapshotChanges {
            pendingSavedPosition = collectionView.captureReadingPosition()
        }
        itemIndexMap = plan.indexMap
        scheduleHeightCachePrune(keepingItemIDs: Set(plan.indexMap.keys).union(plan.headerIDs))

        if previousSignature == newSignature {
            let changedIDs = changedItemIDs(
                in: plan.headerIDs + plan.itemIDs,
                newRenderHashMap: plan.renderHashMap,
                newLoadedItemIDs: plan.loadedItemIDs
            )
            lastRenderHashMap = plan.renderHashMap
            lastLoadedItemIDs = plan.loadedItemIDs
            reconfigureItems(changedIDs)
            restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
            accessVisiblePagingItems()
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        if previousSignature?.headerIDs == newSignature.headerIDs,
           previousSignature?.accessoryIDs == newSignature.accessoryIDs,
           previousSignature?.itemIDs == newSignature.itemIDs {
            let changedIDs = changedItemIDs(
                in: plan.headerIDs + plan.itemIDs,
                newRenderHashMap: plan.renderHashMap,
                newLoadedItemIDs: plan.loadedItemIDs
            )
            applyFooterSnapshot(footerIDs: plan.footerIDs, reconfigureIDs: changedIDs, isRefreshing: plan.isRefreshing)
            restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
            lastAppliedSignature = newSignature
            lastRenderHashMap = plan.renderHashMap
            lastLoadedItemIDs = plan.loadedItemIDs
            accessVisiblePagingItems()
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        // Reconfigure only existing timeline items whose render payload or loaded state changed.
        let existing = Set(dataSource.snapshot().itemIdentifiers)
        let toReconfigure = (plan.headerIDs + plan.itemIDs).filter {
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
                    guard let self, self.snapshotPreparationGeneration == generation else { return }
                    self.restoreScrollAnchorIfNeeded(scrollAnchor)
                    self.restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
                    self.pendingScrollAnchor = nil
                    self.accessVisiblePagingItems()
                    self.validateCurrentAutoplayVisibility()
                    self.scheduleAutoplaySelection()
                }
                restoreScrollAnchorIfNeeded(scrollAnchor)
                collectionView.layer.removeAllAnimations()
                CATransaction.commit()
            }
        } else {
            dataSource.apply(snapshot, animatingDifferences: shouldAnimateDifferences) { [weak self] in
                guard let self, self.snapshotPreparationGeneration == generation else { return }
                self.restorePendingContentOffsetIfNeeded(finalize: !plan.isInitialLoading)
                self.accessVisiblePagingItems()
                self.validateCurrentAutoplayVisibility()
                self.scheduleAutoplaySelection()
            }
            restorePendingContentOffsetIfNeeded(finalize: false)
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
        guard UIApplication.shared.applicationState == .active else { return false }
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
            detachAutoplayPlayer()
            return
        }
        scheduleAutoplaySelection()
    }

    private func scheduleAutoplaySelection(delayNanoseconds: UInt64 = 200_000_000) {
        autoplaySelectionTask?.cancel()
        guard isViewLoaded, isAutoplayViewVisible, currentSuccess != nil || headerItem != nil, isVideoAutoplayAllowed else { return }
        if autoplayImmediateReturn, !isAutoplayViewportMoving {
            selectAutoplayCandidateIfStable()
            if currentAutoplayID != nil { autoplayImmediateReturn = false }
            return
        }
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
        guard isAutoplayViewVisible, isVideoAutoplayAllowed, currentSuccess != nil || headerItem != nil else {
            detachAutoplayPlayer()
            return
        }
        guard !collectionView.isDragging, !collectionView.isDecelerating, !scrollingState.isScrolling,
              !autoplayCarousels.allObjects.contains(where: { $0.isDescendant(of: collectionView) && $0.isCarouselScrolling }) else { return }
        isAutoplayViewportMoving = false
        guard let candidate = bestAutoplayCandidate() else {
            VideoPlaybackArbiter.shared.settledWithoutVideo(self)
            detachAutoplayPlayer()
            return
        }
        playAutoplayCandidate(candidate)
    }

    private func bestAutoplayCandidate() -> TimelineVideoAutoplayCandidate? {
        let candidates = visibleAutoplayCandidates()
        let selection = candidates.compactMap { candidate -> TimelineAutoplayPolicy.Candidate? in
            guard visibleRect(for: candidate.hostView, in: collectionView) != nil else { return nil }
            let bounds = candidate.hostView.convert(candidate.hostView.bounds, to: collectionView)
            let distance = TimelineAutoplayPolicy.centerDistance(of: bounds, in: autoplayViewport, multipleColumns: columnCount > 1)
            return .init(id: candidate.id, groupID: candidate.groupID, isVisible: true,
                         isSelected: candidate.isSelected, canStart: candidate.horizontalFraction >= 0.6,
                         distance: distance, mediaURL: candidate.url.absoluteString)
        }
        let id = autoplayPolicy.select(from: selection, isScrolling: false)
        return candidates.first { $0.id == id }
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
        guard autoplaySession.player == nil || currentAutoplayID != candidate.id || currentAutoplayHostView !== candidate.hostView else {
            return
        }
        guard let newHost = candidate.hostView as? MediaUIView,
              VideoPlaybackArbiter.shared.acquire(self) else { return }

        saveAutoplayPosition()
        autoplaySession.detach()
        if let oldHost = currentAutoplayHostView as? MediaUIView, oldHost !== candidate.hostView {
            oldHost.detachAutoplayPlayer()
        } else if autoplayPlayerView.superview !== candidate.hostView {
            autoplayPlayerView.removeFromSuperview()
        }

        newHost.attachAutoplayPlayer(autoplayPlayerView)
        newHost.setAutoplayOverlay(.loading)

        currentAutoplayID = candidate.id
        currentAutoplayURL = candidate.url
        currentAutoplayHostView = candidate.hostView
        autoplaySession.play(url: candidate.url.absoluteString)
        autoplayPlayerView.canDisplayFrame = autoplaySession.hasRestoredPosition
        autoplayPlayerView.player = autoplaySession.player
        startAutoplayCountdownUpdates()
    }

    private func startAutoplayCountdownUpdates() {
        autoplayCountdownTask?.cancel()
        autoplayCountdownTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                self?.updateAutoplayCountdown()
                do {
                    try await Task.sleep(nanoseconds: 250_000_000)
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
        autoplaySession.refresh()
        autoplayPlayerView.canDisplayFrame = autoplaySession.hasRestoredPosition
        switch autoplaySession.state {
        case .playing(let duration): host.setAutoplayOverlay(.playing(remaining: max(duration - autoplaySession.position, 0)))
        case .loading: host.setAutoplayOverlay(.loading)
        case .error: host.setAutoplayOverlay(.error)
        case .idle, .paused: host.setAutoplayOverlay(.idle)
        }
    }

    private func validateCurrentAutoplayVisibility() {
        guard currentAutoplayHostView != nil else { return }
        guard isVideoAutoplayAllowed,
              let host = currentAutoplayHostView,
              (host as? MediaUIView)?.videoURL == currentAutoplayURL,
              host.window != nil,
              visibleRect(for: host, in: collectionView) != nil else {
            detachAutoplayPlayer()
            return
        }
    }

    private func saveAutoplayPosition() {
        autoplaySession.refresh()
        if let currentAutoplayURL, autoplaySession.player != nil {
            MediaPlaybackMemory.shared.save(autoplaySession.position, for: currentAutoplayURL.absoluteString)
        }
    }

    private func detachAutoplayPlayer() {
        saveAutoplayPosition()
        _ = autoplayPolicy.select(from: [], isScrolling: true)
        autoplaySelectionTask?.cancel()
        stopAutoplayCountdownUpdates()
        autoplaySession.detach()
        autoplayPlayerView.player = nil
        if let host = currentAutoplayHostView as? MediaUIView {
            host.detachAutoplayPlayer()
        } else {
            autoplayPlayerView.removeFromSuperview()
        }
        currentAutoplayID = nil
        currentAutoplayURL = nil
        currentAutoplayHostView = nil
        VideoPlaybackArbiter.shared.release(self)
    }

    private var autoplayViewport: CGRect {
        // Scroll padding can include artificial space for short profiles. Only
        // the safe area represents bars obscuring the actual viewport.
        collectionView.bounds.inset(by: collectionView.safeAreaInsets)
    }

    private func visibleRect(for hostView: UIView, in collectionView: UICollectionView) -> CGRect? {
        guard !hostView.isHidden,
              hostView.alpha > 0.01,
              hostView.window != nil,
              hostView.bounds.width > 1,
              hostView.bounds.height > 1 else {
            return nil
        }
        var visible = hostView.convert(hostView.bounds, to: collectionView).intersection(autoplayViewport)
        var ancestor = hostView.superview
        while let view = ancestor {
            guard !view.isHidden, view.alpha > 0.01 else { return nil }
            if let media = view as? StatusMediaUIView, !media.allowsVideoAutoplay { return nil }
            if view.clipsToBounds {
                visible = visible.intersection(view.convert(view.bounds, to: collectionView))
            }
            if view === collectionView { break }
            ancestor = view.superview
        }
        guard !visible.isEmpty else { return nil }
        return visible
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

        if itemID.hasPrefix(Self.userPrefix) {
            let key = timelineHeightCacheKey(itemID: itemID, renderHash: lastRenderHashMap[itemID] ?? 0, width: width)
            if let height = heightCache[key] { return CGSize(width: width, height: height) }
            if let card = userCard(for: itemID) {
                let height = max(1, ceil(measuredCompressedCardHeight(card, width: width)))
                heightCache[key] = height
                heightCacheKeysByItemID[itemID, default: []].insert(key)
                return CGSize(width: width, height: height)
            }
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
            let totalCount = currentSuccess.map { Int($0.itemCount) } ?? 5
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
           let row = timelineItem(for: itemID) {
            let item = row.data
            let key = timelineHeightCacheKey(itemID: itemID, renderHash: item.renderHash, width: width)
            if let cached = heightCache[key] { return CGSize(width: width, height: cached) }
            sizingTimelineCard.isPlainTimelineDisplayMode = appearance.isPlainTimelineDisplayMode
            sizingTimelineCard.isMultipleColumn = columnCount > 1 && itemID != Self.headerTimelineID
            sizingTimelineCard.configure(index: row.index, totalCount: row.totalCount)
            sizingTimelineView.configure(
                data: item,
                appearance: appearance.status,
                detailStatusKey: detailStatusKey,
                aiTldrEnabled: aiTldrEnabled,
                onOpenURL: nil
            )
            // Compose applies the multi-column card wrapper outside the row:
            // 2pt horizontally, 6pt vertically.
            let contentWidth = max(width - (sizingTimelineCard.isMultipleColumn ? 4 : 0) - 32, 1)
            sizingTimelineView.prepareForFitting(width: contentWidth)
            let measuredHeight: CGFloat
            if sizingTimelineCard.isMultipleColumn, let contentHeight = sizingTimelineView.estimatedHeightForFitting(width: contentWidth) {
                measuredHeight = ceil(contentHeight + 16 + 12) + 1
            } else {
                measuredHeight = ceil(measuredCompressedCardHeight(sizingTimelineCard, width: width))
            }
            let height = max(measuredHeight, 1)
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
        guard contentKind == .profileMedia else { return columnCount == 1 ? TimelineUIKitLayoutMetrics.rowSpacing : 0 }
        switch sectionIdentifier(at: section) {
        case Self.sectionAccessories: return 2
        case Self.sectionMain: return 8
        default: return 0
        }
    }

    // MARK: - UICollectionViewDelegate

    private func accessVisiblePagingItems() {
        // Refresh can replace the paging source without changing any visible IDs.
        // Those cells won't receive willDisplay again, but Paging still needs
        // their access hints to load replies or the next page. peek() sends none.
        for indexPath in collectionView.indexPathsForVisibleItems {
            guard let itemID = dataSource.itemIdentifier(for: indexPath),
                  let index = itemIndexMap[itemID] else { continue }
            switch contentKind {
            case .timeline:
                if let success = currentSuccess, index >= 0, index < Int(success.itemCount) {
                    _ = success.get(index: Int32(index))
                }
            case .users:
                if let success = currentUserSuccess, index >= 0, index < Int(success.itemCount) {
                    _ = success.get(index: Int32(index))
                }
            case .profileMedia:
                if let success = currentProfileMediaSuccess, index >= 0, index < Int(success.itemCount) {
                    _ = success.get(index: Int32(index))
                }
            }
        }
    }

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        if let itemID = dataSource.itemIdentifier(for: indexPath),
           let accessory = accessoryItemMap[itemID] {
            accessory.onVisibilityChanged?(true)
            return
        }
        if dataSource.itemIdentifier(for: indexPath) == Self.headerTimelineID {
            scheduleAutoplaySelection()
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
        case .users:
            if let success = currentUserSuccess, index >= 0, index < Int(success.itemCount) {
                _ = success.get(index: Int32(index))
            }
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
        detachAutoplayPlayer()
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        onScrollInteractionBegan?()
        beginScrollInteraction()
    }

    private func beginScrollInteraction() {
        pendingSavedPosition = nil
        collectionView.resetReadingPosition()
        autoplayImmediateReturn = false
        VideoPlaybackArbiter.shared.interacted(self)
        autoplayPolicy.verticalScrollBegan()
        isAutoplayViewportMoving = true
        if contentKind == .profileMedia {
            profileMediaGeometryTransition = nil
        }
        scrollingState.isScrolling = true
        pendingScrollAnchor = nil
        pendingEffectiveContentOffsetYAfterSnapshot = nil
        autoplaySelectionTask?.cancel()
        postRefreshPoolCleanupTask?.cancel()
        deferredPoolCleanupTask?.cancel()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        updatePinnedHeader()
        if contentKey != nil, scrollingState.isScrolling {
            let requiredDistance = min(minimumVerticalScrollDistance, max(effectiveContentOffsetY, 0))
            if minimumVerticalScrollDistance - requiredDistance > 0.5 {
                minimumVerticalScrollDistance = requiredDistance
            }
        }
        if !scrollingState.isScrolling, !isAutoplayViewportMoving {
            VideoPlaybackArbiter.shared.interacted(self)
            autoplayPolicy.verticalScrollBegan()
        }
        isAutoplayViewportMoving = true
        restorePendingScrollAnchorIfNeeded()
        if allowsScrollAnchorRestoration {
            rememberProfileMediaScrollAnchor()
        }
        reportIsAtTop()
        onContentOffsetChanged?(effectiveContentOffsetY)
        validateCurrentAutoplayVisibility()
        scheduleAutoplaySelection()
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
        beginScrollInteraction()
        return true
    }

    func scrollViewDidScrollToTop(_ scrollView: UIScrollView) {
        endScrollInteraction()
    }

    private func endScrollInteraction() {
        scrollingState.isScrolling = false
        rememberProfileMediaScrollAnchor()
        scheduleAutoplaySelection()
        scheduleDeferredPoolCleanup()
        saveReadingPosition()
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
    private var lastMeasuredWidth: CGFloat?

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
        lastMeasuredWidth = nil
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
        if timelineCard.isMultipleColumn != isMultipleColumn {
            pendingFreshMeasurement = true
        }
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
            pendingFreshMeasurement = true
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
        setHostedView(timelineCard)
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
        setHostedView(placeholderCard)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
        reportPreferredHeightIfNeeded()
    }

    override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        attributes
    }

    func setHostedView(_ view: UIView?) {
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

        if lastMeasuredWidth != width {
            lastMeasuredWidth = width
            pendingFreshMeasurement = true
        }
        if !pendingFreshMeasurement,
           hostedView === timelineCardStorage,
           let cachedHeight = cachedPreferredHeight?(width),
           cachedHeight > 0,
           cachedHeight.isFinite {
            return cachedHeight
        }

        if hostedView === timelineCardStorage {
            let cardWrapperWidth: CGFloat = timelineCardStorage?.isMultipleColumn == true ? 4 : 0
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
        setHostedView(placeholderCard)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds
    }

    override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        attributes
    }

    private func setHostedView(_ view: UIView?) {
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

    override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        attributes
    }

    func setHostedView(_ view: UIView?) {
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
