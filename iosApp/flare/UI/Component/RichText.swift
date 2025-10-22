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
    let expandImageSize: Bool
    func makeImage(url: URL?) -> some View {
        if expandImageSize {
            RichTextImage(url: url)
        } else {
            NetworkImage(data: url)
                .scaledToFit()
                .frame(height: 14)
        }
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

struct RichTextImage: View {
    let url: URL?
    @State private var showFullImage = false
    var body: some View {
        NetworkImage(data: url)
            .onTapGesture {
                showFullImage = true
            }
            .sheet(isPresented: $showFullImage) {
                if let url = url {
                    NavigationStack {
                        MediaScreen(url: url.absoluteString)
                    }
                }
            }
    }
}

extension ImageProvider where Self == KFImageProvider {
    static var kfImage: Self {
        .init(expandImageSize: false)
    }
}

extension InlineImageProvider where Self == KFImageProvider {
    static var kfImage: Self {
        KFImageProvider(expandImageSize: false)
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
