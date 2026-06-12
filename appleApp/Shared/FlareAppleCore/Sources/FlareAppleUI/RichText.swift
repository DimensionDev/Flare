import SwiftUI
import KotlinSharedUI
import Kingfisher
import FlareAppleCore

#if canImport(UIKit)
import UIKit

private typealias PlatformImage = UIImage
#elseif canImport(AppKit)
import AppKit

private typealias PlatformImage = NSImage
#endif

public struct RichText: View {
    private let text: UiRichText
    @State private var images: [String: Image] = [:]
    @ScaledMetric(relativeTo: .body) private var imageSize = 17
    @ScaledMetric(relativeTo: .body) private var quoteBarWidth = 3
    @Environment(\.openURL) private var openURL

    public init(text: UiRichText) {
        self.text = text
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(contents.enumerated()), id: \.offset) { _, content in
                switch content {
                case let textContent as PlatformTextTextContent:
                    renderBlock(textContent: textContent)
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
            for urlString in urls {
                guard let url = URL(string: urlString) else { continue }
                do {
                    let result = try await KingfisherManager.shared.retrieveImage(with: url)
                    let platformImage = result.image
                    let resized = platformImage.resize(height: targetHeight) ?? platformImage
                    images[urlString] = Image(platformImage: resized)
                } catch {
                    continue
                }
            }
        }
    }

    private var contents: [PlatformTextContent] {
        text.platformText.compactMap { $0 as? PlatformTextContent }
    }

    @ViewBuilder
    private func renderBlock(textContent: PlatformTextTextContent) -> some View {
        let renderedText = render(textContent: textContent)
        if textContent.isBlockQuote {
            HStack(alignment: .top, spacing: 11) {
                Rectangle()
                    .fill(.secondary.opacity(0.18))
                    .frame(width: quoteBarWidth)
                renderedText
                    .frame(maxWidth: .infinity, alignment: textAlignment(textContent.alignment))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
        } else if textContent.alignment == .center {
            renderedText
                .frame(maxWidth: .infinity, alignment: .center)
        } else {
            renderedText
        }
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

    private func textAlignment(_ alignment: TextAlignment?) -> Alignment {
        switch alignment {
        case .center:
            return .center
        case .trailing:
            return .trailing
        default:
            return .leading
        }
    }
}

private extension Image {
    init(platformImage: PlatformImage) {
        #if canImport(UIKit)
        self.init(uiImage: platformImage)
        #elseif canImport(AppKit)
        self.init(nsImage: platformImage)
        #endif
    }
}

#if canImport(UIKit)
private extension UIImage {
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
#elseif canImport(AppKit)
private extension NSImage {
    func resize(height: CGFloat) -> NSImage? {
        guard size.height > 0 else { return self }
        let heightRatio = height / size.height
        let width = size.width * heightRatio
        let resizedSize = CGSize(width: width, height: height)
        let resizedImage = NSImage(size: resizedSize)

        resizedImage.lockFocus()
        draw(
            in: CGRect(origin: .zero, size: resizedSize),
            from: CGRect(origin: .zero, size: size),
            operation: .copy,
            fraction: 1.0
        )
        resizedImage.unlockFocus()

        return resizedImage
    }
}
#endif
