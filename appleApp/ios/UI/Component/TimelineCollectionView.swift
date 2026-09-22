import UIKit

/// Coordinates reading anchors and refresh presentation across layout changes.
final class TimelineCollectionView: UICollectionView {
    var readingItemID: ((IndexPath) -> String?)?
    var readingIndexPath: ((String) -> IndexPath?)?
    var readingItemIDs: (() -> [String])?
    var readingTopInset: (() -> CGFloat)?
    // Occlusion affects which item is visible, not the bookmark's viewport origin.
    var readingTopOcclusion: (() -> CGFloat)?
    var isScrollInteractionActive: (() -> Bool)?
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

    private var readingPosition: ReadingPosition?
    private var isRestoringReadingPosition = false
    private var appliedTopInset: CGFloat?
    private var restingAutomaticInset: (amount: CGFloat, safeAreaTop: CGFloat)?

    private enum RefreshPhase { case idle, revealing, refreshing, settling }
    private var refreshPhase = RefreshPhase.idle
    private var refreshOffset: CGFloat = 0
    private var refreshAnimation: (from: CGFloat, to: CGFloat, start: CFTimeInterval)?
    private var refreshDisplayLink: CADisplayLink?

    var hasReadingPosition: Bool { readingPosition != nil }
    var isPresentingRefresh: Bool { refreshPhase != .idle }

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
        rememberRestingAutomaticInset()
    }

    private var hasScrollGesture: Bool { isTracking || isDragging || isDecelerating }

    private var allowsReadingPositionRestoration: Bool {
        !hasScrollGesture && isScrollInteractionActive?() != true
    }

    /// UIKit also writes contentInset while refreshing. Apply only the page's delta.
    func setTopContentInset(_ inset: CGFloat) {
        let previous = appliedTopInset ?? contentInset.top
        appliedTopInset = inset
        if inset != previous {
            contentInset.top += inset - previous
        }
    }

    func beginRefreshing(revealingIndicator: Bool) {
        guard preservesReadingPosition, let refreshControl,
              refreshPhase == .idle || refreshPhase == .settling else { return }
        let wasRefreshing = refreshControl.isRefreshing
        let hasViewport = bounds.width > 1 && bounds.height > 1
        let wasAtTop = contentOffset.y + restingAdjustedTopInset <= 1 ||
            (!hasViewport && readingPosition?.itemID == nil)
        if appliedTopInset == nil { appliedTopInset = contentInset.top }
        if refreshPhase == .idle, !wasRefreshing {
            readingPosition = captureReadingPosition() ?? readingPosition
        }
        stopRefreshAnimation()
        if window != nil || wasRefreshing {
            beginNativeRefreshingIfNeeded()
        }

        if wasRefreshing {
            // A pull has already exposed the control. Its elastic distance is not
            // part of the bookmark saved when the gesture ends.
            refreshOffset = refreshInset
            refreshPhase = .refreshing
        } else if revealingIndicator && wasAtTop && !isTracking && !isDragging && !isDecelerating {
            refreshPhase = .revealing
            startRefreshRevealIfReady()
        } else {
            refreshPhase = .refreshing
        }
    }

    private func startRefreshRevealIfReady() {
        guard refreshPhase == .revealing, refreshAnimation == nil,
              window != nil, bounds.width > 1, bounds.height > 1 else { return }
        beginNativeRefreshingIfNeeded()
        // A control started offscreen can defer its inset until it is exposed.
        let height = max(refreshInset, refreshControl?.bounds.height ?? 0)
        guard height > 0 else { return }
        animateRefreshOffset(to: height)
    }

    private func beginNativeRefreshingIfNeeded() {
        guard let refreshControl, !refreshControl.isRefreshing else { return }
        // A surrounding snapshot/measurement transaction may disable animations.
        // That must not suppress the activity indicator's own repeating animation.
        let animationsEnabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(true)
        defer { UIView.setAnimationsEnabled(animationsEnabled) }
        refreshControl.beginRefreshing()
    }

    func endRefreshing() {
        guard let refreshControl, refreshPhase != .settling,
              refreshControl.isRefreshing || isPresentingRefresh else { return }
        layoutIfNeeded()
        UIView.performWithoutAnimation {
            refreshControl.endRefreshing()
            // Preserve the current presented gap until our own transition removes
            // it. UIKit's inset change must not move the reading item separately.
            restoreReadingPositionIfNeeded()
        }
        if isTracking || isDragging || isDecelerating {
            interruptRefreshForScrolling()
        } else {
            refreshPhase = .settling
            animateRefreshOffset(to: 0)
        }
    }

    func interruptRefreshForScrolling() {
        stopRefreshAnimation()
        let managesRefresh = preservesReadingPosition && refreshControl?.isRefreshing == true
        refreshOffset = managesRefresh ? refreshInset : 0
        refreshPhase = managesRefresh ? .refreshing : .idle
        resetReadingPosition()
    }

    func cancelRefresh() {
        stopRefreshAnimation()
        refreshPhase = .idle
        refreshOffset = 0
        resetReadingPosition()
        UIView.performWithoutAnimation { refreshControl?.endRefreshing() }
    }

    private func animateRefreshOffset(to target: CGFloat) {
        // Animate the gap so each frame can use the item's latest measured geometry.
        stopRefreshAnimation()
        guard window != nil, UIView.areAnimationsEnabled, !UIAccessibility.isReduceMotionEnabled,
              abs(target - refreshOffset) > 0.5 else {
            finishRefreshAnimation(at: target)
            return
        }
        refreshAnimation = (refreshOffset, target, CACurrentMediaTime())
        let link = CADisplayLink(target: RefreshAnimationTarget(self), selector: #selector(RefreshAnimationTarget.tick(_:)))
        refreshDisplayLink = link
        link.add(to: .main, forMode: .common)
    }

    fileprivate func advanceRefreshAnimation(_ link: CADisplayLink) {
        guard let animation = refreshAnimation else { return }
        let progress = min(max((link.timestamp - animation.start) / 0.3, 0), 1)
        if progress >= 1 {
            finishRefreshAnimation(at: animation.to)
            return
        }
        let eased = 1 - pow(1 - progress, 3)
        refreshOffset = animation.from + (animation.to - animation.from) * eased
        layoutIfNeeded()
        restoreReadingPositionIfNeeded()
    }

    private func finishRefreshAnimation(at offset: CGFloat) {
        stopRefreshAnimation()
        refreshOffset = offset
        refreshPhase = refreshPhase == .revealing ? .refreshing : .idle
        setNeedsLayout()
        restoreReadingPositionIfNeeded()
    }

    private func stopRefreshAnimation() {
        refreshDisplayLink?.invalidate()
        refreshDisplayLink = nil
        refreshAnimation = nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil, let animation = refreshAnimation {
            finishRefreshAnimation(at: animation.to)
        } else if window != nil {
            if refreshPhase == .refreshing {
                beginNativeRefreshingIfNeeded()
            }
            setNeedsLayout()
        }
    }

    override var frame: CGRect {
        willSet {
            // Setting frame can resize bounds without calling its setter.
            if frame.size != newValue.size, bounds.width > 1, bounds.height > 1 {
                prepareForLayoutChange()
            }
        }
    }

    override var bounds: CGRect {
        willSet {
            if bounds.size != newValue.size, bounds.width > 1, bounds.height > 1 {
                prepareForLayoutChange()
            }
        }
    }

    func prepareForLayoutChange() {
        guard preservesReadingPosition,
              readingPosition == nil,
              !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }

        readingPosition = captureReadingPosition()
    }

    func prepareForSnapshotChange() {
        guard preservesReadingPosition,
              !isRestoringReadingPosition,
              allowsReadingPositionRestoration else { return }

        // An explicit jump to the top applies to the loaded snapshot. Once items
        // are visible, later snapshots must preserve their IDs instead.
        if readingPosition?.itemID == nil {
            readingPosition = captureReadingPosition()
        }
    }

    func captureReadingPosition() -> ReadingPosition? {
        if isPresentingRefresh, readingPosition?.itemID != nil { return readingPosition }
        guard bounds.width > 1, bounds.height > 1 else { return nil }
        let viewportTop = readingViewportTop()
        if let firstItem = firstVisibleReadingItem(viewportTop: viewportTop) {
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
        if refreshPhase == .refreshing, readingPosition == nil, refreshControl?.isRefreshing == true {
            refreshOffset = min(refreshInset, max(0, -contentOffset.y - restingAdjustedTopInset))
        }
        let top = contentOffset.y + (readingTopInset?() ?? restingAdjustedTopInset) + refreshOffset
        return isPresentingRefresh ? max(top, 0) : top
    }

    private func firstVisibleReadingItem(viewportTop: CGFloat) -> (id: String, frame: CGRect)? {
        let visibleTop = viewportTop + max(readingTopOcclusion?() ?? 0, 0)
        let viewportBottom = contentOffset.y + bounds.height - adjustedContentInset.bottom
        return indexPathsForVisibleItems.compactMap { indexPath -> (id: String, frame: CGRect)? in
            guard let id = readingItemID?(indexPath),
                  let frame = layoutAttributesForItem(at: indexPath)?.frame,
                  frame.maxY > visibleTop,
                  frame.minY < viewportBottom else { return nil }
            return (id, frame)
        }.min { lhs, rhs in
            if abs(lhs.frame.minY - rhs.frame.minY) > 0.5 {
                return lhs.frame.minY < rhs.frame.minY
            }
            return lhs.frame.minX < rhs.frame.minX
        }
    }

    /// Apply measured geometry without moving content under an active gesture.
    /// Called from the controller's coalesced flush, outside cell measurement/layout.
    func invalidateMeasuredHeights() {
        let viewportTop = readingViewportTop()
        let item = preservesReadingPosition && hasScrollGesture
            ? firstVisibleReadingItem(viewportTop: viewportTop) : nil
        let oldOffset = contentOffset.y
        prepareForLayoutChange()
        UIView.performWithoutAnimation {
            collectionViewLayout.invalidateLayout()
            layoutIfNeeded()
            guard let item, let path = readingIndexPath?(item.id),
                  let frame = collectionViewLayout.layoutAttributesForItem(at: path)?.frame else { return }
            let oldDistance = item.frame.minY - viewportTop
            // The estimate may have put the viewport beyond the measured card.
            // Keep that ID visible using the same clamp as an idle reading anchor.
            let distance = max(oldDistance, (readingTopOcclusion?() ?? 0) + 1 - frame.height)
            // UIKit's invalidation delta preserves the pan/deceleration trajectory.
            // Account for any offset adjustment UIKit already made at the bottom.
            let delta = oldOffset + frame.minY - item.frame.minY + oldDistance - distance - contentOffset.y
            guard abs(delta) > 0.5 / max(traitCollection.displayScale, 1) else { return }
            let context = UICollectionViewLayoutInvalidationContext()
            context.contentOffsetAdjustment.y = delta
            collectionViewLayout.invalidateLayout(with: context)
        }
    }

    func restoreReadingPosition(_ position: ReadingPosition) {
        readingPosition = position
        setNeedsLayout()
    }

    func resetReadingPosition() {
        readingPosition = nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        startRefreshRevealIfReady()
        restoreReadingPositionIfNeeded()
    }

    private func restoreReadingPositionIfNeeded() {
        guard preservesReadingPosition, !isRestoringReadingPosition,
              bounds.width > 1, bounds.height > 1 else { return }
        if !allowsReadingPositionRestoration {
            resetReadingPosition()
            return
        }
        if readingPosition == nil, isPresentingRefresh {
            readingPosition = captureReadingPosition()
        }
        guard let readingPosition else { return }

        isRestoringReadingPosition = true
        defer { isRestoringReadingPosition = false }

        let targetY: CGFloat
        switch readingPosition {
        case .top:
            targetY = -restingAdjustedTopInset - refreshOffset
        case .item(let id, let distanceFromTop, let itemOrder):
            let indexPath = readingIndexPath?(id) ?? {
                let index = itemOrder.firstIndex(of: id) ?? 0
                let candidates = Array(itemOrder.dropFirst(index + 1)) + Array(itemOrder.prefix(index).reversed())
                return candidates.lazy.compactMap({ self.readingIndexPath?($0) }).first
            }()
            guard let indexPath,
                  let frame = collectionViewLayout.layoutAttributesForItem(at: indexPath)?.frame else {
                resetReadingPosition()
                return
            }
            // Resizing can make a card shorter; keep the reading item visible.
            let distance = max(distanceFromTop, (readingTopOcclusion?() ?? 0) + 1 - frame.height)
            if distance != distanceFromTop {
                // Refined heights can make an estimated offset unreachable. Keep
                // the visible offset instead of replaying that estimate on resize.
                self.readingPosition = .item(id: id, distanceFromTop: distance, itemOrder: itemOrder)
            }
            targetY = frame.minY - distance - (readingTopInset?() ?? restingAdjustedTopInset) - refreshOffset
        }
        let minimumY = -max(adjustedContentInset.top, restingAdjustedTopInset + refreshOffset)
        let maximumY = max(minimumY, contentSize.height - bounds.height + adjustedContentInset.bottom)
        let offsetY = min(max(targetY, minimumY), maximumY)
        let tolerance = 0.5 / max(traitCollection.displayScale, 1)
        if abs(contentOffset.y - offsetY) > tolerance {
            setContentOffset(CGPoint(x: contentOffset.x, y: offsetY), animated: false)
        }
    }
}

@MainActor
private final class RefreshAnimationTarget: NSObject {
    private weak var collectionView: TimelineCollectionView?

    init(_ collectionView: TimelineCollectionView) { self.collectionView = collectionView }

    @objc func tick(_ link: CADisplayLink) {
        guard let collectionView else { link.invalidate(); return }
        collectionView.advanceRefreshAnimation(link)
    }
}
