import AppKit
import Combine
import SwiftUI
import XCTest
import KotlinSharedUI
import FlareAppleUI

@MainActor
final class TimelineReadingScrollViewTests: XCTestCase {
    func testGapKeepsItsSuccessorAfterInsertion() async throws { try await checkGap() }
    func testClosedGapKeepsItsSuccessorThroughHeightChanges() async throws { try await checkGap(closesGap: true) }
    func testUserScrollingReleasesTheGapAnchor() async throws { try await checkGap(userScroll: true) }
    func testSuccessorReturnsAfterTemporaryWindowReplacement() async throws { try await checkGap(replacesWindow: true) }
    func testSuccessorSurvivesAnEmptyLoadingWindow() async throws { try await checkGap(replacesWindow: true, emptyWindow: true) }
    func testEmptyResultReleasesTheGapAnchor() async throws { try await checkGap(replacesWindow: true, emptyWindow: true, clearsList: true) }
    func testLargeInsertionKeepsItsSuccessor() async throws { try await checkGap(insertedCount: 241) }
    func testTwoColumnInsertionKeepsItsSuccessor() async throws { try await checkGap(columns: 2, insertedCount: 241) }
    func testKeyboardPagingReleasesTheGapAnchor() async throws {
        try await checkGap(scrollAfterGap: { $0.pageDown(nil) })
    }
    func testMouseWheelReleasesTheGapAnchor() async throws {
        try await checkGap(scrollAfterGap: { scroll in
            let event = try XCTUnwrap(CGEvent(scrollWheelEvent2Source: nil, units: .line, wheelCount: 1,
                                             wheel1: -3, wheel2: 0, wheel3: 0))
            scroll.scrollWheel(with: try XCTUnwrap(NSEvent(cgEvent: event)))
        })
    }
    func testUnbracketedLiveScrollReleasesTheGapAnchor() async throws {
        try await checkGap(scrollAfterGap: { scroll in
            scroll.contentView.scroll(to: CGPoint(x: 0, y: scroll.contentView.bounds.minY + 40))
            scroll.reflectScrolledClipView(scroll.contentView)
            NotificationCenter.default.post(name: NSScrollView.didLiveScrollNotification, object: scroll)
        })
    }

    private func checkGap(closesGap: Bool = false, userScroll: Bool = false,
                          replacesWindow: Bool = false, emptyWindow: Bool = false, clearsList: Bool = false,
                          columns: Int = 1, insertedCount: Int = 2,
                          scrollAfterGap: ((NSScrollView) throws -> Void)? = nil) async throws {
        _ = NSApplication.shared
        let model = Model(columns: columns)
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 320, height: 250),
                              styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        var recordFrames = false
        var drawnOffsets: [CGFloat?] = []
        let hosting = RecordingHostingView(rootView: Content(model: model))
        hosting.onDraw = { if recordFrames { drawnOffsets.append(model.displayedOffset) } }
        window.contentView = hosting
        window.makeKeyAndOrderFront(nil)
        defer { window.close() }
        await settle(window)
        let scroll = try XCTUnwrap(descendants(try XCTUnwrap(window.contentView)).compactMap { $0 as? NSScrollView }.first)
        scroll.contentView.scroll(to: CGPoint(x: 0, y: columns == 1 ? 200 : 0))
        scroll.reflectScrolledClipView(scroll.contentView)
        await settle(window)
        var expected = try XCTUnwrap(model.frames["post-8"]).minY
        XCTAssertEqual(expected, 100, accuracy: 0.5)
        XCTAssertEqual(try XCTUnwrap(model.displayedOffset), expected, accuracy: 0.5)
        try XCTUnwrap(model.activate)()
        if userScroll {
            NotificationCenter.default.post(name: NSScrollView.willStartLiveScrollNotification, object: scroll)
            scroll.contentView.scroll(to: CGPoint(x: 0, y: 240))
            scroll.reflectScrolledClipView(scroll.contentView)
            await settle(window)
            NotificationCenter.default.post(name: NSScrollView.didEndLiveScrollNotification, object: scroll)
            expected = 60
        }
        recordFrames = true
        model.rows.insert(contentsOf: (100..<100 + insertedCount).map(post), at: 2)
        if closesGap { model.rows.remove(at: 2 + insertedCount) }
        await settle(window)
        recordFrames = false
        XCTAssertEqual(try XCTUnwrap(model.frames["post-8"]).minY, expected, accuracy: 0.5)
        XCTAssertFalse(drawnOffsets.isEmpty)
        XCTAssertTrue(drawnOffsets.allSatisfy { $0.map { abs($0 - expected) < 0.5 } ?? false }, "Drawn offsets: \(drawnOffsets)")
        if let scrollAfterGap {
            let before = scroll.contentView.bounds.minY
            try scrollAfterGap(scroll)
            await settle(window)
            XCTAssertGreaterThan(scroll.contentView.bounds.minY, before + 1, "User input must move away from the gap anchor")
            return
        }
        if closesGap {
            model.rowHeight = 180
            await settle(window)
            XCTAssertEqual(try XCTUnwrap(model.frames["post-8"]).minY, expected, accuracy: 0.5)
        }
        if replacesWindow {
            let all = model.rows
            model.isEmpty = clearsList
            model.rows = emptyWindow ? [] : Array(all.prefix(4))
            await settle(window)
            XCTAssertNil(model.frames["post-8"])
            model.isEmpty = false
            model.rows = all
            await settle(window)
            if clearsList {
                XCTAssertNotEqual(model.frames["post-8"]?.minY, expected)
                return
            }
            XCTAssertEqual(try XCTUnwrap(model.frames["post-8"]).minY, expected, accuracy: 0.5)
            model.rowHeight = 180
            await settle(window)
            XCTAssertEqual(try XCTUnwrap(model.frames["post-8"]).minY, expected, accuracy: 0.5)
        }
    }

    private func settle(_ window: NSWindow) async {
        for _ in 0..<30 {
            window.contentView?.layoutSubtreeIfNeeded()
            window.contentView?.displayIfNeeded()
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func descendants(_ view: NSView) -> [NSView] {
        [view] + view.subviews.flatMap(descendants)
    }

    @MainActor
    private final class RecordingHostingView<Content: View>: NSHostingView<Content> {
        var onDraw: (() -> Void)?
        override func viewWillDraw() { super.viewWillDraw(); onDraw?() }
    }

    @MainActor
    private final class Model: ObservableObject {
        let gap = UiTimelineV2.Gap(statusKey: MicroBlogKey(id: "gap", host: "local"),
            createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(),
            nextKey: "cursor", lowerBound: [:], revision: 0, itemKey: "gap",
            loading: false, failed: false, onActivate: nil)
        @Published var rows: [UiTimelineV2] = []
        @Published var rowHeight: CGFloat = 100
        @Published var isEmpty = false
        let columns: Int
        weak var anchorView: NSView?
        var displayedOffset: CGFloat? {
            guard let anchorView, let clip = anchorView.enclosingScrollView?.contentView, anchorView.window != nil else { return nil }
            return anchorView.convert(anchorView.bounds, to: clip).minY - clip.bounds.minY
        }
        var frames: [String: CGRect] = [:]
        var activate: (() -> Void)?
        let reading = Reading()

        init(columns: Int) {
            self.columns = columns
            rows = [post(0), post(1), gap] + (8..<30).map { post($0) }
        }
    }

    private struct Content: View {
        @ObservedObject var model: Model

        var body: some View {
            TimelineReadingScrollView(itemKeys: model.rows.map(\.stableItemKey),
                                      gaps: model.rows.compactMap { $0 as? UiTimelineV2.Gap }, isEmpty: model.isEmpty) {
                HStack(alignment: .top, spacing: 0) {
                    ForEach(0..<model.columns, id: \.self) { column in
                        LazyVStack(spacing: 0) {
                            ForEach(stride(from: column, to: model.rows.count, by: model.columns).map { model.rows[$0] }, id: \.stableItemKey) { item in
                                Color.clear.frame(height: item.isPaginationGap ? 100 : model.rowHeight).timelineReadingItem(item)
                                    .background {
                                        if item.stableItemKey == "post-8" { AnchorProbe { model.anchorView = $0 } }
                                    }
                            }
                        }
                    }
                }
                .background(Activate(model: model))
            }
            .environment(\.timelineReading, model.reading)
            .onPreferenceChange(TimelineReadingFrames.self) { model.frames = $0 }
        }
    }

    private struct Activate: View {
        let model: Model
        @Environment(\.timelineGapWillLoad) private var willLoad

        var body: some View {
            Color.clear.onAppear { model.activate = { willLoad?(model.gap) } }
        }
    }

    private struct AnchorProbe: NSViewRepresentable {
        let onResolve: (NSView) -> Void
        func makeNSView(context: Context) -> NSView {
            let view = NSView()
            onResolve(view)
            return view
        }
        func updateNSView(_ nsView: NSView, context: Context) { onResolve(nsView) }
    }

    @MainActor
    private final class Reading: NSObject, TimelineReadingState {
        var restorePosition: TimelineReadingPosition?
        let hasNewContent = false
        let isAtLatest = false
        func viewportChanged(index: Int32, offset: Double) {}
        func positionChanged(index: Int32, offset: Double) {}
        func restorationFinished() { restorePosition = nil }
        func restoreCurrentPosition() {}
        func savePosition() {}
        func showLatest() {}
    }
}

@MainActor
private func post(_ index: Int) -> UiTimelineV2 {
    UiTimelineV2.Feed(title: "post-\(index)", description: nil, descriptionHtml: nil,
        url: "https://example.invalid/\(index)", sourceLanguages: [], translationDisplayState: .hidden,
        createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(),
        source: UiTimelineV2.FeedSource(name: "test", icon: nil, proxy: ProxyRef.Unbound.shared),
        media: nil, clickEvent: ClickEventNoop.shared, accountType: AccountType.Guest.shared,
        itemKey: "post-\(index)", pluginId: nil)
}
