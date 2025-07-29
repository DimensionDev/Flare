import AVKit
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import OrderedCollections
import shared
import SwiftUI

// - VideoCell
class MediaBrowserVideoCell: UIView, UIGestureRecognizerDelegate {
    weak var photoBrowser: JXPhotoBrowser?
    private var videoViewController: MediaPreviewVideoViewController?
    private let mediaSaver: MediaSaver
    private var currentURL: URL?
    private var existedPan: UIPanGestureRecognizer?

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    required init(frame: CGRect, mediaSaver: MediaSaver = DefaultMediaSaver.shared) {
        self.mediaSaver = mediaSaver
        super.init(frame: frame)
        setupUI()
    }

    private func setupUI() {
        backgroundColor = .black

        // 添加拖动手势
        let pan = UIPanGestureRecognizer(target: self, action: #selector(onPan(_:)))
        pan.delegate = self
        addGestureRecognizer(pan)
        existedPan = pan

        // 添加点击手势
        let tap = UITapGestureRecognizer(target: self, action: #selector(click))
        addGestureRecognizer(tap)
    }

    @objc private func onPan(_ pan: UIPanGestureRecognizer) {
        guard let parentView = superview else { return }

        let translation = pan.translation(in: parentView)
        let scale = 1 - abs(translation.y) / parentView.bounds.height

        switch pan.state {
        case .changed:
            // 跟随手指移动
            transform = CGAffineTransform(translationX: translation.x, y: translation.y)
                .scaledBy(x: scale, y: scale)

            // 调整背景透明度
            parentView.backgroundColor = UIColor.black.withAlphaComponent(scale)

        case .ended, .cancelled:
            let velocity = pan.velocity(in: parentView)
            let shouldDismiss = abs(translation.y) > 100 || abs(velocity.y) > 500

            if shouldDismiss {
                // 关闭浏览器
                photoBrowser?.dismiss()
            } else {
                // 恢复原位
                UIView.animate(withDuration: 0.3) {
                    self.transform = .identity
                    parentView.backgroundColor = .black
                }
            }

        default:
            break
        }
    }

    // UIGestureRecognizerDelegate
    public func gestureRecognizer(_: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith _: UIGestureRecognizer) -> Bool {
        // 允许同时识别多个手势
        true
    }

    func load(url: URL, previewUrl: URL?, isGIF: Bool, headers: [String: String] = [:]) {
        // 如果已经加载了相同的 URL，不需要重新加载
        if currentURL == url {
            return
        }

        // 先清理旧的资源
        cleanupCurrentVideo()
        currentURL = url

        // Create view model
        let viewModel = MediaPreviewVideoViewModel(
            mediaSaver: mediaSaver,
            item: isGIF ? .gif(.init(assetURL: url, previewURL: previewUrl, headers: headers))
                : .video(.init(assetURL: url, previewURL: previewUrl, headers: headers))
        )

        // Create and setup new view controller
        let newVC = MediaPreviewVideoViewController()
        newVC.viewModel = viewModel
        videoViewController = newVC

        // Add to view hierarchy
        addSubview(newVC.view)
        newVC.view.translatesAutoresizingMaskIntoConstraints = false

        // Add as child view controller
        if let parentViewController = findViewController() {
            parentViewController.addChild(newVC)
            newVC.didMove(toParent: parentViewController)
        }

        NSLayoutConstraint.activate([
            newVC.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            newVC.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            newVC.view.topAnchor.constraint(equalTo: topAnchor),
            newVC.view.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])

        // 默认不自动播放，等待 willDisplay 时再播放
        viewModel.player?.pause()
    }

    private func cleanupCurrentVideo() {
        // 暂停当前播放的视频
        if let viewModel = videoViewController?.viewModel,
           let player = viewModel.player
        {
            player.pause()
            player.replaceCurrentItem(with: nil) // 清除播放器项
        }

        // 移除视频控制器
        if let vc = videoViewController {
            vc.willMove(toParent: nil)
            vc.view.removeFromSuperview()
            vc.removeFromParent()
            videoViewController = nil
        }
        currentURL = nil
    }

    func willDisplay() {
        // 显示时开始播放视频
        if let viewModel = videoViewController?.viewModel,
           let player = viewModel.player
        {
            player.play()
        }
    }

    func didEndDisplaying() {
        cleanupCurrentVideo()
    }

    @objc private func click() {
        photoBrowser?.dismiss()
    }

    // Helper method to find parent view controller
    private func findViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let nextResponder = responder?.next {
            if let viewController = nextResponder as? UIViewController {
                return viewController
            }
            responder = nextResponder
        }
        return nil
    }
}

// - JXPhotoBrowserCell
extension MediaBrowserVideoCell: JXPhotoBrowserCell {
    static func generate(with browser: JXPhotoBrowser) -> Self {
        let instance = Self(frame: .zero)
        instance.photoBrowser = browser
        return instance
    }
}
