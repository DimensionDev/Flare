import AppKit
import KotlinSharedUI
import SwiftUI

@MainActor
private final class Reading: TimelineReadingState {
    var position: TimelineReadingPosition? { didSet { onPositionChange?() } }
    var onPositionChange: (() -> Void)?
    var key: String?
    var offset = 0.0
    var reports = 0
    var deferAcknowledgement = false
    var restoredRequests: [Int64] = []
    func updateViewport(itemKey: String?, offset: Double, atTop: Bool, interacting: Bool) {
        key = itemKey; self.offset = offset; reports += 1
    }
    func positionRestored(requestId: Int64) {
        restoredRequests.append(requestId)
        if !deferAcknowledgement, position?.requestId == requestId { position = nil }
    }
    func cancelRestoration() { position = nil }
}

private final class WeakView {
    weak var view: NSView?
    init(_ view: NSView) { self.view = view }
}

@MainActor
private final class Model: ObservableObject {
    @Published var data: PagingState<UiTimelineV2>
    @Published var interacting = false
    let bridge = MacTimelineReadingScroll()
    let reading = Reading()
    var views: [String: WeakView] = [:]
    var missingAnchorFrames = 0
    var frameUpdates = 0
    var expectedKey: String?
    var onMissingAnchor: (() -> Void)?
    var materializations = 0
    let inset: CGFloat
    let height: Double
    init(count: Int = 120, height: Double = 240, inset: CGFloat = 0) {
        self.height = height; self.inset = inset
        data = PagingState((0..<count).map { UiTimelineV2(key: String($0), height: height) })
        reading.onPositionChange = { [weak self] in self?.objectWillChange.send() }
    }
    func replace(_ ids: Range<Int>) {
        data = PagingState(ids.map { UiTimelineV2(key: String($0), height: height) })
    }
}

private struct NativeAnchor: NSViewRepresentable {
    let key: String
    let model: Model
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        model.views[key] = WeakView(view)
        return view
    }
    func updateNSView(_ nsView: NSView, context: Context) {}
}

private struct WindowContent: View {
    @ObservedObject var model: Model
    var body: some View {
        GeometryReader { _ in
            let data = model.bridge.present(model.data, snapshot: nil, interacting: model.interacting)
            let snapshot = model.bridge.presentedSnapshot
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 2) {
                        ForEach(data.rows, id: \.key) { row in
                            Text("Post \(row.key)")
                                .frame(maxWidth: .infinity).frame(height: row.height)
                                .timelineReadingAnchor(row.key)
                                .background(NativeAnchor(key: row.key, model: model))
                        }
                    }
                    .padding(.top, model.inset)
                    .coordinateSpace(name: "timeline-reading-content")
                }
                .environment(\.tracksTimelineReadingPosition, true)
                .onPreferenceChange(TimelineReadingFrames.self) { frames in
                    model.frameUpdates += 1
                    if let key = model.expectedKey, frames[key] == nil { model.missingAnchorFrames += 1 }
                    let index = model.reading.position.map { snapshot?.restorationIndex(position: $0, refreshing: false) ?? -1 } ?? -1
                    model.bridge.update(frames: frames, restorationIndex: index, state: model.reading)
                    if let key = model.expectedKey, frames[key] == nil, let action = model.onMissingAnchor {
                        model.onMissingAnchor = nil
                        action()
                    }
                }
                .onChange(of: snapshot.map(ObjectIdentifier.init)) { _, _ in
                    let index = model.reading.position.map { snapshot?.restorationIndex(position: $0, refreshing: false) ?? -1 } ?? -1
                    model.bridge.restorePosition(restorationIndex: index, state: model.reading)
                }
                .onChange(of: model.reading.position?.requestId) { _, _ in
                    let index = model.reading.position.map { snapshot?.restorationIndex(position: $0, refreshing: false) ?? -1 } ?? -1
                    model.bridge.restorePosition(restorationIndex: index, state: model.reading)
                }
                .onAppear {
                    model.bridge.materializeAnchor = { [weak model = model] key in
                        guard let model else { return }
                        model.materializations += 1
                        proxy.scrollTo(key, anchor: .top)
                    }
                }
                .onDisappear { model.bridge.cancelPreservation(); model.bridge.materializeAnchor = nil }
                .onReceive(NotificationCenter.default.publisher(for: NSView.boundsDidChangeNotification, object: model.bridge.scrollView?.contentView)) { event in
                    guard event.object as? NSClipView === model.bridge.scrollView?.contentView else { return }
                    let index = model.reading.position.map { snapshot?.restorationIndex(position: $0, refreshing: false) ?? -1 } ?? -1
                    model.bridge.restorePosition(restorationIndex: index, state: model.reading)
                }
                .onReceive(NotificationCenter.default.publisher(for: NSScrollView.willStartLiveScrollNotification)) { event in
                    guard event.object as? NSScrollView === model.bridge.scrollView else { return }
                    model.interacting = true; model.bridge.isInteracting = true
                    model.reading.cancelRestoration(); model.bridge.report(state: model.reading)
                }
                .onReceive(NotificationCenter.default.publisher(for: NSScrollView.didEndLiveScrollNotification)) { event in
                    guard event.object as? NSScrollView === model.bridge.scrollView else { return }
                    model.interacting = false; model.bridge.isInteracting = false
                    model.bridge.report(state: model.reading)
                }
            }
        }
    }
}

private struct Failure: Error, CustomStringConvertible {
    let description: String
    init(_ description: String) { self.description = description }
}

@MainActor
private final class WindowFixture {
    let model: Model
    let window: NSWindow
    let scroll: NSScrollView
    var key = ""
    var offset: CGFloat = 0

    private init(model: Model, window: NSWindow, scroll: NSScrollView) {
        self.model = model; self.window = window; self.scroll = scroll
    }
    static func open(height: Double = 240, inset: CGFloat = 0, count: Int = 120, offset: CGFloat = 37.25) async throws -> WindowFixture {
        let model = Model(count: count, height: height, inset: inset)
        let host = NSHostingView(rootView: WindowContent(model: model))
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 400, height: 600), styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false; window.contentView = host; window.orderFront(nil)
        try await wait { findScroll(in: host) != nil }
        let scroll = findScroll(in: host)!
        model.bridge.scrollView = scroll
        scroll.contentView.scroll(to: CGPoint(x: 0, y: 20 * (height + 2) + offset + inset))
        scroll.reflectScrolledClipView(scroll.contentView)
        let fixture = WindowFixture(model: model, window: window, scroll: scroll)
        try await wait { fixture.visible() != nil && model.reading.key != nil }
        let visible = fixture.visible()!
        fixture.key = visible.key; fixture.offset = visible.offset; model.expectedKey = visible.key
        try await fixture.expectRetained("initial position")
        return fixture
    }
    func close() { window.close() }
    func visible() -> (key: String, offset: CGFloat)? {
        model.views.compactMap { key, reference -> (key: String, offset: CGFloat)? in
            guard let view = reference.view, view.window === window, !view.isHiddenOrHasHiddenAncestor, !view.visibleRect.isEmpty else { return nil }
            let frame = view.convert(view.bounds, to: scroll.contentView).offsetBy(dx: 0, dy: -scroll.contentView.bounds.minY)
            return frame.maxY > 0 && frame.minY < scroll.contentView.bounds.height ? (key, frame.minY) : nil
        }.min { $0.offset < $1.offset }
    }
    func retained() -> Bool {
        guard let visible = visible() else { return false }
        return visible.key == key && abs(visible.offset - offset) <= 0.5 && model.reading.key == key && abs(model.reading.offset - offset) <= 0.5
    }
    func expectRetained(_ label: String) async throws {
        do { try await Self.wait { self.retained() } }
        catch { throw Failure("\(label): expected \(key)/\(offset), visible \(String(describing: visible())), saved \(model.reading.key ?? "nil")/\(model.reading.offset)") }
        try await Task.sleep(for: .milliseconds(35))
        guard retained() else { throw Failure("\(label): later layout overwrote the anchor") }
    }
    func interaction(_ active: Bool) {
        NotificationCenter.default.post(name: active ? NSScrollView.willStartLiveScrollNotification : NSScrollView.didEndLiveScrollNotification, object: scroll)
    }
    static func findScroll(in view: NSView) -> NSScrollView? {
        if let scroll = view as? NSScrollView { return scroll }
        return view.subviews.lazy.compactMap { findScroll(in: $0) }.first
    }
    static func wait(_ condition: () -> Bool) async throws {
        for _ in 0..<100 {
            if condition() { return }
            try await Task.sleep(for: .milliseconds(16))
        }
        throw Failure("Timed out waiting for native layout")
    }
}

@main
private struct TimelineReadingWindowCheck {
    @MainActor static func main() {
        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)
        Task {
            let checks: [(String, @MainActor () async throws -> Void)] = [
                ("ten consecutive prepend pages", { try await pages(size: 20) }),
                ("ten single-row prepends", { try await pages(size: 1) }),
                ("prepend pages at a post boundary", { try await pages(size: 20, inset: 63.5, offset: 0) }),
                ("variable-height prepend pages", { try await pages(size: 20, variableHeights: true) }),
                ("variable-height single-row prepends", { try await pages(size: 1, variableHeights: true) }),
                ("temporarily missing anchor", missingAnchor),
                ("coalesced updates after live scroll", deferredUpdates),
                ("viewport-height rows and inset", { try await pages(size: 20, height: 600, inset: 63.5) }),
                ("tall rows and inset", { try await pages(size: 20, height: 1000, inset: 63.5) }),
                ("new snapshot during pending restore", supersedingSnapshot),
                ("user scroll cancels queued compensation", userInterruption),
                ("view latest supersedes queued compensation", latestInterruption),
                ("removed anchor releases preservation", removedAnchor),
                ("offscreen saved position", explicitRestoration),
                ("new page during shared acknowledgement", delayedAcknowledgement),
                ("tail appends do not accumulate rounding", { try await tailAppends() }),
                ("tail appends at a post boundary", { try await tailAppends(offset: 0) }),
                ("height changes do not accumulate rounding", heightChanges),
            ]
            var failures = 0
            for (name, check) in checks {
                do { try await check(); print("PASS: \(name)") }
                catch { failures += 1; print("FAIL: \(name): \(error)") }
            }
            print("RESULT: \(checks.count - failures)/\(checks.count) passed")
            exit(failures == 0 ? 0 : 1)
        }
        app.run()
    }
    @MainActor private static func pages(size: Int, height: Double = 240, inset: CGFloat = 0, variableHeights: Bool = false, offset: CGFloat = 37.25) async throws {
        let fixture = try await WindowFixture.open(height: height, inset: inset, offset: offset)
        defer { fixture.close() }
        for page in 1...10 {
            if variableHeights {
                fixture.model.data = PagingState((-size * page..<120).map { id in
                    UiTimelineV2(key: String(id), height: id < 0 ? [120.25, 641.5, 1000.75][abs(id) % 3] : height)
                })
            } else {
                fixture.model.replace(-size * page..<120)
            }
            try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 120 + size * page }
            try await fixture.expectRetained("page \(page)")
        }
        let expected = (-size * 10..<120).map(String.init)
        guard fixture.model.bridge.presentedSnapshot?.rows.map(\.key) == expected else { throw Failure("Rows lost or reordered") }
    }
    @MainActor private static func missingAnchor() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.model.replace(-200..<120)
        try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 320 }
        try await fixture.expectRetained("missing anchor")
        guard fixture.model.missingAnchorFrames > 0 else { throw Failure("Fixture did not exercise missing geometry") }
    }
    @MainActor private static func deferredUpdates() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.interaction(true)
        for page in 1...10 {
            fixture.model.replace(-20 * page..<120)
            try await Task.sleep(for: .milliseconds(16))
            guard fixture.model.bridge.presentedSnapshot?.rows.count == 120, fixture.retained() else { throw Failure("Changed the active scroll snapshot") }
        }
        fixture.interaction(false)
        try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 320 }
        try await fixture.expectRetained("scroll ended")
    }
    @MainActor private static func supersedingSnapshot() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.model.onMissingAnchor = { fixture.model.replace(-240..<120) }
        fixture.model.replace(-200..<120)
        try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 360 }
        try await fixture.expectRetained("superseding snapshot")
    }
    @MainActor private static func userInterruption() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.model.onMissingAnchor = { fixture.interaction(true) }
        fixture.model.replace(-200..<120)
        try await WindowFixture.wait { fixture.model.interacting && fixture.model.bridge.presentedSnapshot?.rows.count == 320 }
        fixture.scroll.contentView.scroll(to: CGPoint(x: 0, y: fixture.scroll.contentView.bounds.minY + 150))
        fixture.scroll.reflectScrolledClipView(fixture.scroll.contentView)
        try await Task.sleep(for: .milliseconds(50))
        guard let visible = fixture.visible(), visible.key != fixture.key else { throw Failure("User's new viewport was overwritten") }
        fixture.key = visible.key; fixture.offset = visible.offset
        try await fixture.expectRetained("user interruption")
        guard fixture.model.materializations == 0 else { throw Failure("Cancelled work still materialized the old anchor") }
        fixture.interaction(false)
        try await fixture.expectRetained("user stopped")
    }
    @MainActor private static func latestInterruption() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.model.onMissingAnchor = {
            fixture.model.reading.position = TimelineReadingPosition(itemKey: "-200", offset: 0, requestId: 1, latest: true)
        }
        fixture.model.replace(-200..<120)
        fixture.key = "-200"; fixture.offset = 0
        try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 320 && fixture.model.reading.position == nil }
        try await fixture.expectRetained("view latest")
        guard abs(fixture.scroll.contentView.bounds.minY) <= 0.5 else { throw Failure("Latest did not reach the head") }
    }
    @MainActor private static func removedAnchor() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.model.replace(200..<320)
        try await WindowFixture.wait { !fixture.model.bridge.preserveAnchor && fixture.model.reading.key.flatMap(Int.init).map { $0 >= 200 } == true }
        guard let visible = fixture.visible(), visible.key == fixture.model.reading.key,
              abs(visible.offset - fixture.model.reading.offset) <= 0.5 else { throw Failure("Removed anchor blocked new viewport reports") }
    }
    @MainActor private static func explicitRestoration() async throws {
        let fixture = try await WindowFixture.open(inset: 63.5)
        defer { fixture.close() }
        fixture.model.reading.position = TimelineReadingPosition(itemKey: "80", offset: -71.25, requestId: 1, latest: false)
        fixture.key = "80"; fixture.offset = -71.25
        try await WindowFixture.wait { fixture.model.reading.position == nil }
        try await fixture.expectRetained("explicit restoration")
        let pixels = fixture.scroll.contentView.bounds.minY * fixture.window.backingScaleFactor
        guard abs(pixels - pixels.rounded()) < 0.001 else { throw Failure("Restored clip is not aligned to backing pixels") }
    }

    @MainActor private static func delayedAcknowledgement() async throws {
        let fixture = try await WindowFixture.open()
        defer { fixture.close() }
        fixture.model.reading.deferAcknowledgement = true
        fixture.model.reading.position = TimelineReadingPosition(itemKey: "80", offset: -71.25, requestId: 1, latest: false)
        try await WindowFixture.wait { fixture.model.reading.restoredRequests == [1] }
        fixture.key = "80"; fixture.offset = -71.25
        fixture.model.reading.position = nil
        fixture.model.replace(-20..<120)
        try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 140 }
        try await fixture.expectRetained("shared acknowledgement and prepend")
    }

    @MainActor private static func tailAppends(offset: CGFloat = 37.25) async throws {
        let fixture = try await WindowFixture.open(count: 240, offset: offset)
        defer { fixture.close() }
        for page in 1...12 {
            fixture.model.replace(0..<(240 + page * 20))
            try await WindowFixture.wait { fixture.model.bridge.presentedSnapshot?.rows.count == 240 + page * 20 }
            try await fixture.expectRetained("append \(page)")
        }
    }
    @MainActor private static func heightChanges() async throws {
        let fixture = try await WindowFixture.open(count: 240)
        defer { fixture.close() }
        for change in 1...12 {
            fixture.model.data = PagingState(fixture.model.data.rows.map { $0.key == fixture.key ? UiTimelineV2(key: $0.key, height: 240 + Double(change % 2) * 12) : $0 })
            try await Task.sleep(for: .milliseconds(35))
            try await fixture.expectRetained("height change \(change)")
        }
    }

}
