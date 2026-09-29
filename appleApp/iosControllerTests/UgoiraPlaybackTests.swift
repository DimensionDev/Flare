import FlareAppleCore
import KotlinSharedUI
import UIKit
import XCTest

@testable import Flare
@testable import FlareAppleUI

final class UgoiraPlaybackTests: XCTestCase {
    @MainActor
    func testPresenterInitialStateCanBeObservedFromSwift() async throws {
        let media = UiMediaUgoira(
            statusKey: MicroBlogKey(id: "ugoira-test", host: "pixiv.net"),
            accountKey: MicroBlogKey(id: "ugoira-test-reader", host: "pixiv.net"),
            previewUrl: "https://i.pximg.net/ugoira-test.jpg", originalFrameUrl: nil,
            description: nil, height: 100, width: 100, sensitive: false, customHeaders: nil)
        let presenter = KotlinPresenter(presenter: UgoiraPresenter(media: media))
        XCTAssertNil(presenter.state.animation)
        XCTAssertEqual(presenter.state.progress, 0)
        XCTAssertFalse(presenter.state.failed)

        let session = UgoiraPlaybackSession()
        defer { session.detach() }
        XCTAssertFalse(session.isActive)
        session.play(media)
        XCTAssertTrue(session.isActive)
        // An absent account fails preparation without a Pixiv request, exercising native recomposition.
        for _ in 0..<500 {
            if session.failed { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(session.failed, "Playback activation must report the missing account through Presenter state")
        XCTAssertNil(session.image)
        session.detach()
        XCTAssertFalse(session.isActive)
    }

    @MainActor
    func testTimelineBadgeKeepsProgressBesideTheSpinnerAndClearsRetryOnReuse() async throws {
        let media = MediaUIView(frame: CGRect(x: 0, y: 0, width: 300, height: 200))
        func layout() async throws {
            // UIStackView updates hidden arranged subviews on the next main-loop pass.
            try await Task.sleep(for: .milliseconds(10))
            media.setNeedsLayout()
            media.layoutIfNeeded()
        }
        func descendants(_ view: UIView) -> [UIView] {
            view.subviews.flatMap { [$0] + descendants($0) }
        }
        let badge = try XCTUnwrap(descendants(media).compactMap { $0 as? UIButton }.first)
        let spinner = try XCTUnwrap(descendants(badge).compactMap { $0 as? UIActivityIndicatorView }.first)
        let label = try XCTUnwrap(descendants(badge).compactMap { $0 as? UILabel }.first)
        let icon = try XCTUnwrap(descendants(badge).compactMap { $0 as? UIImageView }.first)

        media.setAutoplayOverlay(.idle)
        try await layout()
        XCTAssertEqual(badge.frame, CGRect(x: 16, y: 152, width: 32, height: 32))
        let iconCenter = icon.convert(CGPoint(x: icon.bounds.midX, y: icon.bounds.midY), to: media)
        media.setAutoplayOverlay(.loading, progress: Double(Float(0.42)))
        try await layout()
        XCTAssertEqual(label.text, "42%")
        XCTAssertFalse(label.isHidden)
        XCTAssertTrue(spinner.isAnimating)
        let spinnerBounds = spinner.convert(spinner.bounds, to: media)
        XCTAssertEqual(spinnerBounds.midX, iconCenter.x, accuracy: 0.5)
        XCTAssertEqual(spinnerBounds.midY, iconCenter.y, accuracy: 0.5)
        XCTAssertGreaterThan(label.convert(label.bounds, to: media).minX, spinnerBounds.maxX)
        let image = UIGraphicsImageRenderer(bounds: media.bounds).image { context in
            media.layer.render(in: context.cgContext)
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = "Ugoira loading badge"
        attachment.lifetime = .keepAlways
        add(attachment)

        media.setAutoplayOverlay(.loading, progress: 0)
        XCTAssertTrue(label.isHidden)
        XCTAssertTrue(spinner.isAnimating)
        media.setAutoplayOverlay(.idle, showsBadge: false)
        XCTAssertTrue(badge.isHidden)
        XCTAssertFalse(spinner.isAnimating)

        var retries = 0
        media.setAutoplayOverlay(.error, onRetry: { retries += 1 })
        try await layout()
        XCTAssertTrue(badge.isUserInteractionEnabled)
        XCTAssertEqual(media.accessibilityCustomActions?.count, 1)
        XCTAssertTrue(media.hitTest(CGPoint(x: badge.frame.midX, y: badge.frame.midY), with: nil) === badge)
        XCTAssertFalse(media.hitTest(CGPoint(x: 150, y: 60), with: nil) is UIControl)
        badge.sendActions(for: .touchUpInside)
        XCTAssertEqual(retries, 1)
        media.setAutoplayOverlay(.idle)
        XCTAssertFalse(badge.isUserInteractionEnabled)
        XCTAssertNil(media.accessibilityCustomActions)
        badge.sendActions(for: .touchUpInside)
        XCTAssertEqual(retries, 1)
    }

    func testFrameDelayPreservesTimingAndCatchesUpAcrossLoopBoundaries() {
        // The variable-duration sequence is 40, 80, 100 ms (220 ms per loop).
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.12, duration: 0.22, selectedAt: 0.06, now: 0.07), 0.05, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.13), 0.09, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.34, now: 0.35), 0.09, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.23), 0.001, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.68), 0.001, accuracy: 0.000_001)
    }
}
