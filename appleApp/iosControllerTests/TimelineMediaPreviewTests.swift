import XCTest
import AVFoundation
import UIKit
import SwiftUI
import ImageIO
import UniformTypeIdentifiers
import Kingfisher
import KotlinSharedUI
import FlareAppleCore
@testable import FlareAppleUI
@testable import Flare

@MainActor
final class TimelineMediaPreviewTests: XCTestCase {
    func testSinglePostMediaHeightAndScreenshotsOnBothRenderers() async throws {
        for renderer in ["uikit", "swiftui"] {
            for tall in [false, true] {
                let size = CGSize(width: 360, height: tall ? 1_800 : 640)
                let fixture = try await Fixture(imageSize: size)
                let media = fixture.media(path: "single-\(renderer)-\(tall).png", width: Float(size.width), height: Float(size.height))
                let post = singleMediaPost(media)
                let host: UIViewController
                var nativeStatus: StatusUIKitView?
                if renderer == "uikit" {
                    host = UIViewController()
                    let view = StatusUIKitView()
                    var appearance = StatusUIKitAppearance(timeline: singleMediaAppearance())
                    appearance.singleMediaMaxHeight = 400
                    view.configure(data: post, appearance: appearance)
                    host.view.addSubview(view)
                    nativeStatus = view
                } else {
                    host = UIHostingController(rootView:
                        VStack(alignment: .leading) {
                            StatusView(data: post)
                            Spacer(minLength: 0)
                        }
                        .padding(16)
                        .environment(\.timelineAppearance, singleMediaAppearance())
                        .inMediaViewport(height: 800))
                }
                let window = try fixture.show(host)
                window.overrideUserInterfaceStyle = .light
                host.view.backgroundColor = .systemBackground
                defer { window.isHidden = true; window.rootViewController = nil }
                if let nativeStatus {
                    let width = host.view.bounds.width - 32
                    nativeStatus.prepareForFitting(width: width)
                    nativeStatus.frame = CGRect(x: 16, y: host.view.safeAreaInsets.top + 16,
                                                width: width, height: nativeStatus.timelineHeight(for: width) ?? 0)
                }
                for _ in 0..<300 {
                    host.view.layoutIfNeeded()
                    if fixture.cache.imageSize(for: media.previewUrl) != nil { break }
                    try await Task.sleep(for: .milliseconds(10))
                }
                try await Task.sleep(for: .milliseconds(150))
                host.view.layoutIfNeeded()
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1
                let screenshot = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
                    window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
                }
                let attachment = XCTAttachment(image: screenshot)
                attachment.name = "single-media-\(renderer)-\(tall ? "long" : "portrait")"
                attachment.lifetime = .keepAlways
                add(attachment)
                let bounds = try cyanBounds(screenshot)
                XCTAssertEqual(bounds.width, tall ? 400 * 9 / 21 : 400 * 9 / 16, accuracy: 2)
                XCTAssertEqual(bounds.height, 400, accuracy: 2)
                XCTAssertEqual(bounds.minX, 16 + 44 + 8, accuracy: 2, "Keep the existing avatar/content indentation")
            }
        }
    }

    private func singleMediaAppearance(limited: Bool = true, expanded: Bool = true) -> TimelineAppearance {
        let helper = TimelinePresentationAppearancePatchHelper.shared
        return helper.resolve(base: TimelineAppearance.companion.Default, patch:
            helper.setExpandMediaSize(patch: helper.setLimitSingleMediaHeight(patch: helper.empty, value: limited), value: expanded))
    }

    private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }

    func testSingleMediaLimitToggleRestoresOriginalBoundsOnBothRenderers() async throws {
        for renderer in ["uikit", "swiftui"] {
            let fixture = try await Fixture(imageSize: CGSize(width: 360, height: 640))
            let media = fixture.media(path: "toggle-\(renderer).png", width: 360, height: 640)
            let post = singleMediaPost(media)
            let host: UIViewController
            let update: (TimelineAppearance) -> Void
            var native: StatusUIKitView?
            if renderer == "uikit" {
                host = UIViewController()
                let status = StatusUIKitView()
                native = status
                host.view.addSubview(status)
                update = { appearance in
                    var appearance = StatusUIKitAppearance(timeline: appearance)
                    appearance.singleMediaMaxHeight = 200
                    status.configure(data: post, appearance: appearance)
                    status.prepareForFitting(width: 360)
                    status.frame = CGRect(x: 16, y: host.view.safeAreaInsets.top + 16,
                                          width: 360, height: status.timelineHeight(for: 360) ?? 0)
                }
            } else {
                func content(_ appearance: TimelineAppearance) -> some View {
                    VStack(alignment: .leading) {
                        StatusView(data: post).frame(width: 360)
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .environment(\.timelineAppearance, appearance)
                    .inMediaViewport(height: 400)
                }
                let controller = UIHostingController(rootView: content(TimelineAppearance.companion.Default))
                host = controller
                update = { controller.rootView = content($0) }
            }
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            for (index, appearance) in [TimelineAppearance.companion.Default, singleMediaAppearance(),
                                        singleMediaAppearance(limited: true, expanded: false),
                                        singleMediaAppearance(limited: false), singleMediaAppearance()].enumerated() {
                update(appearance)
                for _ in 0..<300 {
                    host.view.layoutIfNeeded()
                    if fixture.cache.imageSize(for: media.previewUrl) != nil { break }
                    try await Task.sleep(for: .milliseconds(10))
                }
                try await Task.sleep(for: .milliseconds(150))
                host.view.layoutIfNeeded()
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1
                let screenshot = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
                    window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
                }
                let bounds = try cyanBounds(screenshot)
                let limited = appearance.limitSingleMediaHeight && appearance.expandMediaSize
                XCTAssertEqual(bounds.width, limited ? 112.5 : 308, accuracy: 2, "\(renderer), step \(index)")
                XCTAssertEqual(bounds.height, limited ? 200 : (appearance.expandMediaSize ? 308 * 16 / 9 : 308 * 9 / 16),
                               accuracy: 2, "\(renderer), step \(index)")
                XCTAssertEqual(bounds.minX, 68, accuracy: 2)
                if let native {
                    XCTAssertEqual(native.bounds.height, native.timelineHeight(for: 360) ?? 0, accuracy: 1)
                }
            }
        }
    }

    func testSingleMediaLimitRespectsScopeAndKeepsVideoAspectRatio() async throws {
        let fixture = try await Fixture()
        let image = fixture.media(width: 360, height: 640)
        let grid = StatusMediaUIView()
        func configure(_ items: [UiMedia], expanded: Bool = true) {
            grid.configure(data: items, sensitive: false, cornerRadius: 16,
                           singleFollowsImageAspect: expanded, limitMediaGridToNine: true,
                           mediaLayout: .grid, carouselLeadingPadding: 0, carouselTrailingPadding: 0)
        }
        configure([image])
        XCTAssertEqual(grid.fittingWidth(for: 360), 360, "Profile media does not supply a height limit")
        grid.singleMediaMaxHeight = 400
        XCTAssertEqual(grid.fittingWidth(for: 360), 225, accuracy: 0.01)
        grid.singleMediaMaxHeight = 200
        XCTAssertEqual(grid.fittingWidth(for: 360), 112.5, accuracy: 0.01)
        configure([image], expanded: false)
        XCTAssertEqual(grid.fittingWidth(for: 360), 360)
        configure([image, image])
        XCTAssertEqual(grid.fittingWidth(for: 360), 360)
        let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
            thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil, height: 1_800, width: 360,
            customHeaders: nil, proxy: ProxyRef.Global.shared, downloadUrl: nil)
        configure([video])
        XCTAssertEqual(grid.fittingWidth(for: 360), 40, accuracy: 0.01, "Tall videos retain their original ratio")
        let audio = UiMediaAudio(url: "https://timeline-preview.invalid/audio.mp3", description: nil,
                                 previewUrl: nil, customHeaders: nil, proxy: ProxyRef.Global.shared)
        configure([audio])
        XCTAssertEqual(grid.fittingWidth(for: 360), 360)
    }

    func testSingleVideoToggleUpdatesExistingThumbnailAndPlayer() throws {
        let video = UiMediaVideo(url: "https://timeline-preview.invalid/tall.mp4",
            thumbnailUrl: "https://timeline-preview.invalid/tall.png", description: nil, height: 1_800, width: 360,
            customHeaders: nil, proxy: ProxyRef.Global.shared, downloadUrl: nil)
        let status = StatusUIKitView()
        let post = singleMediaPost(video, sensitive: true)
        func update(limited: Bool) {
            var appearance = StatusUIKitAppearance(timeline: singleMediaAppearance(limited: limited))
            appearance.singleMediaMaxHeight = 200
            status.configure(data: post, appearance: appearance)
            status.prepareForFitting(width: 360)
            status.frame.size = CGSize(width: 360, height: status.timelineHeight(for: 360) ?? 0)
            status.layoutIfNeeded()
        }
        update(limited: false)
        let media = try XCTUnwrap(descendants(status).compactMap { $0 as? MediaUIView }.first)
        let thumbnail = try XCTUnwrap(descendants(media).compactMap { $0 as? NetworkImageView }.first)
        let grid = try XCTUnwrap(descendants(status).compactMap { $0 as? StatusMediaUIView }.first)
        let blur = try XCTUnwrap(grid.subviews.compactMap { $0 as? UIVisualEffectView }.first)
        let reveal = try XCTUnwrap(grid.subviews.compactMap { $0 as? UIButton }.first)
        reveal.sendActions(for: .touchUpInside)
        XCTAssertTrue(blur.isHidden)
        let surface = VideoPlaybackSurfaceView()
        media.attachAutoplayPlayer(surface)
        for enabled in [true, false, true, false] {
            update(limited: enabled)
            XCTAssertTrue(descendants(status).contains { $0 === media })
            XCTAssertTrue(surface.superview === media)
            XCTAssertTrue(blur.isHidden)
            XCTAssertTrue(grid.allowsVideoAutoplay)
            XCTAssertEqual(thumbnail.contentMode, enabled ? .scaleAspectFit : .scaleAspectFill)
            XCTAssertEqual(surface.playerLayer.videoGravity, enabled ? .resizeAspect : .resizeAspectFill)
        }
    }

    func testSwiftUIMediaKeepsItsInitialHeightWhenLazyRowsAppearAndReturn() async throws {
        let fixture = try await Fixture(imageSize: CGSize(width: 800, height: 1_600))
        let post = singleMediaPost(fixture.media(width: 800, height: 1_600))
        for hasPlaybackCoordinator in [false, true] {
            var heights: [Int: [CGFloat]] = [:]
            let content = ScrollView {
                LazyVStack {
                    ForEach(0..<40) { index in
                        StatusMediaView(post: post, data: post.images, sensitive: false, cornerRadius: 12,
                                        allowsCarousel: false, carouselLeadingPadding: 0, carouselTrailingPadding: 0,
                                        onMediaClicked: { _, _ in })
                            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                                heights[index, default: []].append(height)
                            }
                    }
                }
            }
            .if(hasPlaybackCoordinator) { $0.detectScrolling() }
            .if(!hasPlaybackCoordinator) { $0.timelineMediaViewport() }
            .frame(width: 360, height: 600)
            .environment(\.timelineAppearance, singleMediaAppearance())
            let host = UIHostingController(rootView: content)
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            try await Task.sleep(for: .milliseconds(150))
            let scroll = try XCTUnwrap(descendants(host.view).compactMap { $0 as? UIScrollView }.first)
            for offset: CGFloat in [600, 1_200, 2_400, 4_000, 2_400, 1_200, 0] {
                scroll.setContentOffset(CGPoint(x: 0, y: offset), animated: false)
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(50))
            }
            XCTAssertGreaterThan(heights.count, 8)
            for (index, values) in heights {
                XCTAssertTrue(values.allSatisfy { abs($0 - 300) <= 1 },
                              "Row \(index), coordinator \(hasPlaybackCoordinator): \(values)")
            }
        }
    }

    func testSwiftUIMediaIgnoresPlaybackViewportAndChromeInsetsButRespondsToResize() async throws {
        let fixture = try await Fixture(imageSize: CGSize(width: 800, height: 1_600))
        let post = singleMediaPost(fixture.media(width: 800, height: 1_600))
        var measuredHeight: CGFloat = 0
        func content(height: CGFloat, chrome: CGFloat) -> some View {
            ScrollView {
                StatusMediaView(post: post, data: post.images, sensitive: false, cornerRadius: 12,
                                allowsCarousel: false, carouselLeadingPadding: 0, carouselTrailingPadding: 0,
                                onMediaClicked: { _, _ in })
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { measuredHeight = $0 }
            }
            .safeAreaInset(edge: .top) { Color.clear.frame(height: chrome) }
            .environment(\.timelinePlaybackViewport, CGRect(x: 0, y: chrome, width: 360, height: height - chrome))
            .timelineMediaViewport()
            .frame(width: 360, height: height)
            .environment(\.timelineAppearance, singleMediaAppearance())
        }
        let host = UIHostingController(rootView: content(height: 600, chrome: 0))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for (height, chrome, expected): (CGFloat, CGFloat, CGFloat) in [
            (600, 0, 300), (600, 54, 300), (600, 0, 300), (400, 0, 200), (800, 0, 400)
        ] {
            host.rootView = content(height: height, chrome: chrome)
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(50))
            XCTAssertEqual(measuredHeight, expected, accuracy: 1)
        }
    }

    func testSinglePostMediaUsesScrollViewportWithoutPlaybackCoordinator() async throws {
        let fixture = try await Fixture(imageSize: CGSize(width: 360, height: 640))
        let media = fixture.media(path: "scroll-viewport.png", width: 360, height: 640)
        let post = singleMediaPost(media)
        func content(height: CGFloat) -> some View {
            ScrollView { StatusView(data: post).padding(16) }
                .timelineMediaViewport()
                .environment(\.timelineAppearance, singleMediaAppearance())
                .frame(height: height)
                .frame(maxHeight: .infinity, alignment: .top)
        }
        let host = UIHostingController(rootView: content(height: 400))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for height: CGFloat in [400, 600] {
            host.rootView = content(height: height)
            for _ in 0..<300 {
                host.view.layoutIfNeeded()
                if fixture.cache.imageSize(for: media.previewUrl) != nil { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            try await Task.sleep(for: .milliseconds(150))
            host.view.layoutIfNeeded()
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let screenshot = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let bounds = try cyanBounds(screenshot)
            XCTAssertEqual(bounds.height, height * 0.5, accuracy: 2)
            XCTAssertEqual(bounds.width, height * 0.5 * 9 / 16, accuracy: 2)
        }
    }

    func testNarrowVideoBadgeFitsAndRemainsRetryable() async throws {
        let fixture = try await Fixture()
        let view = fixture.view
        let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
            thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
            height: 1_800, width: 360, customHeaders: nil, proxy: ProxyRef.Global.shared, downloadUrl: nil)
        view.set(media: video, cornerRadius: 12, fitsMedia: true)
        var retries = 0
        for width: CGFloat in [40, 16, 180] {
            view.frame.size = CGSize(width: width, height: 200)
            for state: VideoAutoplayOverlayState in [.idle, .loading, .playing(remaining: 61), .error] {
                view.setAutoplayOverlay(state, progress: 0.42, errorMessage: "Retry video") { retries += 1 }
                view.setNeedsLayout()
                view.layoutIfNeeded()
                let badge = try XCTUnwrap(view.subviews.compactMap { $0 as? UIButton }.first)
                XCTAssertGreaterThan(badge.bounds.width, 0)
                XCTAssertTrue(view.bounds.insetBy(dx: -0.5, dy: -0.5).contains(badge.frame), "Badge must fit inside a \(width)pt video")
                if badge.isUserInteractionEnabled {
                    let before = retries
                    badge.sendActions(for: .touchUpInside)
                    XCTAssertEqual(retries, before + 1)
                    XCTAssertEqual(view.accessibilityCustomActions?.count, 1)
                }
            }
        }
    }

    func testUIKitNarrowAltButtonFitsAndOpensCurrentDescription() async throws {
        let fixture = try await Fixture()
        let grid = StatusMediaUIView()
        grid.singleMediaMaxHeight = 200
        let host = UIViewController()
        host.view.addSubview(grid)
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        var firstButton: UIButton?
        for (width, direction): (Float, UISemanticContentAttribute) in [
            (80, .forceLeftToRight), (200, .forceLeftToRight), (900, .forceLeftToRight),
            (80, .forceLeftToRight), (80, .forceRightToLeft), (900, .forceRightToLeft),
        ] {
            let description = "Description for width \(width)"
            let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
                thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: description,
                height: 1_000, width: width, customHeaders: nil, proxy: ProxyRef.Global.shared, downloadUrl: nil)
            grid.configure(data: [video], sensitive: false, cornerRadius: 12,
                           singleFollowsImageAspect: true, limitMediaGridToNine: true,
                           mediaLayout: .grid, carouselLeadingPadding: 0, carouselTrailingPadding: 0)
            grid.frame = CGRect(x: 16, y: 100, width: grid.fittingWidth(for: 360), height: 200)
            grid.layoutIfNeeded()
            let button = try XCTUnwrap(descendants(grid).compactMap { $0 as? UIButton }.first { $0.configuration?.title == "ALT" })
            if let firstButton { XCTAssertTrue(button === firstButton) } else { firstButton = button }
            let cell = try XCTUnwrap(button.superview)
            cell.semanticContentAttribute = direction
            cell.setNeedsLayout()
            cell.layoutIfNeeded()
            XCTAssertEqual(cell.bounds.width, CGFloat(width / 5), accuracy: 0.5)
            XCTAssertEqual(cell.bounds.height, 200, accuracy: 0.5)
            XCTAssertGreaterThan(button.frame.width, 0)
            XCTAssertTrue(cell.bounds.insetBy(dx: -0.5, dy: -0.5).contains(button.frame),
                          "ALT \(button.frame) must fit inside \(cell.bounds)")
            let label = try XCTUnwrap(button.titleLabel)
            XCTAssertEqual(label.text, "ALT")
            XCTAssertGreaterThanOrEqual(label.bounds.width + 0.5, label.intrinsicContentSize.width)
            XCTAssertTrue(cell.bounds.insetBy(dx: -0.5, dy: -0.5).contains(label.convert(label.bounds, to: cell)))
            XCTAssertTrue(cell.hitTest(button.center, with: nil) === button)
            if width == 900 {
                XCTAssertEqual(button.transform, .identity)
                let trailing = direction == .forceRightToLeft ? button.frame.minX : cell.bounds.maxX - button.frame.maxX
                XCTAssertEqual(trailing, 8, accuracy: 0.5)
                XCTAssertEqual(cell.bounds.maxY - button.frame.maxY, 8, accuracy: 0.5)
            }
            button.sendActions(for: .touchUpInside)
            let alert = try XCTUnwrap(host.presentedViewController as? UIAlertController)
            XCTAssertEqual(alert.message, description)
            for _ in 0..<100 {
                if !alert.isBeingPresented { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            await withCheckedContinuation { continuation in
                host.dismiss(animated: false) { continuation.resume() }
            }
        }
    }

    func testUIKitNarrowSensitiveControlsFitAndToggleRepeatedly() async throws {
        let fixture = try await Fixture()
        let host = UIViewController()
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        let media = StatusMediaUIView()
        media.singleMediaMaxHeight = 200
        host.view.addSubview(media)
        for direction: UISemanticContentAttribute in [.forceLeftToRight, .forceRightToLeft] {
            media.semanticContentAttribute = direction
            for width: Float in [80, 200, 900] {
                let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
                    thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
                    height: 1_000, width: width, customHeaders: nil, proxy: ProxyRef.Global.shared, downloadUrl: nil)
                media.configure(data: [video], sensitive: true, cornerRadius: 12,
                                singleFollowsImageAspect: true, limitMediaGridToNine: true,
                                mediaLayout: .grid, carouselLeadingPadding: 0, carouselTrailingPadding: 0)
                media.frame = CGRect(x: 16, y: 100, width: media.fittingWidth(for: 360), height: 200)
                let toggle = try XCTUnwrap(media.subviews.compactMap { $0 as? UIButton }.first)
                let blur = try XCTUnwrap(media.subviews.compactMap { $0 as? UIVisualEffectView }.first)
                for revealed in [false, true, false, true] {
                    media.layoutIfNeeded()
                    XCTAssertEqual(media.bounds.width, CGFloat(width / 5), accuracy: 0.5)
                    XCTAssertEqual(media.bounds.height, 200, accuracy: 0.5)
                    XCTAssertEqual(blur.isHidden, revealed)
                    XCTAssertEqual(media.allowsVideoAutoplay, revealed)
                    XCTAssertGreaterThan(toggle.frame.width, 0)
                    XCTAssertTrue(media.bounds.insetBy(dx: -0.5, dy: -0.5).contains(toggle.frame),
                                  "Toggle \(toggle.frame) must fit inside \(media.bounds), revealed: \(revealed)")
                    let icon = try XCTUnwrap(toggle.imageView)
                    XCTAssertGreaterThan(icon.bounds.width, 0)
                    XCTAssertTrue(media.bounds.insetBy(dx: -0.5, dy: -0.5).contains(icon.convert(icon.bounds, to: media)))
                    XCTAssertTrue(media.hitTest(toggle.center, with: nil) === toggle)
                    if width == 900, revealed {
                        XCTAssertEqual(toggle.transform, .identity)
                        let leading = direction == .forceRightToLeft ? media.bounds.maxX - toggle.frame.maxX : toggle.frame.minX
                        XCTAssertEqual(leading, 12, accuracy: 0.5)
                        XCTAssertEqual(toggle.frame.minY, 12, accuracy: 0.5)
                    }
                    UIView.performWithoutAnimation { toggle.sendActions(for: .touchUpInside) }
                }
            }
        }
    }

    func testSwiftUINarrowSensitiveMediaShowsRevealAndHideControls() async throws {
        let fixture = try await Fixture()
        let helper = TimelinePresentationAppearancePatchHelper.shared
        let appearance = helper.resolve(base: singleMediaAppearance(),
                                        patch: helper.setVideoAutoplay(patch: helper.empty, value: .never))
        var mediaBounds = CGRect.zero
        func content(width: Float, sensitive: Bool) -> some View {
            let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
                thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
                height: 1_000, width: width, customHeaders: ["X-Preview-Test": "fixture"],
                proxy: ProxyRef.Global.shared, downloadUrl: nil)
            return StatusMediaView(post: singleMediaPost(video, sensitive: true), data: [video], sensitive: sensitive,
                cornerRadius: 12, allowsCarousel: false, carouselLeadingPadding: 0, carouselTrailingPadding: 0,
                onMediaClicked: { _, _ in })
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { mediaBounds = $0 }
                .frame(width: 360, alignment: .leading)
                .tint(.red)
                .environment(\.timelineAppearance, appearance)
                .inMediaViewport(height: 400)
        }
        for startsHidden in [true, false] {
            // Preserve the initial blur state when enabling controls to render both toggle states.
            let host = UIHostingController(rootView: content(width: 80, sensitive: startsHidden))
            host.view.backgroundColor = .black
            let window = try fixture.show(host)
            window.overrideUserInterfaceStyle = .light
            defer { window.isHidden = true; window.rootViewController = nil }
            for width: Float in [80, 200, 900] {
                host.rootView = content(width: width, sensitive: true)
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(150))
                XCTAssertEqual(mediaBounds.width, CGFloat(width / 5), accuracy: 0.5)
                XCTAssertEqual(mediaBounds.height, 200, accuracy: 0.5)
                let format = UIGraphicsImageRendererFormat()
                format.scale = 3
                let screenshot = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
                    window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
                }
                let crop = mediaBounds.applying(CGAffineTransform(scaleX: format.scale, y: format.scale))
                let mediaImage = try XCTUnwrap(screenshot.cgImage?.cropping(to: crop))
                var pixels = [UInt8](repeating: 0, count: mediaImage.width * mediaImage.height * 4)
                let context = try XCTUnwrap(CGContext(data: &pixels, width: mediaImage.width, height: mediaImage.height,
                    bitsPerComponent: 8, bytesPerRow: mediaImage.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue))
                context.draw(mediaImage, in: CGRect(x: 0, y: 0, width: mediaImage.width, height: mediaImage.height))
                let redPixels = stride(from: 0, to: pixels.count, by: 4).filter {
                    Int(pixels[$0]) > Int(pixels[$0 + 1]) + 60 && Int(pixels[$0]) > Int(pixels[$0 + 2]) + 60
                }.count
                XCTAssertGreaterThan(redPixels, 20,
                                     "The \(startsHidden ? "reveal" : "hide") control must be visible in a \(mediaBounds.width)pt video")
            }
        }
    }

    func testSwiftUIPlaybackBadgesStayInsideNarrowMedia() async throws {
        let fixture = try await Fixture()
        var contentBounds = CGRect.zero
        var badgeBounds = CGRect.zero
        var mediaBounds = CGRect.zero
        func content(width: CGFloat, height: CGFloat, state: Int) -> some View {
            Color.blue
                .overlay(alignment: .bottomLeading) {
                    Group {
                        switch state {
                        case 0: Image(fontAwesome: .circlePlay)
                        case 1: ProgressView().tint(.white)
                        default: Text("1:23").font(.caption).lineLimit(1)
                        }
                    }
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { contentBounds = $0 }
                    .mediaVideoBadgeStyle()
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { badgeBounds = $0 }
                }
                .frame(width: width, height: height)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { mediaBounds = $0 }
                .clipped()
        }
        let host = UIHostingController(rootView: content(width: 180, height: 200, state: 0))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for (width, height): (CGFloat, CGFloat) in [(16, 200), (40, 200), (180, 200), (180, 16)] {
            for state in 0..<3 {
                host.rootView = content(width: width, height: height, state: state)
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(50))
                let message = "state \(state), media \(mediaBounds), badge \(badgeBounds), content \(contentBounds)"
                XCTAssertEqual(mediaBounds.width, width, accuracy: 0.5)
                XCTAssertEqual(mediaBounds.height, height, accuracy: 0.5)
                XCTAssertTrue(mediaBounds.insetBy(dx: -0.5, dy: -0.5).contains(badgeBounds), message)
                XCTAssertGreaterThan(contentBounds.width, 0, message)
                XCTAssertGreaterThan(contentBounds.height, 0, message)
                XCTAssertTrue(mediaBounds.insetBy(dx: -0.5, dy: -0.5).contains(contentBounds), message)
                if width == 180, height == 200 {
                    XCTAssertEqual(contentBounds.minX - mediaBounds.minX, 24, accuracy: 0.5)
                    XCTAssertEqual(mediaBounds.maxY - contentBounds.maxY, 24, accuracy: 0.5)
                }
            }
        }
    }

    func testSwiftUINarrowCappedVideoShowsPlayIndicatorWithoutAutoplay() async throws {
        let fixture = try await Fixture()
        let helper = TimelinePresentationAppearancePatchHelper.shared
        let appearance = helper.resolve(base: singleMediaAppearance(),
                                        patch: helper.setVideoAutoplay(patch: helper.empty, value: .never))
        var mediaBounds = CGRect.zero
        func content(width: Float) -> some View {
            let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
                thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
                height: 1_000, width: width, customHeaders: ["X-Preview-Test": "fixture"],
                proxy: ProxyRef.Global.shared, downloadUrl: nil)
            return StatusMediaView(post: singleMediaPost(video), data: [video], sensitive: false,
                cornerRadius: 12, allowsCarousel: false, carouselLeadingPadding: 0, carouselTrailingPadding: 0,
                onMediaClicked: { _, _ in })
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { mediaBounds = $0 }
                .frame(width: 360, alignment: .leading)
                .environment(\.timelineAppearance, appearance)
                .inMediaViewport(height: 400)
        }
        let host = UIHostingController(rootView: content(width: 80))
        host.view.backgroundColor = .black
        let window = try fixture.show(host)
        window.overrideUserInterfaceStyle = .light
        defer { window.isHidden = true; window.rootViewController = nil }
        for width: Float in [80, 200, 900] {
            host.rootView = content(width: width)
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(150))
            XCTAssertEqual(mediaBounds.width, CGFloat(width / 5), accuracy: 0.5)
            XCTAssertEqual(mediaBounds.height, 200, accuracy: 0.5)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let screenshot = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let mediaImage = try XCTUnwrap(screenshot.cgImage?.cropping(to: mediaBounds))
            var pixels = [UInt8](repeating: 0, count: mediaImage.width * mediaImage.height * 4)
            let context = try XCTUnwrap(CGContext(data: &pixels, width: mediaImage.width, height: mediaImage.height,
                bitsPerComponent: 8, bytesPerRow: mediaImage.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue))
            context.draw(mediaImage, in: CGRect(x: 0, y: 0, width: mediaImage.width, height: mediaImage.height))
            let whitePixels = stride(from: 0, to: pixels.count, by: 4).filter {
                pixels[$0] > 200 && pixels[$0 + 1] > 200 && pixels[$0 + 2] > 200
            }.count
            XCTAssertGreaterThan(whitePixels, 30, "The play indicator must remain visible in a \(mediaBounds.width)pt video")
        }
    }

    func testSwiftUIAltButtonFitsNarrowMedia() async throws {
        let fixture = try await Fixture()
        var overlayBounds = CGRect.zero
        var mediaBounds = CGRect.zero
        func content(width: CGFloat) -> some View {
            Color.blue
                .overlay(alignment: .bottomTrailing) {
                    AltTextOverlay(altText: "A tall video with an accessible description")
                        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { overlayBounds = $0 }
                }
                .frame(width: width, height: 200)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { mediaBounds = $0 }
                .clipped()
        }
        let host = UIHostingController(rootView: content(width: 16))
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        for width: CGFloat in [16, 40, 180] {
            host.rootView = content(width: width)
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(150))
            XCTAssertEqual(mediaBounds.width, width, accuracy: 0.5)
            XCTAssertEqual(mediaBounds.height, 200, accuracy: 0.5)
            XCTAssertGreaterThan(overlayBounds.width, 0)
            XCTAssertGreaterThan(overlayBounds.height, 0)
            XCTAssertTrue(mediaBounds.insetBy(dx: -0.5, dy: -0.5).contains(overlayBounds),
                          "ALT \(overlayBounds) must fit inside \(mediaBounds)")
        }
    }

    func testSwiftUINarrowCappedVideoRendersAltText() async throws {
        let fixture = try await Fixture()
        let helper = TimelinePresentationAppearancePatchHelper.shared
        let appearance = helper.resolve(base: singleMediaAppearance(),
                                        patch: helper.setVideoAutoplay(patch: helper.empty, value: .never))
        var mediaBounds = CGRect.zero
        func content(width: Float) -> some View {
            let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
                thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png",
                description: "A tall video with an accessible description",
                height: 1_000, width: width, customHeaders: ["X-Preview-Test": "fixture"],
                proxy: ProxyRef.Global.shared, downloadUrl: nil)
            return StatusMediaView(post: singleMediaPost(video), data: [video], sensitive: false,
                cornerRadius: 12, allowsCarousel: false, carouselLeadingPadding: 0, carouselTrailingPadding: 0,
                onMediaClicked: { _, _ in })
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { mediaBounds = $0 }
                .frame(width: 360, alignment: .leading)
                .tint(.red)
                .environment(\.timelineAppearance, appearance)
                .inMediaViewport(height: 400)
        }
        let host = UIHostingController(rootView: content(width: 80))
        host.view.backgroundColor = .black
        let window = try fixture.show(host)
        window.overrideUserInterfaceStyle = .light
        defer { window.isHidden = true; window.rootViewController = nil }
        for width: Float in [80, 200, 900] {
            host.rootView = content(width: width)
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(150))
            XCTAssertEqual(mediaBounds.width, CGFloat(width / 5), accuracy: 0.5)
            XCTAssertEqual(mediaBounds.height, 200, accuracy: 0.5)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 3
            let screenshot = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let crop = mediaBounds.applying(CGAffineTransform(scaleX: format.scale, y: format.scale))
            let mediaImage = try XCTUnwrap(screenshot.cgImage?.cropping(to: crop))
            var pixels = [UInt8](repeating: 0, count: mediaImage.width * mediaImage.height * 4)
            let context = try XCTUnwrap(CGContext(data: &pixels, width: mediaImage.width, height: mediaImage.height,
                bitsPerComponent: 8, bytesPerRow: mediaImage.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue))
            context.draw(mediaImage, in: CGRect(x: 0, y: 0, width: mediaImage.width, height: mediaImage.height))
            let redPixels = stride(from: 0, to: pixels.count, by: 4).filter {
                Int(pixels[$0]) > Int(pixels[$0 + 1]) + 60 && Int(pixels[$0]) > Int(pixels[$0 + 2]) + 60
            }.count
            XCTAssertGreaterThan(redPixels, 20, "The ALT label must remain visible in a \(mediaBounds.width)pt video")
        }
    }

    private func singleMediaPost(_ media: UiMedia, sensitive: Bool = false) -> UiTimelineV2.Post {
        func rich(_ text: String) -> UiRichText {
            let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false, imageLink: nil)
            return UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
        }
        let profile = UiProfile(key: MicroBlogKey(id: "demo", host: "example.invalid"), handle: UiHandle(raw: "flare", host: "example.invalid", displayOverride: nil), avatar: nil as UiMediaImage?, nameInternal: rich("Flare"), platformId: "mastodon", platformIcon: IconType.Material(icon: .world), clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil, pluginId: nil)
        return UiTimelineV2.Post(platformId: "mastodon", images: [media], sensitive: sensitive, contentWarning: nil, user: profile, platformIcon: IconType.Material(icon: .world), sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: rich("Single attachment · expanded media\nAligned with the post content"), translation: nil), actions: [], poll: nil, statusKey: MicroBlogKey(id: "media-layout", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "media-layout", pluginId: nil, authorSubtitleInternal: nil)
    }

    private func cyanBounds(_ image: UIImage) throws -> CGRect {
        let cg = try XCTUnwrap(image.cgImage)
        var pixels = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let context = try XCTUnwrap(CGContext(data: &pixels, width: cg.width, height: cg.height,
            bitsPerComponent: 8, bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue))
        context.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        var minX = cg.width, minY = cg.height, maxX = -1, maxY = -1
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            let r = Int(pixels[offset]), g = Int(pixels[offset + 1]), b = Int(pixels[offset + 2])
            if g > r + 40 && b > r + 40 {
                let x = (offset / 4) % cg.width, y = (offset / 4) / cg.width
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
        }
        XCTAssertGreaterThanOrEqual(maxX, minX, "The actual preview must load before capture")
        return CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1)
    }

    func testReactionImagesDecodeAtTheirDisplayHeightOnBothRenderers() async throws {
        for kind in ["uikit", "swiftui"] {
            let fixture = try await Fixture()
            KingfisherManager.shared.defaultOptions += [.requestModifier(AnyModifier { request in
                var request = request
                request.setValue("fixture", forHTTPHeaderField: "X-Preview-Test")
                return request
            })]
            let url = fixture.media(path: "reaction-\(kind).png").previewUrl
            let processor = ImageDownsamplingProcessor(
                pixelSize: CGSize(width: CGFloat.greatestFiniteMagnitude, height: 48), fillsBounds: false)
            let reaction = UiTimelineV2.PostEmojiReaction(name: ":test:", url: url, count: UiNumber(value: 2),
                clickEvent: ClickEventNoop.shared, isUnicode: false, me: true, proxy: ProxyRef.Global.shared)
            let host: UIViewController
            if kind == "uikit" {
                host = UIViewController()
                let view = StatusReactionUIView()
                view.traitOverrides.displayScale = 3
                host.view.addSubview(view)
                view.configure(data: [reaction], isDetail: false)
                view.frame = CGRect(x: 0, y: 0, width: 180, height: 100)
            } else {
                host = UIHostingController(rootView: StatusReactionView(data: [reaction], isDetail: false)
                    .environment(\.displayScale, 3).frame(width: 180))
            }
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            for _ in 0..<300 {
                if fixture.cache.imageSize(for: url, processor: processor) != nil { break }
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(10))
            }
            let size = try XCTUnwrap(fixture.cache.imageSize(for: url, processor: processor), kind)
            XCTAssertEqual(size, CGSize(width: 64, height: 48), kind)
        }
    }

    func testRichTextBlockImagesDecodeForTheirWidthAndKeepNaturalAspectRatio() async throws {
        for kind in ["uikit", "swiftui"] {
            let fixture = try await Fixture()
            KingfisherManager.shared.defaultOptions += [.requestModifier(AnyModifier { request in
                var request = request
                request.setValue("fixture", forHTTPHeaderField: "X-Preview-Test")
                return request
            })]
            let url = fixture.media(path: "block-\(kind).png").previewUrl
            let processor = ImageDownsamplingProcessor(
                pixelSize: CGSize(width: 540, height: CGFloat.greatestFiniteMagnitude), fillsBounds: false)
            let text = UiRichText(renderRuns: [RenderContent.BlockImage(url: url, href: nil, proxy: ProxyRef.Global.shared)],
                isRtl: false, raw: url, innerText: "", imageUrls: [url])
            let host: UIViewController
            var native: RichTextUIView?
            var swiftUIHeight: (() -> CGFloat)?
            if kind == "uikit" {
                host = UIViewController()
                let view = RichTextUIView()
                native = view
                view.traitOverrides.displayScale = 3
                host.view.addSubview(view)
                view.configure(text: text, lineLimit: nil, isTextSelectionEnabled: false, onOpenURL: nil)
                view.prepareForFitting(width: 180)
                view.frame = CGRect(x: 0, y: 0, width: 180, height: 135)
            } else {
                let controller = UIHostingController(rootView: RichText(text: text)
                    .environment(\.displayScale, 3).frame(width: 180))
                host = controller
                swiftUIHeight = {
                    controller.sizeThatFits(in: CGSize(width: 180, height: 1_000)).height
                        - controller.view.safeAreaInsets.top - controller.view.safeAreaInsets.bottom
                }
            }
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            for _ in 0..<300 {
                host.view.layoutIfNeeded()
                native?.prepareForFitting(width: 180)
                if fixture.cache.imageSize(for: url, processor: processor) != nil { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            let size = try XCTUnwrap(fixture.cache.imageSize(for: url, processor: processor), kind)
            XCTAssertEqual(size.width, 540, accuracy: 1, kind)
            XCTAssertEqual(size.height, 405, accuracy: 1, kind)
            try await Task.sleep(for: .milliseconds(30))
            if let swiftUIHeight { XCTAssertEqual(swiftUIHeight(), 135, accuracy: 1) }
            let inlineProcessor = ImageDownsamplingProcessor(
                pixelSize: CGSize(width: CGFloat.greatestFiniteMagnitude, height: 51), fillsBounds: false)
            XCTAssertEqual(fixture.cache.imageCachedType(forKey: url, processorIdentifier: inlineProcessor.identifier),
                           .none, "Block images must not also be loaded as inline emoji: \(kind)")
            if let native {
                XCTAssertEqual(try XCTUnwrap(native.timelineHeight(for: 180)), 135, accuracy: 1)
            }
        }
    }

    func testRichTextInlineImagesDecodeAtDisplaySizeOnBothRenderers() async throws {
        for kind in ["uikit", "swiftui"] {
            let fixture = try await Fixture()
            KingfisherManager.shared.defaultOptions += [.requestModifier(AnyModifier { request in
                var request = request
                request.setValue("fixture", forHTTPHeaderField: "X-Preview-Test")
                return request
            })]
            let url = fixture.media(path: "inline-\(kind).png").previewUrl
            let processor = ImageDownsamplingProcessor(
                pixelSize: CGSize(width: CGFloat.greatestFiniteMagnitude, height: 51), fillsBounds: false)
            let largeHeight: CGFloat = kind == "uikit" ? 159 : 144
            let largeProcessor = ImageDownsamplingProcessor(
                pixelSize: CGSize(width: CGFloat.greatestFiniteMagnitude, height: largeHeight), fillsBounds: false)
            let text = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Image(url: url, alt: "emoji", proxy: ProxyRef.Global.shared)],
                block: RenderBlockStyle())], isRtl: false, raw: url, innerText: "emoji", imageUrls: [url])
            let host: UIViewController
            var growText: (() -> Void)?
            if kind == "uikit" {
                host = UIViewController()
                let view = RichTextUIView()
                view.traitOverrides.displayScale = 3
                host.view.addSubview(view)
                view.configure(text: text, lineLimit: nil, isTextSelectionEnabled: false, onOpenURL: nil,
                               preferredContentSizeCategory: .large)
                view.prepareForFitting(width: 200)
                view.frame = CGRect(x: 0, y: 0, width: 200, height: 100)
                growText = {
                    view.configure(text: text, lineLimit: nil, isTextSelectionEnabled: false, onOpenURL: nil,
                                   preferredContentSizeCategory: .accessibilityExtraExtraExtraLarge)
                    view.prepareForFitting(width: 200)
                }
            } else {
                func content(_ size: DynamicTypeSize) -> some View {
                    RichText(text: text).environment(\.displayScale, 3)
                        .environment(\.dynamicTypeSize, size).frame(width: 200)
                }
                let controller = UIHostingController(rootView: content(.large))
                host = controller
                growText = { controller.rootView = content(.accessibility5) }
            }
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            for _ in 0..<300 {
                if fixture.cache.imageSize(for: url, processor: processor) != nil { break }
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(10))
            }
            let size = try XCTUnwrap(fixture.cache.imageSize(for: url, processor: processor), kind)
            XCTAssertEqual(size.height, 51, accuracy: 1, kind)
            XCTAssertEqual(size.width / size.height, 4.0 / 3.0, accuracy: 0.03, kind)
            growText?()
            for _ in 0..<300 {
                if fixture.cache.imageSize(for: url, processor: largeProcessor) != nil { break }
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertEqual(try XCTUnwrap(fixture.cache.imageSize(for: url, processor: largeProcessor)).height,
                           largeHeight, accuracy: 1, kind)
        }
    }

    func testColdPreviewsPersistOriginalAndProcessedImagesForOfflineReuse() async throws {
        for kind in ["uikit", "swiftui", "crossfade"] {
            let fixture = try await Fixture()
            KingfisherManager.shared.defaultOptions.append(.waitForCache)
            let url = fixture.media(path: "\(kind).png").previewUrl
            var window: UIWindow?
            var completed = false
            defer { window?.isHidden = true; window?.rootViewController = nil }

            if kind == "uikit" {
                fixture.view.frame.size = CGSize(width: 180, height: 135)
                fixture.view.set(media: fixture.media(path: "\(kind).png"), cornerRadius: 0)
                fixture.view.layoutIfNeeded()
                _ = try await fixture.loadedImage(width: 540)
            } else {
                let host = UIHostingController(rootView: NetworkImage(
                    data: fixture.image(url),
                    contentMode: .fill, usesCrossfade: kind == "crossfade",
                    onProgress: { completed = $0 == 1 }
                ).frame(width: 180, height: 135).environment(\.displayScale, 3))
                window = try fixture.show(host)
                for _ in 0..<300 {
                    if completed { break }
                    host.view.layoutIfNeeded()
                    try await Task.sleep(for: .milliseconds(10))
                }
                XCTAssertTrue(completed, kind)
            }

            let processor = ImageDownsamplingProcessor(pixelSize: CGSize(width: 540, height: 405), fillsBounds: true)
            XCTAssertEqual(fixture.cache.imageCachedType(forKey: fixture.image(url).cacheKey, processorIdentifier: processor.identifier), .memory, kind)
            fixture.cache.clearMemoryCache()
            XCTAssertEqual(fixture.cache.imageCachedType(forKey: fixture.image(url).cacheKey, processorIdentifier: processor.identifier), .disk,
                           "The processed preview must remain reusable after memory eviction: \(kind)")
            XCTAssertEqual(fixture.cache.imageCachedType(forKey: fixture.image(url).cacheKey), .disk,
                           "The original bytes must remain available offline: \(kind)")
            let result = try await KingfisherManager.shared.retrieveImage(
                with: .network(ImageResource(downloadURL: URL(string: url)!, cacheKey: fixture.image(url).cacheKey)),
                options: [.targetCache(fixture.cache), .onlyFromCache,
                          .processor(processor), .scaleFactor(3)]
            )
            XCTAssertEqual(result.cacheType, .disk, kind)
            XCTAssertEqual(result.image.cgImage?.width, 540, kind)
            XCTAssertEqual(result.image.cgImage?.height, 405, kind)
        }
    }

    func testRichTextBlockImagesStayVisibleAndKeepTheirHeightDuringWidthUpgrades() async throws {
        for kind in ["uikit", "swiftui"] {
            let fixture = try await Fixture()
            KingfisherManager.shared.defaultOptions.append(.requestModifier(AnyModifier { request in
                var request = request
                request.setValue("fixture", forHTTPHeaderField: "X-Preview-Test")
                return request
            }))
            let url = fixture.media(path: "block-upgrade-\(kind).png").previewUrl
            let text = UiRichText(renderRuns: [RenderContent.BlockImage(url: url, href: nil, proxy: ProxyRef.Global.shared)],
                isRtl: false, raw: url, innerText: "", imageUrls: [url])
            let host: UIViewController
            let resize: (CGFloat) -> Void
            let height: () -> CGFloat
            var native: RichTextUIView?
            if kind == "uikit" {
                host = UIViewController()
                let view = RichTextUIView()
                native = view
                view.traitOverrides.displayScale = 3
                host.view.addSubview(view)
                view.configure(text: text, lineLimit: nil, isTextSelectionEnabled: false, onOpenURL: nil)
                resize = { width in
                    view.frame = CGRect(x: 0, y: 0, width: width, height: width * 0.75)
                    view.prepareForFitting(width: width)
                    view.layoutIfNeeded()
                }
                height = { view.timelineHeight(for: view.bounds.width) ?? 0 }
            } else {
                func content(width: CGFloat) -> some View {
                    RichText(text: text).environment(\.displayScale, 3).frame(width: width)
                        .fixedSize(horizontal: false, vertical: true)
                }
                let controller = UIHostingController(rootView: content(width: 180))
                host = controller
                resize = { controller.rootView = content(width: $0) }
                height = {
                    controller.sizeThatFits(in: CGSize(width: 240, height: 1_000)).height
                        - controller.view.safeAreaInsets.top - controller.view.safeAreaInsets.bottom
                }
            }
            resize(180)
            let window = try fixture.show(host)
            defer { window.isHidden = true; window.rootViewController = nil }
            for _ in 0..<300 {
                host.view.layoutIfNeeded()
                if fixture.cache.imageSize(for: url)?.width == 540, abs(height() - 135) < 0.5 { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertEqual(height(), 135, accuracy: 0.5, kind)
            XCTAssertEqual(fixture.cache.imageSize(for: url)?.width, 540, kind)

            let queue = DispatchQueue(label: "block-image-upgrade-\(kind)")
            let gate = DispatchSemaphore(value: 0)
            queue.async { gate.wait() }
            defer { gate.signal() }
            KingfisherManager.shared.defaultOptions.append(.processingQueue(.dispatch(queue)))
            resize(240)
            for _ in 0..<10 {
                host.view.layoutIfNeeded()
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertEqual(height(), 180, accuracy: 0.5, "The loaded image must keep its natural ratio: \(kind)")
            let point = native.map { $0.convert(CGPoint(x: 120, y: 90), to: host.view) }
                ?? CGPoint(x: host.view.bounds.midX, y: host.view.bounds.midY)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let snapshot = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1), format: format).image { context in
                context.cgContext.translateBy(x: -point.x, y: -point.y)
                host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }
            var rgba = [UInt8](repeating: 0, count: 4)
            let context = try XCTUnwrap(CGContext(data: &rgba, width: 1, height: 1, bitsPerComponent: 8,
                bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(try XCTUnwrap(snapshot.cgImage), in: CGRect(x: 0, y: 0, width: 1, height: 1))
            XCTAssertGreaterThan(rgba[2], 200, "The blue image must stay visible while processing: \(kind)")
            XCTAssertLessThan(rgba[0], 50, kind)
            gate.signal()
            for _ in 0..<300 {
                if fixture.cache.imageSize(for: url)?.width == 720 { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            XCTAssertEqual(fixture.cache.imageSize(for: url)?.width, 720, kind)
            XCTAssertEqual(height(), 180, accuracy: 0.5, kind)
        }
    }

    func testMediaCreatesOptionalViewsOnDemandAndPreservesReuseBehavior() async throws {
        let fixture = try await Fixture()
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

    func testImagesDoNotCreateVideoOverlayViews() async throws {
        let fixture = try await Fixture()
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

    func testVideoOverlayKeepsStateAndRetryWhenReusedForAnImage() async throws {
        let fixture = try await Fixture()
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
            height: 768, width: 1_024, customHeaders: nil, proxy: ProxyRef.Global.shared, downloadUrl: nil)
        view.set(media: video, cornerRadius: 12)
        view.layoutIfNeeded()
        XCTAssertEqual(view.videoURL?.absoluteString, video.url)
        XCTAssertFalse(badge.isHidden)
        XCTAssertEqual(view.subviews.filter { $0 is UIButton }.count, 1)
        XCTAssertEqual(badge.frame.height, 32, accuracy: 0.5)
    }

    func testSwiftUICrossfadeKeepsPreviewWhileLargerImageIsProcessing() async throws {
        let fixture = try await Fixture()
        let queue = DispatchQueue(label: "preview-resize-test")
        let gate = DispatchSemaphore(value: 0)
        KingfisherManager.shared.defaultOptions.append(.processingQueue(.dispatch(queue)))
        let url = fixture.media().previewUrl
        func view(width: CGFloat) -> some View {
            NetworkImage(data: fixture.image(url), usesCrossfade: true)
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
        let fixture = try await Fixture()
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
        view.setImage(media: fixture.media().preview) { _ in
            cancelledCompletions += 1
        }
        view.layoutIfNeeded()
        view.setImage(media: fixture.media(path: "second.png").preview) { _ in
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
        let fixture = try await Fixture()
        let view = try XCTUnwrap(fixture.imageView as? NetworkImageView)
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        fixture.view.layoutIfNeeded()
        let media = fixture.media().preview
        KingfisherManager.shared.defaultOptions.append(.onlyFromCache)
        var failed = false
        view.setImage(media: media) { result in
            if case .failure = result { failed = true }
        }
        view.layoutIfNeeded()
        for _ in 0..<100 {
            if failed { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(failed)
        KingfisherManager.shared.defaultOptions.removeLast()
        view.setImage(media: media)
        view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
    }

    func testSwiftUIPreviewsDownsampleForFitAndFill() async throws {
        for mode in [SwiftUI.ContentMode.fit, .fill] {
            let fixture = try await Fixture()
            var completed = false
            let url = "https://timeline-preview.invalid/swiftui.png"
            let host = UIHostingController(rootView: NetworkImage(data: fixture.image(url), contentMode: mode,
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
        let fixture = try await Fixture()
        let first = "https://timeline-preview.invalid/first.png"
        let second = "https://timeline-preview.invalid/second.png"
        func view(_ url: String, width: CGFloat) -> some View {
            NetworkImage(data: fixture.image(url))
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
        let fixture = try await Fixture()
        let url = "https://timeline-preview.invalid/zoom.png"
        var completed = false
        let host = UIHostingController(rootView: NetworkImage(data: fixture.image(url), placeholder: fixture.media().preview, contentMode: .fit, usesCrossfade: true,
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
        let fixture = try await Fixture()
        var size: CGSize = .zero
        let host = UIHostingController(rootView: NetworkImage(data: fixture.media().preview, contentMode: .fit)
            .frame(width: 180).fixedSize(horizontal: false, vertical: true).environment(\.displayScale, 3)
            .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 })
        let window = try fixture.show(host)
        defer { window.isHidden = true; window.rootViewController = nil }
        let processor = ImageDownsamplingProcessor(pixelSize: CGSize(width: 540, height: 540), fillsBounds: false)
        for _ in 0..<300 {
            host.view.layoutIfNeeded()
            if size.height == 135, fixture.cache.imageSize(for: fixture.media().previewUrl, processor: processor) != nil {
                break
            }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(size.width, 180, accuracy: 0.5)
        XCTAssertEqual(size.height, 135, accuracy: 0.5)
        XCTAssertEqual(fixture.cache.imageSize(for: fixture.media().previewUrl, processor: processor)?.width, 540)
    }

    func testSwiftUIGIFWithQueryUsesAnimatedImageView() async throws {
        let fixture = try await Fixture(animated: true)
        let host = UIHostingController(rootView: NetworkImage(data: fixture.image("https://timeline-preview.invalid/animation.GIF?version=1")).frame(width: 180, height: 135))
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
        let fixture = try await Fixture()
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
            let fixture = try await Fixture()
            fixture.view.frame.size = size
            fixture.view.set(media: fixture.media(width: 0, height: 0), cornerRadius: 0)
            fixture.view.layoutIfNeeded()
            let image = try await fixture.loadedImage()
            XCTAssertEqual(image.cgImage?.width, 720)
            XCTAssertEqual(image.cgImage?.height, 540, "The cropped image must still fill the view at 3x")
        }
    }

    func testGrowingOrChangingScaleUpgradesPreviewAndShrinkingKeepsIt() async throws {
        let fixture = try await Fixture()
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
        let fixture = try await Fixture()
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
            let fixture = try await Fixture(animated: true)
            fixture.view.frame.size = CGSize(width: 90, height: 60)
            let media: UiMedia = explicitGIF ? UiMediaGif(url: "https://timeline-preview.invalid/animation.gif",
                previewUrl: "https://timeline-preview.invalid/still.png", description: nil,
                height: 768, width: 1_024, customHeaders: ["X-Preview-Test": "fixture"], proxy: ProxyRef.Global.shared) : fixture.media()
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
        let fixture = try await Fixture(orientation: 6)
        fixture.view.frame.size = CGSize(width: 135, height: 180)
        fixture.view.set(media: fixture.media(), cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        let image = try await fixture.loadedImage()
        XCTAssertEqual(image.cgImage?.width, 405)
        XCTAssertEqual(image.cgImage?.height, 540)
        XCTAssertEqual(image.imageOrientation, .up)
    }

    func testVideoKeepsPlaybackURLWhileThumbnailIsDownsampled() async throws {
        let fixture = try await Fixture()
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        let video = UiMediaVideo(url: "https://timeline-preview.invalid/movie.mp4",
            thumbnailUrl: "https://timeline-preview.invalid/thumbnail.png", description: nil,
            height: 768, width: 1_024, customHeaders: ["X-Preview-Test": "fixture"], proxy: ProxyRef.Global.shared, downloadUrl: nil)
        fixture.view.set(media: video, cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
        XCTAssertEqual(fixture.view.videoURL?.absoluteString, video.url)
    }

    func testPreviewResizeUsesOriginalCacheWithoutReplacingFullSizeMedia() async throws {
        let fixture = try await Fixture()
        fixture.view.frame.size = CGSize(width: 180, height: 135)
        let media = fixture.media()
        fixture.view.set(media: media, cornerRadius: 0)
        fixture.view.layoutIfNeeded()
        _ = try await fixture.loadedImage(width: 540)
        let original = try await fixture.cache.retrieveImageInDiskCache(forKey: media.preview!.cacheKey)
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
        private var downloaders: [(ImageDownloader, URLSessionConfiguration)] = []
        var imageView: UIImageView { view.subviews.compactMap { $0 as? UIImageView }.first! }

        init(animated: Bool = false, orientation: Int? = nil, imageSize: CGSize? = nil) async throws {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let image = UIGraphicsImageRenderer(
                size: imageSize ?? CGSize(width: 1_024, height: 768), format: format
            ).image { context in
                if let imageSize {
                    UIColor(red: 0.05, green: 0.65, blue: 0.8, alpha: 1).setFill()
                    context.fill(CGRect(origin: .zero, size: imageSize))
                    for y in stride(from: 32, to: Int(imageSize.height), by: 120) {
                        ("\(y) / \(Int(imageSize.height))" as NSString).draw(at: CGPoint(x: 32, y: y),
                            withAttributes: [.font: UIFont.monospacedDigitSystemFont(ofSize: 28, weight: .medium), .foregroundColor: UIColor.white])
                    }
                    return
                }
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
            oldOptions = KingfisherManager.shared.defaultOptions
            KingfisherManager.shared.defaultOptions = [.targetCache(cache)]
            for headers in [nil, ["X-Preview-Test": "fixture"]] as [[String: String]?] {
                let prepared = try await ProxyMediaImages.shared.prepare(
                    UiMediaImage(url: "https://timeline-preview.invalid/fixture.png", proxy: ProxyRef.Global.shared, headers: headers))
                downloaders.append((prepared.downloader, prepared.downloader.sessionConfiguration))
                let config = URLSessionConfiguration.ephemeral
                config.protocolClasses = [PreviewImageProtocol.self]
                config.httpAdditionalHeaders = ["X-Preview-Test": "fixture"]
                prepared.downloader.sessionConfiguration = config
            }
            view.traitOverrides.displayScale = 3
            view.updateTraitsIfNeeded()
        }

        isolated deinit {
            view.prepareForPoolRemoval()
            KingfisherManager.shared.defaultOptions = oldOptions
            for (downloader, configuration) in downloaders { downloader.sessionConfiguration = configuration }
            cache.clearMemoryCache()
            cache.clearDiskCache()
        }

        func image(_ url: String) -> UiMediaImage {
            UiMediaImage(url: url, proxy: ProxyRef.Global.shared, headers: ["X-Preview-Test": "fixture"])
        }

        func media(path: String = "preview.png", width: Float = 1_024, height: Float = 768) -> UiMediaImage {
            UiMediaImage(url: "https://timeline-preview.invalid/original.png",
                previewUrl: "https://timeline-preview.invalid/\(path)", description: "Preview description",
                height: height, width: width, sensitive: false, customHeaders: ["X-Preview-Test": "fixture"], proxy: ProxyRef.Global.shared)
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

    @MainActor
    func imageSize(for url: String) -> CGSize? {
        let keys = cacheKeys(for: url)
        return lock.withLock { keys.lazy.compactMap { self.sizes[$0] }.first }
    }

    @MainActor
    private func cacheKeys(for url: String) -> [String] {
        [UiMediaImage(url: url, proxy: ProxyRef.Global.shared, headers: ["X-Preview-Test": "fixture"]).cacheKey,
         UiMediaImage(url: url, proxy: ProxyRef.Global.shared).cacheKey]
    }

    @MainActor
    func imageSize(for key: String, processor: ImageDownsamplingProcessor) -> CGSize? {
        guard let image = cacheKeys(for: key).lazy.compactMap({
            self.retrieveImageInMemoryCache(forKey: $0, options: [.processor(processor)])?.cgImage
        }).first else {
            return nil
        }
        return CGSize(width: image.width, height: image.height)
    }

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

private extension View {
    func inMediaViewport(height: CGFloat) -> some View {
        timelineMediaViewport().frame(height: height).frame(maxHeight: .infinity, alignment: .top)
    }
}
