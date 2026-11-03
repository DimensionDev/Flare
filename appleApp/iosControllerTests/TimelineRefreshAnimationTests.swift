import XCTest
import UIKit
import SwiftUI
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

        XCTAssertGreaterThan(frames.positions.count, 10)
        let largestStep = zip(frames.positions, frames.positions.dropFirst()).map { abs($1 - $0) }.max() ?? 0
        XCTAssertLessThan(largestStep, 12, "The reading item must not jump when the refresh animation ends")
        XCTAssertGreaterThan(expandedTopInset - list.safeAreaInsets.top, 20, "Keep navigation bar minimization enabled")
        XCTAssertFalse(list.isPresentingRefresh)
        XCTAssertFalse(list.hasReadingPosition)
        let path = try XCTUnwrap(list.readingIndexPath?("t:case-0"))
        let cell = try XCTUnwrap(list.cellForItem(at: path))
        XCTAssertEqual(cell.convert(cell.bounds, to: window).minY, list.adjustedContentInset.top, accuracy: 1)
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

    private final class ReadingItemFrames: NSObject {
        let list: TimelineCollectionView
        let itemID: String
        private var displayLink: CADisplayLink?
        private(set) var positions: [CGFloat] = []

        init(list: TimelineCollectionView, itemID: String) {
            self.list = list
            self.itemID = itemID
        }

        func start() {
            displayLink = CADisplayLink(target: self, selector: #selector(sample))
            displayLink?.add(to: .main, forMode: .common)
        }

        func stop() {
            displayLink?.invalidate()
            displayLink = nil
        }

        @objc private func sample() {
            guard let path = list.readingIndexPath?(itemID),
                  let cell = list.cellForItem(at: path), let window = list.window else { return }
            positions.append(cell.convert(cell.bounds, to: window).minY)
        }
    }
}
