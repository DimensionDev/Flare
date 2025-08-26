//
//  CGImage+Extension.swift
//
//
//  Created by MainasuK on 2022-8-31.
//

import CoreImage

public extension CGImage {
    // Reference
    // https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.709-6-201506-I!!PDF-E.pdf
    // Luma Y = 0.2126R + 0.7152G + 0.0722B
    var brightness: CGFloat? {
        let context = CIContext() // default with metal accelerate
        let ciImage = CIImage(cgImage: self)
        let rec709Image = context.createCGImage(
            ciImage,
            from: ciImage.extent,
            format: .RGBA8,
            colorSpace: CGColorSpace(name: CGColorSpace.itur_709) // BT.709 a.k.a Rec.709
        )
        guard let image = rec709Image,
              image.bitsPerPixel == 32,
              let data = rec709Image?.dataProvider?.data,
              let pointer = CFDataGetBytePtr(data) else { return nil }

        let length = CFDataGetLength(data)
        guard length > 0 else { return nil }

        var luma: CGFloat = 0.0
        for i in stride(from: 0, to: length, by: 4) {
            let r = pointer[i]
            let g = pointer[i + 1]
            let b = pointer[i + 2]
            let Y = 0.2126 * CGFloat(r) + 0.7152 * CGFloat(g) + 0.0722 * CGFloat(b)
            luma += Y
        }
        luma /= CGFloat(width * height)
        return luma
    }
}
