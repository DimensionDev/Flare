import SwiftUI
@preconcurrency import KotlinSharedUI

class PlatformTextContent: NSObject {}

final class PlatformTextTextContent: PlatformTextContent {
    let runs: [PlatformTextRun]

    init(runs: [PlatformTextRun]) {
        self.runs = runs
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
    let text: AttributedString

    init(text: AttributedString) {
        self.text = text
        super.init()
    }
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

    func render(richText: UiRichText) -> [Any] {
        let context = RenderContext()

        func renderNode(_ node: KsoupNode) {
            if let element = node as? KsoupElement {
                renderElement(element)
            } else if let textNode = node as? KsoupTextNode {
                context.appendAttributedText(
                    AttributedString(
                        textNode.text(),
                        attributes: context.attributeContainer
                    )
                )
            }
        }

        func renderChildren(of element: KsoupElement) {
            element.childNodes().forEach { renderNode($0) }
        }

        func renderElement(_ element: KsoupElement) {
            switch element.tagName().lowercased() {
            case "a":
                let href = element.attribute(key: "href")?.value ?? ""
                context.withAttributes {
                    $0.link = URL(string: href)
                } render: {
                    renderChildren(of: element)
                }
            case "strong", "b":
                context.withAttributes {
                    $0.font = .system(size: UIFont.systemFontSize, weight: .bold)
                } render: {
                    renderChildren(of: element)
                }
            case "em", "i":
                context.withAttributes {
                    $0.font = .system(size: UIFont.systemFontSize, weight: .regular).italic()
                } render: {
                    renderChildren(of: element)
                }
            case "br":
                context.appendAttributedText(
                    AttributedString(
                        "\n",
                        attributes: context.attributeContainer
                    )
                )
            case "p", "div":
                renderChildren(of: element)
                if element.parent()?.childNodes().last != element {
                    context.appendAttributedText(
                        AttributedString(
                            "\n\n",
                            attributes: context.attributeContainer
                        )
                    )
                }
            case "span":
                renderChildren(of: element)
            case "del", "s":
                context.withAttributes {
                    $0.strikethroughStyle = .single
                } render: {
                    renderChildren(of: element)
                }
            case "code":
                context.withAttributes {
                    $0.font = .system(.body, design: .monospaced)
                } render: {
                    renderChildren(of: element)
                }
            case "blockquote":
                context.withAttributes {
                    $0.font = .system(size: UIFont.systemFontSize, weight: .regular).italic()
                    $0.foregroundColor = .secondary
                } render: {
                    renderChildren(of: element)
                }
            case "u":
                context.withAttributes {
                    $0.underlineStyle = .single
                } render: {
                    renderChildren(of: element)
                }
            case "small":
                context.withAttributes {
                    $0.font = .system(size: UIFont.smallSystemFontSize)
                } render: {
                    renderChildren(of: element)
                }
            case "emoji":
                context.appendImage(
                    url: element.attribute(key: "target")?.value ?? "",
                    alt: element.attribute(key: "alt")?.value ?? ""
                )
            case "figure":
                context.pushBlockState()
                renderChildren(of: element)
                context.popBlockState()
            case "img":
                let src = element.attribute(key: "src")?.value ?? ""
                if context.isInBlockState {
                    context.appendBlockImage(
                        url: src,
                        href: element.attribute(key: "href")?.value
                    )
                } else {
                    context.appendImage(
                        url: src,
                        alt: element.attribute(key: "alt")?.value ?? ""
                    )
                }
            default:
                renderChildren(of: element)
            }
        }

        renderElement(richText.data)
        context.flushTextContent()
        return context.contents
    }
}

private final class RenderContext {
    var contents: [PlatformTextContent] = []
    var currentRuns: [PlatformTextRun] = []
    var attributedString = AttributedString()
    var attributeContainer = AttributeContainer()
    private(set) var isInBlockState = false

    func appendAttributedText(_ text: AttributedString) {
        attributedString = attributedString + text
    }

    func appendImage(url: String, alt: String) {
        commitAttributedString()
        currentRuns.append(PlatformTextImageRun(url: url, alt: alt))
    }

    func appendBlockImage(url: String, href: String?) {
        flushTextContent()
        contents.append(PlatformTextBlockImageContent(url: url, href: href))
    }

    func flushTextContent() {
        commitAttributedString()
        guard !currentRuns.isEmpty else { return }
        contents.append(PlatformTextTextContent(runs: currentRuns))
        currentRuns = []
    }

    func pushBlockState() {
        isInBlockState = true
    }

    func popBlockState() {
        isInBlockState = false
    }

    func withAttributes(
        _ update: (inout AttributeContainer) -> Void,
        render: () -> Void
    ) {
        let currentAttributes = attributeContainer
        var nextAttributes = currentAttributes
        update(&nextAttributes)
        attributeContainer = nextAttributes
        render()
        attributeContainer = currentAttributes
    }

    private func commitAttributedString() {
        guard attributedString.characters.count > 0 else { return }
        currentRuns.append(PlatformTextAttributedRun(text: attributedString))
        attributedString = AttributedString()
    }
}
