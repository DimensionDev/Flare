//
//  MediaPreviewVideoViewModel.swift
//  TwidereX
//
//  Created by MainasuK on 2021-12-8.
//  Copyright 2021 Twidere. All rights reserved.
//

import os.log
import AVKit
import UIKit
import Combine
//import MaskCore

final class MediaPreviewVideoViewModel {
    
    let logger = Logger(subsystem: "MediaPreviewVideoViewModel", category: "ViewModel")
    
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
        case .video(let mediaContext):
            guard let assertURL = mediaContext.assetURL else { return }
            let playerItem = AVPlayerItem(url: assertURL)
            let _player = AVPlayer(playerItem: playerItem)
            self.player = _player
            
        case .gif(let mediaContext):
            guard let assertURL = mediaContext.assetURL else { return }
            let playerItem = AVPlayerItem(url: assertURL)
            let _player = AVQueuePlayer(playerItem: playerItem)
            _player.isMuted = true
            self.player = _player
            if let templateItem = _player.items().first {
                let _playerLooper = AVPlayerLooper(player: _player, templateItem: templateItem)
                self.playerLooper = _playerLooper
            }
            
        case .audio(let mediaContext):
            guard let assertURL = mediaContext.assetURL else { return }
            let playerItem = AVPlayerItem(url: assertURL)
            let _player = AVPlayer(playerItem: playerItem)
            self.player = _player
            
        }
        
 
        guard let player = player else {
            assertionFailure()
            return
        }
        
        // setup player state observer for video & audio
        $playbackState
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self = self else { return }
                
                // not trigger AudioSession for GIFV
                switch item {
                case .gif:    return
                default:      break
                }
                self.logger.log(level: .debug, "\((#file as NSString).lastPathComponent, privacy: .public)[\(#line, privacy: .public)], \(#function, privacy: .public): player state: \(status.description)")
                
                switch status {
                case .unknown, .buffering, .readyToPlay:
                    break
                case .playing:
                    try? AVAudioSession.sharedInstance().setCategory(.playback)
                    try? AVAudioSession.sharedInstance().setActive(true)
                case .paused, .stopped, .failed:
                    try? AVAudioSession.sharedInstance().setCategory(.ambient)  // set to ambient to allow mixed (needed for GIFV)
                    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                }
            }
            .store(in: &disposeBag)
        
        player.publisher(for: \.status, options: [.initial, .new])
            .sink { [weak self] status in
                guard let self = self else { return }
                switch status {
                case .failed:
                    self.playbackState = .failed
                case .readyToPlay:
                    self.playbackState = .readyToPlay
                case .unknown:
                    self.playbackState = .unknown
                @unknown default:
                    assertionFailure()
                }
            }
            .store(in: &disposeBag)
        
        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime, object: nil)
            .sink { [weak self] notification in
                guard let self = self else { return }
                guard let playerItem = notification.object as? AVPlayerItem,
                      let urlAsset = playerItem.asset as? AVURLAsset
                else { return }
                guard urlAsset.url == item.assetURL else { return }
                self.playbackState = .stopped
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
            case .video(let context):       return context.assetURL
            case .gif(let context):         return context.assetURL
            case .audio(let context):       return context.assetURL
            }
        }
        
        var previewURL: URL? {
            switch self {
            case .video(let context):       return context.previewURL
            case .gif(let context):         return context.previewURL
            case .audio(let context):       return context.previewURL
            }
        }
    }
    
    struct RemoteVideoContext {
        let assetURL: URL?
        let previewURL: URL?
        // let thumbnail: UIImage?
    }
    
    struct RemoteGIFContext {
        let assetURL: URL?
        let previewURL: URL?
    }
    
    struct RemoteAudioContext {
        let assetURL: URL?
        let previewURL: URL?
    }
}
