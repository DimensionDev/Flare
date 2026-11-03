import AppKit
import SwiftUI
import KotlinSharedUI
import FlareAppleUI

/// Restores UI coordinates once, then keeps the native item fixed across list updates.
struct TimelineReadingScrollView<Content: View>: View {
    @Environment(\.timelineReading) private var reading
    @State private var viewport = TimelineReadingViewport()
    let itemKeys: [String]
    let gaps: [UiTimelineV2.Gap]
    let isEmpty: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                content().background(TimelineScrollViewProbe { viewport.bind($0) })
            }
            .coordinateSpace(name: "timeline-reading")
            .environment(\.timelineGapWillLoad) { message in viewport.willLoadGap(message, itemKeys: itemKeys) }
            .environment(\.timelineGapDidLoad) { message, keys in viewport.didLoadGap(message, insertedKeys: keys) }
            .onPreferenceChange(TimelineReadingFrames.self) { frames in
                viewport.update(frames: frames, itemKeys: itemKeys, gapKeys: Set(gaps.map(\.stableItemKey)), reading: reading)
                viewport.materializePendingItem(using: proxy)
            }
            .onChange(of: itemKeys) { _, _ in
                viewport.updateItemKeys(itemKeys)
                restore(using: proxy)
                viewport.materializePendingItem(using: proxy)
            }
            .onChange(of: isEmpty) { _, empty in if empty { viewport.reset() } }
            .onChange(of: reading?.restorePosition, initial: true) { _, _ in restore(using: proxy) }
            .onChange(of: gaps.map(\.renderHash)) { _, _ in
                viewport.preserveCurrentItem()
            }
            .onAppear { reading?.restoreCurrentPosition() }
            .onDisappear { viewport.save() }
        }
    }

    private func restore(using proxy: ScrollViewProxy) {
        guard let target = reading?.restorePosition, itemKeys.indices.contains(Int(target.index)) else { return }
        let key = itemKeys[Int(target.index)]
        viewport.beginRestore(key: key, offset: target.offset, reading: reading)
        proxy.scrollTo(key, anchor: .top)
    }
}

@MainActor
private final class TimelineReadingViewport {
    private struct Anchor { let itemKey: String; let offset: Double }
    private var itemKeys: [String] = []
    private weak var scrollView: NSScrollView?
    private var reading: TimelineReadingState?
    private var frames: [String: CGRect] = [:]
    private var anchor: Anchor?
    private var pending: Anchor?
    private var materializingItem: String?
    private var restoringBookmark = false
    private var userScrolling = false
    private var hasUserScrolled = false
    private var gapBookmark: (anchor: Anchor, itemOrder: [String])?
    private var leadingGap: (key: String, revision: Int64, insertedKeys: [String]?, offset: CGFloat)?
    private var gapKeys: Set<String> = []
    private var observers: [NSObjectProtocol] = []

    func bind(_ view: NSScrollView) {
        guard scrollView !== view else { return }
        observers.forEach(NotificationCenter.default.removeObserver)
        scrollView = view
        view.documentView?.postsFrameChangedNotifications = true
        let center = NotificationCenter.default
        observers = [
            center.addObserver(forName: NSView.frameDidChangeNotification, object: view.documentView, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    guard let self, self.gapBookmark != nil || self.pending != nil else { return }
                    // SwiftUI can publish item frames before updating the document's scrollable height.
                    self.update(frames: self.frames, itemKeys: self.itemKeys, gapKeys: self.gapKeys, reading: self.reading)
                }
            },
            center.addObserver(forName: NSScrollView.willStartLiveScrollNotification, object: view, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.userScrolling = true
                    self?.cancelAnchoringForUserScroll()
                }
            },
            center.addObserver(forName: NSScrollView.didLiveScrollNotification, object: view, queue: .main) { [weak self] _ in
                // Legacy mice can scroll without willStart/didEnd notifications.
                MainActor.assumeIsolated {
                    guard let self, !self.userScrolling else { return }
                    self.cancelAnchoringForUserScroll()
                }
            },
            center.addObserver(forName: NSScrollView.didEndLiveScrollNotification, object: view, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.userScrolling = false; self?.updatePosition() }
            },
            center.addObserver(forName: NSApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.save() }
            },
        ]
    }

    func preserveCurrentItem() {
        if !userScrolling, pending == nil { pending = gapBookmark?.anchor ?? anchor }
    }

    private func cancelAnchoringForUserScroll() {
        hasUserScrolled = true
        pending = nil
        materializingItem = nil
        gapBookmark = nil
        leadingGap = nil
        restoringBookmark = false
        reading?.restorationFinished()
    }

    func updateItemKeys(_ keys: [String]) {
        if itemKeys != keys { preserveCurrentItem() }
        itemKeys = keys
    }

    func materializePendingItem(using proxy: ScrollViewProxy) {
        guard !userScrolling, let pending, materializingItem != pending.itemKey,
              frames[pending.itemKey] == nil, itemKeys.contains(pending.itemKey),
              pending.offset < (scrollView?.contentView.bounds.height ?? .greatestFiniteMagnitude) else { return }
        materializingItem = pending.itemKey
        proxy.scrollTo(pending.itemKey, anchor: .top)
    }

    func reset() {
        gapBookmark = nil
        leadingGap = nil
        pending = nil
        materializingItem = nil
        anchor = nil
    }

    func didLoadGap(_ message: UiTimelineV2.Gap, insertedKeys: [String]) {
        guard leadingGap?.key == message.stableItemKey, leadingGap?.revision == message.revision else { return }
        guard !insertedKeys.isEmpty else { leadingGap = nil; return }
        leadingGap?.insertedKeys = insertedKeys
        update(frames: frames, itemKeys: itemKeys, gapKeys: gapKeys, reading: reading)
    }

    func willLoadGap(_ message: UiTimelineV2.Gap, itemKeys: [String]) {
        guard let index = itemKeys.firstIndex(of: message.stableItemKey) else { return }
        gapBookmark = nil
        leadingGap = nil
        pending = nil
        materializingItem = nil
        let successor = itemKeys.dropFirst(index + 1).first { !gapKeys.contains($0) }
        for key in [successor, anchor?.itemKey].compactMap({ $0 }) {
            guard let frame = frames[key] else { continue }
            let position = Anchor(itemKey: key, offset: frame.minY)
            gapBookmark = (position, itemKeys)
            if frame.maxY > 0, frame.minY < (scrollView?.contentView.bounds.height ?? 0),
               let index = itemKeys.firstIndex(of: key) {
                reading?.positionChanged(index: Int32(index), offset: frame.minY)
            }
            return
        }
        if let frame = frames[message.stableItemKey] {
            leadingGap = (message.stableItemKey, message.revision, nil, frame.minY)
        }
    }

    func beginRestore(key: String, offset: Double, reading: TimelineReadingState?) {
        self.reading = reading
        leadingGap = nil
        gapBookmark = nil
        materializingItem = nil
        pending = Anchor(itemKey: key, offset: offset)
        restoringBookmark = true
    }

    func update(frames: [String: CGRect], itemKeys: [String], gapKeys: Set<String>, reading: TimelineReadingState?) {
        updateItemKeys(itemKeys)
        self.frames = frames
        self.gapKeys = gapKeys
        self.reading = reading
        if let key = materializingItem, frames[key] != nil || !itemKeys.contains(key) { materializingItem = nil }
        if let gap = gapBookmark, !itemKeys.contains(gap.anchor.itemKey),
           let index = gap.itemOrder.firstIndex(of: gap.anchor.itemKey) {
            let loaded = Set(itemKeys)
            if gap.itemOrder[..<index].contains(where: loaded.contains) &&
                gap.itemOrder.dropFirst(index + 1).contains(where: loaded.contains) {
                gapBookmark = nil
                pending = nil
            }
        }
        if !userScrolling, !restoringBookmark, let gap = gapBookmark {
            pending = gap.anchor
        } else if !restoringBookmark, let pending, !itemKeys.contains(pending.itemKey) {
            self.pending = nil
        }
        if let leadingGap, let key = leadingGap.insertedKeys?.first(where: { frames[$0] != nil }) {
            pending = Anchor(itemKey: key, offset: leadingGap.offset)
            self.leadingGap = nil
        }
        if let pending, let frame = frames[pending.itemKey], let scrollView, !userScrolling {
            let clip = scrollView.contentView
            let previousY = clip.bounds.minY
            let offset = restoringBookmark ? max(pending.offset, 1 - frame.height) : pending.offset
            let delta = frame.minY - offset
            if abs(delta) > 0.5 {
                let maxY = max(0, (scrollView.documentView?.bounds.height ?? 0) - clip.bounds.height)
                clip.scroll(to: CGPoint(x: clip.bounds.minX, y: min(max(0, clip.bounds.minY + delta), maxY)))
                scrollView.reflectScrolledClipView(clip)
            }
            // Keep the target through SwiftUI's deferred scrollTo layout passes.
            let moved = abs(clip.bounds.minY - previousY) > 0.5
            if moved { self.frames = frames.mapValues { $0.offsetBy(dx: 0, dy: previousY - clip.bounds.minY) } }
            if restoringBookmark {
                restoringBookmark = false
                self.pending = Anchor(itemKey: pending.itemKey, offset: offset)
                DispatchQueue.main.async { reading?.restorationFinished() }
                return
            }
            if moved { return }
        }
        let height = scrollView?.contentView.bounds.height ?? .greatestFiniteMagnitude
        if let first = frames.filter({ !gapKeys.contains($0.key) && $0.value.maxY > 0 && $0.value.minY < height })
            .min(by: { a, b in
                abs(a.value.minY - b.value.minY) > 0.5 ? a.value.minY < b.value.minY : a.value.minX < b.value.minX
            }) {
            anchor = Anchor(itemKey: first.key, offset: first.value.minY)
            if scrollView?.window?.isKeyWindow == true, let index = itemKeys.firstIndex(of: first.key) {
                reading?.viewportChanged(index: Int32(index), offset: first.value.minY)
            }
        }
    }

    func save() {
        updatePosition()
        reading?.savePosition()
    }

    private func updatePosition() {
        guard hasUserScrolled, pending == nil, reading?.restorePosition == nil,
              scrollView?.window?.isKeyWindow == true, let anchor, let index = itemKeys.firstIndex(of: anchor.itemKey) else { return }
        reading?.positionChanged(index: Int32(index), offset: anchor.offset)
        hasUserScrolled = false
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
