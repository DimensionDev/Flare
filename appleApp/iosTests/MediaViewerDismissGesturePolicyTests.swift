import XCTest
import UIKit

final class MediaViewerDismissGesturePolicyTests: XCTestCase {
    func testFastVerticalSwipesDismissInBothDirections() {
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.shouldDismiss(
                translationY: 40,
                velocityY: 1_301,
                containerHeight: 800
            )
        )
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.shouldDismiss(
                translationY: -40,
                velocityY: -1_301,
                containerHeight: 800
            )
        )
    }

    func testLongVerticalDragsDismissInBothDirections() {
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.shouldDismiss(
                translationY: 401,
                velocityY: 0,
                containerHeight: 800
            )
        )
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.shouldDismiss(
                translationY: -401,
                velocityY: 0,
                containerHeight: 800
            )
        )
    }

    func testShortSlowDragDoesNotDismiss() {
        XCTAssertFalse(
            MediaViewerDismissGesturePolicy.shouldDismiss(
                translationY: 100,
                velocityY: 500,
                containerHeight: 800
            )
        )
        XCTAssertFalse(
            MediaViewerDismissGesturePolicy.shouldDismiss(
                translationY: -100,
                velocityY: -500,
                containerHeight: 800
            )
        )
    }

    func testProgressIsDirectionIndependent() {
        XCTAssertEqual(
            MediaViewerDismissGesturePolicy.progress(translationY: 200, containerHeight: 800),
            0.25
        )
        XCTAssertEqual(
            MediaViewerDismissGesturePolicy.progress(translationY: -200, containerHeight: 800),
            0.25
        )
    }

    func testOnlyMediaMovesWithDismissGesture() {
        XCTAssertEqual(
            MediaViewerDismissGesturePolicy.verticalOffset(
                for: .media,
                translationY: 160
            ),
            160
        )
        XCTAssertEqual(
            MediaViewerDismissGesturePolicy.verticalOffset(
                for: .bottomOverlay,
                translationY: 160
            ),
            0
        )
    }

    func testCancelledGestureRestoresTouchedScrollPosition() {
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.shouldRestoreTouchedScrollPosition(
                didDismiss: false
            )
        )
        XCTAssertFalse(
            MediaViewerDismissGesturePolicy.shouldRestoreTouchedScrollPosition(
                didDismiss: true
            )
        )
    }

    func testDismissGestureBeginsOnlyForUnzoomedVerticalPans() {
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.shouldBegin(
                velocityX: 20,
                velocityY: -200,
                zoomScale: 1,
                minimumZoomScale: 1
            )
        )
        XCTAssertFalse(
            MediaViewerDismissGesturePolicy.shouldBegin(
                velocityX: 200,
                velocityY: 20,
                zoomScale: 1,
                minimumZoomScale: 1
            )
        )
        XCTAssertFalse(
            MediaViewerDismissGesturePolicy.shouldBegin(
                velocityX: 20,
                velocityY: 200,
                zoomScale: 2,
                minimumZoomScale: 1
            )
        )
    }

    func testDismissGestureTakesPriorityOnlyOverScrollViewPans() async {
        await MainActor.run {
            let scrollView = UIScrollView()
            XCTAssertTrue(
                MediaViewerDismissGesturePolicy.shouldTakePriority(
                    over: scrollView.panGestureRecognizer
                )
            )

            let ordinaryView = UIView()
            let ordinaryPan = UIPanGestureRecognizer()
            ordinaryView.addGestureRecognizer(ordinaryPan)
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldTakePriority(
                    over: ordinaryPan
                )
            )
        }
    }

    func testDismissGestureDoesNotTakePriorityOverScrollableVerticalContent() async {
        await MainActor.run {
            let scrollView = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
            scrollView.contentSize = CGSize(width: 390, height: 1_200)
            scrollView.contentOffset = CGPoint(x: 0, y: 300)

            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldTakePriority(
                    over: scrollView.panGestureRecognizer,
                    velocityY: -500
                )
            )
        }
    }

    func testTallImageScrollAllowsDismissOnlyPastTheMatchingEdge() async {
        await MainActor.run {
            let scrollView = Self.makeTallImageScrollView()

            scrollView.contentOffset.y = 300
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: 500
                )
            )
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: -500
                )
            )

            scrollView.contentOffset.y = 0
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: 500
                )
            )

            scrollView.contentOffset.y = -20
            XCTAssertTrue(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: 500
                )
            )
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: -500
                )
            )

            scrollView.contentOffset.y = 600
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: -500
                )
            )

            scrollView.contentOffset.y = 630
            XCTAssertFalse(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: 500
                )
            )
            XCTAssertTrue(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: -500
                )
            )
        }
    }

    func testNonScrollableContentAllowsDismissInBothDirections() async {
        await MainActor.run {
            let scrollView = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
            scrollView.contentSize = CGSize(width: 390, height: 600)

            XCTAssertTrue(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: 500
                )
            )
            XCTAssertTrue(
                MediaViewerDismissGesturePolicy.shouldBeginDismissPan(
                    in: scrollView,
                    velocityY: -500
                )
            )
        }
    }

    @MainActor
    private static func makeTallImageScrollView() -> UIScrollView {
        let scrollView = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
        scrollView.contentInsetAdjustmentBehavior = .never
        scrollView.contentInset = UIEdgeInsets(top: 20, left: 0, bottom: 30, right: 0)
        scrollView.contentSize = CGSize(width: 390, height: 1_200)
        return scrollView
    }
}
