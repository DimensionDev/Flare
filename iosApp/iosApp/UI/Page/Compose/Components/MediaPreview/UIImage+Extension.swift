//
//  UIImage.swift
//  
//
//  Created by MainasuK on 2021/11/19.
//

import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins
import func AVFoundation.AVMakeRect

extension UIImage {
    public func resize(for size: CGSize) -> UIImage {
        let rect = AVMakeRect(
            aspectRatio: self.size,
            insideRect: CGRect(origin: .zero, size: size)
        )
        return UIGraphicsImageRenderer(size: rect.size).image { context in
            self.draw(in: CGRect(origin: .zero, size: rect.size))
        }
    }
}

extension UIImage {
    
    public static func placeholder(size: CGSize = CGSize(width: 1, height: 1), color: UIColor) -> UIImage {
        let render = UIGraphicsImageRenderer(size: size)
        
        return render.image { (context: UIGraphicsImageRendererContext) in
            context.cgContext.setFillColor(color.cgColor)
            context.fill(CGRect(origin: .zero, size: size))
        }
    }
    
}

// refs: https://www.hackingwithswift.com/example-code/media/how-to-read-the-average-color-of-a-uiimage-using-ciareaaverage
extension UIImage {
    
    public var dominantColor: UIColor? {
        guard let inputImage = CIImage(image: self) else { return nil }
        
        let filter = CIFilter.areaAverage()
        filter.inputImage = inputImage
        filter.extent = inputImage.extent
        guard let outputImage = filter.outputImage else { return nil }
        
        var bitmap = [UInt8](repeating: 0, count: 4)
        let context = CIContext(options: [.workingColorSpace: kCFNull])
        context.render(outputImage, toBitmap: &bitmap, rowBytes: 4, bounds: CGRect(x: 0, y: 0, width: 1, height: 1), format: .RGBA8, colorSpace: nil)
        
        return UIColor(red: CGFloat(bitmap[0]) / 255, green: CGFloat(bitmap[1]) / 255, blue: CGFloat(bitmap[2]) / 255, alpha: CGFloat(bitmap[3]) / 255)
    }
}

extension UIImage {
    public var domainLumaCoefficientsStyle: UIUserInterfaceStyle? {
        guard let brightness = cgImage?.brightness else { return nil }
        return brightness > 100 ? .light : .dark // 0 ~ 255
    }
}
