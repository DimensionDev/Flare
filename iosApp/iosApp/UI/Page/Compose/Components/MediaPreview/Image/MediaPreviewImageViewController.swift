//
//  MediaPreviewImageViewController.swift
//  TwidereX
//
//  Created by Cirno MainasuK on 2020-11-6.
//  Copyright © 2020 Twidere. All rights reserved.
//

import Combine
import os.log
import UIKit

// import MaskUI
import Kingfisher
import SwiftUICore
import WebKit

// import MaskCore
// import Resources
//
// protocol MediaPreviewImageViewControllerDelegate: AnyObject {
//    func mediaPreviewImageViewController(_ viewController: MediaPreviewImageViewController, tapGestureRecognizerDidTrigger tapGestureRecognizer: UITapGestureRecognizer)
//    func mediaPreviewImageViewController(_ viewController: MediaPreviewImageViewController, longPressGestureRecognizerDidTrigger longPressGestureRecognizer: UILongPressGestureRecognizer)
// }
//
// final class MediaPreviewImageViewController: UIViewController {
//
//    var disposeBag = Set<AnyCancellable>()
//    var viewModel: MediaPreviewImageViewModel!
//    weak var delegate: MediaPreviewImageViewControllerDelegate?
//
//    let progressBarView = ProgressBarView()
//
//    let containerView = UIView()
//    let previewImageView = MediaPreviewImageView()
//    let webView = WKWebView()
//
//    let tapGestureRecognizer = UITapGestureRecognizer()
//    let longPressGestureRecognizer = UILongPressGestureRecognizer()
//
//    deinit {
//        os_log("%{public}s[%{public}ld], %{public}s", ((#file as NSString).lastPathComponent), #line, #function)
//        previewImageView.imageView.kf.cancelDownloadTask()
//    }
//
// }
//
// extension MediaPreviewImageViewController {
//
//    override func viewDidLoad() {
//        super.viewDidLoad()
//
//        progressBarView.tintColor = Colors.white.color
//        progressBarView.translatesAutoresizingMaskIntoConstraints = false
//        view.addSubview(progressBarView)
//        NSLayoutConstraint.activate([
//            progressBarView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
//            progressBarView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
//            progressBarView.widthAnchor.constraint(equalToConstant: 120),
//            progressBarView.heightAnchor.constraint(equalToConstant: 44),
//        ])
//
//        containerView.translatesAutoresizingMaskIntoConstraints = false
//        view.addSubview(containerView)
//        NSLayoutConstraint.activate([
//            containerView.topAnchor.constraint(equalTo: view.topAnchor),
//            containerView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
//            containerView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
//            containerView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
//        ])
//
//        previewImageView.translatesAutoresizingMaskIntoConstraints = false
//        containerView.addSubview(previewImageView)
//        NSLayoutConstraint.activate([
//            previewImageView.frameLayoutGuide.topAnchor.constraint(equalTo: containerView.topAnchor),
//            previewImageView.frameLayoutGuide.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
//            previewImageView.frameLayoutGuide.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
//            previewImageView.frameLayoutGuide.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
//        ])
//
//        tapGestureRecognizer.addTarget(self, action: #selector(MediaPreviewImageViewController.tapGestureRecognizerHandler(_:)))
//        longPressGestureRecognizer.addTarget(self, action: #selector(MediaPreviewImageViewController.longPressGestureRecognizerHandler(_:)))
//        tapGestureRecognizer.require(toFail: previewImageView.doubleTapGestureRecognizer)
//        tapGestureRecognizer.require(toFail: longPressGestureRecognizer)
//        previewImageView.addGestureRecognizer(tapGestureRecognizer)
//        previewImageView.addGestureRecognizer(longPressGestureRecognizer)
//
//        progressBarView.isHidden = viewModel.item.thumbnail != nil
//        previewImageView.imageView.kf.setImage(
//            with: viewModel.item.source,
//            placeholder: nil,
//            options: nil,
//            progressBlock: { [weak self] receivedSize, totalSize in
//                guard let self = self else { return }
//                let progress = (Float(receivedSize) / Float(totalSize))
//                self.progressBarView.progress.value = CGFloat(progress)
//                os_log(.info, log: .debug, "%{public}s[%{public}ld], %{public}s: load %s progress: %.2f", ((#file as NSString).lastPathComponent), #line, #function, viewModel.item.source?.url.debugDescription ?? "nil", progress)
//            }
//        ) { result in
//            switch result {
//            case .failure:
//                // TODO:
//                break
//            case .success(let value):
//                self.progressBarView.isHidden = true
//                self.previewImageView.imageView.image = value.image
//                self.previewImageView.setup(image: value.image, container: self.previewImageView, forceUpdate: true)
//            }
//        }
//        os_log(.info, log: .debug, "%{public}s[%{public}ld], %{public}s: setImage url: %s", ((#file as NSString).lastPathComponent), #line, #function, viewModel.item.source?.url.debugDescription ?? "nil")
//
//        if let url = viewModel.item.source?.url, url.pathExtension.lowercased() == "svg" {
//            progressBarView.isHidden = true
//            previewImageView.isHidden = true
//
//            webView.translatesAutoresizingMaskIntoConstraints = false
//            containerView.addSubview(webView)
//            NSLayoutConstraint.activate([
//                webView.topAnchor.constraint(equalTo: containerView.topAnchor),
//                webView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
//                webView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
//                webView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
//            ])
//            let html = SVGImageWebView.html(for: url)
//            webView.navigationDelegate = self
//            webView.loadHTMLString(html, baseURL: nil)
//        }
//    }
//
// }
//
// extension MediaPreviewImageViewController {
//
//    @objc private func tapGestureRecognizerHandler(_ sender: UITapGestureRecognizer) {
//        os_log(.info, log: .debug, "%{public}s[%{public}ld], %{public}s", ((#file as NSString).lastPathComponent), #line, #function)
//        delegate?.mediaPreviewImageViewController(self, tapGestureRecognizerDidTrigger: sender)
//    }
//
//    @objc private func longPressGestureRecognizerHandler(_ sender: UILongPressGestureRecognizer) {
//        os_log(.info, log: .debug, "%{public}s[%{public}ld], %{public}s", ((#file as NSString).lastPathComponent), #line, #function)
//        switch sender.state {
//        case .began:
//            delegate?.mediaPreviewImageViewController(self, longPressGestureRecognizerDidTrigger: sender)
//        default:
//            break
//        }
//    }
//
// }
//
//// MARK: - ShareActivityProvider
// extension MediaPreviewImageViewController: ShareActivityProvider {
//    var activities: [Any] {
//        return []
//    }
//
//    var applicationActivities: [UIActivity] {
//        guard let url = viewModel.item.source?.url else { return [] }
//        return [
//            SavePhotoActivity(context: viewModel.context, url: url, resourceType: .photo)
//        ]
//    }
// }
//
//// MARK: - MediaPreviewTransitionViewController
// extension MediaPreviewImageViewController: MediaPreviewTransitionViewController {
//    var mediaPreviewTransitionContext: MediaPreviewTransitionContext? {
//        let transitionView = containerView
//        let _snapshot: UIView? = containerView.superview?.snapshotView(afterScreenUpdates: false)
//
//        guard let snapshot = _snapshot else {
//            return nil
//        }
//
//        return MediaPreviewTransitionContext(
//            transitionView: transitionView,
//            supplementaryViews: [progressBarView],
//            snapshot: snapshot,
//            snapshotTransitioning: snapshot
//        )
//    }
// }
//
//// MARK: - WKNavigationDelegate
// extension MediaPreviewImageViewController: WKNavigationDelegate {
//    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
//        webView.isOpaque = false
//        webView.backgroundColor = .clear
//        webView.scrollView.backgroundColor = .clear
//    }
// }
