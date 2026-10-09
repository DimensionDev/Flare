import AppKit
import KotlinSharedUI

@MainActor
final class MacTimelineReadingScroll {
    weak var scrollView: NSScrollView?
    var frames: [String: CGRect] = [:]
    var isInteracting = false
    var preserveAnchor = false
    private var presentedData: PagingState<UiTimelineV2>?
    private var presentedKeys: [String] = []

    func present(_ data: PagingState<UiTimelineV2>, interacting: Bool) -> PagingState<UiTimelineV2> {
        if interacting, let presentedData { return presentedData }
        let keys: [String]
        if case .success(let success) = onEnum(of: data) {
            keys = (0..<Int(success.itemCount)).compactMap { success.peek(index: Int32($0))?.itemKey }
        } else {
            keys = []
        }
        if keys != presentedKeys { preserveAnchor = true }
        presentedKeys = keys
        presentedData = data
        return data
    }

    private var anchor: (key: String, offset: CGFloat)?
    private var restoredRequest: Int64?

    func update(frames: [String: CGRect], state: TimelineReadingState?) {
        self.frames = frames
        guard let state, let scrollView else { return }
        if let position = state.position {
            guard restoredRequest != position.requestId, !isInteracting,
                  let frame = frames[position.itemKey] else { return }
            let delta = position.latest ? -scrollView.contentView.bounds.minY : frame.minY - CGFloat(position.offset)
            adjust(by: delta)
            restoredRequest = position.requestId
            preserveAnchor = false
            DispatchQueue.main.async {
                state.positionRestored(requestId: position.requestId)
                self.report(state: state)
            }
            return
        }
        if preserveAnchor, !isInteracting, let anchor, let frame = frames[anchor.key] {
            adjust(by: frame.minY - anchor.offset)
        }
        preserveAnchor = false
        report(state: state)
    }

    private func adjust(by delta: CGFloat) {
        guard abs(delta) > 0.5, let scrollView else { return }
        let clip = scrollView.contentView
        var target = clip.bounds.origin
        target.y += delta
        let constrained = clip.constrainBoundsRect(CGRect(origin: target, size: clip.bounds.size)).origin
        let applied = constrained.y - clip.bounds.minY
        clip.scroll(to: constrained)
        scrollView.reflectScrolledClipView(clip)
        frames = frames.mapValues { $0.offsetBy(dx: 0, dy: -applied) }
    }

    func report(state: TimelineReadingState?) {
        guard let state, let scrollView else { return }
        let visible = frames.filter { $0.value.maxY > 0 && $0.value.minY < scrollView.contentView.bounds.height }
            .min { lhs, rhs in
                abs(lhs.value.minY - rhs.value.minY) > 0.5
                    ? lhs.value.minY < rhs.value.minY : lhs.value.minX < rhs.value.minX
            }
        anchor = visible.map { ($0.key, $0.value.minY) }
        state.updateViewport(
            itemKey: anchor?.key, offset: Double(anchor?.offset ?? 0),
            atTop: scrollView.contentView.bounds.minY <= 1, interacting: isInteracting
        )
    }
}
