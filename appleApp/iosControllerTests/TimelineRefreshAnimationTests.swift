import XCTest
import UIKit
import SwiftUI
import Kingfisher
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
@testable import Flare

@MainActor
final class TimelineRefreshAnimationTests: XCTestCase {
    func testPrependingDuringPullRefreshStaysSmoothWhileTheNavigationBarMinimizes() async throws {
        guard #available(iOS 27.0, *) else { throw XCTSkip("Navigation bar minimization requires iOS 27") }
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let controller = UITimelineCollectionViewController(detailStatusKey: nil)
        defer {
            controller.refreshCallback = nil
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }

        var input = TimelineContent()
        input.state = .loaded
        input.items = (0..<40).map { .post(makeTimelineTestRow($0, mediaCount: 0, textRepeatCount: 2)) }
        controller.submit(input, columns: 1)
        window.rootViewController = UIHostingController(rootView: RefreshHome(controller: controller))
        window.makeKeyAndVisible()
        try await Task.sleep(for: .milliseconds(800))
        let list = try XCTUnwrap(controller.view.subviews.compactMap { $0 as? TimelineCollectionView }.first)
        let expandedTopInset = list.safeAreaInsets.top
        controller.refreshCallback = {
            input.isRefreshing = true
            controller.submit(input, columns: 1)
            try? await Task.sleep(for: .milliseconds(500))
            input.items.insert(contentsOf: (100..<105).map {
                .post(makeTimelineTestRow($0, mediaCount: 0, textRepeatCount: 2))
            }, at: 0)
            input.isRefreshing = false
            controller.submit(input, columns: 1)
        }

        let frames = ReadingItemFrames(list: list, itemID: "t:case-0")
        frames.start()
        defer { frames.stop() }
        // UIKit reveals the control before sending valueChanged for a released pull.
        list.setContentOffset(CGPoint(x: 0, y: -list.adjustedContentInset.top - 100), animated: false)
        let refreshControl = try XCTUnwrap(list.refreshControl)
        refreshControl.beginRefreshing()
        refreshControl.sendActions(for: .valueChanged)
        list.setContentOffset(CGPoint(x: 0, y: -list.adjustedContentInset.top), animated: false)
        try await Task.sleep(for: .milliseconds(1800))
        frames.stop()

        XCTAssertGreaterThan(frames.samples.count, 10)
        XCTAssertEqual(frames.missingItemFrames, 0, "The reading item must remain visible throughout refresh")
        // Check both motions per 60 Hz frame, allowing one missed frame without masking a jump.
        let steps = zip(frames.samples, frames.samples.dropFirst()).map { previous, current in
            let frameAllowance = min(2, max(1, (current.timestamp - previous.timestamp) * 60))
            return (
                content: abs((current.y - current.topInset) - (previous.y - previous.topInset)) / frameAllowance,
                navigation: abs(current.topInset - previous.topInset) / frameAllowance
            )
        }
        XCTAssertLessThan(steps.map(\.content).max() ?? 0, 12, "The reading item must not jump relative to the navigation bar")
        XCTAssertLessThan(steps.map(\.navigation).max() ?? 0, 12, "Navigation bar minimization must stay smooth")
        XCTAssertGreaterThan(expandedTopInset - list.safeAreaInsets.top, 20, "Keep navigation bar minimization enabled")
        XCTAssertFalse(list.isPresentingRefresh)
        XCTAssertFalse(list.hasReadingPosition)
        let path = try XCTUnwrap(list.readingIndexPath?("t:case-0"))
        let cell = try XCTUnwrap(list.cellForItem(at: path))
        XCTAssertEqual(cell.convert(cell.bounds, to: window).minY, list.adjustedContentInset.top, accuracy: 1)
    }

    func testPrependingDuringPullRefreshKeepsLoadedAvatarAndMediaVisible() async throws {
        guard #available(iOS 27.0, *) else { throw XCTSkip("Navigation bar minimization requires iOS 27") }
        let cache = ImageCache(name: "timeline-refresh-\(UUID())")
        // Disk retrieval must not hide cell recycling behind an immediate memory hit.
        cache.memoryStorage.config.totalCostLimit = 1
        let oldOptions = KingfisherManager.shared.defaultOptions
        KingfisherManager.shared.defaultOptions = [.targetCache(cache)]
        defer {
            KingfisherManager.shared.defaultOptions = oldOptions
            cache.clearMemoryCache()
            cache.clearDiskCache()
        }
        let image = UIGraphicsImageRenderer(size: CGSize(width: 320, height: 480)).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 320, height: 480))
        }
        RefreshImageProtocol.payload = try XCTUnwrap(image.pngData())
        let prepared = try await ProxyMediaImages.shared.prepare(refreshMedia(0))
        let oldConfiguration = prepared.downloader.sessionConfiguration
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RefreshImageProtocol.self]
        prepared.downloader.sessionConfiguration = configuration
        defer { prepared.downloader.sessionConfiguration = oldConfiguration }

        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let controller = UITimelineCollectionViewController(detailStatusKey: nil)
        defer {
            controller.refreshCallback = nil
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        window.rootViewController = UIHostingController(rootView: RefreshHome(controller: controller))
        window.makeKeyAndVisible()
        var input = TimelineContent()
        input.state = .loaded
        input.items = (0..<20).map { .post(makeMediaRow($0)) }
        controller.submit(input, columns: 1)
        try await Task.sleep(for: .milliseconds(800))
        let list = try XCTUnwrap(controller.view.subviews.compactMap { $0 as? TimelineCollectionView }.first)
        let frames = ReadingItemFrames(list: list, itemID: "t:case-0")
        for _ in 0..<300 {
            frames.sample()
            if frames.samples.last?.loadedImages == 2 { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        let initial = try XCTUnwrap(frames.samples.last)
        XCTAssertEqual(initial.loadedImages, 2, "Load the avatar and media before testing refresh")
        guard initial.loadedImages == 2 else { return }

        controller.refreshCallback = {
            input.isRefreshing = true
            controller.submit(input, columns: 1)
            try? await Task.sleep(for: .milliseconds(500))
            input.items.insert(contentsOf: (100..<105).map { .post(self.makeMediaRow($0)) }, at: 0)
            input.isRefreshing = false
            controller.submit(input, columns: 1)
        }
        frames.start()
        defer { frames.stop() }
        list.setContentOffset(CGPoint(x: 0, y: -list.adjustedContentInset.top - 100), animated: false)
        let refreshControl = try XCTUnwrap(list.refreshControl)
        refreshControl.beginRefreshing()
        refreshControl.sendActions(for: .valueChanged)
        list.setContentOffset(CGPoint(x: 0, y: -list.adjustedContentInset.top), animated: false)
        try await Task.sleep(for: .milliseconds(2300))
        frames.stop()

        XCTAssertGreaterThan(frames.samples.count, 10)
        XCTAssertEqual(frames.missingItemFrames, 0, "The retained post must remain present in every frame")
        XCTAssertEqual(frames.samples.map(\.loadedImages).min(), 2, "Loaded images must not revert to placeholders")
        XCTAssertNotNil(list.readingIndexPath?("t:case-100"), "The refresh must apply its prepended posts")
        XCTAssertFalse(list.isPresentingRefresh)
    }

    private func refreshMedia(_ index: Int) -> UiMediaImage {
        let url = "https://timeline-refresh.invalid/\(index).png"
        return UiMediaImage(url: url, previewUrl: url, description: nil, height: 480, width: 320,
                            sensitive: false, customHeaders: nil, proxy: ProxyRef.Global.shared)
    }

    private func makeMediaRow(_ index: Int) -> UiTimelineV2 {
        let media = refreshMedia(index)
        let original = (makeTimelineTestRow(index, mediaCount: 0, textRepeatCount: 0) as! UiTimelineV2.TimelinePostItem).post
        let user = original.user!
        let profile = UiProfile(key: user.key, handle: user.handle, avatar: media, nameInternal: user.name,
            platformId: user.platformId, platformIcon: user.platformIcon, clickEvent: ClickEventNoop.shared,
            banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden,
            matrices: user.matrices, mark: [], bottomContent: nil, pluginId: nil)
        let post = UiTimelineV2.Post(platformId: original.platformId, images: [media], sensitive: false,
            contentWarning: nil, user: profile, platformIcon: original.platformIcon, sourceLanguages: [],
            translationDisplayState: .hidden, content: original.content, actions: original.actions,
            poll: nil, statusKey: original.statusKey, card: nil, createdAt: original.createdAt,
            emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [],
            clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared,
            itemKey: original.itemKey, pluginId: nil, authorSubtitleInternal: nil)
        return UiTimelineV2.TimelinePostItem(post: post,
            presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil),
            itemKey: post.itemKey)
    }

    @available(iOS 27.0, *)
    private struct RefreshHome: View {
        let controller: UITimelineCollectionViewController

        var body: some View {
            TabView {
                Tab("Home", systemImage: "house") {
                    NavigationStack {
                        Timeline(controller: controller)
                            .ignoresSafeArea(edges: .vertical)
                            .navigationTitle("Timeline")
                            .navigationBarTitleDisplayMode(.inline)
                            .modifier(ScrollMinimizingNavigationBar())
                    }
                }
                Tab("Other", systemImage: "person") { Text("Other") }
            }
            .tabBarMinimizeBehavior(.onScrollDown)
        }
    }

    private struct Timeline: UIViewControllerRepresentable {
        let controller: UITimelineCollectionViewController
        func makeUIViewController(context: Context) -> UITimelineCollectionViewController { controller }
        func updateUIViewController(_ controller: UITimelineCollectionViewController, context: Context) {}
    }

    @MainActor
    private final class ReadingItemFrames: NSObject {
        struct Sample {
            let timestamp: TimeInterval
            let y: CGFloat
            let topInset: CGFloat
            let loadedImages: Int
        }

        let list: TimelineCollectionView
        let itemID: String
        private var displayLink: CADisplayLink?
        private(set) var samples: [Sample] = []
        private(set) var missingItemFrames = 0

        init(list: TimelineCollectionView, itemID: String) {
            self.list = list
            self.itemID = itemID
        }

        func start() {
            samples = []
            missingItemFrames = 0
            displayLink = CADisplayLink(target: self, selector: #selector(sample))
            displayLink?.add(to: .main, forMode: .common)
        }

        func stop() {
            displayLink?.invalidate()
            displayLink = nil
        }

        @objc func sample() {
            guard let path = list.readingIndexPath?(itemID),
                  let cell = list.cellForItem(at: path), let window = list.window else {
                missingItemFrames += 1
                return
            }
            samples.append(Sample(timestamp: displayLink?.timestamp ?? CACurrentMediaTime(),
                y: cell.convert(cell.bounds, to: window).minY, topInset: list.safeAreaInsets.top,
                loadedImages: loadedImages(in: cell)))
        }

        private func loadedImages(in view: UIView) -> Int {
            guard !view.isHidden, !view.bounds.isEmpty else { return 0 }
            if let image = view as? NetworkImageView { return image.image == nil ? 0 : 1 }
            return view.subviews.reduce(0) { $0 + loadedImages(in: $1) }
        }
    }
}

private final class RefreshImageProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var payload = Data()
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "timeline-refresh.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil,
                                       headerFields: ["Content-Type": "image/png"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.payload)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
