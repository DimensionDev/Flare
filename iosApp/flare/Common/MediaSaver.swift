import Foundation
import Kingfisher
import UIKit
import SwiftUI
import Drops

class MediaSaver: NSObject {
    private override init() {}
    
    static let shared = MediaSaver()
    
    func saveImage(url: String) {
        if let remoteUrl = URL(string: url) {
            saveRemoteOriginalDataToPhotos(from: remoteUrl)
        }
    }

    private func saveRemoteOriginalDataToPhotos(from url: URL) {
        KingfisherManager.shared.downloader.downloadImage(with: url, options: [], progressBlock: nil) { result in
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
