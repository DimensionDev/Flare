//
//  UIImage+Extension.swift
//
//
//  Created by MainasuK on 2021/11/19.
//

import func AVFoundation.AVMakeRect
import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit
 
public extension UIImage {
    static func placeholder(size: CGSize = CGSize(width: 1, height: 1), color: UIColor) -> UIImage {
        let render = UIGraphicsImageRenderer(size: size)

        return render.image { (context: UIGraphicsImageRendererContext) in
            context.cgContext.setFillColor(color.cgColor)
            context.fill(CGRect(origin: .zero, size: size))
        }
    }
}
 
