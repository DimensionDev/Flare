import XCTest
import SwiftUI
import KotlinSharedUI
@testable import Flare
@testable import FlareAppleUI

@MainActor
final class StatusShareTests: XCTestCase {
    func testCachedPostPreviewRemainsVisibleWhenScreenshotMediaFails() async throws {
        let (cached, url) = try await cachedImagePost()
        defer { try? FileManager.default.removeItem(at: url) }
        let missing = UiMediaImage(url: "file:///nonexistent-flare-share-preview.png", proxy: ProxyRef.Global.shared)
        let root = post("cached", textBlocks: ["Cached post"], images: cached.images + [missing])
        try await withShareSheet(data: root) { host in
            for _ in 0..<100 {
                host.view.layoutIfNeeded()
                if greenPixels(in: host.view, attachOnFailure: false) > 1_000 { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            XCTAssertGreaterThan(greenPixels(in: host.view), 1_000,
                                 "Cached content must remain visible even when screenshot preparation cannot finish")
        }
    }

    func testCachedPostPreviewDoesNotDisappearWhenDetailRefreshes() async throws {
        let (root, url) = try await cachedImagePost()
        defer { try? FileManager.default.removeItem(at: url) }
        try await withShareSheet(data: root) { host in
            try await Task.sleep(for: .milliseconds(400))
            XCTAssertGreaterThan(greenPixels(in: host.view), 1_000)
            let refreshed = UiTimelineV2.TimelinePostItem(
                post: root,
                presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil),
                itemKey: "post_only_cached"
            )
            host.rootView = shareSheet(data: refreshed)
            for _ in 0..<4 {
                try await Task.sleep(for: .milliseconds(30))
                host.view.layoutIfNeeded()
                XCTAssertGreaterThan(greenPixels(in: host.view), 1_000,
                                     "Refreshing an unchanged cached post must not replace its preview with a spinner")
            }
        }
    }

    func testSharePreviewRespondsToShowingOriginalWithTranslation() async throws {
        let (cached, url) = try await cachedImagePost()
        defer { try? FileManager.default.removeItem(at: url) }
        let root = post(
            "translated", textBlocks: [Array(repeating: "Original text line", count: 30).joined(separator: "\n")],
            images: cached.images, translation: post("translation", text: "Translated text").content.original,
            translationDisplayState: .translated
        )
        try await withShareSheet(data: root) { host in
            try await Task.sleep(for: .milliseconds(400))
            let translatedOnly = greenPixels(in: host.view)
            XCTAssertGreaterThan(translatedOnly, 1_000)
            host.rootView = shareSheet(data: root, showOriginalWithTranslation: true)
            try await Task.sleep(for: .milliseconds(400))
            let withOriginal = greenPixels(in: host.view)
            XCTAssertGreaterThan(withOriginal, 100)
            XCTAssertLessThan(withOriginal, translatedOnly / 2,
                              "The original text must join the translated text and resize the live preview")
        }
    }

    func testScreenshotUsesTheCurrentFontSize() async throws {
        let root = post("font-size", text: Array(repeating: "A line of shared post text", count: 20).joined(separator: "\n"))
        let content = StatusShareContent(data: root, statusKey: root.statusKey, replies: []).frame(width: 360)
        let small = try await content.snapshot(colorScheme: .light, dynamicTypeSize: .small)
        let large = try await content.snapshot(colorScheme: .light, dynamicTypeSize: .xxxLarge)
        XCTAssertEqual(small.size.width, large.size.width)
        XCTAssertGreaterThan(large.size.height, small.size.height * 1.2,
                             "Exported text must use the font size from the current preview")
    }

    func testScreenshotKeepsEqualPaddingOnEveryEdge() async throws {
        let root = post("centered", text: "Centered post")
        let reply = post("reply", text: Array(repeating: "Reply line", count: 40).joined(separator: "\n"))
        for theme in [ColorScheme.light, .dark] {
            for replies: [UiTimelineV2] in [[], [reply]] {
                for padding in [CGFloat.zero, 64] {
                    let image = try await StatusShareContent(data: root, statusKey: root.statusKey, replies: replies)
                        .frame(width: 360)
                        .padding(16)
                        .background(Color(red: 1, green: 0, blue: 0))
                        .clipShape(.rect(cornerRadius: padding == 0 ? 0 : 16))
                        .shadow(radius: padding == 0 ? 0 : 8)
                        .padding(padding)
                        .background(Color(red: 0, green: 0, blue: 1))
                        .environment(\.colorScheme, theme)
                        .snapshot(colorScheme: theme)
                    let cgImage = try XCTUnwrap(image.cgImage)
                    let width = cgImage.width
                    let height = cgImage.height
                    var pixels = [UInt8](repeating: 0, count: width * height * 4)
                    let context = try XCTUnwrap(CGContext(data: &pixels, width: width, height: height,
                                                        bitsPerComponent: 8, bytesPerRow: width * 4,
                                                        space: CGColorSpaceCreateDeviceRGB(),
                                                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
                    func isCard(_ x: Int, _ y: Int) -> Bool {
                        let offset = (y * width + x) * 4
                        return pixels[offset] > 240 && pixels[offset + 1] < 15 && pixels[offset + 2] < 15
                    }
                    let columns = (0..<width).filter { isCard($0, height / 2) }
                    let rows = (0..<height).filter { isCard(width / 2, $0) }
                    // A 2x display can antialias the rounded card edge by one pixel in the 3x export.
                    let expected = padding * image.scale
                    XCTAssertEqual(CGFloat(try XCTUnwrap(columns.first)), expected, accuracy: 1, "Left padding")
                    XCTAssertEqual(CGFloat(width - (try XCTUnwrap(columns.last)) - 1), expected, accuracy: 1, "Right padding")
                    XCTAssertEqual(CGFloat(try XCTUnwrap(rows.first)), expected, accuracy: 1, "Top padding")
                    XCTAssertEqual(CGFloat(height - (try XCTUnwrap(rows.last)) - 1), expected, accuracy: 1, "Bottom padding")
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "share-padding-\(Int(padding))-replies-\(replies.count)-\(theme)"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                }
            }
        }
    }

    func testMissingPlatformImageLeavesLogoEmpty() async throws {
        let view = StatusTopEndView(frame: CGRect(x: 0, y: 0, width: 100, height: 20))
        let data = post("missing-icon", textBlocks: [""], platformIcon: IconType.Url(url: "file:///missing-platform-icon.png"))
        view.configure(post: data, showPlatformLogo: true, absoluteTimestamp: false, isDetail: true, showAgentInsight: false, onInsightTapped: nil)
        let logo = try XCTUnwrap(view.subviews.compactMap { $0 as? UIImageView }.first { !$0.isHidden && $0.isAccessibilityElement })
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertNil(logo.image, "A failed plugin image must not become a World icon")
        XCTAssertEqual(view.intrinsicContentSize, CGSize(width: 14, height: 14))
    }

    func testPlatformLogoLoadsLocalImageOnceAndKeepsLayoutStable() async throws {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent("platform-\(UUID().uuidString).png")
        let source = UIGraphicsImageRenderer(size: CGSize(width: 512, height: 512)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 512, height: 512))
        }
        try XCTUnwrap(source.pngData()).write(to: path)
        defer { try? FileManager.default.removeItem(at: path) }
        let view = StatusTopEndView(frame: CGRect(x: 0, y: 0, width: 100, height: 20))
        let data = post("icon", textBlocks: [""], platformIcon: IconType.Url(url: path.absoluteString))
        view.configure(post: data, showPlatformLogo: true, absoluteTimestamp: false, isDetail: true, showAgentInsight: false, onInsightTapped: nil)
        let logo = try XCTUnwrap(view.subviews.compactMap { $0 as? UIImageView }.first { !$0.isHidden && $0.isAccessibilityElement })
        XCTAssertNil(logo.image)
        let size = view.sizeThatFits(CGSize(width: 100, height: 20))
        for _ in 0..<100 {
            if logo.image != nil { break }
            try await Task.sleep(for: .milliseconds(20))
        }
        let image = try XCTUnwrap(logo.image)
        XCTAssertEqual(image.size.width, 14, accuracy: 1)
        XCTAssertLessThanOrEqual(try XCTUnwrap(image.cgImage).width, Int(14 * view.traitCollection.displayScale) + 1)
        XCTAssertEqual(view.sizeThatFits(CGSize(width: 100, height: 20)), size)
        view.configure(post: data, showPlatformLogo: true, absoluteTimestamp: false, isDetail: true, showAgentInsight: false, onInsightTapped: nil)
        XCTAssertTrue(image === logo.image, "Rebinding an unchanged icon must not restart image loading")
        view.configure(post: data, showPlatformLogo: false, absoluteTimestamp: false, isDetail: true, showAgentInsight: false, onInsightTapped: nil)
        XCTAssertTrue(logo.isHidden)
        XCTAssertNil(logo.image)
    }

    func testReusingPlatformLogoCancelsThePreviousImageAndKeepsTheBuiltinIcon() async throws {
        let view = StatusTopEndView(frame: CGRect(x: 0, y: 0, width: 100, height: 20))
        let loading = post("first", textBlocks: [""], platformIcon: IconType.Url(url: "file:///missing-platform-icon.png"))
        view.configure(post: loading, showPlatformLogo: true, absoluteTimestamp: false, isDetail: true, showAgentInsight: false, onInsightTapped: nil)
        let builtin = post("second", textBlocks: [""], platformIcon: IconType.Material(icon: .mastodon))
        view.configure(post: builtin, showPlatformLogo: true, absoluteTimestamp: false, isDetail: true, showAgentInsight: false, onInsightTapped: nil)
        let logo = try XCTUnwrap(view.subviews.compactMap { $0 as? UIImageView }.first { !$0.isHidden && $0.isAccessibilityElement })
        let image = try XCTUnwrap(logo.image)
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertTrue(image === logo.image, "A cancelled image failure must not overwrite the new icon")
        XCTAssertNotNil(logo.accessibilityLabel)
    }

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

    private func post(_ id: String, textBlocks: [String], platformIcon: IconType = IconType.Material(icon: .world), images: [UiMedia] = [],
                      translation: UiRichText? = nil, translationDisplayState: TranslationDisplayState = .hidden) -> UiTimelineV2.Post {
        let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false,
                                    monospace: false, code: false, underline: false, small: false, time: false, imageLink: nil)
        let text = textBlocks.joined(separator: "\n\n")
        let rich = UiRichText(renderRuns: textBlocks.map {
            RenderContent.Text(runs: [RenderRun.Text(text: $0, style: style)], block: RenderBlockStyle())
        },
                              isRtl: false, raw: text, innerText: text, imageUrls: [])
        return UiTimelineV2.Post(platformId: "mastodon", images: images, sensitive: false, contentWarning: nil,
                                user: nil, platformIcon: platformIcon, sourceLanguages: [], translationDisplayState: translationDisplayState,
                                content: UiTranslatableText(original: rich, translation: translation), actions: [], poll: nil,
                                statusKey: MicroBlogKey(id: id, host: "example.invalid"), card: nil,
                                createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(),
                                emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil,
                                references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia,
                                accountType: AccountType.Guest.shared, itemKey: id, pluginId: nil, authorSubtitleInternal: nil)
    }

    private final class ReplyPickerState: NSObject, StatusSharePresenterState {
        let replies: [UiTimelineV2]
        var status: UiState<UiTimelineV2> { UiStateSuccess(data: replies[0]) }
        var selectedReplies: [UiTimelineV2] { selecting ? Array(replies.prefix(1)) : [] }
        var expandableReplies: [MicroBlogKey] { [] }
        var selectedCount: Int32 { Int32(selectedReplies.count) }
        let selecting: Bool
        var loading: Bool { false }
        var hasMore: Bool { false }
        var error: KotlinThrowable? { nil }
        init(replies: [UiTimelineV2], selecting: Bool = true) {
            self.replies = replies
            self.selecting = selecting
        }
        func isSelected(key: MicroBlogKey) -> Bool { key == replies.first?.statusKey }
        func toggleReply(key: MicroBlogKey) {}
        func selectReplies() {}
        func clearSelection() {}
        func cancelSelection() {}
        func confirmSelection() {}
        func loadMore() {}
        func loadReplyChildren(key: MicroBlogKey) {}
    }

    private func cachedImagePost() async throws -> (UiTimelineV2.Post, URL) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("share-preview-\(UUID().uuidString).png")
        let image = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64)).image { context in
            UIColor.green.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 64, height: 64))
        }
        try XCTUnwrap(image.pngData()).write(to: url)
        let media = UiMediaImage(url: url.absoluteString, previewUrl: url.absoluteString,
                                description: nil, height: 64, width: 64, sensitive: false,
                                customHeaders: nil, proxy: ProxyRef.Global.shared)
        _ = try await NetworkImage(data: media).frame(width: 64, height: 64).snapshot(colorScheme: .light)
        return (post("cached", textBlocks: ["Cached post"], images: [media]), url)
    }

    private func shareSheet(data: UiTimelineV2, showOriginalWithTranslation: Bool = false) -> AnyView {
        let config = AppSettings.companion.default.translateConfig
        return AnyView(StatusShareSheetContent(
            statusKey: data.statusKey, shareUrl: "https://example.invalid/post", fxShareUrl: nil,
            fixvxShareUrl: nil, onCrossPost: { _ in }, state: ReplyPickerState(replies: [data], selecting: false)
        ).environment(\.colorScheme, .light)
            .environment(\.translateConfig, config.doCopy(
                preTranslate: config.preTranslate, provider: config.provider,
                autoTranslateExcludedLanguages: config.autoTranslateExcludedLanguages,
                preferPlatformTranslation: config.preferPlatformTranslation,
                showOriginalWithTranslation: showOriginalWithTranslation
            )))
    }

    private func withShareSheet(data: UiTimelineV2, operation: (UIHostingController<AnyView>) async throws -> Void) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let host = UIHostingController(rootView: shareSheet(data: data))
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.layoutIfNeeded()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousWindow?.makeKey()
        }
        try await operation(host)
    }

    private func greenPixels(in view: UIView, attachOnFailure: Bool = true) -> Int {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(bounds: view.bounds, format: format).image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        guard let cgImage = image.cgImage else { return 0 }
        var pixels = [UInt8](repeating: 0, count: cgImage.width * cgImage.height * 4)
        let context = CGContext(data: &pixels, width: cgImage.width, height: cgImage.height,
                                bitsPerComponent: 8, bytesPerRow: cgImage.width * 4,
                                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))
        // Cached images still fade in; identify the fixture by hue rather than final brightness.
        let count = stride(from: 0, to: pixels.count, by: 4).filter {
            Int(pixels[$0 + 1]) > Int(pixels[$0]) + 20 && Int(pixels[$0 + 1]) > Int(pixels[$0 + 2]) + 20
        }.count
        if attachOnFailure && count < 1_000 {
            let attachment = XCTAttachment(image: image)
            attachment.name = "share-preview"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        return count
    }
}
