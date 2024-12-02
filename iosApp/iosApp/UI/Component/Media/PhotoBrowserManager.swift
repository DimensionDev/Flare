import SwiftUI
import shared
import JXPhotoBrowser
import Kingfisher
import UIKit

class PhotoBrowserManager {
    static let shared = PhotoBrowserManager()
    
    private init() {}
    
    @MainActor
    func showPhotoBrowser(media: UiMedia, images: [UiMedia], initialIndex: Int, onDismiss: (() -> Void)? = nil) {
        let browser = JXPhotoBrowser()
        browser.scrollDirection = .horizontal
        browser.numberOfItems = { images.count }
        browser.pageIndex = initialIndex
        browser.transitionAnimator = JXPhotoBrowserFadeAnimator()
        
        // 添加页面指示器
        browser.pageIndicator = JXPhotoBrowserDefaultPageIndicator()
        
        browser.cellClassAtIndex = { index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video:
                return MediaBrowserVideoCell.self
            case .gif:
                return MediaBrowserVideoCell.self
            case .image:
                return JXPhotoBrowserImageCell.self
            default:
                return JXPhotoBrowserImageCell.self
            }
        }
        
        browser.reloadCellAtIndex = { context in
            guard context.index >= 0, context.index < images.count else { return }
            let media = images[context.index]
            
            switch onEnum(of: media) {
            case .video(let data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell {
                    cell.load(url: url, previewUrl: URL(string: data.thumbnailUrl), isGIF: false)
                }
            case .gif(let data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell {
                    cell.load(url: url, previewUrl: URL(string: data.previewUrl), isGIF: true)
                }
            case .image(let data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? JXPhotoBrowserImageCell {
                    cell.imageView.kf.setImage(with: url, options: [
                        .transition(.fade(0.25)),
                        .processor(DownsamplingImageProcessor(size: UIScreen.main.bounds.size))
                    ])
                }
            default:
                break
            }
        }
        
        browser.cellWillAppear = { cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.willDisplay()
                }
            case .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.willDisplay()
                }
            case .image:
                if let imageCell = cell as? JXPhotoBrowserImageCell {
                    // 如果需要对图片 cell 做额外处理
                }
            default:
                break
            }
        }
        
        if let onDismiss = onDismiss {
            browser.didDismiss = { _ in
                onDismiss()
            }
        }
        
        browser.show()
    }
}
