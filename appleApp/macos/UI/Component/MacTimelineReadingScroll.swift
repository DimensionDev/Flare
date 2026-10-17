import AppKit
import KotlinSharedUI

@MainActor
final class MacTimelineReadingScroll {
    weak var scrollView: NSScrollView?
    var frames: [String: CGRect] = [:]
    var materializeAnchor: ((String) -> Void)?
    var isInteracting = false {
        didSet { if isInteracting { cancelPreservation() } }
    }
    var preserveAnchor = false {
        didSet {
            if preserveAnchor, !oldValue { pendingAnchor = anchor }
            if !preserveAnchor { pendingAnchor = nil }
        }
    }
    private var presentedData: PagingState<UiTimelineV2>?
    private(set) var presentedSnapshot: TimelineSnapshot?
    private var anchor: (key: String, offset: CGFloat)?
    private var pendingAnchor: (key: String, offset: CGFloat)?
    private var restoredRequest: Int64?
    private var materializedKey: String?
    private var restoreWork: CFRunLoopObserver?
    private var restoreVersion = 0

    func present(_ data: PagingState<UiTimelineV2>, snapshot: TimelineSnapshot?, interacting: Bool) -> PagingState<UiTimelineV2> {
        if interacting, let presentedData { return presentedData }
        let snapshot = snapshot ?? TimelineSnapshot(data: data)
        // Content changes (including gap labels) can change height without changing keys.
        if !snapshot.hasSameContent(other: presentedSnapshot) {
            preserveAnchor = true
            materializedKey = nil
            cancelRestoreWork()
        }
        presentedSnapshot = snapshot
        presentedData = data
        return data
    }

    func update(frames: [String: CGRect], restorationIndex: Int32 = -1, state: TimelineReadingState?) {
        self.frames = frames
        restorePosition(restorationIndex: restorationIndex, state: state)
    }

    func restorePosition(restorationIndex: Int32, state: TimelineReadingState?) {
        guard let state else { cancelPreservation(); return }
        if isInteracting {
            cancelPreservation()
            report(state: state)
            return
        }
        cancelRestoreWork()
        let version = restoreVersion
        let work = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.beforeWaiting.rawValue, false, CFIndex.max) { [weak self] _, _ in
            MainActor.assumeIsolated {
                guard let self, self.restoreVersion == version, !self.isInteracting else { return }
                self.restoreWork = nil
                self.restoreAfterLayout(restorationIndex: restorationIndex, state: state)
            }
        }
        restoreWork = work
        // SwiftUI can adjust clip bounds after preferences and main-queue blocks have run.
        CFRunLoopAddObserver(CFRunLoopGetMain(), work, .commonModes)
    }

    private func restoreAfterLayout(restorationIndex: Int32, state: TimelineReadingState) {
        guard let scrollView else { return }
        let position = state.position
        if let position {
            guard restoredRequest != position.requestId, restorationIndex >= 0 else { return }
        }
        guard let target = position.map({ (key: $0.itemKey, offset: CGFloat($0.offset)) }) ?? pendingAnchor else {
            preserveAnchor = false
            report(state: state)
            return
        }
        guard let frame = frames[target.key] else {
            if position == nil, let snapshot = presentedSnapshot, snapshot.readingIndexOf(key: target.key) < 0 {
                cancelPreservation()
                report(state: state)
            } else {
                materialize(target.key)
            }
            return
        }
        let clip = scrollView.contentView
        var bounds = clip.bounds
        bounds.origin.y = position?.latest == true ? 0 : frame.minY - target.offset
        if let scale = scrollView.window?.backingScaleFactor {
            // SwiftUI otherwise rounds the fractional clip again on every layout.
            bounds.origin.y = (bounds.origin.y * scale).rounded() / scale
        }
        let constrained = clip.constrainBoundsRect(bounds)
        if abs(constrained.minY - clip.bounds.minY) > 0.001 {
            clip.scroll(to: constrained.origin)
            scrollView.reflectScrolledClipView(clip)
        }
        guard abs(clip.bounds.minY - bounds.minY) <= 0.001 else {
            materialize(target.key)
            return
        }
        // Shared publishes the acknowledgement later; a new page must retain the completed target.
        anchor = (target.key, target.offset)
        cancelRestoreWork()
        preserveAnchor = false
        materializedKey = nil
        if let position {
            restoredRequest = position.requestId
            state.positionRestored(requestId: position.requestId)
        }
        report(state: state)
    }

    private func materialize(_ key: String) {
        guard materializedKey != key, let materializeAnchor else { return }
        materializedKey = key
        materializeAnchor(key)
    }

    private func cancelRestoreWork() {
        restoreVersion += 1
        if let restoreWork { CFRunLoopObserverInvalidate(restoreWork) }
        restoreWork = nil
    }

    func cancelPreservation() {
        cancelRestoreWork()
        preserveAnchor = false
        materializedKey = nil
    }

    func report(state: TimelineReadingState?) {
        guard let state, let scrollView, !preserveAnchor, state.position == nil || isInteracting else { return }
        let bounds = scrollView.contentView.bounds
        let visible = frames.filter { $0.value.maxY > bounds.minY && $0.value.minY < bounds.maxY }
            .min { lhs, rhs in
                abs(lhs.value.minY - rhs.value.minY) > 0.5
                    ? lhs.value.minY < rhs.value.minY : lhs.value.minX < rhs.value.minX
            }
        guard let visible else { return }
        // Content-space geometry stays valid when a delayed preference follows a native scroll.
        let viewportAnchor = (key: visible.key, offset: visible.value.minY - bounds.minY)
        // Do not turn half-point layout rounding into a new target on every page.
        if isInteracting || anchor?.key != viewportAnchor.key || abs((anchor?.offset ?? 0) - viewportAnchor.offset) > 0.5 {
            anchor = viewportAnchor
        }
        state.updateViewport(
            itemKey: viewportAnchor.key, offset: Double(viewportAnchor.offset),
            atTop: bounds.minY <= 1, interacting: isInteracting
        )
    }
}
