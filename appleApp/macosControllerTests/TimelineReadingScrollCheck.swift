import AppKit
import KotlinSharedUI

private final class ReadingState: NSObject, TimelineReadingState {
    var position: TimelineReadingPosition?
    var hasNewContent = false
    var isRefreshing = false
    var itemKey: String?
    var offset = 0.0
    var interacting = false
    func updateViewport(itemKey: String?, offset: Double, atTop: Bool, interacting: Bool) {
        self.itemKey = itemKey
        self.offset = offset
        self.interacting = interacting
    }
    func positionRestored(requestId: Int64) { if position?.requestId == requestId { position = nil } }
    func cancelRestoration() { position = nil }
    func savePosition() {}
    nonisolated func __refreshAutomatically(completionHandler: @escaping @Sendable ((any Error)?) -> Void) { completionHandler(nil) }
    nonisolated func __showLatest(completionHandler: @escaping @Sendable ((any Error)?) -> Void) { completionHandler(nil) }
}

private final class Document: NSView { override var isFlipped: Bool { true } }

@main
struct TimelineReadingScrollCheck {
    @MainActor
    static func main() {
        let scroll = NSScrollView(frame: CGRect(x: 0, y: 0, width: 400, height: 400))
        scroll.documentView = Document(frame: CGRect(x: 0, y: 0, width: 400, height: 4000))
        let bridge = MacTimelineReadingScroll()
        bridge.scrollView = scroll
        let state = ReadingState()
        func frame(_ y: CGFloat) -> CGRect { CGRect(x: 0, y: y, width: 400, height: 200) }
        func assertY(_ expected: CGFloat) { precondition(abs(scroll.contentView.bounds.minY - expected) < 0.01) }

        state.position = TimelineReadingPosition(itemKey: "post", offset: -37.25, requestId: 1, latest: false)
        bridge.update(frames: ["post": frame(600)], state: state)
        assertY(637.25)
        state.position = nil
        bridge.report(state: state)
        precondition(state.itemKey == "post" && abs(state.offset + 37.25) < 0.01)

        bridge.preserveAnchor = true
        bridge.update(frames: ["post": frame(262.75)], state: state)
        assertY(937.25)
        precondition(abs(state.offset + 37.25) < 0.01)

        bridge.isInteracting = true
        bridge.preserveAnchor = true
        bridge.update(frames: ["post": frame(62.75)], state: state)
        assertY(937.25)
        precondition(state.interacting)

        bridge.isInteracting = false
        state.position = TimelineReadingPosition(itemKey: "post", offset: 0, requestId: 2, latest: true)
        bridge.update(frames: ["post": frame(62.75)], state: state)
        assertY(0)
        print("PASS: exact offset, insertion preservation, active-scroll protection, explicit latest")
    }
}
