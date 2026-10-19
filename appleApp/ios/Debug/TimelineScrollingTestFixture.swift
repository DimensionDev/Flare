#if DEBUG
import SwiftUI
import KotlinSharedUI
import FlareAppleUI

/// Fixed offline posts exercise measurements from the production cells while a
/// native pan is in flight. Programmatic offsets do not reproduce this lifecycle.
struct TimelineScrollingTestFixture: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { TimelineScrollingTestController() }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

private final class TimelineScrollingTestController: UIViewController {
    private let timeline = UITimelineCollectionViewController(detailStatusKey: nil)
    private var content = TimelineContent()
    private let columns = ProcessInfo.processInfo.arguments.contains("--two-columns") ? 2 : 1
    private let prependCount = ProcessInfo.processInfo.arguments
        .first { $0.hasPrefix("--prepend-count=") }.flatMap { Int($0.dropFirst("--prepend-count=".count)) } ?? 2
    private var started = false
    private var collection: TimelineCollectionView {
        timeline.view.subviews.compactMap { $0 as? TimelineCollectionView }.first!
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(timeline)
        view.addSubview(timeline.view)
        timeline.didMove(toParent: self)
        content.state = .loaded
        content.items = (0..<60).map { .post(makeScrollingTestRow($0)) }
        timeline.submit(content, columns: columns)
        collection.accessibilityIdentifier = "scrolling-test-list"
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        timeline.view.frame = view.bounds
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        Task { await checkPrependDuringPan() }
    }

    private func show(_ text: String, id: String) {
        let label = UILabel(frame: CGRect(x: 12, y: view.safeAreaInsets.top, width: view.bounds.width - 24, height: 80))
        label.accessibilityIdentifier = id
        label.numberOfLines = 0
        label.text = text
        label.backgroundColor = .systemBackground
        view.addSubview(label)
    }

    private func settle(_ iterations: Int) async {
        for _ in 0..<iterations {
            view.layoutIfNeeded()
            collection.layoutIfNeeded()
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func checkPrependDuringPan() async {
        await settle(40)
        timeline.restoreEffectiveContentOffset(2_500, animated: false)
        await settle(30)
        show("Ready", id: "scrolling-test-ready")
        for _ in 0..<1_500 {
            if collection.isDragging { break }
            try? await Task.sleep(for: .milliseconds(5))
        }
        guard collection.isDragging, let anchor = collection.captureViewportPosition() else {
            show("FAIL: native pan did not start", id: "scrolling-test-result")
            return
        }
        let translation = collection.panGestureRecognizer.translation(in: collection).y
        var largestResidual: CGFloat = 0
        var samples = 0
        var insertedDuringPan = false
        content.items.insert(contentsOf: (100..<(100 + prependCount)).map { .post(makeScrollingTestRow($0)) }, at: 0)
        timeline.submit(content, columns: columns)
        for _ in 0..<50 {
            view.layoutIfNeeded()
            collection.layoutIfNeeded()
            if collection.isDragging,
               let path = collection.readingIndexPath?(anchor.itemID),
               let frame = collection.layoutAttributesForItem(at: path)?.frame {
                insertedDuringPan = insertedDuringPan || collection.readingIndexPath?("t:case-100") != nil
                let screenY = frame.minY - collection.contentOffset.y - collection.restingAdjustedTopInset
                let fingerMovement = collection.panGestureRecognizer.translation(in: collection).y - translation
                largestResidual = max(largestResidual, abs(screenY - anchor.offset - fingerMovement))
                samples += 1
            }
            try? await Task.sleep(for: .milliseconds(10))
        }
        let passed = largestResidual <= 1 && samples >= 5 && insertedDuringPan
        show("\(passed ? "PASS" : "FAIL"): residual=\(largestResidual) samples=\(samples) insertedDuringPan=\(insertedDuringPan)",
             id: "scrolling-test-result")
    }
}

@MainActor
private func makeScrollingTestRow(_ index: Int) -> UiTimelineV2 {
    let text = "Post \(index): " + String(repeating: "Rotation should preserve this reading item while text wraps and media resizes. ", count: index % 4 + 1)
    let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false)
    let rich = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
    let action = ActionMenu.Item(updateKey: "like", icon: .like, text: nil, count: UiNumber(value: 1), color: .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
    let images: [UiMedia] = (0..<(index % 3 == 0 ? 1 : 4)).map { i in UiMediaImage(url: "https://example.invalid/fixture-\(i).png", previewUrl: "https://example.invalid/fixture-\(i).png", description: nil, height: 600, width: 800, sensitive: false, customHeaders: nil) }
    let profile = UiProfile(key: MicroBlogKey(id: "user", host: "example.invalid"), handle: UiHandle(raw: "reader", host: "example.invalid"), avatar: nil as UiMediaImage?, nameInternal: rich, platformId: "mastodon", platformIcon: .world, clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil)
    let post = UiTimelineV2.Post(platformId: "mastodon", images: images, sensitive: false, contentWarning: nil, user: profile, platformIcon: .world, sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: rich, translation: nil), actions: [action], poll: nil, statusKey: MicroBlogKey(id: "case-\(index)", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "case-\(index)")
    return UiTimelineV2.TimelinePostItem(post: post, presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil), itemKey: "case-\(index)")
}

#endif
