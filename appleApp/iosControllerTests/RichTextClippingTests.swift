import XCTest
import UIKit
import SwiftUI
import KotlinSharedUI
@testable import Flare
@testable import FlareAppleUI

@MainActor
final class RichTextClippingTests: XCTestCase {
    func testUIKitPreviewDoesNotClipLastLineInk() throws {
        let fixture = try Canvas()
        let quote = RenderBlockStyle(headingLevel: nil, textAlignment: nil,
                                    isListItem: false, isBlockQuote: true, isFigCaption: false)
        for category in [UIContentSizeCategory.medium, .large, .accessibilityExtraExtraExtraLarge] {
            for link in [false, true] {
                for block in [RenderBlockStyle(), quote] {
                    let view = RichTextUIView()
                    fixture.view.addSubview(view)
                    view.configure(text: text([lines(30)], link: link, block: block), lineLimit: 5,
                                   isTextSelectionEnabled: false, onOpenURL: nil,
                                   preferredContentSizeCategory: category, collapseAboveLineCount: 15)
                    view.prepareForFitting(width: 340)
                    view.frame = CGRect(x: 20, y: 20, width: 340, height: view.timelineHeight(for: 340)!)
                    view.layoutIfNeeded()
                    XCTAssertTrue(view.hasCollapsedOverflow(for: 340))
                    let actual = fixture.ink()
                    view.clipsToBounds = false
                    let reference = fixture.ink()
                    XCTAssertEqual(missingInk(actual, reference: reference), 0,
                                   "\(category.rawValue), link=\(link), quote=\(block.isBlockQuote)")
                    view.removeFromSuperview()
                }
            }
        }
    }

    func testSwiftUIPreviewPreservesWholeLinesAcrossBlocksAndExpansion() async throws {
        let fixture = try Canvas()
        let quote = RenderBlockStyle(headingLevel: nil, textAlignment: nil,
                                    isListItem: false, isBlockQuote: true, isFigCaption: false)
        let samples: [(String, UiRichText, UiRichText)] = [
            ("plain", text([lines(20)]), text([lines(5)])),
            ("link", text([lines(20)], link: true), text([lines(5)], link: true)),
            ("invalid image", text([lines(20)], invalidImage: true), text([lines(5)])),
            ("quote", text([lines(80)], block: quote), text([lines(5)], block: quote)),
            ("paragraphs", text(Array(repeating: lines(1), count: 20)), text(Array(repeating: lines(1), count: 5))),
            ("split block", text([lines(2), lines(20)]), text([lines(2), lines(3)])),
        ]
        for category in [ContentSizeCategory.medium, .large, .accessibilityExtraExtraExtraLarge] {
            for (name, full, preview) in samples {
                let reference = await fixture.swiftUI(RichText(text: preview), category: category)
                let actual = await fixture.swiftUI(CollapsibleRichText(
                    text: full, lineLimit: 5, collapseThreshold: 15, isExpanded: false,
                    isTextSelectionEnabled: true, onOverflowChanged: { _ in }
                ), category: category)
                XCTAssertEqual(missingInk(actual, reference: reference), 0, "\(name), \(category)")
                XCTAssertLessThanOrEqual(actual.lastIndex(of: true) ?? 0,
                                         (reference.lastIndex(of: true) ?? 0) + fixture.pixelWidth,
                                         "Preview must not expose a sixth line: \(name), \(category)")
            }
        }

        var overflow = false
        let short = text([lines(8)])
        let reference = await fixture.swiftUI(RichText(text: short))
        let actual = await fixture.swiftUI(CollapsibleRichText(
            text: short, lineLimit: 5, collapseThreshold: 15, isExpanded: false,
            isTextSelectionEnabled: false, onOverflowChanged: { overflow = $0 }
        ))
        XCTAssertFalse(overflow)
        XCTAssertEqual(missingInk(actual, reference: reference), 0)

        let full = text([lines(16)])
        let foldedReference = await fixture.swiftUI(RichText(text: text([lines(5)])))
        let expandedReference = await fixture.swiftUI(RichText(text: full))
        for expanded in [false, true, false] {
            let actual = await fixture.swiftUI(CollapsibleRichText(
                text: full, lineLimit: 5, collapseThreshold: 15, isExpanded: expanded,
                isTextSelectionEnabled: true, onOverflowChanged: { overflow = $0 }
            ), reuse: true)
            XCTAssertEqual(overflow, !expanded)
            let reference = expanded ? expandedReference : foldedReference
            XCTAssertEqual(missingInk(actual, reference: reference), 0)
            XCTAssertLessThanOrEqual(actual.lastIndex(of: true) ?? 0,
                                     (reference.lastIndex(of: true) ?? 0) + fixture.pixelWidth)
        }
    }

    private func lines(_ count: Int) -> String {
        Array(repeating: "中文边界", count: count).joined(separator: "\n")
    }

    private func text(_ paragraphs: [String], link: Bool = false,
                      block: RenderBlockStyle = RenderBlockStyle(), invalidImage: Bool = false) -> UiRichText {
        let style = RenderTextStyle(link: link ? "https://example.invalid" : nil, bold: false,
                                    italic: false, strikethrough: false, monospace: false,
                                    code: false, underline: false, small: false, time: false)
        var blocks: [RenderContent] = paragraphs.map {
            RenderContent.Text(runs: [RenderRun.Text(text: $0, style: style)], block: block)
        }
        if invalidImage { blocks.insert(RenderContent.BlockImage(url: "https://[", href: nil, proxy: ProxyRef.Global.shared), at: 0) }
        let raw = paragraphs.joined(separator: "\n")
        return UiRichText(renderRuns: blocks, isRtl: false, raw: raw, innerText: raw, imageUrls: [])
    }

    private func missingInk(_ actual: [Bool], reference: [Bool]) -> Int {
        zip(actual, reference).reduce(0) { $0 + ($1.1 && !$1.0 ? 1 : 0) }
    }

    @MainActor private final class Canvas {
        let window: UIWindow
        let controller = UIViewController()
        let view = UIView(frame: CGRect(x: 0, y: 0, width: 380, height: 700))
        let previousKeyWindow: UIWindow?
        private var reusedHost: UIHostingController<AnyView>?
        var pixelWidth: Int { Int(view.bounds.width * window.screen.scale) }

        init() throws {
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
            window.overrideUserInterfaceStyle = .light
            window.rootViewController = controller
            window.makeKeyAndVisible()
            view.backgroundColor = .white
            controller.view.addSubview(view)
        }

        deinit {
            MainActor.assumeIsolated {
                window.isHidden = true
                window.rootViewController = nil
                previousKeyWindow?.makeKey()
            }
        }

        func swiftUI<V: View>(_ content: V, category: ContentSizeCategory = .large,
                             reuse: Bool = false) async -> [Bool] {
            let root = AnyView(content
                .environment(\.sizeCategory, category).environment(\.colorScheme, .light)
                .font(.body).foregroundStyle(.black)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: 340, alignment: .topLeading)
                .frame(maxHeight: .infinity, alignment: .topLeading).ignoresSafeArea())
            let host = reusedHost ?? UIHostingController(rootView: root)
            host.rootView = root
            if host.parent == nil {
                controller.addChild(host)
                host.view.frame = CGRect(x: 20, y: 20, width: 340, height: 680)
                host.view.backgroundColor = .clear
                view.addSubview(host.view)
                host.didMove(toParent: controller)
            }
            for _ in 0..<8 {
                host.view.setNeedsLayout()
                host.view.layoutIfNeeded()
                try? await Task.sleep(for: .milliseconds(25))
            }
            let pixels = ink()
            if reuse {
                reusedHost = host
            } else {
                host.willMove(toParent: nil)
                host.view.removeFromSuperview()
                host.removeFromParent()
            }
            return pixels
        }

        func ink() -> [Bool] {
            view.layoutIfNeeded()
            let format = UIGraphicsImageRendererFormat()
            format.scale = window.screen.scale
            format.opaque = true
            let image = UIGraphicsImageRenderer(size: view.bounds.size, format: format).image { _ in
                view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
            }.cgImage!
            let width = image.width, height = image.height
            var bytes = [UInt8](repeating: 0, count: width * height * 4)
            bytes.withUnsafeMutableBytes { ptr in
                let context = CGContext(data: ptr.baseAddress, width: width, height: height,
                    bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            }
            // Ignore the native truncation ellipsis at the trailing edge.
            let leadingWidth = Int(100 * format.scale)
            let pixels = (0..<(width * height)).map { pixel in
                let i = pixel * 4
                return pixel % width < leadingWidth && bytes[i + 3] > 128
                    && (bytes[i] < 140 || bytes[i + 1] < 140 || bytes[i + 2] < 140)
            }
            XCTAssertTrue(pixels.contains(true), "Snapshot must contain rendered text")
            return pixels
        }
    }
}
