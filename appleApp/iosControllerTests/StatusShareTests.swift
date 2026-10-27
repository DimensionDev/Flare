import XCTest
import SwiftUI
import KotlinSharedUI
@testable import Flare
@testable import FlareAppleUI

@MainActor
final class StatusShareTests: XCTestCase {
    func testReplyScreenshotIncludesFullTextInBothThemes() async throws {
        let root = post("root", text: "Original post")
        let reply = post("reply", text: Array(repeating: "Reply line", count: 40).joined(separator: "\n"))
        for theme in [ColorScheme.light, .dark] {
            let base = try await StatusShareContent(data: root, statusKey: root.statusKey, replies: [])
                .frame(width: 360).background(Color.flareSystemGroupedBackground)
                .environment(\.colorScheme, theme).snapshot(colorScheme: theme)
            let image = try await StatusShareContent(data: root, statusKey: root.statusKey, replies: [reply])
                .frame(width: 360).background(Color.flareSystemGroupedBackground)
                .environment(\.colorScheme, theme).snapshot(colorScheme: theme)
            XCTAssertEqual(image.size.width, base.size.width)
            XCTAssertGreaterThan(image.size.height - base.size.height, 600, "Reply must not use the timeline line limit")
            XCTAssertEqual(image.scale, 3)
            let attachment = XCTAttachment(image: image)
            attachment.name = "selected-reply-\(theme)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    func testReplyScreenshotIncludesTwoTextBlocksInBothThemes() async throws {
        let root = post("root", text: "Original post")
        let reply = post("reply", textBlocks: [
            Array(repeating: "First reply block", count: 20).joined(separator: "\n"),
            Array(repeating: "Second reply block", count: 20).joined(separator: "\n"),
        ])
        for theme in [ColorScheme.light, .dark] {
            let base = try await StatusShareContent(data: root, statusKey: root.statusKey, replies: [])
                .frame(width: 360).background(Color.flareSystemGroupedBackground)
                .environment(\.colorScheme, theme).snapshot(colorScheme: theme)
            let image = try await StatusShareContent(data: root, statusKey: root.statusKey, replies: [reply])
                .frame(width: 360).background(Color.flareSystemGroupedBackground)
                .environment(\.colorScheme, theme).snapshot(colorScheme: theme)
            XCTAssertEqual(image.size.width, base.size.width)
            XCTAssertGreaterThan(image.size.height - base.size.height, 600, "Both reply blocks must render in full")
            let attachment = XCTAttachment(image: image)
            attachment.name = "two-block-reply-\(theme)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    func testOversizedScreenshotFailsBeforeRasterAllocation() async throws {
        do {
            _ = try await Color.white.frame(width: 360, height: 20_000).snapshot(colorScheme: .light)
            XCTFail("Oversized image must fail")
        } catch StatusShareRenderError.tooLarge {
        }
    }

    func testSelectedRepliesDoNotLeaveATrailingSeparator() async throws {
        let root = post("root", text: "Original post")
        for theme in [ColorScheme.light, .dark] {
            for count in [1, 2] {
                let replies = (0..<count).map { post("reply-\($0)", text: "Selected reply") }
                let image = try await StatusShareContent(data: root, statusKey: root.statusKey, replies: replies)
                    .frame(width: 360).background(Color.flareSystemGroupedBackground)
                    .environment(\.colorScheme, theme).snapshot(colorScheme: theme)
                let cgImage = try XCTUnwrap(image.cgImage)
                let width = cgImage.width
                let height = cgImage.height
                var pixels = [UInt8](repeating: 0, count: width * height * 4)
                let context = try XCTUnwrap(CGContext(data: &pixels, width: width, height: height,
                                                    bitsPerComponent: 8, bytesPerRow: width * 4,
                                                    space: CGColorSpaceCreateDeviceRGB(),
                                                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
                let x = width - 12
                let background = Array(pixels[(x * 4)..<(x * 4 + 4)])
                for y in (height - 3)..<height {
                    let offset = (y * width + x) * 4
                    XCTAssertEqual(Array(pixels[offset..<(offset + 4)]), background,
                                   "The final reply must end with content, without a trailing separator")
                }
            }
        }
    }

    func testReplyPickerUsesNativeNavigationAndList() async throws {
        let state = ReplyPickerState(replies: [post("first", text: "A selected reply"), post("second", text: "Another reply")])
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let host = UIHostingController(rootView: Color.clear.sheet(isPresented: .constant(true)) {
            StatusShareReplyPicker(state: state).presentationDetents([.large])
        })
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            host.dismiss(animated: false)
            window.isHidden = true
            window.rootViewController = nil
            previousWindow?.makeKey()
        }
        func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
        for _ in 0..<30 {
            window.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(20))
        }
        let views = descendants(window)
        let navigationBar = try XCTUnwrap(views.compactMap { $0 as? UINavigationBar }.first)
        let navigationItem = try XCTUnwrap(navigationBar.topItem)
        let leadingButtons = (navigationItem.leftBarButtonItems ?? []) + navigationItem.leadingItemGroups.flatMap(\.barButtonItems)
        let trailingButtons = (navigationItem.rightBarButtonItems ?? []) + navigationItem.trailingItemGroups.flatMap(\.barButtonItems)
        XCTAssertFalse(leadingButtons.isEmpty, "Cancel must be available in the native navigation bar")
        XCTAssertFalse(trailingButtons.isEmpty, "Done must be available in the native navigation bar")
        XCTAssertFalse(views.compactMap { $0 as? UICollectionView }.isEmpty, "Reply choices must use the native list")
        for style in [UIUserInterfaceStyle.light, .dark] {
            window.overrideUserInterfaceStyle = style
            try await Task.sleep(for: .milliseconds(150))
            window.layoutIfNeeded()
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let attachment = XCTAttachment(image: image)
            attachment.name = "reply-picker-\(style.rawValue)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    func testFailedMediaCannotBeSharedAsAPlaceholder() async throws {
        do {
            _ = try await NetworkImage(data: UiMediaImage(url: "file:///nonexistent-flare-share-image.png", proxy: ProxyRef.Global.shared))
                .frame(width: 120, height: 120).snapshot(colorScheme: .light)
            XCTFail("Unavailable media must fail")
        } catch StatusShareRenderError.mediaUnavailable {
        }
    }

    func testMissingOptionalImageDoesNotBlockScreenshot() async throws {
        let image = try await NetworkImage(data: Optional<UiMediaImage>.none)
            .frame(width: 120, height: 120).snapshot(colorScheme: .light)
        XCTAssertEqual(image.size.width, 120)
    }

    func testFailedBlockImageCannotBeSharedAsAPlaceholder() async throws {
        let url = "file:///nonexistent-flare-share-block-image.png"
        let text = UiRichText(renderRuns: [RenderContent.BlockImage(url: url, href: nil, proxy: ProxyRef.Global.shared)],
                              isRtl: false, raw: url, innerText: "", imageUrls: [url])
        do {
            _ = try await RichText(text: text).frame(width: 120).snapshot(colorScheme: .light)
            XCTFail("Unavailable block images must fail")
        } catch StatusShareRenderError.mediaUnavailable {
        }
    }

    func testCachedImageCanCompleteBeforeItAppears() async throws {
        let loading = ShareImageLoading()
        let key = UUID()
        loading.complete(key, success: true)
        loading.register(key)
        try await loading.awaitReady()
    }

    func testRemovedMediaDoesNotBlockNextRender() async throws {
        let loading = ShareImageLoading()
        let key = UUID()
        loading.register(key)
        loading.complete(key, success: false)
        do {
            try await loading.awaitReady()
            XCTFail("Failed media must block capture")
        } catch StatusShareRenderError.mediaUnavailable {
        }
        loading.remove(key)
        loading.complete(key, success: false)
        try await loading.awaitReady()
    }

    private func post(_ id: String, text: String) -> UiTimelineV2.Post {
        post(id, textBlocks: [text])
    }

    private func post(_ id: String, textBlocks: [String]) -> UiTimelineV2.Post {
        let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false,
                                    monospace: false, code: false, underline: false, small: false, time: false, imageLink: nil)
        let text = textBlocks.joined(separator: "\n\n")
        let rich = UiRichText(renderRuns: textBlocks.map {
            RenderContent.Text(runs: [RenderRun.Text(text: $0, style: style)], block: RenderBlockStyle())
        },
                              isRtl: false, raw: text, innerText: text, imageUrls: [])
        return UiTimelineV2.Post(platformId: "mastodon", images: [], sensitive: false, contentWarning: nil,
                                user: nil, platformIcon: .world, sourceLanguages: [], translationDisplayState: .hidden,
                                content: UiTranslatableText(original: rich, translation: nil), actions: [], poll: nil,
                                statusKey: MicroBlogKey(id: id, host: "example.invalid"), card: nil,
                                createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(),
                                emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil,
                                references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia,
                                accountType: AccountType.Guest.shared, itemKey: id)
    }

    private final class ReplyPickerState: NSObject, StatusSharePresenterState {
        let replies: [UiTimelineV2]
        var status: UiState<UiTimelineV2> { UiStateSuccess(data: replies[0]) }
        var selectedReplies: [UiTimelineV2] { Array(replies.prefix(1)) }
        var expandableReplies: [MicroBlogKey] { [] }
        var selectedCount: Int32 { 1 }
        var selecting: Bool { true }
        var loading: Bool { false }
        var hasMore: Bool { false }
        var error: KotlinThrowable? { nil }
        init(replies: [UiTimelineV2]) { self.replies = replies }
        func isSelected(key: MicroBlogKey) -> Bool { key == replies.first?.statusKey }
        func toggleReply(key: MicroBlogKey) {}
        func selectReplies() {}
        func clearSelection() {}
        func cancelSelection() {}
        func confirmSelection() {}
        func loadMore() {}
        func loadReplyChildren(key: MicroBlogKey) {}
    }
}
