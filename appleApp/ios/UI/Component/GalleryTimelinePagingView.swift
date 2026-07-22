import SwiftUI
import FlareAppleUI
import UIKit
import Kingfisher
import KotlinSharedUI
import CHTCollectionViewWaterfallLayout
import FlareAppleCore

// MARK: - SwiftUI Wrapper

struct UIGalleryTimelinePagingView: UIViewControllerRepresentable {
    let data: PagingState<UiTimelineV2>
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.openURL) private var openURL
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    func makeUIViewController(context: Context) -> UIGalleryTimelineController {
        let controller = UIGalleryTimelineController()
        apply(to: controller)
        controller.update(data: data)
        return controller
    }

    func updateUIViewController(_ controller: UIGalleryTimelineController, context: Context) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.openURL = { url in openURL.callAsFunction(url) }
        // Apply data before appearance so the appearance setter's reconfigure
        // sees a coherent itemIndexMap / currentSuccess pair.
        controller.update(data: data)
        controller.appearance = GalleryUIKitAppearance(
            timeline: timelineAppearance,
            showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
        )
    }

    private func apply(to controller: UIGalleryTimelineController) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.appearance = GalleryUIKitAppearance(
            timeline: timelineAppearance,
            showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
        )
        controller.openURL = { url in openURL.callAsFunction(url) }
    }
}

// MARK: - Controller

final class UIGalleryTimelineController: UIViewController, UICollectionViewDelegate, CHTCollectionViewDelegateWaterfallLayout {

    private static let sectionMain = 0
    private static let sectionFooter = 1

    private static let itemPrefix = "g:"
    private static let placeholderPrefix = "gp:"
    private static let emptyID = "__g_empty__"
    private static let errorID = "__g_error__"
    private static let footerLoadingID = "__g_fl__"
    private static let footerErrorID = "__g_fe__"
    private static let footerEndID = "__g_fend__"

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

    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?
    private var itemIndexMap: [String: Int] = [:]
    private var lastAppliedSignature: SnapshotSignature?
    private var lastRenderHashMap: [String: Int32] = [:]
    private var lastLoadedItemIDs: Set<String> = []
    private var lastProcessedDataRef: AnyObject?
    private var lastProcessedUpdateSignature: UpdateSignature?

    var refreshCallback: (() async -> Void)?
    var openURL: ((URL) -> Void)?
    var appearance = GalleryUIKitAppearance(timeline: TimelineAppearance.companion.Default) {
        didSet {
            guard isViewLoaded else { return }
            guard oldValue != appearance else {
                return
            }
            removeAllHeightCache()
            collectionView.collectionViewLayout.invalidateLayout()
            reconfigureVisibleCells()
        }
    }

    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private let refreshControl = UIRefreshControl()
    private let sizingPostTile = GalleryPostTileUIView()
    private let sizingFeedTile = GalleryFeedTileUIView()
    private var heightCache: [String: CGFloat] = [:]
    private var heightCacheKeysByItemID: [String: Set<String>] = [:]
    private var isUserRefreshing = false
    private var pendingScrollAnchor: ScrollAnchor?
    private var isRestoringScrollAnchor = false
    private var scrollingState = IsScrollingState()

    private struct SnapshotSignature: Equatable {
        let itemIDs: [String]
        let footerIDs: [String]
    }

    private struct ItemSnapshotState {
        let itemIDs: [String]
        let indexMap: [String: Int]
        let renderHashMap: [String: Int32]
        let loadedItemIDs: Set<String>
    }

    private struct ScrollAnchor {
        let itemID: String
        let distanceFromViewportTop: CGFloat
    }

    private enum UpdateSignature: Equatable {
        case loading
        case error
        case empty
        case success(
            itemIDs: [String],
            renderHashMap: [String: Int32],
            loadedItemIDs: Set<String>,
            isRefreshing: Bool,
            footerIDs: [String]
        )
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        setupCollectionView()
        setupDataSource()
        setupRefreshControl()
        if let data = currentData {
            applySnapshot(data: data)
            lastProcessedDataRef = data as AnyObject
            lastProcessedUpdateSignature = updateSignature(for: data)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateColumnCount()
    }

    // MARK: - Setup

    private func setupCollectionView() {
        let layout = CHTCollectionViewWaterfallLayout()
        layout.columnCount = 2
        layout.minimumColumnSpacing = 8
        layout.minimumInteritemSpacing = 8
        layout.sectionInset = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        layout.itemRenderDirection = .shortestFirst

        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .systemGroupedBackground
        collectionView.delegate = self
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupDataSource() {
        let cellReg = UICollectionView.CellRegistration<GalleryTimelineCollectionViewCell, String> {
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

    private func updateColumnCount() {
        guard let layout = collectionView.collectionViewLayout as? CHTCollectionViewWaterfallLayout else { return }
        let width = collectionView.bounds.width
        guard width > 0 else { return }
        let isWide = width >= 600
        let target: CGFloat = isWide ? 240 : 160
        let columns = max(Int((width / target).rounded(.down)), 2)
        if layout.columnCount != columns {
            layout.columnCount = columns
            removeAllHeightCache()
            layout.invalidateLayout()
        }
    }

    // MARK: - Cell configuration

    private func configureCell(_ cell: GalleryTimelineCollectionViewCell, itemID: String) {
        if itemID.hasPrefix(Self.itemPrefix) {
            if let index = itemIndexMap[itemID],
               let success = currentSuccess,
               index >= 0,
               index < Int(success.itemCount),
               let item = success.peek(index: Int32(index)) {
                cell.configureTile(item: item, appearance: appearance, openURL: openURL)
            } else {
                cell.configurePlaceholder()
            }
        } else if itemID.hasPrefix(Self.placeholderPrefix) {
            cell.configurePlaceholder()
        } else if itemID == Self.emptyID {
            cell.setHostedView(ListEmptyUIView())
        } else if itemID == Self.errorID, let data = currentData,
                  case .error(let errorState) = onEnum(of: data) {
            let view = ListErrorUIView()
            view.onOpenURL = openURL
            view.configure(error: errorState.error) { errorState.onRetry() }
            cell.setHostedView(view)
        } else if itemID == Self.footerLoadingID {
            cell.setHostedView(GalleryLoadingFooterUIView())
        } else if itemID == Self.footerErrorID, let success = currentSuccess,
                  case .error(let error) = onEnum(of: success.appendState) {
            let view = ListErrorUIView()
            view.onOpenURL = openURL
            view.configure(error: error.error) { success.retry() }
            cell.setHostedView(view)
        } else if itemID == Self.footerEndID {
            cell.setHostedView(GalleryEndFooterUIView())
        }
    }

    private func reconfigureVisibleCells() {
        let visibleIDs = collectionView.indexPathsForVisibleItems.compactMap {
            dataSource.itemIdentifier(for: $0)
        }
        guard !visibleIDs.isEmpty else { return }
        var snapshot = dataSource.snapshot()
        snapshot.reconfigureItems(visibleIDs)
        dataSource.apply(snapshot, animatingDifferences: false)
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
        case .loading: return true
        case .success(let success): return success.isRefreshing
        default: return false
        }
    }

    // MARK: - State

    func update(data: PagingState<UiTimelineV2>) {
        let dataRef = data as AnyObject
        let newUpdateSignature = updateSignature(for: data)

        currentData = data
        if case .success(let success) = onEnum(of: data) {
            currentSuccess = success
        } else {
            currentSuccess = nil
        }
        guard isViewLoaded else { return }

        if pagingIsRefreshing(data) {
            if !refreshControl.isRefreshing, !isUserRefreshing {
                refreshControl.beginRefreshing()
            }
        } else if !isUserRefreshing, refreshControl.isRefreshing {
            refreshControl.endRefreshing()
        }

        if let lastRef = lastProcessedDataRef,
           lastRef === dataRef,
           lastProcessedUpdateSignature == newUpdateSignature,
           lastAppliedSignature != nil {
            return
        }

        applySnapshot(data: data)
        lastProcessedDataRef = dataRef
        lastProcessedUpdateSignature = newUpdateSignature
    }

    private func updateSignature(for data: PagingState<UiTimelineV2>) -> UpdateSignature {
        switch onEnum(of: data) {
        case .loading:
            return .loading
        case .error:
            return .error
        case .empty:
            return .empty
        case .success(let success):
            let itemState = makeItemSnapshotState(for: success)
            return .success(
                itemIDs: itemState.itemIDs,
                renderHashMap: itemState.renderHashMap,
                loadedItemIDs: itemState.loadedItemIDs,
                isRefreshing: success.isRefreshing,
                footerIDs: footerItemIDs(for: success)
            )
        }
    }

    private func makeItemSnapshotState(for success: PagingStateSuccess<UiTimelineV2>) -> ItemSnapshotState {
        let itemCount = Int(success.itemCount)
        var loadedIDsByIndex: [Int: String] = [:]
        var loadedRenderHashByItemID: [String: Int32] = [:]
        var loadedItemIDs = Set<String>()

        for i in 0..<itemCount {
            guard let peeked = success.peek(index: Int32(i)) else {
                continue
            }
            let itemKey = Self.itemIdentityKey(for: peeked)
            let id = "\(Self.itemPrefix)\(itemKey)"
            loadedIDsByIndex[i] = id
            loadedRenderHashByItemID[id] = peeked.renderHash
            loadedItemIDs.insert(id)
        }

        var itemIDs: [String] = []
        var indexMap: [String: Int] = [:]
        var renderHashMap: [String: Int32] = [:]
        itemIDs.reserveCapacity(itemCount)

        for i in 0..<itemCount {
            let id: String
            if let loadedID = loadedIDsByIndex[i] {
                id = loadedID
                renderHashMap[id] = loadedRenderHashByItemID[id]
            } else {
                id = "\(Self.placeholderPrefix)\(i)"
            }
            itemIDs.append(id)
            indexMap[id] = i
        }

        return ItemSnapshotState(
            itemIDs: itemIDs,
            indexMap: indexMap,
            renderHashMap: renderHashMap,
            loadedItemIDs: loadedItemIDs
        )
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

    private func isGalleryDataItemID(_ itemID: String) -> Bool {
        itemID.hasPrefix(Self.itemPrefix)
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
                      isGalleryDataItemID(itemID) else {
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

    private func applySnapshot(data: PagingState<UiTimelineV2>) {
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        var newIndexMap: [String: Int] = [:]
        var newRenderHashMap: [String: Int32] = [:]
        var newLoadedItemIDs = Set<String>()
        var itemIDs: [String] = []
        var footerIDs: [String] = []

        switch onEnum(of: data) {
        case .loading:
            snapshot.appendSections([Self.sectionMain])
            let items = (0..<8).map { "\(Self.placeholderPrefix)\($0)" }
            itemIDs = items
            snapshot.appendItems(itemIDs, toSection: Self.sectionMain)
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
            let itemState = makeItemSnapshotState(for: success)
            itemIDs = itemState.itemIDs
            newIndexMap = itemState.indexMap
            newRenderHashMap = itemState.renderHashMap
            newLoadedItemIDs = itemState.loadedItemIDs
            snapshot.appendItems(itemIDs, toSection: Self.sectionMain)

            let footer = footerItemIDs(for: success)
            footerIDs = footer
            if !footer.isEmpty {
                snapshot.appendSections([Self.sectionFooter])
                snapshot.appendItems(footer, toSection: Self.sectionFooter)
            }
        }

        let newSignature = SnapshotSignature(itemIDs: itemIDs, footerIDs: footerIDs)
        let previousSignature = lastAppliedSignature
        let scrollAnchor = previousSignature != nil &&
            previousSignature?.itemIDs != newSignature.itemIDs &&
            allowsScrollAnchorRestoration
            ? captureScrollAnchor()
            : nil

        itemIndexMap = newIndexMap
        pruneHeightCache(keepingItemIDs: Set(newIndexMap.keys))

        if previousSignature == newSignature {
            let changedIDs = changedItemIDs(
                in: itemIDs,
                newRenderHashMap: newRenderHashMap,
                newLoadedItemIDs: newLoadedItemIDs
            )
            lastRenderHashMap = newRenderHashMap
            lastLoadedItemIDs = newLoadedItemIDs
            reconfigureItems(changedIDs)
            return
        }

        if previousSignature?.itemIDs == newSignature.itemIDs {
            let changedIDs = changedItemIDs(
                in: itemIDs,
                newRenderHashMap: newRenderHashMap,
                newLoadedItemIDs: newLoadedItemIDs
            )
            applyFooterSnapshot(footerIDs: footerIDs, reconfigureIDs: changedIDs, data: data)
            lastAppliedSignature = newSignature
            lastRenderHashMap = newRenderHashMap
            lastLoadedItemIDs = newLoadedItemIDs
            return
        }

        let existing = dataSource.snapshot().itemIdentifiers
        let newSet = Set(snapshot.itemIdentifiers)
        let toReconfigure = existing.filter {
            newSet.contains($0) && itemNeedsReconfigure(
                $0,
                newRenderHashMap: newRenderHashMap,
                newLoadedItemIDs: newLoadedItemIDs
            )
        }
        if !toReconfigure.isEmpty {
            snapshot.reconfigureItems(toReconfigure)
        }

        let shouldAnimate =
            !pagingIsRefreshing(data) &&
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
                }
                restoreScrollAnchorIfNeeded(scrollAnchor)
                collectionView.layer.removeAllAnimations()
                CATransaction.commit()
            }
        } else {
            dataSource.apply(snapshot, animatingDifferences: shouldAnimate)
        }
        lastAppliedSignature = newSignature
        lastRenderHashMap = newRenderHashMap
        lastLoadedItemIDs = newLoadedItemIDs
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

    private func reconfigureItems(_ itemIDs: [String]) {
        guard !itemIDs.isEmpty else { return }
        var snapshot = dataSource.snapshot()
        let existingItems = Set(snapshot.itemIdentifiers)
        let reconfigureIDs = itemIDs.filter { existingItems.contains($0) }
        guard !reconfigureIDs.isEmpty else { return }
        snapshot.reconfigureItems(reconfigureIDs)
        dataSource.apply(snapshot, animatingDifferences: false)
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

        let shouldAnimate =
            !pagingIsRefreshing(data) &&
            !refreshControl.isRefreshing &&
            !collectionView.isDragging &&
            !collectionView.isDecelerating
        dataSource.apply(snapshot, animatingDifferences: shouldAnimate)
    }

    private func footerItemIDs(for success: PagingStateSuccess<UiTimelineV2>) -> [String] {
        switch onEnum(of: success.appendState) {
        case .error: return [Self.footerErrorID]
        case .loading: return [Self.footerLoadingID]
        case .notLoading(let notLoading):
            return notLoading.endOfPaginationReached ? [Self.footerEndID] : []
        }
    }

    // MARK: - CHT sizing

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> CGSize {
        guard let layout = collectionViewLayout as? CHTCollectionViewWaterfallLayout else {
            return CGSize(width: collectionView.bounds.width, height: 200)
        }
        let section = indexPath.section
        let columns = section == Self.sectionFooter ? 1 : max(layout.columnCount, 1)
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
        default: break
        }

        if itemID.hasPrefix(Self.placeholderPrefix) {
            return CGSize(width: width, height: width + 40)
        }

        if itemID.hasPrefix(Self.itemPrefix),
           let index = itemIndexMap[itemID],
           let success = currentSuccess,
           index >= 0,
           index < Int(success.itemCount),
           let item = success.peek(index: Int32(index)) {
            return CGSize(width: width, height: estimatedHeight(for: item, itemID: itemID, width: width))
        }

        return CGSize(width: width, height: 200)
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        columnCountFor section: Int
    ) -> Int {
        guard let layout = collectionViewLayout as? CHTCollectionViewWaterfallLayout else { return 2 }
        return section == Self.sectionFooter ? 1 : max(layout.columnCount, 1)
    }

    private func estimatedHeight(for item: UiTimelineV2, itemID: String, width: CGFloat) -> CGFloat {
        let cacheKey = heightCacheKey(for: item, itemID: itemID, width: width)
        if let cached = heightCache[cacheKey] {
            return cached
        }

        let measuredHeight: CGFloat
        switch onEnum(of: item) {
        case .post(let post):
            sizingPostTile.configure(post: post, appearance: appearance, loadsRemoteImages: false)
            measuredHeight = measuredTileHeight(sizingPostTile, width: width)
        case .timelinePostItem:
            if let post = item.timelineContentPost {
                sizingPostTile.configure(post: post, appearance: appearance, loadsRemoteImages: false)
                measuredHeight = measuredTileHeight(sizingPostTile, width: width)
            } else {
                measuredHeight = 180
            }
        case .feed(let feed):
            sizingFeedTile.configure(feed: feed, appearance: appearance, loadsRemoteImages: false)
            measuredHeight = measuredTileHeight(sizingFeedTile, width: width)
        default:
            measuredHeight = 180
        }

        setCachedHeight(measuredHeight, for: cacheKey, itemID: itemID)
        return measuredHeight
    }

    private func heightCacheKey(for item: UiTimelineV2, itemID: String, width: CGFloat) -> String {
        heightCacheKey(itemID: itemID, renderHash: item.renderHash, width: width)
    }

    private func heightCacheKey(itemID: String, renderHash: Int32, width: CGFloat) -> String {
        let scaledWidth = Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
        return [
            itemID,
            String(renderHash),
            String(scaledWidth),
            appearance.showMedia ? "media" : "text",
            appearance.avatarShapeID,
            traitCollection.preferredContentSizeCategory.rawValue,
        ].joined(separator: "|")
    }

    private func setCachedHeight(_ height: CGFloat, for key: String, itemID: String) {
        heightCache[key] = height
        heightCacheKeysByItemID[itemID, default: []].insert(key)
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

    private func removeAllHeightCache() {
        heightCache.removeAll(keepingCapacity: true)
        heightCacheKeysByItemID.removeAll(keepingCapacity: true)
    }

    private func measuredTileHeight(_ tile: UIView, width: CGFloat) -> CGFloat {
        tile.bounds = CGRect(x: 0, y: 0, width: width, height: UIView.layoutFittingExpandedSize.height)
        tile.setNeedsLayout()
        let size = tile.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingExpandedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return ceil(size.height) + 1
    }

    // MARK: - Delegate

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        guard let success = currentSuccess,
              let itemID = dataSource.itemIdentifier(for: indexPath),
              let index = itemIndexMap[itemID],
              index >= 0,
              index < Int(success.itemCount) else { return }
        _ = success.get(index: Int32(index))
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = true
        pendingScrollAnchor = nil
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        restorePendingScrollAnchorIfNeeded()
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            scrollingState.isScrolling = false
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = false
    }

    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = false
    }
}

// MARK: - UIKit tile views

private final class GalleryTimelineCollectionViewCell: UICollectionViewCell {
    private var hostedView: UIView?
    private var hostedConstraints: [NSLayoutConstraint] = []
    private let tileView = GalleryTimelineTileUIView()
    private let placeholderView = GalleryPlaceholderTileUIView()

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
        tileView.prepareForReuse()
    }

    func configureTile(item: UiTimelineV2, appearance: GalleryUIKitAppearance, openURL: ((URL) -> Void)?) {
        tileView.onOpenURL = openURL
        tileView.configure(item: item, appearance: appearance)
        setHostedView(tileView)
    }

    func configurePlaceholder() {
        setHostedView(placeholderView)
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
        NSLayoutConstraint.activate(hostedConstraints)
    }
}

private final class GalleryTimelineTileUIView: UIView {
    var onOpenURL: ((URL) -> Void)? {
        didSet {
            postTile.onOpenURL = onOpenURL
            feedTile.onOpenURL = onOpenURL
        }
    }

    private let postTile = GalleryPostTileUIView()
    private let feedTile = GalleryFeedTileUIView()
    private var installedTile: UIView?
    private var installedConstraints: [NSLayoutConstraint] = []

    func prepareForReuse() {
        setInstalledTile(nil)
        onOpenURL = nil
        postTile.prepareForReuse()
        feedTile.prepareForReuse()
    }

    func configure(item: UiTimelineV2, appearance: GalleryUIKitAppearance) {
        switch onEnum(of: item) {
        case .post(let post):
            postTile.configure(post: post, appearance: appearance)
            setInstalledTile(postTile)
        case .timelinePostItem:
            if let post = item.timelineContentPost {
                postTile.configure(post: post, appearance: appearance)
                setInstalledTile(postTile)
            } else {
                setInstalledTile(nil)
            }
        case .feed(let feed):
            feedTile.configure(feed: feed, appearance: appearance)
            setInstalledTile(feedTile)
        default:
            setInstalledTile(nil)
        }
    }

    private func setInstalledTile(_ tile: UIView?) {
        if installedTile === tile {
            tile?.invalidateIntrinsicContentSize()
            tile?.setNeedsLayout()
            return
        }

        NSLayoutConstraint.deactivate(installedConstraints)
        installedConstraints = []
        installedTile?.removeFromSuperview()
        installedTile = tile

        guard let tile else { return }
        tile.translatesAutoresizingMaskIntoConstraints = false
        addSubview(tile)
        installedConstraints = [
            tile.topAnchor.constraint(equalTo: topAnchor),
            tile.leadingAnchor.constraint(equalTo: leadingAnchor),
            tile.trailingAnchor.constraint(equalTo: trailingAnchor),
            tile.bottomAnchor.constraint(equalTo: bottomAnchor),
        ]
        NSLayoutConstraint.activate(installedConstraints)
    }
}

private final class GalleryPostTileUIView: UIView, UIGestureRecognizerDelegate {
    var onOpenURL: ((URL) -> Void)? {
        didSet {
            bodyText.onOpenURL = onOpenURL
            bodyTranslationText.onOpenURL = onOpenURL
            userName.onOpenURL = onOpenURL
        }
    }

    private var post: UiTimelineV2.Post?
    private var mediaPreviewURL: String?

    private let stack = UIStackView()
    private let imageView = GalleryNetworkImageView(frame: .zero)
    private let playBadge: UIImageView = {
        let imageView = UIImageView(image: UIImage(fontAwesome: .circlePlay))
        imageView.contentMode = .scaleAspectFit
        imageView.tintColor = .white
        imageView.translatesAutoresizingMaskIntoConstraints = false
        return imageView
    }()
    private let playBadgeBackground: UIView = {
        let view = UIView()
        view.backgroundColor = .black
        view.layer.cornerRadius = 16
        view.translatesAutoresizingMaskIntoConstraints = false
        view.isHidden = true
        return view
    }()
    private let textContainer = UIView()
    private let bodyText = RichTextUIView()
    private let bodyTranslationText = RichTextUIView()
    private let userRow = UIStackView()
    private let avatar = AvatarUIView()
    private let userName = RichTextUIView()
    private var imageAspectConstraint: NSLayoutConstraint?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .secondarySystemGroupedBackground
        layer.cornerRadius = 12
        clipsToBounds = true

        stack.axis = .vertical
        stack.alignment = .fill
        stack.distribution = .fill
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        imageView.isUserInteractionEnabled = true
        stack.addArrangedSubview(imageView)
        imageView.addSubview(playBadgeBackground)
        playBadgeBackground.addSubview(playBadge)
        NSLayoutConstraint.activate([
            playBadgeBackground.leadingAnchor.constraint(equalTo: imageView.leadingAnchor, constant: 12),
            playBadgeBackground.bottomAnchor.constraint(equalTo: imageView.bottomAnchor, constant: -12),
            playBadgeBackground.widthAnchor.constraint(equalToConstant: 32),
            playBadgeBackground.heightAnchor.constraint(equalToConstant: 32),
            playBadge.centerXAnchor.constraint(equalTo: playBadgeBackground.centerXAnchor),
            playBadge.centerYAnchor.constraint(equalTo: playBadgeBackground.centerYAnchor),
            playBadge.widthAnchor.constraint(equalToConstant: 16),
            playBadge.heightAnchor.constraint(equalToConstant: 16),
        ])

        bodyText.baseTextStyle = .subheadline
        bodyText.lineLimit = 5
        bodyText.fixedVertical = true
        bodyText.setContentHuggingPriority(.required, for: .vertical)
        bodyText.setContentCompressionResistancePriority(.required, for: .vertical)
        bodyTranslationText.baseTextStyle = .subheadline
        bodyTranslationText.lineLimit = 5
        bodyTranslationText.fixedVertical = true
        bodyTranslationText.setContentHuggingPriority(.required, for: .vertical)
        bodyTranslationText.setContentCompressionResistancePriority(.required, for: .vertical)
        textContainer.setContentHuggingPriority(.required, for: .vertical)
        textContainer.setContentCompressionResistancePriority(.required, for: .vertical)
        bodyText.translatesAutoresizingMaskIntoConstraints = false
        bodyTranslationText.translatesAutoresizingMaskIntoConstraints = false
        textContainer.addSubview(bodyText)
        textContainer.addSubview(bodyTranslationText)
        NSLayoutConstraint.activate([
            bodyText.topAnchor.constraint(equalTo: textContainer.topAnchor, constant: 8),
            bodyText.leadingAnchor.constraint(equalTo: textContainer.leadingAnchor, constant: 8),
            bodyText.trailingAnchor.constraint(equalTo: textContainer.trailingAnchor, constant: -8),
            bodyTranslationText.topAnchor.constraint(equalTo: bodyText.bottomAnchor, constant: 4),
            bodyTranslationText.leadingAnchor.constraint(equalTo: textContainer.leadingAnchor, constant: 8),
            bodyTranslationText.trailingAnchor.constraint(equalTo: textContainer.trailingAnchor, constant: -8),
            bodyTranslationText.bottomAnchor.constraint(equalTo: textContainer.bottomAnchor),
        ])
        stack.addArrangedSubview(textContainer)

        userRow.axis = .horizontal
        userRow.alignment = .center
        userRow.spacing = 6
        userRow.isLayoutMarginsRelativeArrangement = true
        userRow.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 6, leading: 8, bottom: 6, trailing: 8)
        userRow.setContentHuggingPriority(.required, for: .vertical)
        userRow.setContentCompressionResistancePriority(.required, for: .vertical)
        userRow.addArrangedSubview(avatar)
        userRow.addArrangedSubview(userName)
        avatar.widthAnchor.constraint(equalToConstant: 20).isActive = true
        avatar.heightAnchor.constraint(equalToConstant: 20).isActive = true
        avatar.setContentHuggingPriority(.required, for: .vertical)
        avatar.setContentCompressionResistancePriority(.required, for: .vertical)
        userName.baseTextStyle = .caption1
        userName.lineLimit = 1
        userName.fixedVertical = true
        userName.setContentHuggingPriority(.required, for: .vertical)
        userName.setContentCompressionResistancePriority(.required, for: .vertical)
        stack.addArrangedSubview(userRow)

        let rootTap = UITapGestureRecognizer(target: self, action: #selector(onRootTapped))
        rootTap.delegate = self
        addGestureRecognizer(rootTap)

        let imageTap = UITapGestureRecognizer(target: self, action: #selector(onImageTapped))
        imageView.addGestureRecognizer(imageTap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func prepareForReuse() {
        post = nil
        mediaPreviewURL = nil
        imageAspectConstraint?.isActive = false
        imageAspectConstraint = nil
        imageView.cancel()
        playBadgeBackground.isHidden = true
        avatar.set(url: nil)
        bodyText.text = nil
        bodyTranslationText.text = nil
        bodyTranslationText.isHidden = true
        userName.text = nil
        onOpenURL = nil
    }

    func configure(post: UiTimelineV2.Post, appearance: GalleryUIKitAppearance, loadsRemoteImages: Bool = true) {
        self.post = post
        avatar.avatarShape = appearance.avatarShape

        imageView.isHidden = true
        playBadgeBackground.isHidden = true
        imageView.cancel()
        textContainer.isHidden = true
        bodyText.text = nil
        bodyTranslationText.text = nil
        bodyTranslationText.isHidden = true
        mediaPreviewURL = nil
        imageAspectConstraint?.isActive = false
        imageAspectConstraint = nil

        if appearance.showMedia, let media = post.images.first {
            let preview = previewURL(for: media)
            mediaPreviewURL = preview
            if let preview {
                imageView.isHidden = false
                playBadgeBackground.isHidden = !isVideo(media)
                if loadsRemoteImages {
                    imageView.set(url: preview, customHeaders: media.customHeaders)
                }
                let ratio = max(media.aspectRatio ?? 1.0, 0.3)
                imageAspectConstraint = imageView.heightAnchor.constraint(equalTo: imageView.widthAnchor, multiplier: 1 / ratio)
                imageAspectConstraint?.isActive = true
            }
        } else {
            let contents: [UiRichText] = if post.translationDisplayState == .translated,
                                            let translation = post.content.translation {
                appearance.showOriginalWithTranslation ? [post.content.original, translation] : [translation]
            } else {
                [post.content.original]
            }
            if contents.contains(where: { !$0.isEmpty }) {
                textContainer.isHidden = false
                bodyText.text = contents[0]
                if contents.count > 1 {
                    bodyTranslationText.text = contents[1]
                    bodyTranslationText.isHidden = false
                }
            }
        }

        if let user = post.user {
            userRow.isHidden = false
            avatar.set(
                url: loadsRemoteImages ? user.avatar?.url : nil,
                customHeaders: loadsRemoteImages ? user.avatar?.customHeaders : nil
            )
            userName.text = user.name
        } else {
            userRow.isHidden = true
            avatar.set(url: nil)
            userName.text = nil
        }
    }

    private func isVideo(_ media: any UiMedia) -> Bool {
        if case .video = onEnum(of: media) {
            return true
        }
        return false
    }

    private func previewURL(for media: any UiMedia) -> String? {
        switch onEnum(of: media) {
        case .image(let image): return image.previewUrl
        case .video(let video): return video.thumbnailUrl
        case .gif(let gif): return gif.previewUrl
        case .audio: return nil
        }
    }

    @objc private func onImageTapped() {
        guard let post, let mediaPreviewURL else { return }
        if post.mediaClickPolicy == .openPostClickEvent {
            post.onClicked(ClickContext(launcher: makeLauncher()))
            return
        }
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

    @objc private func onRootTapped() {
        guard let post else { return }
        post.onClicked(ClickContext(launcher: makeLauncher()))
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard mediaPreviewURL != nil, touch.view?.isDescendant(of: imageView) == true else {
            return true
        }
        return false
    }

    private func makeLauncher() -> AppleUriLauncher {
        AppleUriLauncher(openUrl: OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })
    }
}

private final class GalleryFeedTileUIView: UIView {
    var onOpenURL: ((URL) -> Void)?

    private var feed: UiTimelineV2.Feed?

    private let stack = UIStackView()
    private let imageView = GalleryNetworkImageView(frame: .zero)
    private let textStack = UIStackView()
    private let titleLabel = UILabel()
    private let descriptionLabel = UILabel()
    private let sourceRow = UIStackView()
    private let sourceIcon = GalleryNetworkImageView(frame: .zero)
    private let sourceName = UILabel()
    private var imageAspectConstraint: NSLayoutConstraint?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .secondarySystemGroupedBackground
        layer.cornerRadius = 12
        clipsToBounds = true

        stack.axis = .vertical
        stack.alignment = .fill
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        stack.addArrangedSubview(imageView)

        textStack.axis = .vertical
        textStack.alignment = .fill
        textStack.spacing = 4
        textStack.isLayoutMarginsRelativeArrangement = true
        textStack.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 8, leading: 8, bottom: 0, trailing: 8)
        stack.addArrangedSubview(textStack)

        titleLabel.font = .preferredFont(forTextStyle: .caption1).bold()
        titleLabel.numberOfLines = 2
        textStack.addArrangedSubview(titleLabel)

        descriptionLabel.font = .preferredFont(forTextStyle: .caption1)
        descriptionLabel.textColor = .secondaryLabel
        descriptionLabel.numberOfLines = 3
        textStack.addArrangedSubview(descriptionLabel)

        sourceRow.axis = .horizontal
        sourceRow.alignment = .center
        sourceRow.spacing = 6
        sourceRow.isLayoutMarginsRelativeArrangement = true
        sourceRow.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 6, leading: 8, bottom: 6, trailing: 8)
        sourceRow.addArrangedSubview(sourceIcon)
        sourceRow.addArrangedSubview(sourceName)
        sourceIcon.widthAnchor.constraint(equalToConstant: 20).isActive = true
        sourceIcon.heightAnchor.constraint(equalToConstant: 20).isActive = true
        sourceIcon.layer.cornerRadius = 4
        sourceIcon.clipsToBounds = true
        sourceName.font = .preferredFont(forTextStyle: .caption1)
        sourceName.numberOfLines = 1
        stack.addArrangedSubview(sourceRow)

        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(onRootTapped)))
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func prepareForReuse() {
        feed = nil
        imageAspectConstraint?.isActive = false
        imageAspectConstraint = nil
        imageView.cancel()
        sourceIcon.cancel()
        titleLabel.text = nil
        descriptionLabel.text = nil
        sourceName.text = nil
        onOpenURL = nil
    }

    func configure(feed: UiTimelineV2.Feed, appearance: GalleryUIKitAppearance, loadsRemoteImages: Bool = true) {
        self.feed = feed
        imageView.isHidden = true
        imageView.cancel()
        imageAspectConstraint?.isActive = false
        imageAspectConstraint = nil
        textStack.isHidden = true

        if appearance.showMedia, let media = feed.media {
            imageView.isHidden = false
            if loadsRemoteImages {
                imageView.set(url: media.url, customHeaders: media.customHeaders)
            }
            let ratio = max(CGFloat(media.aspectRatio), 0.3)
            imageAspectConstraint = imageView.heightAnchor.constraint(equalTo: imageView.widthAnchor, multiplier: 1 / ratio)
            imageAspectConstraint?.isActive = true
        } else {
            let title = feed.title
            let description = feed.description_
            titleLabel.text = title
            titleLabel.isHidden = title?.isEmpty ?? true
            descriptionLabel.text = description
            descriptionLabel.isHidden = description?.isEmpty ?? true
            textStack.isHidden = titleLabel.isHidden && descriptionLabel.isHidden
        }

        if let icon = feed.source.icon, !icon.isEmpty {
            sourceIcon.isHidden = false
            if loadsRemoteImages {
                sourceIcon.set(url: icon, customHeaders: nil)
            } else {
                sourceIcon.cancel()
            }
        } else {
            sourceIcon.isHidden = true
            sourceIcon.cancel()
        }
        sourceName.text = feed.source.name
    }

    @objc private func onRootTapped() {
        guard let feed else { return }
        feed.onClicked(ClickContext(launcher: makeLauncher()))
    }

    private func makeLauncher() -> AppleUriLauncher {
        AppleUriLauncher(openUrl: OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })
    }
}

private final class GalleryPlaceholderTileUIView: UIView {
    private let imageBlock = UIView()
    private let avatarBlock = UIView()
    private let labelBlock = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .secondarySystemGroupedBackground
        layer.cornerRadius = 12
        clipsToBounds = true

        imageBlock.backgroundColor = UIColor.gray.withAlphaComponent(0.2)
        avatarBlock.backgroundColor = UIColor.gray.withAlphaComponent(0.2)
        labelBlock.backgroundColor = UIColor.gray.withAlphaComponent(0.2)
        avatarBlock.layer.cornerRadius = 10
        labelBlock.layer.cornerRadius = 3
        [imageBlock, avatarBlock, labelBlock].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            addSubview($0)
        }
        NSLayoutConstraint.activate([
            imageBlock.topAnchor.constraint(equalTo: topAnchor),
            imageBlock.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageBlock.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageBlock.heightAnchor.constraint(equalTo: imageBlock.widthAnchor),

            avatarBlock.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            avatarBlock.topAnchor.constraint(equalTo: imageBlock.bottomAnchor, constant: 4),
            avatarBlock.widthAnchor.constraint(equalToConstant: 20),
            avatarBlock.heightAnchor.constraint(equalToConstant: 20),

            labelBlock.leadingAnchor.constraint(equalTo: avatarBlock.trailingAnchor, constant: 6),
            labelBlock.centerYAnchor.constraint(equalTo: avatarBlock.centerYAnchor),
            labelBlock.widthAnchor.constraint(equalToConstant: 58),
            labelBlock.heightAnchor.constraint(equalToConstant: 10),
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }
}

private final class GalleryNetworkImageView: UIImageView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        contentMode = .scaleAspectFill
        clipsToBounds = true
        backgroundColor = .placeholderText.withAlphaComponent(0.12)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func set(url: String, customHeaders: [String: String]?) {
        guard let url = URL(string: url) else {
            image = nil
            return
        }
        var options: KingfisherOptionsInfo = [.transition(.fade(0.25)), .cacheOriginalImage, .backgroundDecode]
        if let customHeaders, !customHeaders.isEmpty {
            options.append(.requestModifier(AnyModifier { request in
                var request = request
                for (key, value) in customHeaders {
                    request.setValue(value, forHTTPHeaderField: key)
                }
                return request
            }))
        }
        kf.setImage(with: url, options: options)
    }

    func cancel() {
        kf.cancelDownloadTask()
        image = nil
    }
}

private final class GalleryLoadingFooterUIView: UIView {
    private let indicator = UIActivityIndicatorView(style: .medium)

    override init(frame: CGRect) {
        super.init(frame: frame)
        indicator.translatesAutoresizingMaskIntoConstraints = false
        addSubview(indicator)
        NSLayoutConstraint.activate([
            indicator.centerXAnchor.constraint(equalTo: centerXAnchor),
            indicator.centerYAnchor.constraint(equalTo: centerYAnchor),
        ])
        indicator.startAnimating()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }
}

private final class GalleryEndFooterUIView: UIView {
    private let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        label.text = String(localized: "end_of_list")
        label.font = .preferredFont(forTextStyle: .footnote)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: centerXAnchor),
            label.centerYAnchor.constraint(equalTo: centerYAnchor),
            label.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 8),
            label.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -8),
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }
}

private extension UIFont {
    func bold() -> UIFont {
        guard let descriptor = fontDescriptor.withSymbolicTraits(.traitBold) else { return self }
        return UIFont(descriptor: descriptor, size: pointSize)
    }
}
