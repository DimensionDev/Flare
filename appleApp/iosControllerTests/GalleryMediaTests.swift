import FlareAppleUI
import Kingfisher
import KotlinSharedUI
import SwiftUI
import UIKit
import XCTest

@MainActor
final class GalleryMediaTests: XCTestCase {
    func testUgoiraEntersLazyGalleryAndShowsItsPoster() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let preview = try await fixture.cacheImage(.blue)
        let media = UiMediaUgoira(
            statusKey: MicroBlogKey(id: UUID().uuidString, host: "pixiv.net"),
            accountKey: MicroBlogKey(id: "missing-gallery-test-account", host: "pixiv.net"),
            previewUrl: preview, originalFrameUrl: nil, description: nil,
            height: 560, width: 390, sensitive: false, customHeaders: nil)
        let generation = MediaPlaybackMemory.shared.generation(for: media.url)
        MediaPlaybackMemory.shared.save(2, for: media.url)

        fixture.show(media)
        let pixels = try await fixture.waitForPixels(.blue)
        XCTAssertGreaterThan(pixels, 500)
        XCTAssertEqual(MediaPlaybackMemory.shared.generation(for: media.url), generation + 1)
        XCTAssertEqual(MediaPlaybackMemory.shared.position(for: media.url), 0)
    }

    func testImageKeepsItsPreviewWhenTheOriginalFails() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let preview = try await fixture.cacheImage(.blue)
        // A missing local file deterministically fails without contacting Pixiv.
        let original = FileManager.default.temporaryDirectory
            .appendingPathComponent("missing-gallery-\(UUID()).png").absoluteString
        let media = UiMediaImage(url: original, previewUrl: preview, description: nil,
                                 height: 560, width: 390, sensitive: false, customHeaders: nil)

        fixture.show(media)
        let pixels = try await fixture.waitForPixels(.blue)
        XCTAssertGreaterThan(pixels, 500)
        XCTAssertFalse(fixture.cache.isCached(forKey: URL(string: original)!.cacheKey))
    }

    func testAvailableOriginalReplacesThePreview() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let preview = try await fixture.cacheImage(.blue)
        let original = try await fixture.cacheImage(.red)
        let media = UiMediaImage(url: original, previewUrl: preview, description: nil,
                                 height: 560, width: 390, sensitive: false, customHeaders: nil)

        fixture.show(media)
        let pixels = try await fixture.waitForPixels(.red)
        XCTAssertGreaterThan(pixels, 500)
        XCTAssertEqual(try fixture.pixelCount(.blue), 0)
    }
}

@MainActor
private final class Fixture {
    enum Channel: Int { case red = 0, blue = 2 }

    let cache: ImageCache
    private let window: UIWindow
    private let previousKeyWindow: UIWindow?
    private let previousOptions: KingfisherOptionsInfo

    init() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
        window.overrideUserInterfaceStyle = .light
        cache = ImageCache(name: "gallery-media-tests-\(UUID())")
        previousOptions = KingfisherManager.shared.defaultOptions
        KingfisherManager.shared.defaultOptions = previousOptions + [.targetCache(cache)]
    }

    func close() {
        window.isHidden = true
        window.rootViewController = nil
        previousKeyWindow?.makeKey()
        KingfisherManager.shared.defaultOptions = previousOptions
        cache.clearMemoryCache()
    }

    func cacheImage(_ color: UIColor) async throws -> String {
        let url = "https://example.invalid/gallery-\(UUID()).png"
        let image = UIGraphicsImageRenderer(size: CGSize(width: 390, height: 560)).image { context in
            color.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 390, height: 560))
        }
        try await cache.store(image, forKey: url, toDisk: false)
        return url
    }

    func show(_ media: any UiMedia) {
        // Keep the nested lazy stacks and ForEach used by the vertical gallery detail.
        let root = ScrollView {
            LazyVStack(spacing: 2) {
                LazyVStack(spacing: 0) {
                    ForEach(0..<1, id: \.self) { _ in
                        GalleryMedia(media: media, contentMode: .fit)
                            .aspectRatio(390.0 / 560.0, contentMode: .fit)
                            .frame(maxWidth: .infinity)
                    }
                }
                Text("After media")
            }
        }
        window.rootViewController = UIHostingController(rootView: root)
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
    }

    func waitForPixels(_ channel: Channel) async throws -> Int {
        for _ in 0..<50 {
            let count = try pixelCount(channel)
            if count > 500 { return count }
            try await Task.sleep(for: .milliseconds(40))
        }
        return try pixelCount(channel)
    }

    func pixelCount(_ channel: Channel) throws -> Int {
        let image = UIGraphicsImageRenderer(size: window.bounds.size).image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        let cgImage = try XCTUnwrap(image.cgImage)
        var rgba = [UInt8](repeating: 0, count: 100 * 100 * 4)
        return try rgba.withUnsafeMutableBytes { bytes in
            let context = try XCTUnwrap(CGContext(
                data: bytes.baseAddress, width: 100, height: 100, bitsPerComponent: 8, bytesPerRow: 400,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: 100, height: 100))
            let pixels = bytes.bindMemory(to: UInt8.self)
            let other = channel == .red ? 2 : 0
            return stride(from: 0, to: pixels.count, by: 4).filter {
                pixels[$0 + channel.rawValue] > 200 && pixels[$0 + 1] < 40 && pixels[$0 + other] < 40
            }.count
        }
    }
}
