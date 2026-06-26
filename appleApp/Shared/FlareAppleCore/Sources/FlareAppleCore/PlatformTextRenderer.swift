import Foundation
@preconcurrency import KotlinSharedUI
import SwiftUI

#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

public class PlatformTextContent: NSObject {}

public final class PlatformTextTextContent: PlatformTextContent {
    public let runs: [PlatformTextRun]
    public let alignment: TextAlignment?
    public let isBlockQuote: Bool
    /// `true` when any run carries a tappable link. UIKit renderers use this
    /// to decide whether the block needs a `UITextView` (link interaction)
    /// or can fall back to a plain `UILabel`.
    public let hasLink: Bool
    /// `true` when any run is an inline image (custom emoji, inline image).
    /// `UILabel` can render `NSTextAttachment`, but to keep the fast path
    /// simple and predictable we still route these blocks through `UITextView`.
    public let hasInlineImage: Bool

    public init(
        runs: [PlatformTextRun],
        alignment: TextAlignment?,
        isBlockQuote: Bool,
        hasLink: Bool,
        hasInlineImage: Bool
    ) {
        self.runs = runs
        self.alignment = alignment
        self.isBlockQuote = isBlockQuote
        self.hasLink = hasLink
        self.hasInlineImage = hasInlineImage
        super.init()
    }
}

public final class PlatformTextBlockImageContent: PlatformTextContent {
    public let url: String
    public let href: String?

    public init(url: String, href: String?) {
        self.url = url
        self.href = href
        super.init()
    }
}

public class PlatformTextRun: NSObject {}

public final class PlatformTextAttributedRun: PlatformTextRun {
    public let attributedText: NSAttributedString
    public let text: AttributedString

    public init(attributedText: NSAttributedString, text: AttributedString) {
        self.attributedText = attributedText
        self.text = text
        super.init()
    }
}

public final class PlatformTextStyleDescriptor: NSObject {
    public let link: String?
    public let bold: Bool
    public let italic: Bool
    public let strikethrough: Bool
    public let monospace: Bool
    public let code: Bool
    public let underline: Bool
    public let small: Bool
    public let time: Bool
    public let headingLevel: Int?
    public let isBlockQuote: Bool
    public let isFigCaption: Bool

    public init(style: RenderTextStyle, block: RenderBlockStyle) {
        link = style.link
        bold = style.bold
        italic = style.italic
        strikethrough = style.strikethrough
        monospace = style.monospace
        code = style.code
        underline = style.underline
        small = style.small
        time = style.time
        headingLevel = block.headingLevel?.intValue
        isBlockQuote = block.isBlockQuote
        isFigCaption = block.isFigCaption
        super.init()
    }
}

public extension NSAttributedString.Key {
    static let platformTextStyleDescriptor = NSAttributedString.Key("dev.dimension.flare.platformTextStyleDescriptor")
}

public final class PlatformTextImageRun: PlatformTextRun {
    public let url: String
    public let alt: String

    public init(url: String, alt: String) {
        self.url = url
        self.alt = alt
        super.init()
    }
}

public final class PlatformTextRenderer: SwiftPlatformTextRenderer, @unchecked Sendable {
    public static let shared = PlatformTextRenderer()

    private init() {}

    public func render(renderRuns: [RenderContent]) -> [Any] {
        dispatchPrecondition(condition: .onQueue(.main))

        let context = RenderContext()
        let count = renderRuns.count

        for index in 0..<count {
            let content = renderRuns[index]

            switch content {
            case let textContent as RenderContent.Text:
                context.appendTextContent(textContent)
            case let blockImage as RenderContent.BlockImage:
                context.appendBlockImage(
                    url: blockImage.url,
                    href: blockImage.href
                )
            default:
                continue
            }
        }

        return context.contents
    }
}

private final class RenderContext {
    var contents: [PlatformTextContent] = []

    func appendTextContent(_ content: RenderContent.Text) {
        let runCount = content.runs.count
        var renderedRuns: [PlatformTextRun] = []
        let attributedBuffer = NSMutableAttributedString()
        var swiftBuffer = AttributedString()

        func commitAttributedBuffer() {
            guard attributedBuffer.length > 0 else { return }
            renderedRuns.append(
                PlatformTextAttributedRun(
                    attributedText: attributedBuffer.copy() as! NSAttributedString,
                    text: swiftBuffer
                )
            )
            attributedBuffer.deleteCharacters(in: NSRange(location: 0, length: attributedBuffer.length))
            swiftBuffer = AttributedString()
        }

        for index in 0..<runCount {
            let run = content.runs[index]

            switch run {
            case let textRun as RenderRun.Text:
                attributedBuffer.append(
                    NSAttributedString(
                        string: textRun.text,
                        attributes: templateAttributes(
                            for: textRun.style,
                            block: content.block
                        )
                    )
                )
                swiftBuffer += AttributedString(
                    textRun.text,
                    attributes: swiftAttributes(
                        for: textRun.style,
                        block: content.block
                    )
                )
            case let imageRun as RenderRun.Image:
                commitAttributedBuffer()
                renderedRuns.append(
                    PlatformTextImageRun(
                        url: imageRun.url,
                        alt: imageRun.alt
                    )
                )
            default:
                continue
            }
        }

        commitAttributedBuffer()

        guard !renderedRuns.isEmpty else { return }
        contents.append(
            PlatformTextTextContent(
                runs: renderedRuns,
                alignment: textAlignment(from: content.block),
                isBlockQuote: content.block.isBlockQuote,
                hasLink: content.hasLink,
                hasInlineImage: content.hasInlineImage
            )
        )
    }

    func appendBlockImage(url: String, href: String?) {
        contents.append(PlatformTextBlockImageContent(url: url, href: href))
    }

    private func templateAttributes(
        for style: RenderTextStyle,
        block: RenderBlockStyle
    ) -> [NSAttributedString.Key: Any] {
        var attributes: [NSAttributedString.Key: Any] = [
            .platformTextStyleDescriptor: PlatformTextStyleDescriptor(style: style, block: block),
        ]
        if let link = style.link, let url = URL(string: link) {
            attributes[.link] = url
        }
        return attributes
    }

    private func swiftAttributes(
        for style: RenderTextStyle,
        block: RenderBlockStyle
    ) -> AttributeContainer {
        var container = AttributeContainer()
        if let font = font(for: style, block: block) {
            container.font = font
        }

        if let link = style.link, let url = URL(string: link) {
            container.link = url
        }
        if style.strikethrough {
            container.strikethroughStyle = .single
        }
        if style.underline {
            container.underlineStyle = .single
        }
        if block.isBlockQuote {
            container.foregroundColor = .secondary
        }
        if style.time {
            container.foregroundColor = .secondary
            container.backgroundColor = .secondary.opacity(0.08)
        }

        return container
    }

    private func font(
        for style: RenderTextStyle,
        block: RenderBlockStyle
    ) -> Font? {
        if let headingLevel = block.headingLevel {
            switch headingLevel.intValue {
            case 1:
                return .title
            case 2:
                return .title2
            case 3:
                return .title3
            case 4:
                return .headline
            case 5:
                return .subheadline
            default:
                return .body
            }
        }

        if style.code || style.monospace {
            return .system(.body, design: .monospaced)
        }

        guard style.small || style.bold || style.italic || block.isBlockQuote || block.isFigCaption else {
            return nil
        }

        var font = Font.system(size: style.small ? Self.platformSmallSystemFontSize : Self.platformSystemFontSize)
        if style.bold {
            font = font.weight(.bold)
        }
        if style.italic || block.isBlockQuote || block.isFigCaption {
            font = font.italic()
        }
        return font
    }

    private func textAlignment(from block: RenderBlockStyle) -> TextAlignment? {
        switch block.textAlignment {
        case .center:
            return .center
        default:
            return nil
        }
    }

    private static var platformSmallSystemFontSize: CGFloat {
        #if canImport(UIKit)
        return UIFont.smallSystemFontSize
        #elseif canImport(AppKit)
        return NSFont.smallSystemFontSize
        #else
        return 13
        #endif
    }

    private static var platformSystemFontSize: CGFloat {
        #if canImport(UIKit)
        return UIFont.systemFontSize
        #elseif canImport(AppKit)
        return NSFont.systemFontSize
        #else
        return 17
        #endif
    }
}
