//
// MediaPreviewVideoViewController.swift
// TwidereX
//
// Created by MainasuK on 2021-12-8.
// Copyright 2021 Twidere. All rights reserved.
//

import AVKit
import Combine
import os.log
import UIKit

// import MaskCore
// import MaskUI
import Kingfisher

final class MediaPreviewVideoViewController: UIViewController {
    var disposeBag = Set<AnyCancellable>()
    var viewModel: MediaPreviewVideoViewModel!

    let playerViewController = AVPlayerViewController()
    let previewImageView = UIImageView()

    // 添加 loading 指示器
    private lazy var loadingIndicator: UIActivityIndicatorView = {
        let indicator = UIActivityIndicatorView(style: .large)
        indicator.color = .white
        indicator.hidesWhenStopped = true
        indicator.translatesAutoresizingMaskIntoConstraints = false
        return indicator
    }()

    deinit {
        playerViewController.player?.pause()
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        os_log("%{public}s[%{public}ld], %{public}s", (#file as NSString).lastPathComponent, #line, #function)
    }
}

extension MediaPreviewVideoViewController {
    override func viewDidLoad() {
        super.viewDidLoad()

        addChild(playerViewController)
        playerViewController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(playerViewController.view)
        NSLayoutConstraint.activate([
            playerViewController.view.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            playerViewController.view.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            playerViewController.view.widthAnchor.constraint(equalTo: view.widthAnchor),
            playerViewController.view.heightAnchor.constraint(equalTo: view.heightAnchor),
        ])
        playerViewController.didMove(toParent: self)

        // 添加 loading 指示器
        view.addSubview(loadingIndicator)
        NSLayoutConstraint.activate([
            loadingIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])

        if let contentOverlayView = playerViewController.contentOverlayView {
            previewImageView.translatesAutoresizingMaskIntoConstraints = false
            contentOverlayView.addSubview(previewImageView)
            NSLayoutConstraint.activate([
                previewImageView.topAnchor.constraint(equalTo: contentOverlayView.topAnchor),
                previewImageView.leadingAnchor.constraint(equalTo: contentOverlayView.leadingAnchor),
                previewImageView.trailingAnchor.constraint(equalTo: contentOverlayView.trailingAnchor),
                previewImageView.bottomAnchor.constraint(equalTo: contentOverlayView.bottomAnchor),
            ])
        }

        playerViewController.view.backgroundColor = .clear
        playerViewController.player = viewModel.player
        playerViewController.delegate = self

        switch viewModel.item {
        case .gif:
            playerViewController.showsPlaybackControls = false
        default:
            break
        }

        // 监听视频加载状态
        viewModel.player?.currentItem?.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self else { return }
                switch status {
                case .unknown:
                    // 开始加载时显示 loading
                    loadingIndicator.startAnimating()
                case .readyToPlay:
                    // 视频准备好时停止 loading
                    loadingIndicator.stopAnimating()
                case .failed:
                    // 加载失败时停止 loading
                    loadingIndicator.stopAnimating()
                }
            }
            .store(in: &disposeBag)

        // 监听视频是否在播放
        viewModel.player?.publisher(for: \.timeControlStatus)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self else { return }
                switch status {
                case .waitingToPlayAtSpecifiedRate:
                    // 等待播放时（例如在缓冲）显示 loading
                    loadingIndicator.startAnimating()
                case .playing:
                    // 正在播放时隐藏 loading
                    loadingIndicator.stopAnimating()
                case .paused:
                    // 暂停时也隐藏 loading
                    loadingIndicator.stopAnimating()
                }
            }
            .store(in: &disposeBag)

        // 初始化时不自动播放
        viewModel.player?.pause()
        viewModel.playbackState = .paused

        if let previewURL = viewModel.item.previewURL {
            previewImageView.contentMode = .scaleAspectFit
            previewImageView.kf.setImage(
                with: previewURL,
                placeholder: UIImage.placeholder(color: .systemFill)
            )

            playerViewController.publisher(for: \.isReadyForDisplay)
                .receive(on: DispatchQueue.main)
                .sink { [weak self] isReadyForDisplay in
                    guard let self else { return }
                    previewImageView.isHidden = isReadyForDisplay
                }
                .store(in: &disposeBag)
        }
    }
}
 

// - AVPlayerViewControllerDelegate

extension MediaPreviewVideoViewController: AVPlayerViewControllerDelegate {}
