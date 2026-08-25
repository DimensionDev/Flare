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

    func testCompletedDismissSkipsSystemPresentationAnimation() {
        XCTAssertTrue(
            MediaViewerDismissGesturePolicy.disablesSystemAnimationOnDismiss
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
}
