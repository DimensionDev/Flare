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
    private let previewLineLimit: Int?
    private let previewLineHeight: CGFloat
    @State private var images: [String: Image] = [:]
    @State private var previewMeasurements: [Int: [Int: CGFloat]] = [:]
    @ScaledMetric(relativeTo: .body) private var imageSize = 17
    @ScaledMetric(relativeTo: .body) private var quoteBarWidth = 3
    @Environment(\.openURL) private var openURL
    @Environment(\.displayScale) private var displayScale
    @Environment(\.shareImageLoading) private var shareImageLoading
    @State private var shareImageID = UUID()

    public init(text: UiRichText) {
        self.text = text
        previewLineLimit = nil
        previewLineHeight = 0
    }

    init(text: UiRichText, previewLineLimit: Int?, previewLineHeight: CGFloat) {
        self.text = text
        self.previewLineLimit = previewLineLimit.map { max($0, 1) }
        self.previewLineHeight = previewLineHeight
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(previewContents.enumerated()), id: \.offset) { index, content in
                let remainingLines = remainingLines(before: index)
                if remainingLines != 0 {
                    switch content {
                    case let textContent as PlatformTextTextContent:
                        if let remainingLines {
                            renderBlock(textContent: textContent)
                                .lineLimit(remainingLines)
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            renderBlock(textContent: textContent)
                        }
                    case let imageContent as PlatformTextBlockImageContent:
                        if let url = URL(string: imageContent.url) {
                            let image = RichTextBlockImage(url: url)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .contentShape(Rectangle())
                                .accessibilityLabel(
                                    Text("media_image_no_alt", bundle: FlareAppleUILocalization.bundle)
                                )
                                .onTapGesture {
                                    if let href = imageContent.href, let link = URL(string: href) {
                                        openURL(link)
                                    }
                                }
                            if let remainingLines {
                                image
                                    .fixedSize(horizontal: false, vertical: true)
                                    .background {
                                        GeometryReader { proxy in
                                            Color.clear.preference(key: RichTextPreviewMeasurements.self,
                                                value: [index: [0: proxy.size.height]])
                                        }
                                    }
                                    .frame(maxHeight: CGFloat(remainingLines) * previewLineHeight, alignment: .top)
                                    .clipped()
                            } else {
                                image
                            }
                        }
                    default:
                        EmptyView()
                    }
                }
            }
        }
        .frame(maxWidth: previewLineLimit == nil ? nil : .infinity, alignment: .leading)
        .background(alignment: .topLeading) {
            if let previewLineLimit {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(previewContents.dropLast().enumerated()), id: \.offset) { index, content in
                        if let content = content as? PlatformTextTextContent {
                            ZStack(alignment: .topLeading) {
                                ForEach(1...(previewLineLimit + 1), id: \.self) { lines in
                                    renderBlock(textContent: content)
                                        .lineLimit(lines)
                                        .fixedSize(horizontal: false, vertical: true)
                                        .background {
                                            GeometryReader { proxy in
                                                Color.clear.preference(key: RichTextPreviewMeasurements.self,
                                                    value: [index: [lines: proxy.size.height]])
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
                .hidden()
            }
        }
        .onPreferenceChange(RichTextPreviewMeasurements.self) { previewMeasurements = $0 }
        .task(id: InlineImageRequest(urls: inlineImageURLs, height: imageSize, scale: displayScale)) {
            let urls = inlineImageURLs
            images = images.filter { urls.contains($0.key) }
            let targetHeight = imageSize
            if !urls.isEmpty { shareImageLoading?.register(shareImageID) }
            var succeeded = true
            for urlString in urls {
                guard let url = URL(string: urlString) else { succeeded = false; continue }
                do {
                    let processor = ImageDownsamplingProcessor(
                        pixelSize: CGSize(width: .greatestFiniteMagnitude, height: ceil(targetHeight * displayScale)),
                        fillsBounds: false)
                    let result = try await KingfisherManager.shared.retrieveImage(with: url, options: [
                        .processor(processor), .scaleFactor(displayScale), .cacheOriginalImage,
                        .backgroundDecode, .asyncCacheTypeCheck,
                    ])
                    guard !Task.isCancelled else { return }
                    let platformImage = result.image
                    let resized = platformImage.resize(height: targetHeight) ?? platformImage
                    images[urlString] = Image(platformImage: resized)
                } catch is CancellationError {
                    shareImageLoading?.remove(shareImageID)
                    return
                } catch {
                    succeeded = false
                }
            }
            if !urls.isEmpty { shareImageLoading?.complete(shareImageID, success: succeeded) }
        }
        .onDisappear { shareImageLoading?.remove(shareImageID) }
    }

    private var contents: [PlatformTextContent] {
        text.platformText.compactMap { $0 as? PlatformTextContent }
    }

    private var inlineImageURLs: [String] {
        contents.compactMap { $0 as? PlatformTextTextContent }.flatMap { content in
            content.runs.compactMap { ($0 as? PlatformTextImageRun)?.url }
        }
    }

    private var previewContents: [PlatformTextContent] {
        let contents = contents
        guard let previewLineLimit else { return contents }
        return Array(contents.lazy.filter { content in
            if let image = content as? PlatformTextBlockImageContent {
                return URL(string: image.url) != nil
            }
            return content is PlatformTextTextContent
        }.prefix(previewLineLimit))
    }

    private func remainingLines(before index: Int) -> Int? {
        guard let previewLineLimit else { return nil }
        var remaining = previewLineLimit
        for previous in 0..<index {
            let heights = previewMeasurements[previous] ?? [:]
            let count: Int
            if let imageHeight = heights[0] {
                count = max(Int(ceil(imageHeight / max(previewLineHeight, 1))), 1)
            } else {
                // Equal native heights at adjacent limits mean the whole block fits.
                count = (1...previewLineLimit).first { lines in
                    guard let height = heights[lines], let next = heights[lines + 1] else { return false }
                    return abs(height - next) < 0.01
                } ?? previewLineLimit
            }
            remaining -= count
            if remaining <= 0 { return 0 }
        }
        return remaining
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
        textContent.runs.reduce(Text(verbatim: "")) { partial, run in
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

private struct InlineImageRequest: Hashable {
    let urls: [String]
    let height: CGFloat
    let scale: CGFloat
}

private struct RichTextBlockImage: View {
    let url: URL
    @Environment(\.displayScale) private var displayScale
    @Environment(\.shareImageLoading) private var shareImageLoading
    @State private var shareImageID = UUID()
    @State private var pixelWidth: CGFloat = 0
    @State private var loadedImage: (url: URL, image: KFCrossPlatformImage)?

    var body: some View {
        KFImage(pixelWidth > 0 ? url : nil)
            .setProcessor(ImageDownsamplingProcessor(
                pixelSize: CGSize(width: pixelWidth, height: .greatestFiniteMagnitude), fillsBounds: false))
            .scaleFactor(displayScale)
            .cacheOriginalImage()
            .flareAsyncCacheTypeCheck()
            .flareBackgroundDecodeIfSupported()
            .loadDiskFileSynchronously(false)
            .onSuccess {
                loadedImage = (url, $0.image)
                shareImageLoading?.complete(shareImageID, success: true)
            }
            .onFailure { _ in
                if pixelWidth > 0 { shareImageLoading?.complete(shareImageID, success: false) }
            }
            .placeholder {
                if let loadedImage, loadedImage.url == url {
                    Image(platformImage: loadedImage.image).resizable().scaledToFit()
                } else {
                    Color.clear.frame(height: 1)
                }
            }
            .resizable()
            .scaledToFit()
            .frame(maxWidth: .infinity)
            .onGeometryChange(for: CGFloat.self) { proxy in
                ceil(proxy.size.width * displayScale)
            } action: { width in
                pixelWidth = max(pixelWidth, width)
            }
            .onAppear { shareImageLoading?.register(shareImageID) }
            .onDisappear { shareImageLoading?.remove(shareImageID) }
    }
}

private struct RichTextPreviewMeasurements: PreferenceKey {
    static let defaultValue: [Int: [Int: CGFloat]] = [:]

    static func reduce(value: inout [Int: [Int: CGFloat]], nextValue: () -> [Int: [Int: CGFloat]]) {
        for (index, heights) in nextValue() {
            value[index, default: [:]].merge(heights) { _, new in new }
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
