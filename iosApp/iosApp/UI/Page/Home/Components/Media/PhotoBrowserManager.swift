import SwiftUI
import shared
import JXPhotoBrowser
import Kingfisher
import UIKit
import Photos

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
                   let previewUrl = URL(string: data.previewUrl),
                   let cell = context.cell as? JXPhotoBrowserImageCell {
                    print("开始加载预览图: \(previewUrl)")
                    
                    // 移除已存在的原图标记（如果有）
                    cell.imageView.subviews.forEach { view in
                        if view is OriginalImageMarkView {
                            view.removeFromSuperview()
                        }
                    }
                    
                    // 加载预览图
                    cell.imageView.kf.setImage(
                        with: previewUrl,
                        placeholder: nil,
                        options: [
                            .transition(.fade(0.25)),
                            .processor(DownsamplingImageProcessor(size: UIScreen.main.bounds.size))
                        ]
                    ) { result in
                        switch result {
                        case .success(_):
                            print("预览图加载完成，开始加载原图: \(url)")
                            // 预览图加载完成后，加载原图
                            cell.imageView.kf.setImage(
                                with: url,
                                placeholder: cell.imageView.image,
                                options: [
                                    .transition(.fade(0.5)),
                                    .loadDiskFileSynchronously,
                                    .cacheOriginalImage
                                ]
                            ) { result in
                                switch result {
                                case .success(let value):
                                    print("原图加载完成: \(url)")
                                    // 添加原图标记，并显示图片大小
                                    DispatchQueue.main.async {
                                        let imageSize = value.image.jpegData(compressionQuality: 1.0)?.count
                                        let markView = OriginalImageMarkView(imageSize: imageSize)
                                        cell.imageView.addSubview(markView)
                                        markView.translatesAutoresizingMaskIntoConstraints = false
                                        
                                        // 计算宽高比
                                        let aspectRatio = value.image.size.width / value.image.size.height
                                        // 宽高比大于1为宽图，小于1为长图
                                        let bottomPadding: CGFloat = aspectRatio > 1 ? -10 : -30
                                        
                                        NSLayoutConstraint.activate([
                                            markView.trailingAnchor.constraint(equalTo: cell.imageView.trailingAnchor, constant: -16),
                                            markView.bottomAnchor.constraint(equalTo: cell.imageView.bottomAnchor, constant: bottomPadding)
                                        ])
                                    }
                                case .failure(let error):
                                    print("原图加载失败: \(url), 错误: \(error.localizedDescription)")
                                }
                            }
                        case .failure(let error):
                            print("预览图加载失败: \(previewUrl), 错误: \(error.localizedDescription)")
                        }
                    }
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
                    // 在 cell 即将显示时设置长按手势
                    imageCell.longPressedAction = { [weak self] cell, _ in
                        guard let self = self else { return }
                        print("长按手势被触发")
                        
                        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
                        
                        // 获取当前图片的 URL
                        let media = images[index]
                        if case let .image(data) = onEnum(of: media),
                           let url = URL(string: data.url) {
                            // 添加下载选项
                            let downloadAction = UIAlertAction(title: "Save", style: .default) { _ in
                                // 先显示加载中的 Toast
                                // self.showToast(message: "正在获取原图...", icon: UIImage(systemName: "arrow.clockwise"))
                                
                                // 使用 Kingfisher 获取原图
                                KingfisherManager.shared.retrieveImage(with: url, options: [.loadDiskFileSynchronously, .cacheOriginalImage]) { result in
                                    switch result {
                                    case .success(let value):
                                        // 保存原图到相册
                                        self.saveImageToAlbum(image: value.image) { success in
                                            DispatchQueue.main.async {
                                                if success {
                                                    self.showToast(message: "saved", icon: UIImage(systemName: "checkmark.circle.fill"))
                                                } else {
                                                    // 保存失败时显示错误提示
                                                    let alert = UIAlertController(
                                                        title: "save failed",
                                                        message: "please check the album access permission",
                                                        preferredStyle: .alert
                                                    )
                                                    alert.addAction(UIAlertAction(title: "OK", style: .default))
                                                    browser.present(alert, animated: true)
                                                }
                                            }
                                        }
                                    case .failure:
                                        DispatchQueue.main.async {
                                            self.showToast(message: "获取原图失败", icon: UIImage(systemName: "xmark.circle.fill"))
                                        }
                                    }
                                }
                            }
                            alert.addAction(downloadAction)
                            
                            // 添加分享选项
                            let shareAction = UIAlertAction(title: "Share", style: .default) { _ in
                                // 先显示加载中的 Toast
                                // self.showToast(message: "正在获取原图...", icon: UIImage(systemName: "arrow.clockwise"))
                                
                                // 使用 Kingfisher 获取原图
                                KingfisherManager.shared.retrieveImage(with: url, options: [.loadDiskFileSynchronously, .cacheOriginalImage]) { result in
                                    switch result {
                                    case .success(let value):
                                        DispatchQueue.main.async {
                                            let activityViewController = UIActivityViewController(
                                                activityItems: [value.image],
                                                applicationActivities: nil
                                            )
                                            
                                            // 在 iPad 上需要设置弹出位置
                                            if let popoverController = activityViewController.popoverPresentationController {
                                                popoverController.sourceView = cell
                                                popoverController.sourceRect = cell.bounds
                                            }
                                            
                                            browser.present(activityViewController, animated: true)
                                        }
                                    case .failure:
                                        DispatchQueue.main.async {
                                            self.showToast(message: "获取原图失败", icon: UIImage(systemName: "xmark.circle.fill"))
                                        }
                                    }
                                }
                            }
                            alert.addAction(shareAction)
                        }
                        
                        // 添加取消选项
                        let cancelAction = UIAlertAction(title: "Cancel", style: .cancel)
                        alert.addAction(cancelAction)
                        
                        // 在 iPad 上需要设置弹出位置
                        if let popoverController = alert.popoverPresentationController {
                            popoverController.sourceView = cell
                            popoverController.sourceRect = cell.bounds
                        }
                        
                        browser.present(alert, animated: true)
                    }
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
    
    // 保存图片到相册
    private func saveImageToAlbum(image: UIImage, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization { status in
            switch status {
            case .authorized:
                PHPhotoLibrary.shared().performChanges({
                    PHAssetChangeRequest.creationRequestForAsset(from: image)
                }) { success, error in
                    completion(success)
                    if let error = error {
                        print("保存图片失败: \(error.localizedDescription)")
                    }
                }
            default:
                completion(false)
                print("没有相册访问权限")
            }
        }
    }
    
    private func showToast(message: String, icon: UIImage? = nil) {
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first {
            let toastView = ToastView(
                icon: icon ?? UIImage(systemName: "checkmark.circle.fill"),
                message: message
            )
            toastView.show(in: window)
        }
    }
}
