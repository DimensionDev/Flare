import SwiftUI
import shared
import JXPhotoBrowser
import Kingfisher
import UIKit

// 添加原图标记视图
class OriginalImageMarkView: UIView {
    private let stackView: UIStackView = {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 4
        stack.alignment = .center
        return stack
    }()
    
    private let iconLabel: UILabel = {
        let label = UILabel()
        label.text = "HD"
        label.textColor = .white
        label.font = .systemFont(ofSize: 12, weight: .bold)
        return label
    }()
    
    private let sizeLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.font = .systemFont(ofSize: 11)
        return label
    }()
    
    init(imageSize: Int?) {
        super.init(frame: .zero)
        setupView(imageSize: imageSize)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupView(imageSize: Int?) {
        backgroundColor = UIColor.black.withAlphaComponent(0.6)
        layer.cornerRadius = 4
        clipsToBounds = true
        
        addSubview(stackView)
        stackView.addArrangedSubview(iconLabel)
        if let size = imageSize {
            let mbSize = Double(size) / 1024.0 / 1024.0
            sizeLabel.text = String(format: "%.1fMB", mbSize)
            stackView.addArrangedSubview(sizeLabel)
        }
        
        stackView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stackView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            stackView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            stackView.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            stackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
    }
}

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
                    
                    // 先加载预览图
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
