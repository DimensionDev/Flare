import Foundation
import Kingfisher
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
                UIImageWriteToSavedPhotosAlbum(v.image, self, #selector(self.saveCompleted), nil)
            case .failure:
                DispatchQueue.main.async {
                    Drops.show(
                        .init(
                            title: .init(localized: "notification_save_image_error"),
                            icon: .faCircleExclamation
                        )
                    )
                }
            }
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
    
    @objc func saveCompleted(_ image: UIImage, didFinishSavingWithError error: Error?, contextInfo: UnsafeRawPointer) {
        DispatchQueue.main.async {
            Drops.show(
                .init(
                    title: .init(localized: "notification_save_image_success"),
                    icon: .faCircleCheck
                )
            )
        }
    }
}
