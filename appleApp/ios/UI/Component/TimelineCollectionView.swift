import UIKit

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
        case item(id: String, distanceFromTop: CGFloat, itemOrder: [String])

        var itemID: String? {
            if case .item(let id, _, _) = self { return id }
            return nil
        }
    }

    // This is a pending layout transaction, not a continuously enforced offset.
    private var readingPosition: ReadingPosition?
    // Passive across size changes: a different column may become visually first,
    // or a shorter card may temporarily clamp the original intra-item distance.
    private var geometryReadingPosition: ReadingPosition?
    private var readingPositionGeneration = 0
    private var isRestoringReadingPosition = false
    private var appliedTopInset: CGFloat?
    private var restingAutomaticInset: (amount: CGFloat, safeAreaTop: CGFloat)?

    private var refreshRequested = false
    private var pendingRefreshReveal = false
    private var isEndingRefresh = false
    private var isRevealingRefresh = false
    private(set) var isProgrammaticScrolling = false

    var hasReadingPosition: Bool { readingPosition != nil }
    var isPresentingRefresh: Bool { refreshRequested || isEndingRefresh || refreshControl?.isRefreshing == true }

    // Prepending during an elastic pull moves the offset into the normal content
    // range, so UIKit can no longer spring back to the original reading position.
    // The owner keeps its latest input queued until the interaction ends.
    var shouldDeferSnapshotChanges: Bool {
        preservesReadingPosition && (isRevealingRefresh ||
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

    private func rememberRestingAutomaticInset() {
        guard !isPresentingRefresh, refreshControl?.isRefreshing != true else { return }
        restingAutomaticInset = (adjustedContentInset.top - contentInset.top, safeAreaInsets.top)
    }

    override func adjustedContentInsetDidChange() {
        super.adjustedContentInsetDidChange()
        if isEndingRefresh, refreshInset <= 0.5 {
            isEndingRefresh = false
            setNeedsLayout()
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
        let wasAtTop = contentOffset.y + restingAdjustedTopInset <= 1 || bounds.height <= 1
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
        }
        // Preserve the reading item through UIKit's inset removal. The bookmark
        // is consumed when the native refresh inset is gone, not on every frame.
        prepareForLayoutChange()
        readingPositionGeneration += 1
        refreshRequested = false
        isEndingRefresh = refreshControl?.isRefreshing == true
        withRefreshAnimations { refreshControl?.endRefreshing() }
        if refreshInset <= 0.5 { isEndingRefresh = false }
        setNeedsLayout()
    }

    private func withRefreshAnimations(_ action: () -> Void) {
        let enabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(true)
        defer { UIView.setAnimationsEnabled(enabled) }
        action()
    }

    func interruptRefreshForScrolling() {
        pendingRefreshReveal = false
        isRevealingRefresh = false
        resetReadingPosition()
    }

    func cancelRefresh() {
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
    }

    func endProgrammaticScrolling() {
        isProgrammaticScrolling = false
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
            readingPosition = captureCurrentLayoutPosition(preferringVisibleTop: preferringVisibleTop)
        }
    }

    func prepareForGeometryChange() {
        guard preservesReadingPosition, !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }
        if geometryReadingPosition == nil {
            geometryReadingPosition = captureReadingPosition()
        }
        readingPositionGeneration += 1
        readingPosition = geometryReadingPosition
    }

    func prepareForSnapshotChange() {
        guard preservesReadingPosition,
              !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }

        // Reuse the item bookmark if a snapshot arrives during reflow. Once a top
        // restore completes, later prepends capture the loaded reading item.
        if readingPosition?.itemID == nil {
            readingPosition = captureCurrentLayoutPosition()
        }
        readingPositionGeneration += 1
    }

    private func captureCurrentLayoutPosition(preferringVisibleTop: Bool = false) -> ReadingPosition? {
        if let readingPosition { return readingPosition }
        if case .item(let id, _, let itemOrder) = geometryReadingPosition,
           let path = readingIndexPath?(id),
           let frame = layoutAttributesForItem(at: path)?.frame {
            // A later content/inset update preserves the displayed distance. Keep
            // the unclamped geometry bookmark only for the next size change.
            return .item(id: id, distanceFromTop: frame.minY - readingViewportTop(), itemOrder: itemOrder)
        }
        return captureReadingPosition(preferringVisibleTop: preferringVisibleTop)
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
        let top = contentOffset.y + (readingTopInset?() ?? restingAdjustedTopInset) + refreshInset
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
        // A partly hidden estimate can shrink without moving its own top edge,
        // while every card below it moves. Anchor an edge the reader can see.
        // Fall back to the partly visible card when it fills the whole viewport.
        let visibleTops = preferringVisibleTop ? candidates.filter { $0.frame.minY >= visibleTop } : []
        return (visibleTops.isEmpty ? candidates : visibleTops).min { lhs, rhs in
            if abs(lhs.frame.minY - rhs.frame.minY) > 0.5 {
                return lhs.frame.minY < rhs.frame.minY
            }
            return lhs.frame.minX < rhs.frame.minX
        }
    }

    /// Apply measured geometry without moving content under an active gesture.
    /// Called from the controller's coalesced flush, outside cell measurement/layout.
    func invalidateMeasuredHeights() {
        prepareForLayoutChange(preferringVisibleTop: true)
        UIView.performWithoutAnimation {
            performUpdatesPreservingReadingPosition(preferringVisibleTop: true) {
                collectionViewLayout.invalidateLayout()
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
        var item = preservesReadingPosition && hasScrollGesture &&
            !isProgrammaticScrolling && !isExternalScrollInteractionActive && !isRevealingRefresh
            ? firstVisibleReadingItem(viewportTop: viewportTop, preferringVisibleTop: preferringVisibleTop) : nil
        if let anchor = item, let keepingItemIDs, !keepingItemIDs.contains(anchor.id) {
            let order = readingItemIDs?() ?? []
            let index = order.firstIndex(of: anchor.id) ?? 0
            let replacement = order.dropFirst(index + 1).first(where: keepingItemIDs.contains)
                ?? order.prefix(index).reversed().first(where: keepingItemIDs.contains)
            item = replacement.map { (id: $0, frame: anchor.frame) }
        }
        let oldOffset = contentOffset.y
        let wasWithinBounds = oldOffset >= -adjustedContentInset.top &&
            oldOffset <= max(-adjustedContentInset.top, contentSize.height - bounds.height + adjustedContentInset.bottom)
        updates()
        guard let item, preservesReadingPosition, !isProgrammaticScrolling, !isRevealingRefresh else { return }
        UIView.performWithoutAnimation {
            layoutIfNeeded()
            guard let path = readingIndexPath?(item.id),
                  let frame = collectionViewLayout.layoutAttributesForItem(at: path)?.frame else { return }
            let oldDistance = item.frame.minY - viewportTop
            // The estimate may have put the viewport beyond the measured card.
            // Keep that ID visible using the same clamp as an idle reading anchor.
            let distance = max(oldDistance, (readingTopOcclusion?() ?? 0) + 1 - frame.height)
            // UIKit's invalidation delta preserves the pan/deceleration trajectory.
            // Account for any offset adjustment UIKit already made at the bottom.
            var targetOffset = oldOffset + frame.minY - item.frame.minY + oldDistance - distance
            if wasWithinBounds {
                let minimum = -adjustedContentInset.top
                let maximum = max(minimum, contentSize.height - bounds.height + adjustedContentInset.bottom)
                targetOffset = min(max(targetOffset, minimum), maximum)
            }
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
        readingPosition = position
        setNeedsLayout()
    }

    func resetReadingPosition() {
        readingPositionGeneration += 1
        readingPosition = nil
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
            if self.geometryReadingPosition?.itemID == nil { self.geometryReadingPosition = nil }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        startRefreshIfReady()
        restoreReadingPositionIfNeeded()
    }

    private func restoreReadingPositionIfNeeded() {
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
        case .item(let id, let distanceFromTop, let itemOrder):
            let indexPath = readingIndexPath?(id) ?? {
                let index = itemOrder.firstIndex(of: id) ?? 0
                let candidates = Array(itemOrder.dropFirst(index + 1)) + Array(itemOrder.prefix(index).reversed())
                return candidates.lazy.compactMap({ self.readingIndexPath?($0) }).first
            }()
            guard let indexPath else {
                // No old item survives a replacement. Start below the bars;
                // never turn the disappearing refresh gap into an item offset.
                self.readingPosition = .top
                geometryReadingPosition = nil
                setNeedsLayout()
                return
            }
            targetIndexPath = indexPath
            if let replacementID = readingItemID?(indexPath), replacementID != id {
                let replacement = ReadingPosition.item(id: replacementID, distanceFromTop: distanceFromTop,
                                                       itemOrder: readingItemIDs?() ?? [replacementID])
                self.readingPosition = replacement
                if geometryReadingPosition != nil { geometryReadingPosition = replacement }
            }
            guard let frame = collectionViewLayout.layoutAttributesForItem(at: indexPath)?.frame else { return }
            // Resizing can make a card shorter; keep the reading item visible.
            let distance = max(distanceFromTop, (readingTopOcclusion?() ?? 0) + 1 - frame.height)
            targetY = frame.minY - distance - (readingTopInset?() ?? restingAdjustedTopInset) - refreshInset
        }
        let minimumY = -adjustedContentInset.top
        let maximumY = max(minimumY, contentSize.height - bounds.height + adjustedContentInset.bottom)
        let offsetY = min(max(targetY, minimumY), maximumY)
        let tolerance = 0.5 / max(traitCollection.displayScale, 1)
        if abs(contentOffset.y - offsetY) > tolerance {
            super.setContentOffset(CGPoint(x: contentOffset.x, y: offsetY), animated: false)
        }
        finishReadingPositionRestoration(at: targetIndexPath)
    }
}
