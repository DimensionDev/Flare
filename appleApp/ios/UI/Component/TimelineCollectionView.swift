import UIKit
import CHTCollectionViewWaterfallLayoutObjC

/// Applies bookmarks only while a snapshot or geometry change is being laid out.
/// UIKit owns scrolling and refresh animations.
final class TimelineCollectionView: UICollectionView {
    var readingItemID: ((IndexPath) -> String?)?
    var readingIndexPath: ((String) -> IndexPath?)?
    var readingItemIDs: (() -> [String])?
    var readingTopInset: (() -> CGFloat)?
    // Occlusion affects which item is visible, not the bookmark's viewport origin.
    var readingTopOcclusion: (() -> CGFloat)?
    // The owner knows when the snapshot and measurements for this layout are ready.
    // A nil path denotes a restore to the top, without a target item.
    var isReadingLayoutReady: ((IndexPath?) -> Bool)?
    var onReadingLayoutChanged: (() -> Void)?
    private var isExternalScrollInteractionActive = false
    var isScrollInteractionActive: Bool { hasScrollGesture || isProgrammaticScrolling || isExternalScrollInteractionActive }
    var onProgrammaticScrollBegan: (() -> Void)?
    var onProgrammaticScrollEnded: (() -> Void)?
    var preservesReadingPosition = true {
        didSet {
            if !preservesReadingPosition {
                if isPresentingRefresh {
                    cancelRefresh()
                } else {
                    resetReadingPosition()
                }
            }
        }
    }

    enum ReadingPosition {
        case top
        case item(id: String, distanceFromTop: CGFloat, itemOrder: [String], keepVisible: Bool = true)

        var itemID: String? {
            if case .item(let id, _, _, _) = self { return id }
            return nil
        }
    }

    // This is a pending layout transaction, not a continuously enforced offset.
    private var readingPosition: ReadingPosition?
    private var readingPositionWasAtTop = false
    // Passive across size changes: a different column may become visually first,
    // or a shorter card may temporarily clamp the original intra-item distance.
    private var geometryReadingPosition: ReadingPosition?
    private var geometryReadingPositionWasAtTop = false
    private var readingPositionGeneration = 0
    private var isRestoringReadingPosition = false
    private var gestureReadingItemID: String?
    private var appliedTopInset: CGFloat?
    private var restingAutomaticInset: (amount: CGFloat, safeAreaTop: CGFloat)?

    private var refreshRequested = false
    private var pendingRefreshReveal = false
    private var isEndingRefresh = false
    private var isAnimatingRefreshCollapse = false
    private var isRevealingRefresh = false
    // A refresh started away from the top has no visible gap to collapse.
    private var preservesRefreshGap = false
    private(set) var isProgrammaticScrolling = false

    var hasReadingPosition: Bool { readingPosition != nil }
    var canRestoreTopReadingPosition: Bool {
        if geometryReadingPosition?.itemID != nil { return geometryReadingPositionWasAtTop }
        return readingPosition?.itemID == nil || readingPositionWasAtTop
    }
    var isPresentingRefresh: Bool { refreshRequested || isEndingRefresh || refreshControl?.isRefreshing == true }

    // Prepending during an elastic pull moves the offset into the normal content
    // range, so UIKit can no longer spring back to the original reading position.
    // The owner keeps its latest input queued until the interaction ends.
    var shouldDeferSnapshotChanges: Bool {
        preservesReadingPosition && (isRevealingRefresh || isEndingRefresh ||
            (isPresentingRefresh && isScrollInteractionActive && contentOffset.y <= -restingAdjustedTopInset))
    }

    var restingAdjustedTopInset: CGFloat {
        rememberRestingAutomaticInset()
        let automaticInset: CGFloat
        if contentInsetAdjustmentBehavior == .never {
            automaticInset = 0
        } else if let restingAutomaticInset {
            // The native refresh contribution can live in adjustedContentInset
            // without changing contentInset. Only geometry changes update this base.
            automaticInset = max(0, restingAutomaticInset.amount + safeAreaInsets.top - restingAutomaticInset.safeAreaTop)
        } else {
            automaticInset = safeAreaInsets.top
        }
        return (appliedTopInset ?? contentInset.top) + automaticInset
    }

    private var refreshInset: CGFloat {
        max(adjustedContentInset.top - restingAdjustedTopInset, 0)
    }

    private var readingRefreshInset: CGFloat { preservesRefreshGap ? refreshInset : 0 }

    private func rememberRestingAutomaticInset() {
        guard !isPresentingRefresh, refreshControl?.isRefreshing != true else { return }
        restingAutomaticInset = (adjustedContentInset.top - contentInset.top, safeAreaInsets.top)
    }

    override func adjustedContentInsetDidChange() {
        super.adjustedContentInsetDidChange()
        if isEndingRefresh, refreshInset <= 0.5 {
            DispatchQueue.main.async { [weak self] in self?.finishRefreshingIfReady() }
        }
        rememberRestingAutomaticInset()
    }

    private var hasScrollGesture: Bool { isTracking || isDragging || isDecelerating }

    private var allowsReadingPositionRestoration: Bool {
        !isScrollInteractionActive && !isRevealingRefresh
    }

    /// UIKit also writes contentInset while refreshing. Apply only the page's delta.
    func setTopContentInset(_ inset: CGFloat) {
        let previous = appliedTopInset ?? contentInset.top
        if inset != previous { prepareForLayoutChange() }
        appliedTopInset = inset
        if inset != previous {
            contentInset.top += inset - previous
            if isRevealingRefresh {
                isRevealingRefresh = false
                super.setContentOffset(contentOffset, animated: false)
                pendingRefreshReveal = true
                setNeedsLayout()
            }
        }
    }

    func beginRefreshing(revealingIndicator: Bool) {
        guard preservesReadingPosition, let refreshControl, !refreshRequested else { return }
        if isEndingRefresh {
            super.setContentOffset(contentOffset, animated: false)
            endProgrammaticScrolling()
        }
        let wasAtTop = contentOffset.y + restingAdjustedTopInset <= 1 || bounds.height <= 1
        preservesRefreshGap = wasAtTop
        if appliedTopInset == nil { appliedTopInset = contentInset.top }
        pendingRefreshReveal = revealingIndicator && wasAtTop && !refreshControl.isRefreshing && !hasScrollGesture
        refreshRequested = true
        isEndingRefresh = false
        startRefreshIfReady()
    }

    private func startRefreshIfReady() {
        guard refreshRequested, let refreshControl, window != nil,
              bounds.width > 1, bounds.height > 1 else { return }
        if !refreshControl.isRefreshing {
            // Diffable updates can disable UIView animations. Do not inherit that
            // setting for the native spinner's repeating animation.
            withRefreshAnimations { refreshControl.beginRefreshing() }
        }
        guard pendingRefreshReveal else { return }
        pendingRefreshReveal = false
        let target = -restingAdjustedTopInset - max(refreshInset, refreshControl.bounds.height)
        guard target < contentOffset.y else { return }
        resetReadingPosition()
        isRevealingRefresh = true
        super.setContentOffset(CGPoint(x: contentOffset.x, y: target), animated: true)
    }

    func endRefreshing() {
        guard refreshRequested || refreshControl?.isRefreshing == true else { return }
        pendingRefreshReveal = false
        if isRevealingRefresh {
            isRevealingRefresh = false
            super.setContentOffset(contentOffset, animated: false)
            restoreReadingPosition(.top)
        }
        // UIKit removes the model inset before its collapse animation finishes.
        // Hand the final reading position to one native scroll animation.
        prepareForLayoutChange()
        readingPositionGeneration += 1
        refreshRequested = false
        isEndingRefresh = refreshControl?.isRefreshing == true
        let offsetBeforeEnding = contentOffset
        withRefreshAnimations { refreshControl?.endRefreshing() }
        // Automatic insets can already have moved the model offset to the endpoint.
        if refreshInset <= 0.5, readingPosition != nil, allowsReadingPositionRestoration {
            super.setContentOffset(offsetBeforeEnding, animated: false)
        }
        isEndingRefresh = true
        finishRefreshingIfReady()
    }

    private func finishRefreshingIfReady() {
        guard isEndingRefresh, !isAnimatingRefreshCollapse, refreshInset <= 0.5 else { return }
        isEndingRefresh = false
        restoreReadingPositionIfNeeded(animated: true)
        isEndingRefresh = isAnimatingRefreshCollapse
    }

    private func withRefreshAnimations(_ action: () -> Void) {
        let enabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(true)
        defer { UIView.setAnimationsEnabled(enabled) }
        action()
    }

    func interruptRefreshForScrolling() {
        gestureReadingItemID = nil
        if isAnimatingRefreshCollapse || isRevealingRefresh {
            super.setContentOffset(contentOffset, animated: false)
        }
        pendingRefreshReveal = false
        isRevealingRefresh = false
        isEndingRefresh = false
        isAnimatingRefreshCollapse = false
        preservesRefreshGap = false
        resetReadingPosition()
    }

    func cancelRefresh() {
        if isEndingRefresh {
            super.setContentOffset(contentOffset, animated: false)
            endProgrammaticScrolling()
        }
        interruptRefreshForScrolling()
        refreshRequested = false
        isEndingRefresh = refreshControl?.isRefreshing == true
        UIView.performWithoutAnimation { refreshControl?.endRefreshing() }
        if refreshInset <= 0.5 { isEndingRefresh = false }
    }

    override func setContentOffset(_ contentOffset: CGPoint, animated: Bool) {
        // SwiftUI tab reselection uses this entry directly, without calling
        // scrollViewShouldScrollToTop. Explicit navigation supersedes bookmarks.
        // UICollectionView also issues animated requests for the unchanged offset
        // during layout. Those are not navigation and must leave the update alone.
        if animated && abs(self.contentOffset.y - contentOffset.y) <= 0.5 { return }
        if animated || isProgrammaticScrolling {
            interruptRefreshForScrolling()
        }
        let shouldAnimate = animated && window != nil && abs(self.contentOffset.y - contentOffset.y) > 0.5
        let wasProgrammaticScrolling = isProgrammaticScrolling
        if shouldAnimate {
            beginProgrammaticScrolling()
        } else if isProgrammaticScrolling {
            endProgrammaticScrolling()
        }
        super.setContentOffset(contentOffset, animated: shouldAnimate)
        if wasProgrammaticScrolling && !shouldAnimate { onProgrammaticScrollEnded?() }
    }

    func beginProgrammaticScrolling() {
        if isAnimatingRefreshCollapse { interruptRefreshForScrolling() }
        isProgrammaticScrolling = true
        onProgrammaticScrollBegan?()
    }

    func beginExternalScrollInteraction() {
        endProgrammaticScrolling()
        isExternalScrollInteractionActive = true
        interruptRefreshForScrolling()
    }

    func endScrollInteraction() {
        endProgrammaticScrolling()
        isExternalScrollInteractionActive = false
        gestureReadingItemID = nil
    }

    func endProgrammaticScrolling() {
        if isAnimatingRefreshCollapse { setNeedsLayout() }
        isAnimatingRefreshCollapse = false
        isProgrammaticScrolling = false
        isEndingRefresh = false
        isRevealingRefresh = false
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            startRefreshIfReady()
        } else {
            endProgrammaticScrolling()
        }
    }

    override var frame: CGRect {
        willSet {
            // Setting frame can resize bounds without calling its setter.
            if frame.size != newValue.size, bounds.width > 1, bounds.height > 1 {
                prepareForGeometryChange()
            }
        }
    }

    override var bounds: CGRect {
        willSet {
            if bounds.size != newValue.size, bounds.width > 1, bounds.height > 1 {
                prepareForGeometryChange()
            }
        }
    }

    func prepareForLayoutChange(preferringVisibleTop: Bool = false) {
        guard preservesReadingPosition,
              !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }

        readingPositionGeneration += 1
        if readingPosition == nil {
            // Measuring a section below a pinned title can capture an item even at the top.
            readingPositionWasAtTop = canRestoreTopReadingPosition && contentOffset.y + restingAdjustedTopInset <= 1
            readingPosition = captureCurrentLayoutPosition(preferringVisibleTop: preferringVisibleTop)
        }
    }

    func prepareForGeometryChange() {
        guard preservesReadingPosition, !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }
        if geometryReadingPosition == nil {
            // A later size change can clamp a scrolled list to zero without making it a top bookmark.
            geometryReadingPositionWasAtTop = canRestoreTopReadingPosition &&
                contentOffset.y + restingAdjustedTopInset <= 1
            geometryReadingPosition = captureReadingPosition()
        }
        readingPositionGeneration += 1
        readingPosition = geometryReadingPosition
        readingPositionWasAtTop = geometryReadingPositionWasAtTop
    }

    func prepareForSnapshotChange() {
        guard preservesReadingPosition,
              !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }

        // Reuse the item bookmark if a snapshot arrives during reflow. Once a top
        // restore completes, later prepends capture the loaded reading item.
        if readingPosition?.itemID == nil {
            readingPositionWasAtTop = canRestoreTopReadingPosition && contentOffset.y + restingAdjustedTopInset <= 1
            readingPosition = captureCurrentLayoutPosition()
        }
        readingPositionGeneration += 1
    }

    private func captureCurrentLayoutPosition(preferringVisibleTop: Bool = false) -> ReadingPosition? {
        if let readingPosition { return readingPosition }
        if case .item(let id, _, let itemOrder, let keepVisible) = geometryReadingPosition,
           let path = readingIndexPath?(id),
           let frame = layoutAttributesForItem(at: path)?.frame {
            // A later content/inset update preserves the displayed distance. Keep
            // the unclamped geometry bookmark only for the next size change.
            return .item(id: id, distanceFromTop: frame.minY - readingViewportTop(), itemOrder: itemOrder, keepVisible: keepVisible)
        }
        return captureReadingPosition(preferringVisibleTop: preferringVisibleTop)
    }

    func captureViewportPosition() -> (itemID: String, offset: CGFloat)? {
        guard bounds.width > 1, bounds.height > 1 else { return nil }
        let top = readingViewportTop()
        guard let item = firstVisibleReadingItem(viewportTop: top, preferringVisibleTop: false) else { return nil }
        return (item.id, item.frame.minY - top)
    }

    func captureReadingPosition(preferringVisibleTop: Bool = false) -> ReadingPosition? {
        if let readingPosition { return readingPosition }
        if let geometryReadingPosition, geometryReadingPosition.itemID != nil { return geometryReadingPosition }
        guard bounds.width > 1, bounds.height > 1 else { return nil }
        let viewportTop = readingViewportTop()
        if let firstItem = firstVisibleReadingItem(viewportTop: viewportTop, preferringVisibleTop: preferringVisibleTop) {
            return .item(
                id: firstItem.id,
                distanceFromTop: firstItem.frame.minY - viewportTop,
                itemOrder: readingItemIDs?() ?? [firstItem.id]
            )
        }
        // Without a visible item (for example during initial loading), keep the
        // top as a fallback rather than treating it as a permanent reading anchor.
        let isAtTop = isPresentingRefresh
            ? viewportTop <= 1
            : contentOffset.y + adjustedContentInset.top <= 1
        return isAtTop ? .top : nil
    }

    private func readingViewportTop() -> CGFloat {
        let top = contentOffset.y + (readingTopInset?() ?? restingAdjustedTopInset) + readingRefreshInset
        // Elastic pull distance is not a saved reading offset.
        return isPresentingRefresh ? max(top, 0) : top
    }

    private func firstVisibleReadingItem(viewportTop: CGFloat, preferringVisibleTop: Bool = false) -> (id: String, frame: CGRect)? {
        let visibleTop = viewportTop + max(readingTopOcclusion?() ?? 0, 0)
        let viewportBottom = contentOffset.y + bounds.height - adjustedContentInset.bottom
        let candidates = indexPathsForVisibleItems.compactMap { indexPath -> (id: String, frame: CGRect)? in
            guard let id = readingItemID?(indexPath),
                  let frame = layoutAttributesForItem(at: indexPath)?.frame,
                  frame.maxY > visibleTop,
                  frame.minY < viewportBottom else { return nil }
            return (id, frame)
        }
        // Capture and compensation must agree on the same still-visible item.
        if hasScrollGesture, let id = gestureReadingItemID,
           let item = candidates.first(where: { $0.id == id }) {
            return item
        }
        // A partly hidden estimate can shrink without moving its own top edge,
        // while every card below it moves. Anchor an edge the reader can see.
        // Fall back to the partly visible card when it fills the whole viewport.
        // Masonry columns can shift independently; keep the current reading item
        // instead of choosing a visible edge from another column.
        let singleColumn = ((collectionViewLayout as? CHTCollectionViewWaterfallLayout)?.columnCount ?? 1) == 1
        let visibleTops = preferringVisibleTop && singleColumn ? candidates.filter { $0.frame.minY >= visibleTop } : []
        return (visibleTops.isEmpty ? candidates : visibleTops).min { lhs, rhs in
            if abs(lhs.frame.minY - rhs.frame.minY) > 0.5 {
                return lhs.frame.minY < rhs.frame.minY
            }
            return lhs.frame.minX < rhs.frame.minX
        }
    }

    /// Apply measured geometry without moving content under an active gesture.
    /// Publish heights after capturing the old anchor, outside cell measurement.
    /// Layouts triggered by offset compensation must see the same committed batch.
    func invalidateMeasuredHeights(at indexPaths: [IndexPath]? = nil, applying measurements: () -> Bool = { true }) {
        prepareForLayoutChange(preferringVisibleTop: true)
        UIView.performWithoutAnimation {
            performUpdatesPreservingReadingPosition(preferringVisibleTop: true) {
                guard measurements() else {
                    // Discarding an obsolete batch can make an existing bookmark
                    // ready without invalidating any item geometry.
                    if hasReadingPosition { setNeedsLayout() }
                    return
                }
                if let indexPaths, let layout = collectionViewLayout as? TimelineWaterfallLayout {
                    layout.invalidateMeasuredItems(at: indexPaths)
                } else {
                    collectionViewLayout.invalidateLayout()
                }
                layoutIfNeeded()
            }
        }
    }

    /// Capture and compensate in the same main-thread update, before another pan
    /// or deceleration step can run. Never restore this offset from a completion.
    func performUpdatesPreservingReadingPosition(
        keepingItemIDs: Set<String>? = nil,
        preferringVisibleTop: Bool = false,
        _ updates: () -> Void
    ) {
        let viewportTop = readingViewportTop()
        var item = preservesReadingPosition && (hasScrollGesture || isAnimatingRefreshCollapse) &&
            !isProgrammaticScrolling && !isExternalScrollInteractionActive && !isRevealingRefresh
            ? firstVisibleReadingItem(viewportTop: viewportTop, preferringVisibleTop: preferringVisibleTop) : nil
        let keepsGestureItem = hasScrollGesture &&
            ((collectionViewLayout as? TimelineWaterfallLayout)?.columnCount ?? 1) > 1
        // Another column's first visible item may not move with the collapse target.
        if isAnimatingRefreshCollapse,
           let id = readingPosition?.itemID,
           let path = readingIndexPath?(id),
           let frame = collectionViewLayout.layoutAttributesForItem(at: path)?.frame {
            item = (id, frame)
        }
        if let anchor = item, let keepingItemIDs, !keepingItemIDs.contains(anchor.id) {
            let order = readingItemIDs?() ?? []
            let index = order.firstIndex(of: anchor.id) ?? 0
            let replacement = order.dropFirst(index + 1).first(where: keepingItemIDs.contains)
                ?? order.prefix(index).reversed().first(where: keepingItemIDs.contains)
            item = replacement.map { (id: $0, frame: anchor.frame) }
        }
        gestureReadingItemID = keepsGestureItem ? item?.id : nil
        let oldOffset = contentOffset.y
        let wasWithinBounds = oldOffset >= -adjustedContentInset.top &&
            oldOffset <= max(-adjustedContentInset.top, contentSize.height - bounds.height + adjustedContentInset.bottom)
        let readingOffset = { [unowned self] () -> CGFloat? in
            guard let item, preservesReadingPosition, !isProgrammaticScrolling, !isRevealingRefresh else { return nil }
            guard let path = readingIndexPath?(item.id),
                  let frame = collectionViewLayout.layoutAttributesForItem(at: path)?.frame else { return nil }
            let oldDistance = item.frame.minY - viewportTop
            // The estimate may have put the viewport beyond the measured card.
            // Keep that ID visible using the same clamp as an idle reading anchor.
            let distance = max(oldDistance, (readingTopOcclusion?() ?? 0) + 1 - frame.height)
            // UIKit's invalidation delta preserves the gesture or collapse trajectory.
            // Account for any offset adjustment UIKit already made at the bottom.
            var targetOffset = oldOffset + frame.minY - item.frame.minY + oldDistance - distance
            if wasWithinBounds {
                let minimum = -adjustedContentInset.top
                let maximum = max(minimum, collectionViewLayout.collectionViewContentSize.height - bounds.height + adjustedContentInset.bottom)
                targetOffset = min(max(targetOffset, minimum), maximum)
            }
            return targetOffset
        }
        // UIKit can clamp an elastic offset when content size changes. Compensate
        // it once after layout instead of replaying it against every clamp.
        if let layout = collectionViewLayout as? TimelineWaterfallLayout, item != nil, wasWithinBounds {
            let previous = layout.readingOffset
            layout.readingOffset = readingOffset
            defer { layout.readingOffset = previous }
            updates()
            layoutIfNeeded()
            return
        }
        updates()
        guard item != nil else { return }
        UIView.performWithoutAnimation {
            layoutIfNeeded()
            guard let targetOffset = readingOffset() else { return }
            let delta = targetOffset - contentOffset.y
            guard abs(delta) > 0.5 / max(traitCollection.displayScale, 1) else { return }
            let context = UICollectionViewLayoutInvalidationContext()
            context.contentOffsetAdjustment.y = delta
            collectionViewLayout.invalidateLayout(with: context)
        }
    }

    func restoreReadingPosition(_ position: ReadingPosition) {
        readingPositionGeneration += 1
        geometryReadingPosition = position.itemID == nil ? nil : position
        geometryReadingPositionWasAtTop = false
        readingPosition = position
        readingPositionWasAtTop = position.itemID == nil
        setNeedsLayout()
    }

    func resetReadingPosition() {
        readingPositionGeneration += 1
        readingPosition = nil
        readingPositionWasAtTop = false
        geometryReadingPosition = nil
    }

    private func finishReadingPositionRestoration(at indexPath: IndexPath?) {
        // Child cells measure after the collection's layout pass. Check readiness
        // outside layout; a snapshot/measurement event will retry if still pending.
        let generation = readingPositionGeneration
        DispatchQueue.main.async { [weak self] in
            guard let self, self.readingPositionGeneration == generation,
                  !self.isEndingRefresh, self.allowsReadingPositionRestoration,
                  self.isReadingLayoutReady?(indexPath) != false else { return }
            self.readingPosition = nil
            self.readingPositionWasAtTop = false
            if self.geometryReadingPosition?.itemID == nil { self.geometryReadingPosition = nil }
            self.onReadingLayoutChanged?()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        startRefreshIfReady()
        restoreReadingPositionIfNeeded()
        onReadingLayoutChanged?()
    }

    private func restoreReadingPositionIfNeeded(animated: Bool = false) {
        guard preservesReadingPosition, !isRestoringReadingPosition,
              bounds.width > 1, bounds.height > 1 else { return }
        if !allowsReadingPositionRestoration {
            resetReadingPosition()
            return
        }
        guard let readingPosition, !isEndingRefresh else { return }

        isRestoringReadingPosition = true
        defer { isRestoringReadingPosition = false }

        let targetY: CGFloat
        let targetIndexPath: IndexPath?
        switch readingPosition {
        case .top:
            targetIndexPath = nil
            targetY = -adjustedContentInset.top
        case .item(let id, let distanceFromTop, let itemOrder, let keepVisible):
            let indexPath = readingIndexPath?(id) ?? {
                let index = itemOrder.firstIndex(of: id) ?? 0
                let candidates = Array(itemOrder.dropFirst(index + 1)) + Array(itemOrder.prefix(index).reversed())
                return candidates.lazy.compactMap({ self.readingIndexPath?($0) }).first
            }()
            guard let indexPath else {
                // No old item survives a replacement. Start below the bars;
                // never turn the disappearing refresh gap into an item offset.
                self.readingPosition = .top
                readingPositionWasAtTop = true
                geometryReadingPosition = nil
                setNeedsLayout()
                return
            }
            targetIndexPath = indexPath
            if let replacementID = readingItemID?(indexPath), replacementID != id {
                let replacement = ReadingPosition.item(id: replacementID, distanceFromTop: distanceFromTop,
                                                       itemOrder: readingItemIDs?() ?? [replacementID], keepVisible: keepVisible)
                self.readingPosition = replacement
                if geometryReadingPosition != nil { geometryReadingPosition = replacement }
            }
            guard let frame = collectionViewLayout.layoutAttributesForItem(at: indexPath)?.frame else { return }
            // Resizing can make a card shorter; keep the reading item visible.
            // Gap filling keeps its predecessor's exact offset even above the viewport.
            let distance = keepVisible ? max(distanceFromTop, (readingTopOcclusion?() ?? 0) + 1 - frame.height) : distanceFromTop
            targetY = frame.minY - distance - (readingTopInset?() ?? restingAdjustedTopInset) - readingRefreshInset
        }
        let minimumY = -adjustedContentInset.top
        let maximumY = max(minimumY, contentSize.height - bounds.height + adjustedContentInset.bottom)
        let offsetY = min(max(targetY, minimumY), maximumY)
        let tolerance = 0.5 / max(traitCollection.displayScale, 1)
        if abs(contentOffset.y - offsetY) > tolerance {
            if animated, window != nil {
                // Internal collapse retains its bookmark; explicit navigation discards it.
                isAnimatingRefreshCollapse = true
                super.setContentOffset(CGPoint(x: contentOffset.x, y: offsetY), animated: true)
                return
            }
            super.setContentOffset(CGPoint(x: contentOffset.x, y: offsetY), animated: false)
        }
        finishReadingPositionRestoration(at: targetIndexPath)
    }
}

/// Resolve the reading offset before UICollectionView chooses visible cells.
final class TimelineWaterfallLayout: CHTCollectionViewWaterfallLayout {
    var readingOffset: (() -> CGFloat?)?
    private var needsGeometryUpdate = true
    private var measuredItems: Set<IndexPath> = []
    private var sections: [SectionGeometry] = []
    private var cachedSize: CGSize?

    private struct SectionGeometry {
        var frames: [CGRect]
        var unions: [CGRect] = []
        let columns: Int
        let width: CGFloat
        let left: CGFloat
        let columnSpacing: CGFloat
        let spacing: CGFloat
        let topPadding: CGFloat
        let bottomPadding: CGFloat
        var startY: CGFloat

        mutating func updateUnions(from item: Int) {
            let first = item / 20
            unions.removeSubrange(min(first, unions.count)..<unions.count)
            for start in stride(from: first * 20, to: frames.count, by: 20) {
                unions.append(frames[start..<min(start + 20, frames.count)].reduce(CGRect.null) { $0.union($1) })
            }
        }

        func column(of frame: CGRect) -> Int {
            guard columns > 1 else { return 0 }
            return Int(((frame.minX - left) / (width + columnSpacing)).rounded())
        }
    }

    override func invalidateLayout() {
        needsGeometryUpdate = true
        super.invalidateLayout()
    }

    override func invalidateLayout(with context: UICollectionViewLayoutInvalidationContext) {
        if let context = context as? MeasuredHeightInvalidationContext, cachedSize != nil {
            measuredItems.formUnion(context.items)
        } else if !(context is ReadingOffsetInvalidationContext) {
            needsGeometryUpdate = true
        }
        super.invalidateLayout(with: context)
    }

    func invalidateMeasuredItems(at paths: [IndexPath]) {
        invalidateLayout(with: MeasuredHeightInvalidationContext(items: paths))
    }

    override func prepare() {
        if !needsGeometryUpdate, !measuredItems.isEmpty, !updateMeasuredItems() {
            needsGeometryUpdate = true
        }
        if needsGeometryUpdate {
            needsGeometryUpdate = false
            cachedSize = nil
            sections = []
            super.prepare()
            cacheGeometry()
        }
        measuredItems.removeAll(keepingCapacity: true)
        guard let collectionView, let target = readingOffset?() else { return }
        let delta = target - collectionView.contentOffset.y
        guard abs(delta) > 0.5 / max(collectionView.traitCollection.displayScale, 1) else { return }
        // Offset compensation changes the pan trajectory, not the item geometry.
        let context = ReadingOffsetInvalidationContext()
        context.contentOffsetAdjustment.y = delta
        super.invalidateLayout(with: context)
    }

    override func targetContentOffset(forProposedContentOffset proposedContentOffset: CGPoint) -> CGPoint {
        guard let target = readingOffset?() else { return super.targetContentOffset(forProposedContentOffset: proposedContentOffset) }
        return CGPoint(x: proposedContentOffset.x, y: target)
    }

    override var collectionViewContentSize: CGSize {
        guard let cachedSize else { return super.collectionViewContentSize }
        guard !sections.isEmpty else { return .zero }
        return CGSize(width: collectionView?.bounds.width ?? cachedSize.width,
                      height: max(cachedSize.height, minimumContentHeight))
    }

    override func layoutAttributesForItem(at indexPath: IndexPath) -> UICollectionViewLayoutAttributes? {
        guard cachedSize != nil else { return super.layoutAttributesForItem(at: indexPath) }
        guard sections.indices.contains(indexPath.section),
              sections[indexPath.section].frames.indices.contains(indexPath.item) else { return nil }
        let attributes = UICollectionViewLayoutAttributes(forCellWith: indexPath)
        attributes.frame = sections[indexPath.section].frames[indexPath.item]
        return attributes
    }

    override func layoutAttributesForElements(in rect: CGRect) -> [UICollectionViewLayoutAttributes]? {
        guard cachedSize != nil else { return super.layoutAttributesForElements(in: rect) }
        var result: [UICollectionViewLayoutAttributes] = []
        for (section, geometry) in sections.enumerated() {
            for (group, bounds) in geometry.unions.enumerated() where bounds.intersects(rect) {
                for item in (group * 20)..<min((group + 1) * 20, geometry.frames.count) where geometry.frames[item].intersects(rect) {
                    if let attributes = layoutAttributesForItem(at: IndexPath(item: item, section: section)) {
                        result.append(attributes)
                    }
                }
            }
        }
        return result
    }

    private func cacheGeometry() {
        guard let collectionView,
              let delegate = collectionView.delegate as? CHTCollectionViewDelegateWaterfallLayout else { return }
        var cached: [SectionGeometry] = []
        var top: CGFloat = 0
        for section in 0..<collectionView.numberOfSections {
            // Timeline headers/footers are ordinary cells. Supplementary views
            // retain CHT's complete layout until their geometry is cached too.
            guard super.layoutAttributesForSupplementaryView(ofKind: CHTCollectionElementKindSectionHeader, at: IndexPath(item: 0, section: section)) == nil,
                  super.layoutAttributesForSupplementaryView(ofKind: CHTCollectionElementKindSectionFooter, at: IndexPath(item: 0, section: section)) == nil else { return }
            let columns = delegate.collectionView?(collectionView, layout: self, columnCountForSection: section) ?? columnCount
            let inset = delegate.collectionView?(collectionView, layout: self, insetForSectionAt: section) ?? sectionInset
            let header = delegate.collectionView?(collectionView, layout: self, insetForHeaderInSection: section) ?? headerInset
            let footer = delegate.collectionView?(collectionView, layout: self, insetForFooterInSection: section) ?? footerInset
            let spacing = delegate.collectionView?(collectionView, layout: self, minimumInteritemSpacingForSectionAt: section) ?? minimumInteritemSpacing
            let columnSpacing = delegate.collectionView?(collectionView, layout: self, minimumColumnSpacingForSectionAt: section) ?? minimumColumnSpacing
            let width = itemWidthInSection(at: section)
            guard columns > 0, width > 0, width + columnSpacing > 0, spacing >= 0 else { return }
            var frames: [CGRect] = []
            for item in 0..<collectionView.numberOfItems(inSection: section) {
                guard let frame = super.layoutAttributesForItem(at: IndexPath(item: item, section: section))?.frame else { return }
                frames.append(frame)
            }
            top += header.top + inset.top
            var geometry = SectionGeometry(frames: frames, columns: columns, width: width, left: inset.left,
                                           columnSpacing: columnSpacing, spacing: spacing,
                                           topPadding: header.top + inset.top, bottomPadding: inset.bottom + footer.top, startY: top)
            geometry.updateUnions(from: 0)
            // CHT reads NSNumber column heights through floatValue, including
            // empty sections. Preserve that rounding when rebuilding a suffix.
            let bottom = frames.map { Float($0.maxY + spacing) }.max() ?? Float(top)
            top = CGFloat(bottom) - spacing + geometry.bottomPadding
            cached.append(geometry)
        }
        sections = cached
        cachedSize = CGSize(width: collectionView.bounds.width, height: CGFloat(Float(top)))
    }

    private func updateMeasuredItems() -> Bool {
        guard let collectionView, let size = cachedSize, size.width == collectionView.bounds.width,
              sections.count == collectionView.numberOfSections,
              let delegate = collectionView.delegate as? CHTCollectionViewDelegateWaterfallLayout else { return false }
        for section in sections.indices {
            guard sections[section].frames.count == collectionView.numberOfItems(inSection: section) else { return false }
        }
        let paths = measuredItems.sorted()
        var heights: [CGFloat] = []
        for path in paths {
            guard sections.indices.contains(path.section), sections[path.section].frames.indices.contains(path.item) else { return false }
            let width = sections[path.section].width
            let measured = delegate.collectionView(collectionView, layout: self, sizeForItemAt: path)
            let scale = UIScreen.main.scale
            let height = measured.width > 0 && measured.height > 0
                ? floor(measured.height * width / measured.width * scale) / scale : 0
            guard height.isFinite else { return false }
            heights.append(height)
        }
        guard let first = paths.first else { return true }
        for (path, height) in zip(paths, heights) { sections[path.section].frames[path.item].size.height = height }
        var top = sections[first.section].startY
        for section in first.section..<sections.count {
            var geometry = sections[section]
            if section != first.section { top += geometry.topPadding }
            geometry.startY = top
            var columns = Array(repeating: Float(top), count: geometry.columns)
            let start = section == first.section ? first.item : 0
            for frame in geometry.frames.prefix(start) {
                columns[geometry.column(of: frame)] = Float(frame.maxY + geometry.spacing)
            }
            for item in start..<geometry.frames.count {
                let column: Int
                switch itemRenderDirection {
                case .leftToRight: column = item % geometry.columns
                case .rightToLeft: column = geometry.columns - 1 - item % geometry.columns
                default: column = columns.indices.min { columns[$0] < columns[$1] } ?? 0
                }
                geometry.frames[item].origin = CGPoint(x: geometry.left + (geometry.width + geometry.columnSpacing) * CGFloat(column),
                                                      y: CGFloat(columns[column]))
                columns[column] = Float(geometry.frames[item].maxY + geometry.spacing)
            }
            top = CGFloat(columns.max() ?? Float(top)) - geometry.spacing + geometry.bottomPadding
            geometry.updateUnions(from: start)
            sections[section] = geometry
        }
        cachedSize?.height = CGFloat(Float(top))
        return true
    }

    private final class ReadingOffsetInvalidationContext: UICollectionViewLayoutInvalidationContext {}

    private final class MeasuredHeightInvalidationContext: UICollectionViewLayoutInvalidationContext {
        let items: [IndexPath]

        init(items: [IndexPath]) {
            self.items = items
            super.init()
        }

        // Every following position may move, even though only these sizes changed.
        // UIKit must drop its attributes too; the layout keeps its own geometry.
        override var invalidateEverything: Bool { true }
    }
}
