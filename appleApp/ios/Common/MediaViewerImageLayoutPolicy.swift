import CoreGraphics

enum MediaViewerImageLayoutPolicy {
    static let tallHeightToWidthThreshold: CGFloat = 19.5 / 9.0

    static func shouldFillWidth(mediaAspectRatio: CGFloat?) -> Bool {
        guard let mediaAspectRatio,
              mediaAspectRatio.isFinite,
              mediaAspectRatio > 0 else {
            return false
        }
        return 1 / mediaAspectRatio > tallHeightToWidthThreshold
    }

    static func shouldFillWidth(imageSize: CGSize) -> Bool {
        guard imageSize.width.isFinite,
              imageSize.height.isFinite,
              imageSize.width > 0,
              imageSize.height > 0 else {
            return false
        }
        return imageSize.height / imageSize.width > tallHeightToWidthThreshold
    }

    static func shouldFillWidth(
        mediaAspectRatio: CGFloat?,
        measuredImageSize: CGSize?
    ) -> Bool {
        if let mediaAspectRatio,
           mediaAspectRatio.isFinite,
           mediaAspectRatio > 0 {
            return shouldFillWidth(mediaAspectRatio: mediaAspectRatio)
        }
        if let measuredImageSize {
            return shouldFillWidth(imageSize: measuredImageSize)
        }
        return shouldFillWidth(mediaAspectRatio: mediaAspectRatio)
    }
}
