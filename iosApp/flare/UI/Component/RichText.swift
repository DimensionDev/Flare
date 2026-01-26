import SwiftUI
import KotlinSharedUI
import Kingfisher
import SwiftUIBackports

struct RichText: View {
    let text: UiRichText
    @State private var images: [String: Image] = [:]
    @ScaledMetric(relativeTo: .body) var imageSize = 17
    @Environment(\.openURL) var openURL
    
    enum RichTextContent: Identifiable {
        var id: String {
            switch self {
            case .text(let text):
                return "text-\(text)"
            case .blockImage(let url, let href):
                return "img-\(url)-\(href ?? "")"
            }
        }
        case text(Text)
        case blockImage(url: String, href: String?)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(render(text: text, images: images)) { content in
                switch content {
                case .text(let text):
                    text
                case .blockImage(let url, let href):
                    if let url = URL(string: url) {
                        KFImage(url)
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .contentShape(Rectangle())
                            .onTapGesture {
                                if let href, let link = URL(string: href) {
                                    openURL(link)
                                }
                            }
                    }
                }
            }
        }
        .task(id: text.raw) {
            let urls = text.imageUrls
            let targetHeight = imageSize
            await withTaskGroup(of: (String, Image?).self) { group in
                for urlString in urls {
                    group.addTask {
                        guard let url = URL(string: urlString) else { return (urlString, nil) }
                        do {
                            let result = try await KingfisherManager.shared.retrieveImage(with: url)
                            let uiImage = result.image
                            if let resized = await uiImage.resize(height: targetHeight) {
                                return (urlString, Image(uiImage: resized))
                            } else {
                                return (urlString, Image(uiImage: uiImage))
                            }
                        } catch {
                            return (urlString, nil)
                        }
                    }
                }
                
                for await (urlString, image) in group {
                    if let image = image {
                        images[urlString] = image
                    }
                }
            }
        }
    }

    func render(text: UiRichText, images: [String: Image]) -> [RichTextContent] {
        var contents: [RichTextContent] = []
        
        // Context to maintain state during traversal
        class RenderContext {
            var currentText = Text("")
            var attributedString = AttributedString()
            var attributeContainer = AttributeContainer()
            var isEmpty = true
            var isBlockState = false
            
            func flush(to list: inout [RichTextContent]) {
                if !isEmpty {
                    // Combine result+Text(attributedString)
                    // But `Text` doesn't expose concatenation easily outside of ViewBuilder or `+` operator on Text values
                    // We must commit attributedString to currentText first
                    commitAttributedString()
                    list.append(.text(currentText))
                    currentText = Text("")
                    isEmpty = true
                }
            }
            
            func commitAttributedString() {
                if attributedString.characters.count > 0 {
                    currentText = currentText + Text(attributedString)
                    attributedString = AttributedString()
                }
            }
            
            func appendText(_ text: Text) {
                commitAttributedString()
                currentText = currentText + text
                isEmpty = false
            }
        }
        
        let context = RenderContext()
        
        func renderNode(_ node: KsoupNode) {
            if let element = node as? KsoupElement {
                renderElement(element)
            } else if let textNode = node as? KsoupTextNode {
                context.attributedString = context.attributedString + AttributedString(textNode.text(), attributes: context.attributeContainer)
                // Text added implicitly makes it not empty, but we verify emptiness on flush by character count or flag
                context.isEmpty = false
            }
        }
        
        func renderElement(_ element: KsoupElement) {
            switch element.tagName().lowercased() {
            case "a":
                let href = element.attribute(key: "href")?.value ?? ""
                let currentAttributes = context.attributeContainer
                context.attributeContainer = AttributeContainer()
                context.attributeContainer.link = URL(string: href)
                element.childNodes().forEach { renderNode($0) }
                context.attributeContainer = currentAttributes
            case "strong", "b":
                let currentAttributes = context.attributeContainer
                context.attributeContainer = AttributeContainer()
                context.attributeContainer.font = .system(size: UIFont.systemFontSize, weight: .bold)
                element.childNodes().forEach { renderNode($0) }
                context.attributeContainer = currentAttributes
            case "em", "i":
                let currentAttributes = context.attributeContainer
                context.attributeContainer = AttributeContainer()
                context.attributeContainer.font = .system(size: UIFont.systemFontSize, weight: .regular).italic()
                element.childNodes().forEach { renderNode($0) }
                context.attributeContainer = currentAttributes
            case "br":
                context.attributedString = context.attributedString + AttributedString("\n", attributes: context.attributeContainer)
                context.isEmpty = false
            case "p", "div":
                element.childNodes().forEach { renderNode($0) }
                if element.parent()?.childNodes().last != element {
                    context.attributedString = context.attributedString + AttributedString("\n\n", attributes: context.attributeContainer)
                    context.isEmpty = false
                }
            case "span":
                element.childNodes().forEach { renderNode($0) }
            case "del", "s":
                let currentAttributes = context.attributeContainer
                context.attributeContainer = AttributeContainer()
                context.attributeContainer.strikethroughStyle = .single
                element.childNodes().forEach { renderNode($0) }
                context.attributeContainer = currentAttributes
            case "code":
                context.commitAttributedString()
                let codeText = element.text()
                let codeView = Text(codeText).font(.system(.body, design: .monospaced))
                context.appendText(codeView)
            case "blockquote":
                context.commitAttributedString()
                let blockquoteText = element.text()
                let quoteView = Text(blockquoteText).foregroundColor(.secondary).italic()
                context.appendText(quoteView)
            case "u":
                let currentAttributes = context.attributeContainer
                context.attributeContainer = AttributeContainer()
                context.attributeContainer.underlineStyle = .single
                element.childNodes().forEach { renderNode($0) }
                context.attributeContainer = currentAttributes
            case "small":
                let currentAttributes = context.attributeContainer
                context.attributeContainer = AttributeContainer()
                context.attributeContainer.font = .system(size: UIFont.smallSystemFontSize)
                element.childNodes().forEach { renderNode($0) }
                context.attributeContainer = currentAttributes
            case "emoji":
                let src = element.attribute(key: "target")?.value ?? ""
                if let image = images[src] {
                    context.commitAttributedString()
                    context.appendText(Text(image).baselineOffset(-3))
                } else {
                    let alt = element.attribute(key: "alt")?.value ?? ""
                    context.attributedString = context.attributedString + AttributedString(alt, attributes: context.attributeContainer)
                    context.isEmpty = false
                }
            case "figure":
                context.isBlockState = true
                element.childNodes().forEach { renderNode($0) }
                context.isBlockState = false
            case "img":
                let src = element.attribute(key: "src")?.value ?? ""
                if context.isBlockState {
                    // Block image: Flush text, add image
                    context.flush(to: &contents)
                    let href = element.attribute(key: "href")?.value
                    contents.append(.blockImage(url: src, href: href))
                } else {
                    // Inline image
                    if let image = images[src] {
                        context.commitAttributedString()
                        context.appendText(Text(image).baselineOffset(-3))
                    } else {
                        let alt = element.attribute(key: "alt")?.value ?? ""
                        context.attributedString = context.attributedString + AttributedString(alt, attributes: context.attributeContainer)
                        context.isEmpty = false
                    }
                }
            default:
                element.childNodes().forEach { renderNode($0) }
            }
        }
        
        renderNode(text.data)
        context.flush(to: &contents)
        return contents
    }
}
extension UIImage {
    func resize(height: CGFloat) -> UIImage? {
        let heightRatio = height / size.height
        let width = size.width * heightRatio

        let resizedSize = CGSize(width: width, height: height)

        UIGraphicsBeginImageContextWithOptions(resizedSize, false, 0.0)
        draw(in: CGRect(origin: .zero, size: resizedSize))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resizedImage
    }
}
