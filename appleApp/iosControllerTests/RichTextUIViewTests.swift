import XCTest
import UIKit
import KotlinSharedUI
@testable import Flare

@MainActor
final class RichTextUIViewTests: XCTestCase {
    func testCollapsedTextBoundsRenderingAndRestoresContentAcrossUpdates() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        let host = UIViewController()
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        let view = RichTextUIView()
        host.view.addSubview(view)

        func configure(_ text: UiRichText, expanded: Bool = false, width: CGFloat = 340,
                       category: UIContentSizeCategory = .medium) {
            view.configure(text: text, lineLimit: expanded ? nil : 5,
                           isTextSelectionEnabled: false, onOpenURL: nil,
                           preferredContentSizeCategory: category,
                           collapseAboveLineCount: expanded ? nil : 15)
            view.prepareForFitting(width: width)
            view.frame = CGRect(x: 0, y: 0, width: width, height: view.timelineHeight(for: width)!)
            view.layoutIfNeeded()
        }

        for link in [false, true] {
            for category in [UIContentSizeCategory.medium, .accessibilityExtraExtraExtraLarge] {
                for count in [5, 15, 16] {
                    let text = richText([Array(repeating: "字", count: count).joined(separator: "\n")], link: link)
                    configure(text, category: category)
                    XCTAssertEqual(view.hasCollapsedOverflow(for: 340), count > 15)
                }
            }

            let text = richText([String(repeating: "Long text 中文 👩🏽‍💻 with wrapping. ", count: 4_000)], link: link)
            configure(text)
            XCTAssertTrue(view.hasCollapsedOverflow(for: 340))
            let renderer = try XCTUnwrap(textViews(in: view).first)
            XCTAssertLessThan(renderedText(in: view).utf16.count, 2_000)
            if let label = renderer as? UILabel {
                XCTAssertEqual(label.numberOfLines, 5)
            } else if let textView = renderer as? UITextView {
                XCTAssertEqual(textView.textContainer.maximumNumberOfLines, 5)
            }
            configure(text)
            XCTAssertTrue(textViews(in: view).first === renderer)
            configure(richText([text.innerText], link: link))
            XCTAssertTrue(textViews(in: view).first === renderer)
            view.onOpenURL = { _ in }
            _ = view.timelineHeight(for: 340)
            XCTAssertTrue(textViews(in: view).first === renderer)

            configure(text, expanded: true)
            XCTAssertFalse(view.hasCollapsedOverflow(for: 340))
            XCTAssertEqual(renderedText(in: view), text.innerText)
            configure(text, width: 160)
            XCTAssertTrue(view.hasCollapsedOverflow(for: 160))
            XCTAssertLessThan(renderedText(in: view).utf16.count, 2_000)

            let oldHeight = view.timelineHeight(for: 160)!
            configure(text, width: 160, category: .accessibilityExtraExtraExtraLarge)
            XCTAssertGreaterThan(view.timelineHeight(for: 160)!, oldHeight)

            // Translation/reuse replaces the body even when the previous one was folded.
            let replacement = richText(["replacement ترجمة"], link: link)
            configure(replacement)
            XCTAssertEqual(renderedText(in: view), replacement.innerText)
            XCTAssertFalse(view.hasCollapsedOverflow(for: 340))
        }

        let paragraphs = richText((0..<100).map { "Paragraph \($0)" }, link: true)
        configure(paragraphs)
        XCTAssertTrue(view.hasCollapsedOverflow(for: 340))
        XCTAssertEqual(textViews(in: view).count, 5)
        configure(paragraphs, expanded: true)
        XCTAssertEqual(textViews(in: view).count, 100)

        let wrapping = richText([String(repeating: "中文 text ", count: 35)], link: false)
        configure(wrapping, width: 80)
        XCTAssertTrue(view.hasCollapsedOverflow(for: 80))
        view.prepareForFitting(width: 1_000)
        XCTAssertFalse(view.hasCollapsedOverflow(for: 1_000))
        XCTAssertEqual(renderedText(in: view), wrapping.innerText)

        let quote = RenderBlockStyle(headingLevel: nil, textAlignment: nil,
                                     isListItem: false, isBlockQuote: true, isFigCaption: false)
        let quotedText = richText([String(repeating: "quoted 中文 👩🏽‍💻 text ", count: 200)], link: true, block: quote)
        configure(quotedText)
        XCTAssertTrue(view.hasCollapsedOverflow(for: 340))
        XCTAssertLessThan(renderedText(in: view).utf16.count, 2_000)
        XCTAssertTrue(quotedText.innerText.hasPrefix(renderedText(in: view)))
        configure(quotedText, expanded: true)
        XCTAssertEqual(renderedText(in: view), quotedText.innerText)

        let longText = String(repeating: "text 中文 ", count: 500)
        let emojiURL = "https://[" // Invalid URL keeps this rendering check offline.
        let plainRun = RenderRun.Text(text: longText, style: RenderTextStyle(
            link: nil, bold: false, italic: false, strikethrough: false,
            monospace: false, code: false, underline: false, small: false, time: false
        ))
        let emoji = RenderRun.Image(url: emojiURL, alt: "emoji")
        for runs in [[plainRun, emoji] as [RenderRun], [emoji, plainRun]] {
            let source = UiRichText(renderRuns: [RenderContent.Text(runs: runs, block: RenderBlockStyle())],
                                    isRtl: false, raw: longText, innerText: longText, imageUrls: [emojiURL])
            configure(source)
            XCTAssertLessThan(renderedText(in: view).utf16.count, 2_000)
            // An attachment beyond the preview must not force a UITextView.
            XCTAssertEqual(textViews(in: view).first is UITextView, runs.first is RenderRun.Image)
            configure(source, expanded: true)
            let renderer = try XCTUnwrap(textViews(in: view).first as? UITextView)
            XCTAssertEqual(renderer.attributedText.length, longText.utf16.count + 1)
        }
    }

    private func richText(_ paragraphs: [String], link: Bool, block: RenderBlockStyle = RenderBlockStyle()) -> UiRichText {
        let style = RenderTextStyle(link: link ? "https://example.invalid" : nil, bold: false,
                                    italic: false, strikethrough: false, monospace: false,
                                    code: false, underline: false, small: false, time: false)
        let blocks = paragraphs.map {
            RenderContent.Text(runs: [RenderRun.Text(text: $0, style: style)], block: block)
        }
        let text = paragraphs.joined()
        return UiRichText(renderRuns: blocks, isRtl: false, raw: text, innerText: text, imageUrls: [])
    }

    private func textViews(in view: UIView) -> [UIView] {
        if view is UILabel || view is UITextView { return [view] }
        return view.subviews.flatMap { textViews(in: $0) }
    }

    private func renderedText(in view: UIView) -> String {
        textViews(in: view).map {
            ($0 as? UILabel)?.attributedText?.string ?? ($0 as? UITextView)?.attributedText.string ?? ""
        }.joined()
    }
}
