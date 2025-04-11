import Foundation
import Tiercel
import CommonCrypto

class DownloadHelper {
    static let shared = DownloadHelper()
    
 
    private let previewImageKeyPrefix = "download_preview_"
    
    private init() {}
    
    
    func startDownload(url: String, fileName: String? = nil) {
        let downloadFileName = fileName ?? URL(string: url)?.lastPathComponent ?? UUID().uuidString
        DownloadManager.shared.download(url: url, fileName: downloadFileName)
    }
    
  
    func startMediaDownload(url: String, mediaType: MediaType, previewImageUrl: String? = nil) {
        let fileName = getFileNameWithExtension(url: url, mediaType: mediaType)
        let task = DownloadManager.shared.download(url: url, fileName: fileName)
        
        
        if let task = task, let previewUrl = previewImageUrl {
            savePreviewImageUrl(for: task.url.absoluteString, previewUrl: previewUrl)
        }
    }
    
    
    private func getFileNameWithExtension(url: String, mediaType: MediaType) -> String {
     
        if let urlObj = URL(string: url), !urlObj.lastPathComponent.isEmpty {
            let fileName = urlObj.lastPathComponent
           
            if fileName.contains(".") {
                return fileName
            }
        }
        
        
        let uuid = UUID().uuidString
        switch mediaType {
        case .image:
            return "\(uuid).jpg"
        case .gif:
            return "\(uuid).gif"
        case .video:
            return "\(uuid).mp4"
        case .audio:
            return "\(uuid).mp3"
        case .unknown:
            return "\(uuid)"
        }
    }
    
   
    private func savePreviewImageUrl(for downloadUrl: String, previewUrl: String) {
        let key = getPreviewImageKey(for: downloadUrl)
        UserDefaults.standard.set(previewUrl, forKey: key)
    }
    
  
    func getPreviewImageUrl(for downloadUrl: String) -> String? {
        let key = getPreviewImageKey(for: downloadUrl)
        return UserDefaults.standard.string(forKey: key)
    }
    
    
    func removePreviewImageUrl(for downloadUrl: String) {
        let key = getPreviewImageKey(for: downloadUrl)
        UserDefaults.standard.removeObject(forKey: key)
    }
    
  
    private func getPreviewImageKey(for downloadUrl: String) -> String {
       
        return previewImageKeyPrefix + String(downloadUrl.hashValue)
    }
}

 
enum MediaType {
    case image
    case gif
    case video
    case audio
    case unknown
} 