import Foundation
import SwiftUI
import MarkdownUI
import NetworkImage


public struct EmojiInlineImageProvider: InlineImageProvider {
    public func image(with url: URL, label: String) async throws -> Image {
        let img = try await DefaultNetworkImageLoader.shared.image(from: url)
        return if let uiimg = UIImage(cgImage: img).resize(height: 14) {
            Image(uiImage: uiimg)
        } else {
            Image(
                img,
                scale: 1,
                label: Text(label)
            )
        }
    }
}

extension InlineImageProvider where Self == EmojiInlineImageProvider {
  public static var emoji: Self {
    .init()
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
