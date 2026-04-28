import SwiftUI
import UIKit
@preconcurrency import KotlinSharedUI

class PlatformTextContent: NSObject {}

final class PlatformTextTextContent: PlatformTextContent {
    let runs: [PlatformTextRun]
    let alignment: TextAlignment?
    let isBlockQuote: Bool
    /// `true` when any run carries a tappable link. UIKit renderers use this
    /// to decide whether the block needs a `UITextView` (link interaction)
    /// or can fall back to a plain `UILabel`.
    let hasLink: Bool
    /// `true` when any run is an inline image (custom emoji, inline image).
    /// `UILabel` *can* render `NSTextAttachment`, but to keep the fast path
    /// simple and predictable we still route these blocks through
    /// `UITextView`.
    let hasInlineImage: Bool

    init(
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

final class PlatformTextBlockImageContent: PlatformTextContent {
    let url: String
    let href: String?

    init(url: String, href: String?) {
        self.url = url
        self.href = href
        super.init()
    }
}

class PlatformTextRun: NSObject {}

final class PlatformTextAttributedRun: PlatformTextRun {
    let attributedText: NSAttributedString
    let text: AttributedString

    init(attributedText: NSAttributedString, text: AttributedString) {
        self.attributedText = attributedText
        self.text = text
        super.init()
    }
}

final class PlatformTextStyleDescriptor: NSObject {
    let link: String?
    let bold: Bool
    let italic: Bool
    let strikethrough: Bool
    let monospace: Bool
    let code: Bool
    let underline: Bool
    let small: Bool
    let time: Bool
    let headingLevel: Int?
    let isBlockQuote: Bool
    let isFigCaption: Bool

    init(style: RenderTextStyle, block: RenderBlockStyle) {
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

extension NSAttributedString.Key {
    static let platformTextStyleDescriptor = NSAttributedString.Key("dev.dimension.flare.platformTextStyleDescriptor")
}

final class PlatformTextImageRun: PlatformTextRun {
    let url: String
    let alt: String

    init(url: String, alt: String) {
        self.url = url
        self.alt = alt
        super.init()
    }
}

final class PlatformTextRenderer: SwiftPlatformTextRenderer {
    static let shared = PlatformTextRenderer()

    private init() {}

    func render(renderRuns: [RenderContent]) -> [Any] {
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

        var font = Font.system(size: style.small ? UIFont.smallSystemFontSize : UIFont.systemFontSize)
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
}
