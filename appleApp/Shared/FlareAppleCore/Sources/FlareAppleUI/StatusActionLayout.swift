import SwiftUI
import KotlinSharedUI
import FlareAppleCore

#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

public nonisolated struct StatusActionLayoutMetrics: Sendable {
    public static let iconWidth: CGFloat = 20
    public static let padding: CGFloat = 4
    public static let labelSpacing: CGFloat = 2
    public static let actionSpacing: CGFloat = 4

    public let fontSize: CGFloat
    public let scale: CGFloat
    public let lineHeight: CGFloat
    public let numberWidth: CGFloat

    #if canImport(UIKit)
    public var nativeFont: UIFont { .systemFont(ofSize: fontSize) }
    #else
    public var nativeFont: NSFont { .systemFont(ofSize: fontSize) }
    #endif

    public init(fontSize: CGFloat, scale: CGFloat) {
        self.fontSize = fontSize
        self.scale = max(scale, 1)
        #if canImport(UIKit)
        let font = UIFont.systemFont(ofSize: fontSize)
        let height = font.lineHeight
        #else
        let font = NSFont.systemFont(ofSize: fontSize)
        let height = font.ascender - font.descender + font.leading
        #endif
        lineHeight = ceil(height * self.scale) / self.scale
        numberWidth = ceil(("0000" as NSString).size(withAttributes: [.font: font]).width * self.scale) / self.scale
    }

    public var iconHeight: CGFloat { fontSize + 2 }

    #if canImport(UIKit)
    @MainActor
    public func iconImage(_ icon: UiIcon) -> UIImage? {
        let key = "\(icon.fontAwesomeIcon.rawValue):\(fontSize):\(scale)" as NSString
        if let image = StatusActionIconCache.images.object(forKey: key) { return image }
        let image = UIImage(fontAwesome: icon.fontAwesomeIcon)?
            .applyingSymbolConfiguration(.init(pointSize: fontSize))?
            .withRenderingMode(.alwaysTemplate)
        if let image { StatusActionIconCache.images.setObject(image, forKey: key) }
        return image
    }
    #endif

    public func iconScale(imageWidth: CGFloat) -> CGFloat {
        imageWidth > 0 ? min(1, Self.iconWidth / imageWidth) : 1
    }

    public func textWidth(_ text: String) -> CGFloat {
        ceil((text as NSString).size(withAttributes: [.font: nativeFont]).width * scale) / scale
    }

    public func size(titleWidth: CGFloat?, reservesNumberWidth: Bool, hasIcon: Bool = true, expanded: Bool = true) -> CGSize {
        let inset = expanded ? Self.padding : 0
        let hasTitle = titleWidth != nil || reservesNumberWidth
        let width = max(titleWidth ?? 0, reservesNumberWidth ? numberWidth : 0)
        return CGSize(
            width: (hasIcon ? Self.iconWidth : 0) + (hasIcon && hasTitle ? Self.labelSpacing : 0) + width + inset * 2,
            height: ceil(max(hasIcon ? iconHeight : 0, hasTitle ? lineHeight : 0) + inset * 2)
        )
    }

    public func frames(
        width: CGFloat,
        sizes: [CGSize],
        style: PostActionStyle,
        allowSpacer: Bool,
        lastHasNumber: Bool,
        rightToLeft: Bool = false
    ) -> [CGRect] {
        guard !sizes.isEmpty else { return [] }
        let stretched = style == .stretch && allowSpacer && sizes.count > 1
        let hasSpacer = style == .leftAligned && allowSpacer
        let gaps = stretched ? 0 : CGFloat(max(sizes.count - 1, 0) + (hasSpacer ? 1 : 0)) * Self.actionSpacing
        var widths = sizes.map(\.width)
        if stretched {
            let lastWidth = min(width, Self.iconWidth + Self.padding * 2 + (lastHasNumber ? fontSize * 2.5 + Self.labelSpacing : 0))
            widths = Array(repeating: max(width - lastWidth, 0) / CGFloat(sizes.count - 1), count: sizes.count - 1) + [lastWidth]
        } else if widths.reduce(0, +) + gaps > width {
            // Count placeholders can shrink in narrow columns while keeping every icon available.
            let minimum = widths.map { min($0, Self.iconWidth + Self.padding * 2) }
            let flexible = zip(widths, minimum).reduce(CGFloat.zero) { $0 + $1.0 - $1.1 }
            let factor = flexible > 0 ? max(0, min(1, (width - gaps - minimum.reduce(0, +)) / flexible)) : 0
            widths = zip(widths, minimum).map { $0.1 + ($0.0 - $0.1) * factor }
        }
        let totalWidth = widths.reduce(0, +) + gaps
        let spacer = hasSpacer ? max(width - totalWidth, 0) : 0
        let height = sizes.map(\.height).max() ?? 0
        var x = style == .rightAligned ? max(width - totalWidth, 0) : 0
        return widths.enumerated().map { index, itemWidth in
            if hasSpacer && index == widths.count - 1 { x += spacer + Self.actionSpacing }
            let start = (x * scale).rounded() / scale
            let end = ((x + itemWidth) * scale).rounded() / scale
            let frame = CGRect(x: rightToLeft ? width - end : start,
                               y: height - sizes[index].height, width: end - start, height: sizes[index].height)
            x += itemWidth + (stretched ? 0 : Self.actionSpacing)
            return frame
        }
    }
}

#if canImport(UIKit)
@MainActor
private enum StatusActionIconCache {
    static let images: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 128
        return cache
    }()
}
#endif
