import SwiftUI
import KotlinSharedUI
import Kingfisher
import SwiftUIBackports

struct RichText: View {
    let text: UiRichText
    @State var renderer: RichTextRenderer
    @ScaledMetric(relativeTo: .body) var imageSize = 17
    
    var body: some View {
        renderer
            .displayText
            .backport
            .textRenderer(CodeEffect())
            .task(id: renderer.imageURLs) {
                if !renderer.imageURLs.isEmpty, renderer.imageURLs.count != renderer.images.count, renderer.imageURLs.allSatisfy({ renderer.images[$0] == nil }) {
                    var images: [String: Image?] = [:]
                    for urlString in renderer.imageURLs {
                        if let url = URL(string: urlString) {
                            do {
                                let image = try await KingfisherManager.shared.retrieveImage(with: url)
                                images[urlString] = Image(uiImage: image.image.resize(height: imageSize) ?? image.image)
                            } catch {
                                images[urlString] = nil
                            }
                        } else {
                            images[urlString] = nil
                        }
                    }
                    renderer.images = images
                    renderer.render()
                }
            }
    }
}

extension RichText {
    init(text: UiRichText) {
        self.text = text
        self.renderer = RichTextRenderer(text: text)
    }
}

struct RichTextRenderer {
    let text: UiRichText
    var images: [String: Image?] = [:]
    private var attributeContainer = AttributeContainer()
    private var attributedString = AttributedString()
    var displayText = Text("")
    var result = Text("")
    var imageURLs: [String] = []
    
    init(text: UiRichText) {
        self.text = text
        render()
    }
    
    mutating func render() {
        self.result = Text("")
        renderNode(node: text.data)
        commitAndReset()
        self.displayText = self.result
    }
    
    mutating func renderNode(node: KsoupNode) {
        if let element = node as? KsoupElement {
            renderElement(element: element)
        } else if let textNode = node as? KsoupTextNode {
            self.attributedString = self.attributedString + AttributedString(textNode.text(), attributes: self.attributeContainer)
        } else {
        }
    }
    
    mutating func renderElement(element: KsoupElement) {
        switch (element.tagName().lowercased()) {
        case "a":
            let href = element.attribute(key: "href")?.value ?? ""
            let currentAttributes = self.attributeContainer
            self.attributeContainer = .init()
            self.attributeContainer.link = URL(string: href)
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributeContainer = currentAttributes
            break;
        case "strong", "b":
            let currentAttributes = self.attributeContainer
            self.attributeContainer = .init()
            self.attributeContainer.font = .system(size: UIFont.systemFontSize, weight: .bold)
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributeContainer = currentAttributes
            break;
        case "em", "i":
            let currentAttributes = self.attributeContainer
            self.attributeContainer = .init()
            self.attributeContainer.font = .system(size: UIFont.systemFontSize, weight: .regular).italic()
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributeContainer = currentAttributes
            break;
        case "br":
            self.attributedString = self.attributedString + AttributedString("\n", attributes: self.attributeContainer)
            break;
        case "p", "div":
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributedString = self.attributedString + AttributedString("\n\n", attributes: self.attributeContainer)
            break;
        case "span":
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            break;
        case "emoji":
            let src = element.attribute(key: "target")?.value ?? ""
            if !src.isEmpty {
                if !imageURLs.contains(src) {
                    imageURLs.append(src)
                }
                if let image = images[src], let img = image {
                    commitAndReset()
                    self.result = self.result + Text(img)
                        .baselineOffset(-3)
                } else {
                    // Fallback to alt text
                    let alt = element.attribute(key: "alt")?.value ?? ""
                    self.attributedString = self.attributedString + AttributedString(alt, attributes: self.attributeContainer)
                }
            }
            break;
        case "img":
            let src = element.attribute(key: "src")?.value ?? ""
            let alt = element.attribute(key: "alt")?.value ?? ""
            if !src.isEmpty {
                if !imageURLs.contains(src) {
                    imageURLs.append(src)
                }
                if let image = images[src], let img = image {
                    commitAndReset()
                    self.result = self.result + Text(img)
                        .baselineOffset(-3)
                } else {
                    // Fallback to alt text
                    self.attributedString = self.attributedString + AttributedString(alt, attributes: self.attributeContainer)
                }
            }
            break;
        case "del", "s":
            let currentAttributes = self.attributeContainer
            self.attributeContainer = .init()
            self.attributeContainer.strikethroughStyle = .single
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributeContainer = currentAttributes
            break;
        case "code":
            commitAndReset()
            let codeText = element.text()
            self.result = self.result + Text(codeText)
                .font(.system(.body, design: .monospaced))
                .customAttribute(CodeAttribute())
            break;
//            let currentAttributes = self.attributeContainer
//            self.attributeContainer = .init()
//            self.attributeContainer.font = .system(.body, design: .monospaced)
//            element.childNodes().forEach { node in
//                renderNode(node: node)
//            }
//            self.attributeContainer = currentAttributes
//            break;
        case "blockquote":
            commitAndReset()
            let blockquoteText = element.text()
            self.result = self.result + Text(blockquoteText)
                .foregroundColor(.secondary)
                .italic()
                .customAttribute(CodeAttribute())
            break;
//            let currentAttributes = self.attributeContainer
//            self.attributeContainer = .init()
//            self.attributeContainer.foregroundColor = .secondary
//            element.childNodes().forEach { node in
//                renderNode(node: node)
//            }
//            self.attributeContainer = currentAttributes
//            break;
        case "u":
            let currentAttributes = self.attributeContainer
            self.attributeContainer = .init()
            self.attributeContainer.underlineStyle = .single
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributeContainer = currentAttributes
            break;
        case "small":
            let currentAttributes = self.attributeContainer
            self.attributeContainer = .init()
            self.attributeContainer.font = .system(size: UIFont.smallSystemFontSize)
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
            self.attributeContainer = currentAttributes
            break;
            
        default:
            element.childNodes().forEach { node in
                renderNode(node: node)
            }
        }
    }
    
    mutating func commitAndReset() {
        self.result = self.result + Text(self.attributedString)
        self.attributedString = .init()
    }
}

struct CodeAttribute: TextAttribute {}
struct CodeEffect: TextRenderer {
    func draw(layout: Text.Layout, in ctx: inout GraphicsContext) {
        for line in layout {
            for run in line {
                if run[CodeAttribute.self] != nil {
                    let rect = run.typographicBounds.rect
                    let copy = ctx
                    let shape = RoundedRectangle(cornerRadius: 5).path(in: rect)
                    copy.fill(shape, with: .color(Color(.systemGray6)))
                }
                ctx.draw(run)
            }
        }
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
