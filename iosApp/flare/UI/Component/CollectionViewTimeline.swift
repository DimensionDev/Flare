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
    @Environment(\.appearanceSettings) private var appearanceSettings
    @Environment(\.appearanceSettings.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.networkKind) private var networkKind
    @Environment(\.openURL) private var openURL
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        topContentInset: CGFloat = 0,
        columnCount: Int = 1
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.topContentInset = topContentInset
        self.columnCount = max(columnCount, 1)
    }

    func makeUIViewController(context: Context) -> CollectionViewTimelineController {
        let controller = CollectionViewTimelineController(detailStatusKey: detailStatusKey)
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.appearanceSettings = appearanceSettings
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.networkKind = networkKind
        controller.usesCardBackground = timelineDisplayMode != .plain
        controller.columnCount = columnCount
        controller.update(data: data)
        return controller
    }

    func updateUIViewController(_ controller: CollectionViewTimelineController, context: Context) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.appearanceSettings = appearanceSettings
        controller.openURL = { url in
            openURL.callAsFunction(url)
        }
        controller.networkKind = networkKind
        controller.usesCardBackground = timelineDisplayMode != .plain
        controller.columnCount = columnCount
        controller.update(data: data)
    }
}

// MARK: - Controller

final class CollectionViewTimelineController: UIViewController, UICollectionViewDelegate, UIScrollViewDelegate, CHTCollectionViewDelegateWaterfallLayout {

    // Use Int for section and String for item to avoid Sendable issues
    private static let sectionMain = 0
    private static let sectionFooter = 1

    private let detailStatusKey: MicroBlogKey?
    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?

    var refreshCallback: (() async -> Void)?
    var openURL: ((URL) -> Void)?
    var appearanceSettings: AppearanceSettings = AppearanceSettings.companion.Default {
        didSet {
            guard isViewLoaded else { return }
            guard AppearanceSignature(settings: oldValue) != AppearanceSignature(settings: appearanceSettings) else {
                return
            }
            usesCardBackground = appearanceSettings.timelineDisplayMode != .plain
            heightCache.removeAll()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
            handleAutoplayAvailabilityChanged()
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
    var usesCardBackground: Bool = true {
        didSet {
            guard isViewLoaded else { return }
            guard oldValue != usesCardBackground else { return }
            updateBackgroundColors()
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
            heightCache.removeAll()
            applyLayoutForColumnCount()
            reconfigureVisibleCells()
        }
    }

    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var refreshControl = UIRefreshControl()
    private var isUserRefreshing = false
    private var shouldRevealRefreshControl = false
    private var scrollingState = IsScrollingState()
    private var lastAppliedSignature: SnapshotSignature?
    private var lastRenderHashMap: [String: Int32] = [:]
    private let autoplayPlayerView = VideoPlayerView()
    private var autoplaySelectionTask: Task<Void, Never>?
    private var autoplayCountdownTask: Task<Void, Never>?
    private weak var currentAutoplayHostView: UIView?
    private var currentAutoplayID: String?

    // Maps item identifier → index for timeline cells
    private var itemIndexMap: [String: Int] = [:]

    private struct SnapshotSignature: Equatable {
        let itemIDs: [String]
        let footerIDs: [String]
    }

    private struct AppearanceSignature: Equatable {
        let timelineDisplayMode: String
        let fullWidthPost: Bool
        let avatarShape: String
        let showPlatformLogo: Bool
        let absoluteTimestamp: Bool
        let postActionStyle: String
        let showNumbers: Bool
        let showMedia: Bool
        let showSensitiveContent: Bool
        let showLinkPreview: Bool
        let compatLinkPreview: Bool
        let expandMediaSize: Bool
        let videoAutoplay: String

        init(settings: AppearanceSettings) {
            timelineDisplayMode = String(describing: settings.timelineDisplayMode)
            fullWidthPost = settings.fullWidthPost
            avatarShape = String(describing: settings.avatarShape)
            showPlatformLogo = settings.showPlatformLogo
            absoluteTimestamp = settings.absoluteTimestamp
            postActionStyle = String(describing: settings.postActionStyle)
            showNumbers = settings.showNumbers
            showMedia = settings.showMedia
            showSensitiveContent = settings.showSensitiveContent
            showLinkPreview = settings.showLinkPreview
            compatLinkPreview = settings.compatLinkPreview
            expandMediaSize = settings.expandMediaSize
            videoAutoplay = String(describing: settings.videoAutoplay)
        }
    }

    // Item ID prefixes / constants
    private static let timelinePrefix = "t:"
    private static let placeholderPrefix = "p:"
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

        if shouldRevealRefreshControl {
            revealRefreshControlIfNeeded()
        }
        scheduleAutoplaySelection()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        detachAutoplayPlayer(pause: true)
    }

    deinit {
        autoplaySelectionTask?.cancel()
        autoplayCountdownTask?.cancel()
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
        let horizontalInset = appearanceSettings.timelineDisplayMode == .plain ? 0 : 16
        return UICollectionViewCompositionalLayout { _, _ in
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

    private func applyLayoutForColumnCount() {
        guard collectionView != nil else { return }
        let newLayout: UICollectionViewLayout = columnCount > 1
            ? makeWaterfallLayout(columns: columnCount)
            : makeSingleColumnLayout()
        collectionView.setCollectionViewLayout(newLayout, animated: false)
        collectionView.collectionViewLayout.invalidateLayout()
    }

    private func setupDataSource() {
        let cellReg = UICollectionView.CellRegistration<TimelineUIKitCollectionViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            self.configureCell(cell, itemID: itemID)
        }

        dataSource = UICollectionViewDiffableDataSource<Int, String>(
            collectionView: collectionView
        ) { (collectionView: UICollectionView, indexPath: IndexPath, itemID: String) -> UICollectionViewCell? in
            collectionView.dequeueConfiguredReusableCell(using: cellReg, for: indexPath, item: itemID)
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
        collectionView.contentInset.top = topContentInset
        collectionView.verticalScrollIndicatorInsets.top = topContentInset
    }

    private func updateBackgroundColors() {
        let backgroundColor: UIColor = usesCardBackground ? .systemGroupedBackground : .clear
        view.backgroundColor = backgroundColor
        collectionView.backgroundColor = backgroundColor
    }

    @objc private func handleTimelineVideoAutoplayNeedsUpdate() {
        validateCurrentAutoplayVisibility()
        scheduleAutoplaySelection()
    }

    // MARK: - Cell Configuration

    private func configureCell(_ cell: TimelineUIKitCollectionViewCell, itemID: String) {
        if itemID.hasPrefix(Self.timelinePrefix) {
            if let index = itemIndexMap[itemID] {
                configureTimelineCell(cell, index: index)
            }
        } else if itemID.hasPrefix(Self.placeholderPrefix) {
            let indexStr = itemID.dropFirst(Self.placeholderPrefix.count)
            let index = Int(indexStr) ?? 0
            configurePlaceholderCell(cell, index: index)
        } else if itemID == Self.emptyID {
            cell.setHostedView(CenteredCellContentView(content: ListEmptyUIView()))
        } else if itemID == Self.errorID {
            configureErrorCell(cell)
        } else if itemID == Self.footerLoadingID {
            cell.setHostedView(makeLoadingFooterView())
        } else if itemID == Self.footerErrorID {
            configureFooterErrorCell(cell)
        } else if itemID == Self.footerEndID {
            cell.setHostedView(makeTextFooterView(text: String(localized: "end_of_list")))
        }
    }

    private func configureTimelineCell(_ cell: TimelineUIKitCollectionViewCell, index: Int) {
        guard let success = currentSuccess else { return }
        let totalCount = Int(success.itemCount)
        let item = (index >= 0 && index < totalCount) ? success.peek(index: Int32(index)) : nil
        if let item {
            cell.configureTimeline(
                data: item,
                index: index,
                totalCount: totalCount,
                appearance: appearanceSettings,
                detailStatusKey: detailStatusKey,
                isMultipleColumn: columnCount > 1,
                openURL: openURL
            )
        } else {
            configurePlaceholderCell(cell, index: index)
        }
    }

    private func configurePlaceholderCell(_ cell: TimelineUIKitCollectionViewCell, index: Int) {
        let totalCount: Int
        if let success = currentSuccess {
            totalCount = Int(success.itemCount)
        } else {
            totalCount = 5
        }
        cell.configurePlaceholder(
            index: index,
            totalCount: totalCount,
            appearance: appearanceSettings,
            isMultipleColumn: columnCount > 1
        )
    }

    private func configureErrorCell(_ cell: TimelineUIKitCollectionViewCell) {
        guard let data = currentData, case .error(let errorState) = onEnum(of: data) else { return }
        let errorView = ListErrorUIView()
        errorView.onOpenURL = openURL
        errorView.configure(error: errorState.error) {
            errorState.onRetry()
        }
        cell.setHostedView(CenteredCellContentView(content: errorView))
    }

    private func configureFooterErrorCell(_ cell: TimelineUIKitCollectionViewCell) {
        guard let success = currentSuccess else { return }
        if case .error(let error) = onEnum(of: success.appendState) {
            let errorView = ListErrorUIView()
            errorView.onOpenURL = openURL
            errorView.configure(error: error.error) {
                success.retry()
            }
            cell.setHostedView(UIView.padding(errorView, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)))
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

    // MARK: - State Update

    func update(data: PagingState<UiTimelineV2>) {
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
    }

    private func syncRefreshControl(data: PagingState<UiTimelineV2>) {
        let isRefreshing = pagingIsRefreshing(data)
        if isRefreshing {
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

    private func applySnapshot(data: PagingState<UiTimelineV2>) {
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        var newIndexMap: [String: Int] = [:]
        var newRenderHashMap: [String: Int32] = [:]
        var itemIDs: [String] = []
        var footerIDs: [String] = []

        switch onEnum(of: data) {
        case .loading:
            snapshot.appendSections([Self.sectionMain])
            let items = (0..<5).map { "\(Self.placeholderPrefix)\($0)" }
            itemIDs = items
            snapshot.appendItems(items, toSection: Self.sectionMain)

        case .error:
            snapshot.appendSections([Self.sectionMain])
            itemIDs = [Self.errorID]
            snapshot.appendItems(itemIDs, toSection: Self.sectionMain)

        case .empty:
            snapshot.appendSections([Self.sectionMain])
            itemIDs = [Self.emptyID]
            snapshot.appendItems(itemIDs, toSection: Self.sectionMain)

        case .success(let success):
            snapshot.appendSections([Self.sectionMain])
            let itemCount = Int(success.itemCount)
            var items: [String] = []
            items.reserveCapacity(itemCount)
            for i in 0..<itemCount {
                let peeked = success.peek(index: Int32(i))
                let id = "\(Self.timelinePrefix)\(peeked?.itemKey ?? "idx_\(i)")"
                items.append(id)
                newIndexMap[id] = i
                if let peeked {
                    newRenderHashMap[id] = peeked.renderHash
                }
            }
            itemIDs = items
            snapshot.appendItems(items, toSection: Self.sectionMain)

            // Footer
            let footer = footerItemIDs(for: success)
            footerIDs = footer
            if !footer.isEmpty {
                snapshot.appendSections([Self.sectionFooter])
                snapshot.appendItems(footer, toSection: Self.sectionFooter)
            }
        }

        itemIndexMap = newIndexMap
        let newSignature = SnapshotSignature(itemIDs: itemIDs, footerIDs: footerIDs)
        let previousSignature = lastAppliedSignature

        if previousSignature?.itemIDs == newSignature.itemIDs,
           previousSignature?.footerIDs == newSignature.footerIDs {
            let changedIDs = itemIDs.filter { lastRenderHashMap[$0] != newRenderHashMap[$0] }
            lastRenderHashMap = newRenderHashMap
            reconfigureItems(changedIDs)
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        if previousSignature?.itemIDs == newSignature.itemIDs {
            let changedIDs = itemIDs.filter { lastRenderHashMap[$0] != newRenderHashMap[$0] }
            applyFooterSnapshot(footerIDs: footerIDs, reconfigureIDs: changedIDs, data: data)
            lastAppliedSignature = newSignature
            lastRenderHashMap = newRenderHashMap
            validateCurrentAutoplayVisibility()
            scheduleAutoplaySelection()
            return
        }

        // Reconfigure existing items so cells pick up data changes (e.g. like count)
        let existing = dataSource.snapshot().itemIdentifiers
        let newSet = Set(snapshot.itemIdentifiers)
        let toReconfigure = existing.filter { newSet.contains($0) }
        if !toReconfigure.isEmpty {
            snapshot.reconfigureItems(toReconfigure)
        }
        
        let shouldAnimateDifferences =
                    !pagingIsRefreshing(data) &&
                    !refreshControl.isRefreshing &&
                    !collectionView.isDragging &&
                    !collectionView.isDecelerating
        dataSource.apply(snapshot, animatingDifferences: shouldAnimateDifferences)
        lastAppliedSignature = newSignature
        lastRenderHashMap = newRenderHashMap
        validateCurrentAutoplayVisibility()
        scheduleAutoplaySelection()
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

    private func applyFooterSnapshot(footerIDs: [String], reconfigureIDs: [String], data: PagingState<UiTimelineV2>) {
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
                    !pagingIsRefreshing(data) &&
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
        switch appearanceSettings.videoAutoplay {
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
            let key = "__placeholder__:\(Int(width))"
            if let cached = heightCache[key] { return CGSize(width: width, height: cached) }
            let totalCount = currentSuccess.map { Int($0.itemCount) } ?? 5
            sizingPlaceholderCard.timelineDisplayMode = appearanceSettings.timelineDisplayMode
            sizingPlaceholderCard.isMultipleColumn = true
            sizingPlaceholderCard.configure(index: 0, totalCount: totalCount)
            let target = CGSize(width: width, height: UIView.layoutFittingCompressedSize.height)
            let size = sizingPlaceholderCard.systemLayoutSizeFitting(
                target,
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            )
            let height = max(size.height, 120)
            heightCache[key] = height
            return CGSize(width: width, height: height)
        }

        if itemID.hasPrefix(Self.timelinePrefix),
           let index = itemIndexMap[itemID],
           let success = currentSuccess,
           index >= 0,
           index < Int(success.itemCount),
           let item = success.peek(index: Int32(index)) {
            let key = "\(itemID):\(item.renderHash):\(Int(width))"
            if let cached = heightCache[key] { return CGSize(width: width, height: cached) }
            sizingTimelineCard.timelineDisplayMode = appearanceSettings.timelineDisplayMode
            sizingTimelineCard.isMultipleColumn = true
            sizingTimelineCard.configure(index: index, totalCount: Int(success.itemCount))
            sizingTimelineView.configure(
                data: item,
                appearance: appearanceSettings,
                detailStatusKey: detailStatusKey,
                onOpenURL: nil
            )
            let target = CGSize(width: width, height: UIView.layoutFittingCompressedSize.height)
            let size = sizingTimelineCard.systemLayoutSizeFitting(
                target,
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            )
            let height = max(size.height, 120)
            heightCache[key] = height
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
        guard let host = currentAutoplayHostView,
              host.isDescendant(of: cell) else {
            return
        }
        detachAutoplayPlayer(pause: true)
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = true
        autoplaySelectionTask?.cancel()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        validateCurrentAutoplayVisibility()
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            scrollingState.isScrolling = false
            scheduleAutoplaySelection()
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = false
        scheduleAutoplaySelection()
    }
}

private final class TimelineUIKitCollectionViewCell: UICollectionViewCell {
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private var hostedBottomConstraint: NSLayoutConstraint?
    private let timelineView = TimelineUIView()
    private let timelineCard = AdaptiveTimelineCardUIView()
    private let placeholderView = TimelinePlaceholderUIView()
    private let placeholderCard = AdaptiveTimelineCardUIView()

    // Rebuild-skip signature. When the incoming data + appearance + detail-key are
    // identical to the previous configure we short-circuit the expensive
    // `TimelineUIView.configure` → `StatusUIKitView.rebuild()` path.
    private var lastRenderHash: Int32?
    private var lastItemKey: String?
    private var lastAppearanceIdentity: ObjectIdentifier?
    private var lastDetailStatusKey: String?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
        timelineCard.setContent(UIView.padding(timelineView, insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
        placeholderCard.setContent(UIView.padding(placeholderView, insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        // Reset signature so a recycled cell always rebuilds for its new tenant,
        // even in the (unlikely) event that renderHash/itemKey collide.
        lastRenderHash = nil
        lastItemKey = nil
        lastAppearanceIdentity = nil
        lastDetailStatusKey = nil
    }

    func autoplayCandidates(prefix: String) -> [TimelineVideoAutoplayCandidate] {
        timelineView.autoplayCandidates(prefix: prefix)
    }

    func configureTimeline(
        data: UiTimelineV2,
        index: Int,
        totalCount: Int,
        appearance: AppearanceSettings,
        detailStatusKey: MicroBlogKey?,
        isMultipleColumn: Bool,
        openURL: ((URL) -> Void)?
    ) {
        // Card styling is cheap; always reapply so index/totalCount changes
        // (affecting the card's outer rounded corners) are picked up.
        timelineCard.timelineDisplayMode = appearance.timelineDisplayMode
        timelineCard.isMultipleColumn = isMultipleColumn
        timelineCard.configure(index: index, totalCount: totalCount)

        let itemKey = data.itemKey ?? ""
        let detailKeyStr = detailStatusKey.map { String(describing: $0) } ?? ""
        let appearanceId = ObjectIdentifier(appearance)

        let dataUnchanged =
            lastRenderHash == data.renderHash &&
            lastItemKey == itemKey &&
            lastAppearanceIdentity == appearanceId &&
            lastDetailStatusKey == detailKeyStr

        if !dataUnchanged {
            lastRenderHash = data.renderHash
            lastItemKey = itemKey
            lastAppearanceIdentity = appearanceId
            lastDetailStatusKey = detailKeyStr
            timelineView.configure(
                data: data,
                appearance: appearance,
                detailStatusKey: detailStatusKey,
                onOpenURL: openURL
            )
        } else {
            // Same render state — just refresh the click callback in case the
            // parent routed a new openURL handler through.
            timelineView.onOpenURL = openURL
        }
        setHostedView(timelineCard)
    }

    func configurePlaceholder(index: Int, totalCount: Int, appearance: AppearanceSettings, isMultipleColumn: Bool) {
        placeholderCard.timelineDisplayMode = appearance.timelineDisplayMode
        placeholderCard.isMultipleColumn = isMultipleColumn
        placeholderCard.configure(index: index, totalCount: totalCount)
        setHostedView(placeholderCard)
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
