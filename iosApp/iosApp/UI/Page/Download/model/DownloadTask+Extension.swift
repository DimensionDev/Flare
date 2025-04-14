import Foundation
import Tiercel
import ObjectiveC

extension DownloadTask {
    private static var PREVIEW_IMAGE_KEY: Void?
    
    var previewImageUrl: String? {
        get {
            return objc_getAssociatedObject(self, &Self.PREVIEW_IMAGE_KEY) as? String
        }
        set {
            objc_setAssociatedObject(self, &Self.PREVIEW_IMAGE_KEY, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
    }
} 