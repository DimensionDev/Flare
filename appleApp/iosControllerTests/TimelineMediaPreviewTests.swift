import XCTest
import UIKit
import SwiftUI
import ImageIO
import UniformTypeIdentifiers
import Kingfisher
import KotlinSharedUI
import FlareAppleUI
@testable import Flare

@MainActor
final class TimelineMediaPreviewTests: XCTestCase {
    func testMediaCreatesOptionalViewsOnDemandAndPreservesReuseBehavior() throws {
        let fixture = try Fixture()
        let host = UIViewController()
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        let view = StatusMediaUIView()
        host.view.addSubview(view)
        let items: [UiMedia] = (0..<4).map { fixture.media(path: "grid-\($0).png") }

        func configure(_ layout: TimelineMediaLayout, sensitive: Bool = false, count: Int = 4) {
            view.configure(data: Array(items.prefix(count)), sensitive: sensitive, cornerRadius: 16,
                           singleFollowsImageAspect: true, limitMediaGridToNine: true,
                           mediaLayout: layout, carouselLeadingPadding: 16, carouselTrailingPadding: 16)
            view.frame = CGRect(x: 0, y: 0, width: 340, height: view.timelineHeight(for: 340) ?? 0)
            view.layoutIfNeeded()
            XCTAssertGreaterThan(view.bounds.height, 0)
        }

        configure(.grid)
        XCTAssertFalse(view.isCarouselScrolling)
        view.performLightweightPoolCleanup()
        view.performDeferredPoolCleanup()
        XCTAssertFalse(view.subviews.contains { $0 is UICollectionView }, "Grid media must not create a hidden carousel")
        XCTAssertFalse(view.subviews.contains { $0 is UIVisualEffectView }, "Ordinary media must not create a blur overlay")
        XCTAssertFalse(view.subviews.contains { $0 is UIButton }, "Ordinary media must not create sensitive-content controls")

        configure(.grid, sensitive: true)
        let blur = try XCTUnwrap(view.subviews.compactMap { $0 as? UIVisualEffectView }.first)
        let toggle = try XCTUnwrap(view.subviews.compactMap { $0 as? UIButton }.first)
        XCTAssertFalse(blur.isHidden)
        XCTAssertFalse(toggle.isHidden)
        XCTAssertFalse(view.allowsVideoAutoplay)
        XCTAssertEqual(blur.frame, view.bounds)
        XCTAssertEqual(toggle.center.x, view.bounds.midX, accuracy: 0.5)
        XCTAssertEqual(toggle.center.y, view.bounds.midY, accuracy: 0.5)
        XCTAssertNotNil(toggle.accessibilityLabel)
        toggle.sendActions(for: .touchUpInside)
        XCTAssertTrue(blur.isHidden)
        XCTAssertTrue(view.allowsVideoAutoplay)

        configure(.carousel, sensitive: true)
        let carousel = try XCTUnwrap(view.subviews.compactMap { $0 as? UICollectionView }.first)
        XCTAssertFalse(carousel.isHidden)
        XCTAssertFalse(view.isCarouselScrolling)
        XCTAssertEqual(carousel.frame, view.bounds)
        XCTAssertEqual(carousel.numberOfItems(inSection: 0), 4)
        XCTAssertTrue(blur.isHidden, "Changing layout must preserve the user's sensitive-content choice")
        XCTAssertLessThan(try XCTUnwrap(view.subviews.firstIndex(of: carousel)),
                          try XCTUnwrap(view.subviews.firstIndex(of: blur)))
        XCTAssertEqual(view.subviews.last, toggle)
        toggle.sendActions(for: .touchUpInside)
        XCTAssertFalse(blur.isHidden)
        XCTAssertFalse(view.allowsVideoAutoplay)

        configure(.grid, count: 1)
        XCTAssertTrue(carousel.isHidden)
        XCTAssertEqual(carousel.numberOfItems(inSection: 0), 0)
        XCTAssertTrue(blur.isHidden)
        XCTAssertTrue(toggle.isHidden)
        XCTAssertTrue(view.allowsVideoAutoplay)
        configure(.carousel)
        XCTAssertTrue(view.subviews.compactMap { $0 as? UICollectionView }.first === carousel)
        XCTAssertEqual(carousel.numberOfItems(inSection: 0), 4)
        XCTAssertEqual(view.subviews.filter { $0 is UICollectionView }.count, 1)
        XCTAssertEqual(view.subviews.filter { $0 is UIVisualEffectView }.count, 1)
    }

    func testImagesDoNotCreateVideoOverlayViews() throws {
        let fixture = try Fixture()
        let view = fixture.view
        view.frame.size = CGSize(width: 180, height: 135)
        view.set(media: fixture.media(), cornerRadius: 12)
        view.layoutIfNeeded()
        XCTAssertFalse(view.subviews.contains { $0 is UIButton }, "Static images must not create video controls")
        XCTAssertEqual(fixture.imageView.frame, view.bounds)
        XCTAssertEqual(view.accessibilityLabel, "Preview description")
        view.prepareForPoolRemoval()
        XCTAssertFalse(view.subviews.contains { $0 is UIButton }, "Pool cleanup must not create video controls")
        XCTAssertNil(view.accessibilityCustomActions)
    }

    func testVideoOverlayKeepsStateAndRetryWhenReusedForAnImage() throws {
        let fixture = try Fixture()
        let view = fixture.view
        view.frame.size = CGSize(width: 180, height: 135)
        view.set(media: fixture.media(), cornerRadius: 12)
        let player = UIView()
        view.attachAutoplayPlayer(player)
        view.setAutoplayOverlay(.loading, progress: 0.42)
        view.layoutIfNeeded()
        let badge = try XCTUnwrap(view.subviews.compactMap { $0 as? UIButton }.first)
        let stack = try XCTUnwrap(badge.subviews.compactMap { $0 as? UIStackView }.first)
        let spinner = try XCTUnwrap(stack.arrangedSubviews.compactMap { $0 as? UIActivityIndicatorView }.first)
        let label = try XCTUnwrap(stack.arrangedSubviews.compactMap { $0 as? UILabel }.first)
        XCTAssertFalse(badge.isHidden)
        XCTAssertTrue(spinner.isAnimating)
        XCTAssertEqual(label.text, "42%")
        XCTAssertEqual(view.accessibilityValue, "42%")
        XCTAssertEqual(view.subviews.last, badge, "Video controls must remain above a newly attached player")
        XCTAssertEqual(player.frame, view.bounds)
        view.setAutoplayOverlay(.playing(remaining: 61))
        XCTAssertFalse(spinner.isAnimating)
        XCTAssertEqual(label.text, "1:01")
        XCTAssertNil(view.accessibilityValue)
        var retries = 0
        view.setAutoplayOverlay(.error) { retries += 1 }
        badge.sendActions(for: .touchUpInside)
        XCTAssertEqual(retries, 1)
        XCTAssertEqual(view.accessibilityCustomActions?.count, 1)
        view.prepareForPoolRemoval()
        view.set(media: fixture.media(path: "second.png"), cornerRadius: 0)
        XCTAssertTrue(badge.isHidden)
        XCTAssertFalse(badge.isUserInteractionEnabled)
        XCTAssertFalse(spinner.isAnimating)
        XCTAssertNil(view.accessibilityCustomActions)
        XCTAssertNil(view.accessibilityValue)
        XCTAssertNil(player.superview)
        badge.sendActions(for: .touchUpInside)
        XCTAssertEqual(retries, 1, "Image reuse must clear the old retry callback")
        let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
            thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
            height: 768, width: 1_024, customHeaders: nil, downloadUrl: nil)
        view.set(media: video, cornerRadius: 12)
        view.layoutIfNeeded()
        XCTAssertEqual(view.videoURL?.absoluteString, video.url)
        XCTAssertFalse(badge.isHidden)
        XCTAssertEqual(view.subviews.filter { $0 is UIButton }.count, 1)
        XCTAssertEqual(badge.frame.height, 32, accuracy: 0.5)
    }

    func testSwiftUICrossfadeKeepsPreviewWhileLargerImageIsProcessing() async throws {
        let fixture = try Fixture()
        let queue = DispatchQueue(label: "preview-resize-test")
        let gate = DispatchSemaphore(value: 0)
        KingfisherManager.shared.defaultOptions.append(.processingQueue(.dispatch(queue)))
        let url = fixture.media().previewUrl
        func view(width: CGFloat) -> some View {
            NetworkImage(data: url, customHeader: ["X-Preview-Test": "fixture"], usesCrossfade: true)
                .frame(width: width, height: width * 0.75).environment(\.displayScale, 3)
        }
        let host = UIHostingController(rootView: view(width: 180))
        let window = try fixture.show(host)
        defer { gate.signal(); window.isHidden = true; window.rootViewController = nil }
        for _ in 0..<300 {
            if fixture.cache.imageSize(for: url)?.width == 540 { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(fixture.cache.imageSize(for: url)?.width, 540)
        try await Task.sleep(for: .milliseconds(350))
        queue.async { gate.wait() }
        host.rootView = view(width: 240)
        host.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(100))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let snapshot = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1), format: format).image { context in
            context.cgContext.translateBy(x: -host.view.bounds.midX, y: -host.view.bounds.midY)
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        var rgba = [UInt8](repeating: 0, count: 4)
        let context = try XCTUnwrap(CGContext(data: &rgba, width: 1, height: 1, bitsPerComponent: 8,
            bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.draw(try XCTUnwrap(snapshot.cgImage), in: CGRect(x: 0, y: 0, width: 1, height: 1))
        XCTAssertGreaterThan(rgba[2], 200, "The blue preview must remain visible during the size upgrade")
        XCTAssertLessThan(rgba[0], 50)
        gate.signal()
        for _ in 0..<300 {
            if fixture.cache.imageSize(for: url)?.width == 720 { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(fixture.cache.imageSize(for: url)?.width, 720)
    }

    func testUIKitReplacementIgnoresCancelledCompletionAndPublishesLatestCallback() async throws {
        let fixture = try Fixture()
        let view = try XCTUnwrap(fixture.imageView as? NetworkImageView)
        let queue = DispatchQueue(label: "preview-cancellation-test")
        let gate = DispatchSemaphore(value: 0)
        queue.async { gate.wait() }
        defer { gate.signal() }
        KingfisherManager.shared.defaultOptions.append(.processingQueue(.dispatch(queue)))
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        fixture.view.layoutIfNeeded()
        var cancelledCompletions = 0
        var currentCompletions = 0
        view.setImage(with: URL(string: fixture.media().previewUrl), customHeaders: ["X-Preview-Test": "fixture"]) { _ in
            cancelledCompletions += 1
        }
        view.layoutIfNeeded()
        view.setImage(with: URL(string: fixture.media(path: "second.png").previewUrl), customHeaders: ["X-Preview-Test": "fixture"]) { _ in
            currentCompletions += 1
        }
        view.layoutIfNeeded()
        gate.signal()
        _ = try await fixture.loadedImage(width: 540)
        for _ in 0..<100 {
            if currentCompletions > 0 { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(cancelledCompletions, 0)
        XCTAssertEqual(currentCompletions, 1)
    }

    func testUIKitFailedRequestCanRetryAtTheSameSize() async throws {
        let fixture = try Fixture()
        let view = try XCTUnwrap(fixture.imageView as? NetworkImageView)
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        fixture.view.layoutIfNeeded()
        let url = URL(string: fixture.media().previewUrl)
        KingfisherManager.shared.defaultOptions.append(.onlyFromCache)
        var failed = false
        view.setImage(with: url, customHeaders: ["X-Preview-Test": "fixture"]) { result in
            if case .failure = result { failed = true }
        }
        view.layoutIfNeeded()
        for _ in 0..<100 {
            if failed { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(failed)
        KingfisherManager.shared.defaultOptions.removeLast()
        view.setImage(with: url, customHeaders: ["X-Preview-Test": "fixture"])
        view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
    }

    func testSwiftUIPreviewsDownsampleForFitAndFill() async throws {
        for mode in [SwiftUI.ContentMode.fit, .fill] {
            let fixture = try Fixture()
            var completed = false
            let url = "https://timeline-preview.invalid/swiftui.png"
            let host = UIHostingController(rootView: NetworkImage(data: url,
                customHeader: ["X-Preview-Test": "fixture"], contentMode: mode,
                onProgress: { completed = $0 == 1 }).frame(width: 180, height: 180).environment(\.displayScale, 3))
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            for _ in 0..<300 {
                host.view.layoutIfNeeded()
                if completed { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertTrue(completed)
            XCTAssertEqual(fixture.cache.imageSize(for: url), mode == .fill
                ? CGSize(width: 720, height: 540) : CGSize(width: 540, height: 405))
        }
    }

    func testSwiftUIResizingAndURLReplacementKeepTheExpectedPixels() async throws {
        let fixture = try Fixture()
        let first = "https://timeline-preview.invalid/first.png"
        let second = "https://timeline-preview.invalid/second.png"
        func view(_ url: String, width: CGFloat) -> some View {
            NetworkImage(data: url, customHeader: ["X-Preview-Test": "fixture"])
                .frame(width: width, height: width * 0.75).environment(\.displayScale, 3)
        }
        let host = UIHostingController(rootView: view(first, width: 180))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for (url, width) in [(first, CGFloat(180)), (first, 240), (second, 120)] {
            host.rootView = view(url, width: width)
            for _ in 0..<300 {
                host.view.layoutIfNeeded()
                if fixture.cache.imageSize(for: url)?.width == width * 3 { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertEqual(fixture.cache.imageSize(for: url)?.width, width * 3)
        }
    }

    func testSwiftUIOriginalImageAndCrossfadePreserveFullResolutionForZoom() async throws {
        let fixture = try Fixture()
        let url = "https://timeline-preview.invalid/zoom.png"
        var completed = false
        let host = UIHostingController(rootView: NetworkImage(data: url, placeholder: fixture.media().previewUrl,
            customHeader: ["X-Preview-Test": "fixture"], contentMode: .fit, usesCrossfade: true,
            onProgress: { completed = $0 == 1 }).originalImage().frame(width: 180, height: 135))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for _ in 0..<300 {
            if completed { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(completed)
        XCTAssertEqual(fixture.cache.imageSize(for: url), CGSize(width: 1_024, height: 768))
    }

    func testSwiftUIIntrinsicImageSettlesAtItsNaturalAspectRatio() async throws {
        let fixture = try Fixture()
        var size: CGSize = .zero
        let host = UIHostingController(rootView: NetworkImage(data: fixture.media().previewUrl,
            customHeader: ["X-Preview-Test": "fixture"], contentMode: .fit)
            .frame(width: 180).fixedSize(horizontal: false, vertical: true).environment(\.displayScale, 3)
            .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 })
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(size.width, 180, accuracy: 0.5)
        XCTAssertEqual(size.height, 135, accuracy: 0.5)
        XCTAssertEqual(fixture.cache.imageSize(for: fixture.media().previewUrl)?.width, 540)
    }

    func testSwiftUIGIFWithQueryUsesAnimatedImageView() async throws {
        let fixture = try Fixture(animated: true)
        let host = UIHostingController(rootView: NetworkImage(data: "https://timeline-preview.invalid/animation.GIF?version=1",
            customHeader: ["X-Preview-Test": "fixture"]).frame(width: 180, height: 135))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        func find(_ view: UIView) -> AnimatedImageView? {
            (view as? AnimatedImageView) ?? view.subviews.lazy.compactMap(find).first
        }
        for _ in 0..<300 {
            if find(host.view)?.animator?.frame(at: 1) != nil { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        let imageView = try XCTUnwrap(find(host.view))
        XCTAssertTrue(imageView.needsPrescaling)
        XCTAssertTrue(imageView.isAnimating)
        XCTAssertNotNil(imageView.animator?.frame(at: 1))
    }

    func testStaticPreviewDecodesAtDisplayPixelsAndWaitsForLayout() async throws {
        let fixture = try Fixture()
        let view = fixture.view
        view.frame = .zero
        view.set(media: fixture.media(), cornerRadius: 12)
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertNil(fixture.imageView.image, "A zero-size cell must not decode the full image")
        view.frame.size = CGSize(width: 180, height: 135)
        view.layoutIfNeeded()
        let image = try await fixture.loadedImage()
        XCTAssertEqual(image.cgImage?.width, 540)
        XCTAssertEqual(image.cgImage?.height, 405)
        XCTAssertEqual(view.layer.cornerRadius, 12)
        XCTAssertEqual(view.accessibilityLabel, "Preview description")
    }

    func testAspectFillUsesEncodedDimensionsWhenMetadataIsMissingOrWrong() async throws {
        for size in [CGSize(width: 180, height: 180), CGSize(width: 90, height: 180)] {
            let fixture = try Fixture()
            fixture.view.frame.size = size
            fixture.view.set(media: fixture.media(width: 0, height: 0), cornerRadius: 0)
            fixture.view.layoutIfNeeded()
            let image = try await fixture.loadedImage()
            XCTAssertEqual(image.cgImage?.width, 720)
            XCTAssertEqual(image.cgImage?.height, 540, "The cropped image must still fill the view at 3x")
        }
    }

    func testGrowingOrChangingScaleUpgradesPreviewAndShrinkingKeepsIt() async throws {
        let fixture = try Fixture()
        fixture.view.frame.size = CGSize(width: 90, height: 60)
        fixture.view.set(media: fixture.media(), cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 270)
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        fixture.view.layoutIfNeeded()
        let larger = try await fixture.loadedImage(width: 540)
        fixture.view.frame.size = CGSize(width: 90, height: 60)
        fixture.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertTrue(fixture.imageView.image === larger, "Shrinking should not start another decode")
        fixture.view.traitOverrides.displayScale = 4
        fixture.view.updateTraitsIfNeeded()
        XCTAssertEqual(fixture.view.traitCollection.displayScale, 4)
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 720)
    }

    func testPoolRemovalCancelsDeferredPreviewAndReuseLoadsTheNewMedia() async throws {
        let fixture = try Fixture()
        fixture.view.frame = .zero
        fixture.view.set(media: fixture.media(), cornerRadius: 0)
        fixture.view.prepareForPoolRemoval()
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        fixture.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertNil(fixture.imageView.image)
        fixture.view.set(media: fixture.media(path: "replacement.png"), cornerRadius: 8)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
        fixture.view.prepareForPoolRemoval()
        XCTAssertNil(fixture.imageView.image)
        XCTAssertNil(fixture.view.videoURL)
    }

    func testAnimatedPreviewAndExplicitGIFKeepTheirFrames() async throws {
        for explicitGIF in [false, true] {
            let fixture = try Fixture(animated: true)
            fixture.view.frame.size = CGSize(width: 90, height: 60)
            let media: UiMedia = explicitGIF ? UiMediaGif(url: "https://timeline-preview.invalid/animation.gif",
                previewUrl: "https://timeline-preview.invalid/still.png", description: nil,
                height: 768, width: 1_024, customHeaders: ["X-Preview-Test": "fixture"]) : fixture.media()
            fixture.view.set(media: media, cornerRadius: 0)
            fixture.view.layoutIfNeeded()
            let image = try await fixture.loadedImage()
            let animatedView = try XCTUnwrap(fixture.imageView as? AnimatedImageView)
            XCTAssertEqual(image.kf.imageFrameCount, 2)
            for _ in 0..<100 {
                if animatedView.animator?.frame(at: 1) != nil { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            let frame = try XCTUnwrap(animatedView.animator?.frame(at: 1))
            XCTAssertLessThan(try XCTUnwrap(frame.cgImage?.width), 1_024)
            XCTAssertGreaterThan(animatedView.animator?.currentFrameDuration ?? 0, 0)
        }
    }

    func testEncodedOrientationIsAppliedBeforeAspectFillSizing() async throws {
        let fixture = try Fixture(orientation: 6)
        fixture.view.frame.size = CGSize(width: 135, height: 180)
        fixture.view.set(media: fixture.media(), cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        let image = try await fixture.loadedImage()
        XCTAssertEqual(image.cgImage?.width, 405)
        XCTAssertEqual(image.cgImage?.height, 540)
        XCTAssertEqual(image.imageOrientation, .up)
    }

    func testVideoKeepsPlaybackURLWhileThumbnailIsDownsampled() async throws {
        let fixture = try Fixture()
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
            thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
            height: 768, width: 1_024, customHeaders: ["X-Preview-Test": "fixture"], downloadUrl: nil)
        fixture.view.set(media: video, cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
        XCTAssertEqual(fixture.view.videoURL?.absoluteString, video.url)
    }

    func testPreviewResizeUsesOriginalCacheWithoutReplacingFullSizeMedia() async throws {
        let fixture = try Fixture()
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        let media = fixture.media()
        fixture.view.set(media: media, cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
        let original = try await fixture.cache.retrieveImageInDiskCache(forKey: media.previewUrl)
        XCTAssertEqual(original?.cgImage?.width, 1_024)
        fixture.cache.clearMemoryCache()
        KingfisherManager.shared.defaultOptions.append(.onlyFromCache)
        fixture.view.frame.size = CGSize(width: 240, height: 180)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 720)
        XCTAssertEqual(media.url, "https://timeline-preview.invalid/original.png")
    }

    @MainActor
    private final class Fixture {
        let view = MediaUIView()
        let cache = PreviewImageCache(name: "timeline-preview-tests-\(UUID().uuidString)")
        let oldOptions: KingfisherOptionsInfo
        var imageView: UIImageView { view.subviews.compactMap { $0 as? UIImageView }.first! }

        init(animated: Bool = false, orientation: Int? = nil) throws {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let image = UIGraphicsImageRenderer(
                size: CGSize(width: 1_024, height: 768), format: format
            ).image { context in
                UIColor.blue.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 1_024, height: 768))
            }
            let data = NSMutableData()
            let type = animated ? UTType.gif : orientation == nil ? UTType.png : UTType.jpeg
            let destination = try XCTUnwrap(CGImageDestinationCreateWithData(data, type.identifier as CFString,
                animated ? 2 : 1, nil))
            let properties: [CFString: Any] = animated
                ? [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFDelayTime: 0.1]]
                : [kCGImagePropertyOrientation: orientation ?? 1]
            for _ in 0..<(animated ? 2 : 1) {
                CGImageDestinationAddImage(destination, try XCTUnwrap(image.cgImage), properties as CFDictionary)
            }
            XCTAssertTrue(CGImageDestinationFinalize(destination))
            PreviewImageProtocol.payload = data as Data
            let downloader = ImageDownloader(name: "timeline-preview-tests-\(UUID().uuidString)")
            let config = URLSessionConfiguration.ephemeral
            config.protocolClasses = [PreviewImageProtocol.self]
            downloader.sessionConfiguration = config
            oldOptions = KingfisherManager.shared.defaultOptions
            KingfisherManager.shared.defaultOptions = [.downloader(downloader), .targetCache(cache)]
            view.traitOverrides.displayScale = 3
            view.updateTraitsIfNeeded()
        }

        isolated deinit {
            view.prepareForPoolRemoval()
            KingfisherManager.shared.defaultOptions = oldOptions
            cache.clearMemoryCache()
            cache.clearDiskCache()
        }

        func media(path: String = "preview.png", width: Float = 1_024, height: Float = 768) -> UiMediaImage {
            UiMediaImage(url: "https://timeline-preview.invalid/original.png",
                previewUrl: "https://timeline-preview.invalid/\(path)", description: "Preview description",
                height: height, width: width, sensitive: false, customHeaders: ["X-Preview-Test": "fixture"])
        }

        func loadedImage(width: Int? = nil) async throws -> UIImage {
            for _ in 0..<300 {
                if let image = imageView.image, width == nil || image.cgImage?.width == width { return image }
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTFail("Preview did not load at expected width \(String(describing: width)); got \(String(describing: imageView.image?.cgImage?.width))")
            return try XCTUnwrap(imageView.image)
        }

        func show(_ controller: UIViewController) throws -> UIWindow {
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            let window = UIWindow(windowScene: scene)
            window.rootViewController = controller
            window.isHidden = false
            controller.view.layoutIfNeeded()
            return window
        }
    }
}

private nonisolated final class PreviewImageCache: ImageCache, @unchecked Sendable {
    private let lock = NSLock()
    private var sizes: [String: CGSize] = [:]

    func imageSize(for key: String) -> CGSize? { lock.withLock { sizes[key] } }

    override func store(_ image: UIImage, original: Data?, forKey key: String,
                        options: KingfisherParsedOptionsInfo, toDisk: Bool,
                        completionHandler: (@Sendable (CacheStoreResult) -> Void)?) {
        if let cg = image.cgImage {
            lock.withLock { sizes[key] = CGSize(width: cg.width, height: cg.height) }
        }
        super.store(image, original: original, forKey: key, options: options, toDisk: toDisk,
                    completionHandler: completionHandler)
    }
}

private final class PreviewImageProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var payload = Data()
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "timeline-preview.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard request.value(forHTTPHeaderField: "X-Preview-Test") == "fixture" else {
            client?.urlProtocol(self, didFailWithError: URLError(.userAuthenticationRequired))
            return
        }
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil,
            headerFields: ["Content-Type": "image/png"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.payload)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
