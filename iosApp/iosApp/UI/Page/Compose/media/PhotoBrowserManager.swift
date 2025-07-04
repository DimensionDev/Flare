import JXPhotoBrowser
import Kingfisher
import Photos
import shared
import SwiftUI
import UIKit

class PhotoBrowserManager {
    static let shared = PhotoBrowserManager()
    private var currentVideoCell: MediaBrowserVideoCell?
    private var currentHeaders: [String: String]?

    private init() {}

    @MainActor
    func showPhotoBrowser(
        media _: UiMedia,
        images: [UiMedia],
        initialIndex: Int,
        headers: [String: String] = [:],
        onDismiss: (() -> Void)? = nil
    ) {
        currentHeaders = headers
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

        browser.reloadCellAtIndex = { [weak self] context in
            guard let self,
                  context.index >= 0,
                  context.index < images.count
            else { return }

            let media = images[context.index]
            let headers = currentHeaders ?? [:]

            switch onEnum(of: media) {
            case let .video(data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell
                {
                    cell.load(
                        url: url,
                        previewUrl: URL(string: data.thumbnailUrl),
                        isGIF: false,
                        headers: headers
                    )
                }
            case let .gif(data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell
                {
                    cell.load(
                        url: url,
                        previewUrl: URL(string: data.previewUrl),
                        isGIF: true,
                        headers: headers
                    )
                }
            case let .image(data):
                if let url = URL(string: data.url),
                   let previewUrl = URL(string: data.previewUrl),
                   let cell = context.cell as? JXPhotoBrowserImageCell
                {
                    // 移除已存在的原图标记（如果有）
                    for view in cell.imageView.subviews {
                        if view is OriginalImageMarkView {
                            view.removeFromSuperview()
                        }
                    }

                    // 创建headers修改器
                    let modifier = AnyModifier { request in
                        var r = request
                        for (key, value) in headers {
                            r.setValue(value, forHTTPHeaderField: key)
                        }
                        return r
                    }

                    // 加载预览图
                    cell.imageView.kf.setImage(
                        with: previewUrl,
                        placeholder: nil,
                        options: [
                            .processor(DownsamplingImageProcessor(size: UIScreen.main.bounds.size)),
                            .scaleFactor(UIScreen.main.scale),
                            .memoryCacheExpiration(.seconds(180)), // 3分钟内存缓存
                            .diskCacheExpiration(.days(14)), // 14天磁盘缓存
                            .requestModifier(modifier),
                        ]
                    ) { result in
                        switch result {
                        case .success:
                            // 预览图加载完成后，加载原图
                            cell.imageView.kf.setImage(
                                with: url,
                                placeholder: cell.imageView.image,
                                options: [
                                    .transition(.fade(0.5)),
                                    .loadDiskFileSynchronously,
                                    .cacheOriginalImage,
                                    .requestModifier(modifier),
                                ]
                            ) { result in
                                switch result {
                                case let .success(value):
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
                                            markView.bottomAnchor.constraint(equalTo: cell.imageView.bottomAnchor, constant: bottomPadding),
                                        ])
                                    }
                                case let .failure(error):
                                    FlareLog.error("PhotoBrowserManager load original image failed: \(url), error: \(error.localizedDescription)")
                                }
                            }
                        case let .failure(error):
                            FlareLog.error("PhotoBrowserManager load preview image failed: \(previewUrl), error: \(error.localizedDescription)")
                        }
                    }
                }
            default:
                break
            }
        }

        browser.cellWillAppear = { [weak self] cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    self?.currentVideoCell = videoCell
                    videoCell.willDisplay()
                }
            case .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    self?.currentVideoCell = videoCell
                    videoCell.willDisplay()
                }
            case .image:
                self?.currentVideoCell = nil
                if let imageCell = cell as? JXPhotoBrowserImageCell {
                    // 在 cell 即将显示时设置长按手势
                    imageCell.longPressedAction = { [weak self] cell, _ in
                        guard let self else { return }

                        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)

                        // 获取当前图片的 URL
                        let media = images[index]
                        if case let .image(data) = onEnum(of: media),
                           let url = URL(string: data.url)
                        {
                            // 添加下载选项
                            let downloadAction = UIAlertAction(title: "Save", style: .default) { _ in
                                // 先显示加载中的 Toast
                                // self.showToast(message: "正在获取原图...", icon: UIImage(systemName: "arrow.clockwise"))

                                // 使用 Kingfisher 获取原图
                                KingfisherManager.shared.retrieveImage(with: url, options: [.loadDiskFileSynchronously, .cacheOriginalImage]) { result in
                                    switch result {
                                    case let .success(value):
                                        // 保存原图到相册
                                        self.saveImageToAlbum(image: value.image) { success in
                                            DispatchQueue.main.async {
                                                if success {
//                                     self.showToast(message: "saved", icon: UIImage(systemName: "checkmark.circle.fill"))
                                                    ToastView(
                                                        icon: UIImage(systemName: "checkmark.circle.fill"),
                                                        message: " saved to photos"
                                                    ).show()

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
//                            self.showToast(message: "get original image failed", icon: UIImage(systemName: "xmark.circle.fill"))

                                            ToastView(
                                                icon: UIImage(systemName: "xmark.circle.fill"),
                                                message: " get original image failed"
                                            ).show()
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
                                    case let .success(value):
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
//                                            self.showToast(message: "", icon: UIImage(systemName: ""))
                                            ToastView(
                                                icon: UIImage(systemName: "xmark.circle.fill"),
                                                message: " get original image failed"
                                            ).show()
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
                self?.currentVideoCell = nil
            }
        }

        browser.cellWillDisappear = { [weak self] cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video, .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.didEndDisplaying()
                    // 停止音频会话
                    try? AVAudioSession.sharedInstance().setCategory(.ambient)
                    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                    self?.currentVideoCell = nil
                }
            default:
                break
            }
        }

        browser.willDismiss = { [weak self] _ in
            // 使用记录的当前视频 cell
            if let videoCell = self?.currentVideoCell {
                videoCell.didEndDisplaying()
                // 停止音频会话
                try? AVAudioSession.sharedInstance().setCategory(.ambient)
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                self?.currentVideoCell = nil
            }
            return true
        }

        if let onDismiss {
            browser.didDismiss = { [weak self] _ in
                // 确保在 dismiss 时也停止音频会话
                try? AVAudioSession.sharedInstance().setCategory(.ambient)
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                self?.currentVideoCell = nil
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
                    if let error {
                        FlareLog.error("PhotoBrowserManager save image failed: \(error.localizedDescription)")
                    }
                }
            default:
                completion(false)
                FlareLog.warning("PhotoBrowserManager no album access permission")
            }
        }
    }
}
