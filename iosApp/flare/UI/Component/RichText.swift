import SwiftUI
import KotlinSharedUI
import Kingfisher
import SwiftUIBackports

struct RichText: View {
    let text: UiRichText
    @State private var images: [String: Image] = [:]
    @ScaledMetric(relativeTo: .body) var imageSize = 17
    
    var body: some View {
        render(text: text, images: images)
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

    func render(text: UiRichText, images: [String: Image]) -> Text {
        var result = Text("")
        var attributedString = AttributedString()
        var attributeContainer = AttributeContainer()
        
        func commitAndReset() {
            result = result + Text(attributedString)
            attributedString = AttributedString()
        }
        
        func renderNode(_ node: KsoupNode) {
            if let element = node as? KsoupElement {
                renderElement(element)
            } else if let textNode = node as? KsoupTextNode {
                attributedString = attributedString + AttributedString(textNode.text(), attributes: attributeContainer)
            }
        }
        
        func renderElement(_ element: KsoupElement) {
            switch element.tagName().lowercased() {
            case "a":
                let href = element.attribute(key: "href")?.value ?? ""
                let currentAttributes = attributeContainer
                attributeContainer = AttributeContainer()
                attributeContainer.link = URL(string: href)
                element.childNodes().forEach { renderNode($0) }
                attributeContainer = currentAttributes
            case "strong", "b":
                let currentAttributes = attributeContainer
                attributeContainer = AttributeContainer()
                attributeContainer.font = .system(size: UIFont.systemFontSize, weight: .bold)
                element.childNodes().forEach { renderNode($0) }
                attributeContainer = currentAttributes
            case "em", "i":
                let currentAttributes = attributeContainer
                attributeContainer = AttributeContainer()
                attributeContainer.font = .system(size: UIFont.systemFontSize, weight: .regular).italic()
                element.childNodes().forEach { renderNode($0) }
                attributeContainer = currentAttributes
            case "br":
                attributedString = attributedString + AttributedString("\n", attributes: attributeContainer)
            case "p", "div":
                element.childNodes().forEach { renderNode($0) }
                if element.parent()?.childNodes().last != element {
                    attributedString = attributedString + AttributedString("\n\n", attributes: attributeContainer)
                }
            case "span":
                element.childNodes().forEach { renderNode($0) }
            case "del", "s":
                let currentAttributes = attributeContainer
                attributeContainer = AttributeContainer()
                attributeContainer.strikethroughStyle = .single
                element.childNodes().forEach { renderNode($0) }
                attributeContainer = currentAttributes
            case "code":
                commitAndReset()
                let codeText = element.text()
                result = result + Text(codeText)
                    .font(.system(.body, design: .monospaced))
            case "blockquote":
                commitAndReset()
                let blockquoteText = element.text()
                result = result + Text(blockquoteText)
                    .foregroundColor(.secondary)
                    .italic()
            case "u":
                let currentAttributes = attributeContainer
                attributeContainer = AttributeContainer()
                attributeContainer.underlineStyle = .single
                element.childNodes().forEach { renderNode($0) }
                attributeContainer = currentAttributes
            case "small":
                let currentAttributes = attributeContainer
                attributeContainer = AttributeContainer()
                attributeContainer.font = .system(size: UIFont.smallSystemFontSize)
                element.childNodes().forEach { renderNode($0) }
                attributeContainer = currentAttributes
            case "emoji":
                let src = element.attribute(key: "target")?.value ?? ""
                if let image = images[src] {
                    commitAndReset()
                    result = result + Text(image).baselineOffset(-3)
                } else {
                    let alt = element.attribute(key: "alt")?.value ?? ""
                    attributedString = attributedString + AttributedString(alt, attributes: attributeContainer)
                }
            case "img":
                let src = element.attribute(key: "src")?.value ?? ""
                if let image = images[src] {
                    commitAndReset()
                    result = result + Text(image).baselineOffset(-3)
                } else {
                    let alt = element.attribute(key: "alt")?.value ?? ""
                    attributedString = attributedString + AttributedString(alt, attributes: attributeContainer)
                }
            default:
                element.childNodes().forEach { renderNode($0) }
            }
        }
        
        renderNode(text.data)
        commitAndReset()
        return result
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
