//
// MediaPreviewVideoViewModel.swift
// TwidereX
//
// Created by MainasuK on 2021-12-8.
// Copyright 2021 Twidere. All rights reserved.
//

import AVKit
import Combine
import shared
import UIKit

// import MaskCore

final class MediaPreviewVideoViewModel {
    var disposeBag = Set<AnyCancellable>()

    // input
    let mediaSaver: MediaSaver
    let item: Item

    // output
    public private(set) var player: AVPlayer?
    private var playerLooper: AVPlayerLooper?
    @Published var playbackState = PlaybackState.unknown

    init(mediaSaver: MediaSaver = DefaultMediaSaver.shared, item: Item) {
        self.mediaSaver = mediaSaver
        self.item = item
        // end init

        switch item {
        case let .video(mediaContext):
            guard let assertURL = mediaContext.assetURL else { return }
            let asset = AVURLAsset(url: assertURL, options: [
                "AVURLAssetHTTPHeaderFieldsKey": mediaContext.headers
            ])
            let playerItem = AVPlayerItem(asset: asset)
            let _player = AVPlayer(playerItem: playerItem)
            self.player = _player

        case let .gif(mediaContext):
            guard let assertURL = mediaContext.assetURL else { return }
            let asset = AVURLAsset(url: assertURL, options: [
                "AVURLAssetHTTPHeaderFieldsKey": mediaContext.headers
            ])
            let playerItem = AVPlayerItem(asset: asset)
            let _player = AVQueuePlayer(playerItem: playerItem)
            _player.isMuted = true
            self.player = _player
            if let templateItem = _player.items().first {
                let _playerLooper = AVPlayerLooper(player: _player, templateItem: templateItem)
                playerLooper = _playerLooper
            }

        case let .audio(mediaContext):
            guard let assertURL = mediaContext.assetURL else { return }
            let asset = AVURLAsset(url: assertURL, options: [
                "AVURLAssetHTTPHeaderFieldsKey": mediaContext.headers
            ])
            let playerItem = AVPlayerItem(asset: asset)
            let _player = AVPlayer(playerItem: playerItem)
            self.player = _player
        }

        guard let player else {
            assertionFailure()
            return
        }

        // setup player state observer for video & audio
        $playbackState
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self else { return }

                // not trigger AudioSession for GIFV
                switch item {
                case .gif: return
                default: break
                }
//               FlareLog.debug("[MediaPreviewVideoViewModel] player state: \(status.description)")

                switch status {
                case .unknown, .buffering, .readyToPlay:
                    break
                case .playing:
                    try? AVAudioSession.sharedInstance().setCategory(.playback)
                    try? AVAudioSession.sharedInstance().setActive(true)
                case .paused, .stopped, .failed:
                    try? AVAudioSession.sharedInstance().setCategory(.ambient) // set to ambient to allow mixed (needed for GIFV)
                    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                }
            }
            .store(in: &disposeBag)

        player.publisher(for: \.status, options: [.initial, .new])
            .sink { [weak self] status in
                guard let self else { return }
                switch status {
                case .failed:
                    playbackState = .failed
                case .readyToPlay:
                    playbackState = .readyToPlay
                case .unknown:
                    playbackState = .unknown
                @unknown default:
                    assertionFailure()
                }
            }
            .store(in: &disposeBag)

        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime, object: nil)
            .sink { [weak self] notification in
                guard let self else { return }
                guard let playerItem = notification.object as? AVPlayerItem,
                      let urlAsset = playerItem.asset as? AVURLAsset
                else { return }
                guard urlAsset.url == item.assetURL else { return }
                playbackState = .stopped
            }
            .store(in: &disposeBag)
    }
}

extension MediaPreviewVideoViewModel {
    enum Item {
        case video(RemoteVideoContext)
        case gif(RemoteGIFContext)
        case audio(RemoteAudioContext)

        var assetURL: URL? {
            switch self {
            case let .video(context): context.assetURL
            case let .gif(context): context.assetURL
            case let .audio(context): context.assetURL
            }
        }

        var previewURL: URL? {
            switch self {
            case let .video(context): context.previewURL
            case let .gif(context): context.previewURL
            case let .audio(context): context.previewURL
            }
        }

        var headers: [String: String] {
            switch self {
            case let .video(context): context.headers
            case let .gif(context): context.headers
            case let .audio(context): context.headers
            }
        }
    }

    struct RemoteVideoContext {
        let assetURL: URL?
        let previewURL: URL?
        let headers: [String: String]

        init(assetURL: URL?, previewURL: URL?, headers: [String: String] = [:]) {
            self.assetURL = assetURL
            self.previewURL = previewURL
            self.headers = headers
        }
    }

    struct RemoteGIFContext {
        let assetURL: URL?
        let previewURL: URL?
        let headers: [String: String]

        init(assetURL: URL?, previewURL: URL?, headers: [String: String] = [:]) {
            self.assetURL = assetURL
            self.previewURL = previewURL
            self.headers = headers
        }
    }

    struct RemoteAudioContext {
        let assetURL: URL?
        let previewURL: URL?
        let headers: [String: String]

        init(assetURL: URL?, previewURL: URL?, headers: [String: String] = [:]) {
            self.assetURL = assetURL
            self.previewURL = previewURL
            self.headers = headers
        }
    }
}
