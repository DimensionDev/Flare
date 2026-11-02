import Foundation
import CoreGraphics
import ImageIO
import Kingfisher
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

public nonisolated struct ImageDownsamplingProcessor: ImageProcessor {
    public let pixelSize: CGSize
    public let fillsBounds: Bool
    public var identifier: String { "dev.dimension.flare.preview(\(pixelSize),\(fillsBounds))" }

    public init(pixelSize: CGSize, fillsBounds: Bool) {
        self.pixelSize = pixelSize
        self.fillsBounds = fillsBounds
    }

    public func process(item: ImageProcessItem, options: KingfisherParsedOptionsInfo) -> KFCrossPlatformImage? {
        let sourceSize: CGSize
        switch item {
        case .data(let data):
            guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
                  CGImageSourceGetCount(source) == 1,
                  let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                  let width = properties[kCGImagePropertyPixelWidth] as? CGFloat,
                  let height = properties[kCGImagePropertyPixelHeight] as? CGFloat else {
                // Preview URLs can contain animation even without a GIF extension.
                return DefaultImageProcessor.default.process(item: item, options: options)
            }
            let rotated = (5...8).contains(properties[kCGImagePropertyOrientation] as? Int ?? 1)
            sourceSize = rotated ? CGSize(width: height, height: width) : CGSize(width: width, height: height)
        case .image(let image):
            guard (image.kf.imageFrameCount ?? 1) <= 1 else { return image }
            sourceSize = image.size
        }
        guard sourceSize.width > 0, sourceSize.height > 0 else { return nil }
        let widthRatio = pixelSize.width / sourceSize.width
        let heightRatio = pixelSize.height / sourceSize.height
        let ratio = fillsBounds ? max(widthRatio, heightRatio) : min(widthRatio, heightRatio)
        let size = CGSize(width: sourceSize.width * ratio / options.scaleFactor,
                          height: sourceSize.height * ratio / options.scaleFactor)
        return DownsamplingImageProcessor(size: size).process(item: item, options: options)
    }
}
