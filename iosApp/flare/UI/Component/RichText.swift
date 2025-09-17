import SwiftUI
import KotlinSharedUI
import MarkdownUI
import Kingfisher

struct RichText: View {
    let text: UiRichText

    var body: some View {
        Markdown(text.markdown)
            .markdownImageProvider(.kfImage)
            .markdownInlineImageProvider(.kfImage)

    }
}

struct KFImageProvider: ImageProvider, InlineImageProvider {
    func makeImage(url: URL?) -> some View {
        NetworkImage(data: url)
            .frame(width: 14, height: 14)
    }

    func image(with url: URL, label: String) async throws -> Image {
        let image = try await KingfisherManager.shared.retrieveImage(with: url)
        if let resized = image.image.resize(height: 14) {
            return Image(uiImage: resized)
        } else {
            return Image(uiImage: image.image)
        }
    }
}

extension ImageProvider where Self == KFImageProvider {
    static var kfImage: Self {
        .init()
    }
}

extension InlineImageProvider where Self == KFImageProvider {
    static var kfImage: Self {
        KFImageProvider()
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
