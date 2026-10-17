import AppKit
import KotlinSharedUI

private final class ReadingState: NSObject, TimelineReadingState {
    var position: TimelineReadingPosition?
    var hasNewContent = false
    var isRefreshing = false
    var itemKey: String?
    var offset = 0.0
    var interacting = false
    var reports = 0
    func updateViewport(itemKey: String?, offset: Double, atTop: Bool, interacting: Bool) {
        reports += 1
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
        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)
        Task { await check(); exit(0) }
        app.run()
    }

    @MainActor
    private static func check() async {
        let scroll = NSScrollView(frame: CGRect(x: 0, y: 0, width: 400, height: 400))
        scroll.documentView = Document(frame: CGRect(x: 0, y: 0, width: 400, height: 4000))
        let bridge = MacTimelineReadingScroll()
        bridge.scrollView = scroll
        let state = ReadingState()
        func frame(_ y: CGFloat) -> CGRect { CGRect(x: 0, y: y, width: 400, height: 200) }
        func assertY(_ expected: CGFloat) { precondition(abs(scroll.contentView.bounds.minY - expected) < 0.01) }
        func settle() async { try? await Task.sleep(for: .milliseconds(10)) }

        state.position = TimelineReadingPosition(itemKey: "post", offset: -37.25, requestId: 1, latest: false)
        bridge.update(frames: ["post": frame(600)], restorationIndex: 1, state: state)
        await settle()
        assertY(637.25)
        state.position = nil
        bridge.report(state: state)
        precondition(state.itemKey == "post" && abs(state.offset + 37.25) < 0.01)

        bridge.preserveAnchor = true
        bridge.update(frames: ["post": frame(900)], restorationIndex: 1, state: state)
        await settle()
        assertY(937.25)
        precondition(abs(state.offset + 37.25) < 0.01)

        // A gap label above the viewport changes height while its source is loading.
        bridge.preserveAnchor = true
        bridge.update(frames: ["post": frame(924)], restorationIndex: -1, state: state)
        await settle()
        assertY(961.25)
        precondition(state.itemKey == "post" && abs(state.offset + 37.25) < 0.01)
        precondition(state.position == nil)
        scroll.contentView.scroll(to: CGPoint(x: 0, y: 0))
        bridge.update(frames: ["new-head": frame(0), "post": frame(924)], restorationIndex: -1, state: state)
        await settle()
        assertY(0)
        precondition(state.itemKey == "new-head" && state.position == nil)

        scroll.contentView.scroll(to: CGPoint(x: 0, y: 937.25))
        bridge.update(frames: ["post": frame(900)], restorationIndex: -1, state: state)
        bridge.isInteracting = true
        bridge.preserveAnchor = true
        bridge.update(frames: ["post": frame(1000)], restorationIndex: 1, state: state)
        await settle()
        assertY(937.25)
        precondition(state.interacting)

        bridge.isInteracting = false
        bridge.update(frames: ["post": frame(900)], state: state)
        await settle()
        bridge.preserveAnchor = true
        bridge.update(frames: [:], state: state)
        await settle()
        precondition(bridge.preserveAnchor && state.itemKey == "post")
        bridge.update(frames: ["post": frame(1200)], state: state)
        await settle()
        assertY(1237.25)
        precondition(!bridge.preserveAnchor && abs(state.offset + 37.25) < 0.01)

        bridge.preserveAnchor = true
        bridge.update(frames: ["post": frame(1600)], state: state)
        bridge.isInteracting = true
        await settle()
        assertY(1237.25)
        bridge.isInteracting = false
        scroll.contentView.scroll(to: CGPoint(x: 0, y: 937.25))
        state.position = TimelineReadingPosition(itemKey: "post", offset: 0, requestId: 2, latest: true)
        bridge.update(frames: ["post": frame(1000)], restorationIndex: -1, state: state)
        await settle()
        assertY(937.25)
        precondition(state.position?.requestId == 2)
        bridge.update(frames: ["post": frame(1000)], restorationIndex: 0, state: state)
        await settle()
        assertY(0)
        try? await Task.sleep(for: .milliseconds(20))
        precondition(state.position == nil)
        let reports = state.reports
        bridge.isInteracting = true
        for offset in 0..<100 {
            bridge.update(frames: ["post": frame(-Double(offset) - 0.25)], state: state)
        }
        precondition(state.reports == reports + 100)
        precondition(state.itemKey == "post" && abs(state.offset + 99.25) < 0.01)
        print("PASS: exact offset, insertion and gap-height preservation, direct scroll to new posts, active-scroll protection, latest waits for shared snapshot validation, 100 visible-only geometry reports")
    }
}
