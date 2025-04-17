import SwiftUI
import UIKit

// Copied from ExyteChat source code to enable accurate text layout calculations
// Source: package_source/Chat/Sources/ExyteChat/Extensions/AttributedString+Extensions.swift

extension AttributedString {

    func width(withConstrainedWidth width: CGFloat, font: UIFont) -> CGFloat {
        let constraintRect = CGSize(width: width, height: .greatestFiniteMagnitude)
        let boundingBox = toAttrStringSimple(font: font).boundingRect(with: constraintRect, options: .usesLineFragmentOrigin, context: nil)

        return ceil(boundingBox.width)
    }

    func toAttrStringSimple(font: UIFont) -> NSAttributedString {
        let plainString = String(self.characters)
        return NSAttributedString(string: plainString, attributes: [.font: font])
    }

    func toAttrStringComplex(font: UIFont) -> NSAttributedString {
        var str = self
        str.font = font
        return NSAttributedString(str)
    }

    public func lastLineWidth(labelWidth: CGFloat, font: UIFont) -> CGFloat {
        let attrString = toAttrStringSimple(font: font)
        let availableSize = CGSize(width: labelWidth, height: .infinity)
        let layoutManager = NSLayoutManager()
        let textContainer = NSTextContainer(size: availableSize)
        let textStorage = NSTextStorage(attributedString: attrString)

        layoutManager.addTextContainer(textContainer)
        textStorage.addLayoutManager(layoutManager)

        textContainer.lineFragmentPadding = 0.0
        textContainer.lineBreakMode = .byWordWrapping
        textContainer.maximumNumberOfLines = 0

        layoutManager.ensureLayout(for: textContainer)

        guard attrString.length > 0 else { return 0 }
        let lastCharacterIndex = attrString.length - 1
        let lastGlyphIndex = layoutManager.glyphIndexForCharacter(at: lastCharacterIndex)

        let lastLineFragmentRect = layoutManager.lineFragmentUsedRect(
            forGlyphAt: lastGlyphIndex,
            effectiveRange: nil)

        return ceil(lastLineFragmentRect.maxX)
    }

    func numberOfLines(labelWidth: CGFloat, font: UIFont) -> Int {
        let attrString = toAttrStringSimple(font: font)
        let availableSize = CGSize(width: labelWidth, height: .infinity)
        let textSize = attrString.boundingRect(with: availableSize, options: .usesLineFragmentOrigin, context: nil)
        let lineHeight = font.lineHeight
        guard lineHeight > 0 else { return 0 }
        return Int(ceil(textSize.height / lineHeight))
    }

}

public extension AttributedString {
    var urls: [URL] {
        runs[\.link].map { (link, range) in
            link?.absoluteURL
        }
        .compactMap { $0 }
    }
}