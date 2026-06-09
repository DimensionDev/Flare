import Foundation
import Kingfisher
import Photos
import UIKit
import SwiftUI
import Drops

class MediaSaver: NSObject {
    private override init() {}
    
    static let shared = MediaSaver()
    
    func saveImage(url: String, customHeaders: [String: String]? = nil) {
        if let remoteUrl = URL(string: url) {
            saveRemoteOriginalDataToPhotos(from: remoteUrl, customHeaders: customHeaders)
        }
    }

    private func saveRemoteOriginalDataToPhotos(from url: URL, customHeaders: [String: String]?) {
        KingfisherManager.shared.downloader.downloadImage(with: url, options: kingfisherOptions(customHeaders: customHeaders), progressBlock: nil) { result in
            switch result {
            case .success(let v):
                self.saveOriginalDataToPhotos(v.originalData)
            case .failure:
                self.showSaveResult(success: false)
            }
        }
    }

    nonisolated private func saveOriginalDataToPhotos(_ data: Data) {
        PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset()
            request.addResource(with: .photo, data: data, options: nil)
        } completionHandler: { success, error in
            self.showSaveResult(success: success && error == nil)
        }
    }

    private func kingfisherOptions(customHeaders: [String: String]?) -> KingfisherOptionsInfo {
        guard let customHeaders, !customHeaders.isEmpty else {
            return []
        }
        return [.requestModifier(AnyModifier { request in
            var request = request
            for (key, value) in customHeaders {
                request.setValue(value, forHTTPHeaderField: key)
            }
            return request
        })]
    }
    
    nonisolated private func showSaveResult(success: Bool) {
        DispatchQueue.main.async {
            Drops.show(
                .init(
                    title: .init(localized: success ? "notification_save_image_success" : "notification_save_image_error"),
                    icon: success ? .faCircleCheck : .faCircleExclamation
                )
            )
        }
    }
}
