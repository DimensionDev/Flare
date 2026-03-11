import SwiftUI
import KotlinSharedUI
import Kingfisher

struct RichText: View {
    let text: UiRichText
    @State private var images: [String: Image] = [:]
    @ScaledMetric(relativeTo: .body) var imageSize = 17
    @Environment(\.openURL) var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(contents.enumerated()), id: \.offset) { _, content in
                switch content {
                case let textContent as PlatformTextTextContent:
                    if textContent.alignment == .center {
                        render(textContent: textContent)
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        render(textContent: textContent)
                    }
                case let imageContent as PlatformTextBlockImageContent:
                    if let url = URL(string: imageContent.url) {
                        KFImage(url)
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .contentShape(Rectangle())
                            .onTapGesture {
                                if let href = imageContent.href, let link = URL(string: href) {
                                    openURL(link)
                                }
                            }
                    }
                default:
                    EmptyView()
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

    private var contents: [PlatformTextContent] {
        (text.platformText as? NSArray)?.compactMap { $0 as? PlatformTextContent } ?? []
    }

    private func render(textContent: PlatformTextTextContent) -> Text {
        textContent.runs.reduce(Text("")) { partial, run in
            switch run {
            case let attributedRun as PlatformTextAttributedRun:
                partial + Text(attributedRun.text)
            case let imageRun as PlatformTextImageRun:
                if let image = images[imageRun.url] {
                    partial + Text(image).baselineOffset(-3)
                } else {
                    partial + Text(imageRun.alt)
                }
            default:
                partial
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
