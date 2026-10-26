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

    func testOversizedScreenshotFailsBeforeRasterAllocation() async throws {
        do {
            _ = try await Color.white.frame(width: 360, height: 20_000).snapshot(colorScheme: .light)
            XCTFail("Oversized image must fail")
        } catch StatusShareRenderError.tooLarge {
        }
    }

    func testFailedMediaCannotBeSharedAsAPlaceholder() async throws {
        do {
            _ = try await NetworkImage(data: "file:///nonexistent-flare-share-image.png")
                .frame(width: 120, height: 120).snapshot(colorScheme: .light)
            XCTFail("Unavailable media must fail")
        } catch StatusShareRenderError.mediaUnavailable {
        }
    }

    func testMissingOptionalImageDoesNotBlockScreenshot() async throws {
        let image = try await NetworkImage(data: Optional<String>.none)
            .frame(width: 120, height: 120).snapshot(colorScheme: .light)
        XCTAssertEqual(image.size.width, 120)
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
        let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false,
                                    monospace: false, code: false, underline: false, small: false, time: false)
        let rich = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)],
                                                            block: RenderBlockStyle())],
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
}
