import SwiftUI
@preconcurrency import KotlinSharedUI

public class PlatformTextContent: NSObject {}

public final class PlatformTextTextContent: PlatformTextContent {
    public let runs: [PlatformTextRun]
    public let alignment: TextAlignment?
    public let isBlockQuote: Bool

    init(runs: [PlatformTextRun], alignment: TextAlignment?, isBlockQuote: Bool) {
        self.runs = runs
        self.alignment = alignment
        self.isBlockQuote = isBlockQuote
        super.init()
    }
}

public final class PlatformTextBlockImageContent: PlatformTextContent {
    public let url: String
    public let href: String?

    init(url: String, href: String?) {
        self.url = url
        self.href = href
        super.init()
    }
}

public class PlatformTextRun: NSObject {}

public final class PlatformTextAttributedRun: PlatformTextRun {
    public let text: AttributedString

    init(text: AttributedString) {
        self.text = text
        super.init()
    }
}

public final class PlatformTextImageRun: PlatformTextRun {
    public let url: String
    public let alt: String

    init(url: String, alt: String) {
        self.url = url
        self.alt = alt
        super.init()
    }
}

public final class PlatformTextRenderer: SwiftPlatformTextRenderer {
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
        var attributedBuffer = AttributedString()

        func commitAttributedBuffer() {
            guard !attributedBuffer.characters.isEmpty else { return }
            renderedRuns.append(PlatformTextAttributedRun(text: attributedBuffer))
            attributedBuffer = AttributedString()
        }

        for index in 0..<runCount {
            let run = content.runs[index]

            switch run {
            case let textRun as RenderRun.Text:
                attributedBuffer += AttributedString(
                    textRun.text,
                    attributes: attributes(
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
                isBlockQuote: content.block.isBlockQuote
            )
        )
    }

    func appendBlockImage(url: String, href: String?) {
        contents.append(PlatformTextBlockImageContent(url: url, href: href))
    }

    private func attributes(
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
