import AppKit
import SwiftUI
import KotlinSharedUI
import FlareAppleUI

/// Keeps the measured item fixed while SwiftUI replaces a local paging window.
struct TimelineReadingScrollView<Content: View>: View {
    @Environment(\.timelineReading) private var reading
    @State private var viewport = TimelineReadingViewport()
    let itemKeys: [String]
    let gaps: [UiTimelineV2.Message]
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                content().background(TimelineScrollViewProbe { viewport.bind($0) })
            }
            .coordinateSpace(name: "timeline-reading")
            .environment(\.timelineGapWillLoad) { message in viewport.willLoadGap(message, itemKeys: itemKeys) }
            .onPreferenceChange(TimelineReadingFrames.self) { frames in
                viewport.update(frames: frames, reading: reading)
            }
            .onChange(of: itemKeys) { _, _ in
                viewport.preserveCurrentItem()
                restore(using: proxy)
            }
            .onChange(of: reading?.restorePosition, initial: true) { _, _ in restore(using: proxy) }
            .onChange(of: gaps.map(\.renderHash)) { _, _ in viewport.updateGaps(gaps) }
            .onDisappear { viewport.save() }
        }
    }

    private func restore(using proxy: ScrollViewProxy) {
        guard let target = reading?.restorePosition, itemKeys.contains(target.itemKey) else { return }
        viewport.beginRestore(target, reading: reading)
        proxy.scrollTo(target.itemKey, anchor: .top)
    }
}

@MainActor
private final class TimelineReadingViewport {
    private weak var scrollView: NSScrollView?
    private var reading: TimelineReadingState?
    private var frames: [String: CGRect] = [:]
    private var anchor: TimelineReadingPosition?
    private var pending: TimelineReadingPosition?
    private var restoringBookmark = false
    private var userScrolling = false
    private var hasUserScrolled = false
    private var gapBookmark: (key: String, hash: Int32, anchor: TimelineReadingPosition)?
    private var observers: [NSObjectProtocol] = []

    func bind(_ view: NSScrollView) {
        guard scrollView !== view else { return }
        observers.forEach(NotificationCenter.default.removeObserver)
        scrollView = view
        let center = NotificationCenter.default
        observers = [
            center.addObserver(forName: NSScrollView.willStartLiveScrollNotification, object: view, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.userScrolling = true
                    self?.hasUserScrolled = true
                    self?.pending = nil
                    self?.gapBookmark = nil
                    self?.restoringBookmark = false
                    self?.reading?.restorationFinished()
                }
            },
            center.addObserver(forName: NSScrollView.didEndLiveScrollNotification, object: view, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.userScrolling = false; self?.save() }
            },
            center.addObserver(forName: NSApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.save() }
            },
        ]
    }

    func preserveCurrentItem() {
        if !userScrolling, pending == nil { pending = gapBookmark?.anchor ?? anchor }
    }

    func willLoadGap(_ message: UiTimelineV2.Message, itemKeys: [String]) {
        guard let index = itemKeys.firstIndex(of: message.stableItemKey),
              let key = itemKeys[..<index].last(where: { frames[$0] != nil }),
              let frame = frames[key] else { return }
        let position = TimelineReadingPosition(itemKey: key, offset: frame.minY)
        gapBookmark = (message.stableItemKey, message.renderHash, position)
        reading?.positionChanged(itemKey: key, offset: frame.minY)
    }

    func updateGaps(_ messages: [UiTimelineV2.Message]) {
        guard let gap = gapBookmark else { return }
        let message = messages.first { $0.stableItemKey == gap.key }
        let loading = (message?.type as? UiTimelineV2.MessageTypeLocalized)?.data == .loadingMissingPosts
        if message == nil || (!loading && message?.renderHash != gap.hash) {
            pending = gap.anchor
            gapBookmark = nil
        }
    }

    func beginRestore(_ target: TimelineReadingPosition, reading: TimelineReadingState?) {
        self.reading = reading
        pending = target
        restoringBookmark = true
    }

    func update(frames: [String: CGRect], reading: TimelineReadingState?) {
        self.frames = frames
        self.reading = reading
        if let pending, let frame = frames[pending.itemKey], let scrollView, !userScrolling {
            let clip = scrollView.contentView
            let delta = frame.minY - pending.offset
            if abs(delta) > 0.5 {
                let maxY = max(0, (scrollView.documentView?.bounds.height ?? 0) - clip.bounds.height)
                clip.scroll(to: CGPoint(x: clip.bounds.minX, y: min(max(0, clip.bounds.minY + delta), maxY)))
                scrollView.reflectScrolledClipView(clip)
            }
            self.pending = nil
            anchor = pending
            if restoringBookmark {
                restoringBookmark = false
                DispatchQueue.main.async { reading?.restorationFinished() }
            }
            return
        }
        let height = scrollView?.contentView.bounds.height ?? .greatestFiniteMagnitude
        if let first = frames.filter({ $0.value.maxY > 0 && $0.value.minY < height })
            .min(by: { a, b in
                abs(a.value.minY - b.value.minY) > 0.5 ? a.value.minY < b.value.minY : a.value.minX < b.value.minX
            }) {
            anchor = TimelineReadingPosition(itemKey: first.key, offset: first.value.minY)
            if userScrolling { reading?.viewportChanged(itemKey: first.key, offset: first.value.minY) }
        }
    }

    func save() {
        guard hasUserScrolled, pending == nil, reading?.restorePosition == nil,
              scrollView?.window?.isKeyWindow == true, let anchor else { return }
        reading?.positionChanged(itemKey: anchor.itemKey, offset: anchor.offset)
    }

    isolated deinit { observers.forEach(NotificationCenter.default.removeObserver) }
}

private struct TimelineScrollViewProbe: NSViewRepresentable {
    let onResolve: (NSScrollView) -> Void
    func makeNSView(context: Context) -> Probe { Probe(onResolve: onResolve) }
    func updateNSView(_ nsView: Probe, context: Context) { nsView.resolve() }

    final class Probe: NSView {
        let onResolve: (NSScrollView) -> Void
        init(onResolve: @escaping (NSScrollView) -> Void) { self.onResolve = onResolve; super.init(frame: .zero) }
        required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }
        override func viewDidMoveToWindow() { super.viewDidMoveToWindow(); resolve() }
        func resolve() {
            DispatchQueue.main.async { [weak self] in
                if let self, let scroll = self.enclosingScrollView { self.onResolve(scroll) }
            }
        }
    }
}
