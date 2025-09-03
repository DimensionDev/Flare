import AVFoundation
import Foundation
import MediaPlayer
import shared
import SwiftUI
import UIKit

enum PodcastPlaybackState: Equatable {
    case stopped
    case loading
    case playing
    case paused
    case failed(Error?)

    static func == (lhs: PodcastPlaybackState, rhs: PodcastPlaybackState) -> Bool {
        switch (lhs, rhs) {
        case (.stopped, .stopped), (.loading, .loading), (.playing, .playing), (.paused, .paused):
            true
        case (.failed, .failed):
            true
        default:
            false
        }
    }
}

@Observable
class IOSPodcastManager {
    static let shared = IOSPodcastManager()

    private(set) var currentPodcast: UiPodcast?
    private(set) var isPlaying: Bool = false
    private(set) var playbackState: PodcastPlaybackState = .stopped
    private(set) var duration: Double?
    private(set) var canSeek: Bool = false
    private(set) var isSeeking: Bool = false
    private(set) var currentTime: Double = 0.0

    var currentPlaybackTime: Double {
        currentTime
    }

    private var player: AVPlayer?
    private var playerItem: AVPlayerItem?
    private var playerItemStatusObserver: NSKeyValueObservation?
    private var playerItemDurationObserver: NSKeyValueObservation?
    private var playerItemSeekableObserver: NSKeyValueObservation?
    private var timeObserverToken: Any?
    private var playerItemDidPlayToEndObserver: NSObjectProtocol?

    private var artworkCache: [String: MPMediaItemArtwork] = [:]
    private var artworkLoadingTasks: [String: Task<Void, Never>] = [:]

    private init() {
        configureAudioSession()
        setupRemoteTransportControls()
    }

    deinit {
        removePlayerItemDidPlayToEndObserver()
    }

    func getPlayingPodcast() -> UiPodcast? {
        currentPodcast
    }

    func playPodcast(podcast: UiPodcast) {
        FlareLog.debug("iOSPodcastManager Request received: playPodcast")
        FlareLog.debug("iOSPodcastManager ID: \(podcast.id)")
        FlareLog.debug("iOSPodcastManager Title: \(podcast.title)")
        FlareLog.debug("iOSPodcastManager Playback URL: \(podcast.playbackUrl ?? "Not Available")")
        FlareLog.debug("iOSPodcastManager Is Ended: \(podcast.ended)")

        stopPodcastInternal()

        guard let urlString = podcast.playbackUrl, let url = URL(string: urlString) else {
            FlareLog.error("iOSPodcastManager Error: Invalid playback URL.")
            DispatchQueue.main.async {
                self.currentPodcast = podcast
                self.playbackState = .failed(NSError(domain: "PodcastError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid playback URL"]))
                self.isPlaying = false
                self.updateNowPlayingInfo()
            }
            return
        }

        //  let urlString = "https://prod-fastly-ap-southeast-1.video.pscp.tv/Transcoding/v1/hls/kThmok5-gtIE6I8bO4uQySMbVRGxqsWF9vP8g3UYkRoKNJn0Y6l-qvqAp-JTQKavP5hM0v4YjSHtEmuMydejJA/non_transcode/ap-southeast-1/periscope-replay-direct-prod-ap-southeast-1-public/audio-space/playlist_16702103663266561072.m3u8?type=replay"
        guard let url = URL(string: urlString) else {
            FlareLog.error("iOSPodcastManager Error: Hardcoded Replay URL is invalid?!")
            DispatchQueue.main.async {
                self.playbackState = .failed(NSError(domain: "PodcastError", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hardcoded test URL invalid"]))
                self.currentPodcast = podcast
            }
            return
        }
        FlareLog.debug("iOSPodcastManager Using HARDCODED Replay URL for testing: \(urlString)")

        DispatchQueue.main.async {
            self.currentPodcast = podcast
            self.playbackState = .loading
            self.isPlaying = false
            self.duration = nil
            self.currentTime = 0.0
            self.canSeek = false
            self.isSeeking = false
            self.updateNowPlayingInfo()
        }

        // --- background player , locked screen mini player (Asynchronous) ---
        fetchAndCacheArtwork(for: podcast)

        playerItem = AVPlayerItem(url: url)
        addObservers(to: playerItem!)

        player = AVPlayer(playerItem: playerItem)
    }

    func stopPodcast() {
        FlareLog.debug("iOSPodcastManager Request received: stopPodcast")
        stopPodcastInternal()
        DispatchQueue.main.async {
            if let podcastId = self.currentPodcast?.id {
                self.artworkLoadingTasks[podcastId]?.cancel()
                self.artworkLoadingTasks.removeValue(forKey: podcastId)
            }
            self.currentPodcast = nil
            self.playbackState = .stopped
            self.isPlaying = false
            self.duration = nil
            self.currentTime = 0.0
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        }
    }

    func pause() {
        FlareLog.debug("iOSPodcastManager Pause requested")
        guard player?.rate != 0 else {
            FlareLog.debug("iOSPodcastManager Player not playing, pause ignored.")
            return
        }
        player?.pause()
        DispatchQueue.main.async {
            self.playbackState = .paused
            self.isPlaying = false
            self.updateNowPlayingInfo()
        }
    }

    func resume() {
        FlareLog.debug("iOSPodcastManager Resume requested")
        guard player?.currentItem != nil else {
            FlareLog.debug("iOSPodcastManager No player item, resume ignored.")
            return
        }
        player?.play()
        DispatchQueue.main.async {
            self.playbackState = .playing
            self.isPlaying = true
            self.updateNowPlayingInfo()
        }
    }

    func seek(to time: Double) {
        FlareLog.debug("iOSPodcastManager Seek requested to \(time)")
        guard canSeek else {
            FlareLog.debug("iOSPodcastManager Seek ignored: Item not seekable.")
            return
        }
        guard let player else {
            FlareLog.debug("iOSPodcastManager Seek ignored: Player not available.")
            return
        }
        guard time >= 0, duration == nil || time <= duration! else {
            FlareLog.debug("iOSPodcastManager Seek ignored: Target time out of bounds (0 - \(duration ?? -1)).")
            return
        }

        DispatchQueue.main.async {
            self.isSeeking = true
        }

        let targetTime = CMTime(seconds: time, preferredTimescale: 600)
        player.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] completed in
            guard let self else { return }

            DispatchQueue.main.async {
                self.isSeeking = false
            }
            if completed {
                FlareLog.debug("iOSPodcastManager Seek completed successfully to \(time).")
                DispatchQueue.main.async {
                    self.currentTime = time
                    self.updateNowPlayingInfo()
                }
            } else {
                FlareLog.warning("iOSPodcastManager Seek did not complete.")
            }
        }
    }

    private func stopPodcastInternal() {
        player?.pause()
        removeTimeObserver()
        removeObservers()
        playerItem = nil
        player = nil
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            FlareLog.debug("iOSPodcastManager Audio session configured for playback.")
        } catch {
            FlareLog.error("iOSPodcastManager Error setting up audio session: \(error.localizedDescription)")
        }
    }

    private func addObservers(to item: AVPlayerItem) {
        removeObservers()

        playerItemStatusObserver = item.observe(\.status, options: [.new, .initial]) { [weak self] playerItem, _ in
            guard let self else { return }
            DispatchQueue.main.async {
                FlareLog.debug("iOSPodcastManager PlayerItem Status Changed")
                switch playerItem.status {
                case .readyToPlay:
                    FlareLog.debug("iOSPodcastManager Status: Ready to Play")
                    self.updateDuration(from: playerItem)
                    self.updateSeekability(from: playerItem)

                    FlareLog.debug("iOSPodcastManager Initiating playback.")
                    self.player?.play()
                    self.playbackState = .playing
                    self.isPlaying = true
                    self.addTimeObserver()
                    self.updateNowPlayingInfo()

                case .failed:
                    FlareLog.error("iOSPodcastManager Status: Failed")
                    FlareLog.error("iOSPodcastManager Error: \(playerItem.error?.localizedDescription ?? "Unknown error")")
                    self.playbackState = .failed(playerItem.error)
                    self.isPlaying = false
                    self.removeTimeObserver()
                    self.updateNowPlayingInfo()

                case .unknown:
                    FlareLog.debug("iOSPodcastManager Status: Unknown")

                    if self.playbackState != .paused, self.playbackState != .stopped, self.playbackState == .loading {
                    } else if self.playbackState != .failed(nil), self.playbackState != .paused, self.playbackState != .stopped {
                        self.playbackState = .loading
                        self.isPlaying = false
                    }

                @unknown default:
                    FlareLog.debug("iOSPodcastManager Status: Unknown (default case)")
                    if self.playbackState != .paused, self.playbackState != .failed(nil), self.playbackState != .stopped {
                        self.playbackState = .loading
                        self.isPlaying = false
                    }
                }

                self.updateNowPlayingInfo()
            }
        }

        // current time Duration Observer
        playerItemDurationObserver = item.observe(\.duration, options: [.new]) { [weak self] playerItem, _ in
            self?.updateDuration(from: playerItem)
        }

        // Seekable Ranges Observer
        playerItemSeekableObserver = item.observe(\.seekableTimeRanges, options: [.new]) { [weak self] playerItem, _ in
            self?.updateSeekability(from: playerItem)
        }

        // *** Add DidPlayToEndTime Observer ***
        removePlayerItemDidPlayToEndObserver()

        playerItemDidPlayToEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            FlareLog.debug("iOSPodcastManager Notification: AVPlayerItemDidPlayToEndTime received.")
            self?.handlePlaybackDidEnd()
        }
        FlareLog.debug("iOSPodcastManager AVPlayerItemDidPlayToEndTime observer added.")
    }

    private func removeObservers() {
        playerItemStatusObserver?.invalidate()
        playerItemDurationObserver?.invalidate()
        playerItemSeekableObserver?.invalidate()
        playerItemStatusObserver = nil
        playerItemDurationObserver = nil
        playerItemSeekableObserver = nil

        removePlayerItemDidPlayToEndObserver()

        FlareLog.debug("iOSPodcastManager All observers removed.")
    }

    private func handlePlaybackDidEnd() {
        FlareLog.debug("iOSPodcastManager Handling playback end.")
        DispatchQueue.main.async {
            self.playbackState = .paused
            self.isPlaying = false

            // self.currentTime = 0.0
            // self.currentTimeSubject.send(0.0)

            // Update lock screen info to show paused state at the end time
            self.updateNowPlayingInfo()
        }
    }

    private func removePlayerItemDidPlayToEndObserver() {
        if let observer = playerItemDidPlayToEndObserver {
            NotificationCenter.default.removeObserver(observer)
            playerItemDidPlayToEndObserver = nil
            FlareLog.debug("iOSPodcastManager AVPlayerItemDidPlayToEndTime observer removed.")
        }
    }

    // Time Observation
    private func addTimeObserver() {
        // Avoid adding multiple observers
        guard timeObserverToken == nil else { return }
        guard let player else { return } // Ensure player exists

        // Observe time every 1 second
        let interval = CMTime(seconds: 1.0, preferredTimescale: 600)
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self else { return }
            let currentTimeSeconds = CMTimeGetSeconds(time)

            // Only update if time has actually changed significantly and not during seeking
            if abs(currentTimeSeconds - currentTime) > 0.01, !isSeeking {
                currentTime = currentTimeSeconds
                updateNowPlayingInfo()
            }
        }
        FlareLog.debug("iOSPodcastManager Periodic time observer added (interval: 1.0s)")
    }

    private func removeTimeObserver() {
        if let token = timeObserverToken {
            player?.removeTimeObserver(token)
            timeObserverToken = nil
            FlareLog.debug("iOSPodcastManager Periodic time observer removed.")
        }
    }

    // Now Playing Info / Remote Commands

    private func setupRemoteTransportControls() {
        let commandCenter = MPRemoteCommandCenter.shared()

        // Add handler for Play Command
        commandCenter.playCommand.addTarget { [unowned self] _ -> MPRemoteCommandHandlerStatus in
            FlareLog.debug("iOSPodcastManager RemoteCommand Play command received")
            resume()
            return .success
        }

        // Add handler for Pause Command
        commandCenter.pauseCommand.addTarget { [unowned self] _ -> MPRemoteCommandHandlerStatus in
            FlareLog.debug("iOSPodcastManager RemoteCommand Pause command received")
            pause()
            return .success
        }

        // Optional: Add handler for seek/change playback position
        // commandCenter.changePlaybackPositionCommand.addTarget { [unowned self] event -> MPRemoteCommandHandlerStatus in
        //     if let positionEvent = event as? MPChangePlaybackPositionCommandEvent {
        //         FlareLog.debug("[RemoteCommand] Change position to: \(positionEvent.positionTime)")
        //         self.seek(to: positionEvent.positionTime)
        //         return .success
        //     }
        //     return .commandFailed
        // }

        // Enable the commands
        commandCenter.playCommand.isEnabled = true
        commandCenter.pauseCommand.isEnabled = true
        // commandCenter.changePlaybackPositionCommand.isEnabled = true // Enable if implemented

        FlareLog.debug("iOSPodcastManager Remote transport controls configured.")
    }

    private func updateNowPlayingInfo() {
        guard let podcast = currentPodcast else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var nowPlayingInfo = [String: Any]()
        nowPlayingInfo[MPMediaItemPropertyTitle] = podcast.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = podcast.creator.name.raw

        if let duration {
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration
        }
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime

        // Determine playback rate based on state (important for lock screen controls)
        let playbackRate: Float = switch playbackState {
        case .playing: 1.0
        case .paused, .stopped, .loading, .failed: 0.0
        }
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate

        // *** Add Artwork from cache if available ***
        if let artwork = artworkCache[podcast.id] {
            nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
            FlareLog.debug("iOSPodcastManager Adding cached artwork to Now Playing Info.")
        } else {
            FlareLog.debug("iOSPodcastManager Artwork not yet in cache for Now Playing Info.")
            // Optional: Could trigger fetch here if needed, but playPodcast handles initial fetch
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
        // Limit logging noise maybe
        // FlareLog.debug("[iOSPodcastManager] Now Playing Info Updated: Title=\(podcast.title), Time=\(self.currentTime)")
    }

    private func updateDuration(from playerItem: AVPlayerItem) {
        let duration = playerItem.duration
        let seconds = CMTimeGetSeconds(duration)
        DispatchQueue.main.async {
            FlareLog.debug("iOSPodcastManager Duration Check")
            if seconds.isNaN || seconds.isInfinite || seconds <= 0 {
                FlareLog.debug("iOSPodcastManager Duration: Indefinite")
                if self.duration != nil { // Update only if changed
                    self.duration = nil
                    self.updateNowPlayingInfo() // Update lock screen if duration changes
                }
            } else {
                FlareLog.debug("iOSPodcastManager Duration: \(seconds) seconds")
                if self.duration != seconds { // Update only if changed
                    self.duration = seconds
                    self.updateNowPlayingInfo() // Update lock screen if duration changes
                }
            }
        }
    }

    private func updateSeekability(from playerItem: AVPlayerItem) {
        let canCurrentlySeek = !playerItem.seekableTimeRanges.isEmpty && CMTimeGetSeconds(playerItem.duration) > 0 // Must have duration to be seekable
        DispatchQueue.main.async {
            FlareLog.debug("iOSPodcastManager Seekability Check")
            if self.canSeek != canCurrentlySeek { // Update only if changed
                self.canSeek = canCurrentlySeek
                FlareLog.debug("iOSPodcastManager Seekable: \(self.canSeek)")
                // No need to update lock screen just for seekability change
            } else {
                FlareLog.debug("iOSPodcastManager Seekable: \(self.canSeek) (Unchanged)")
            }
        }
    }

    // --- locked screen image ---
    // --- locked screen mini player ,background player ---
    private func fetchAndCacheArtwork(for podcast: UiPodcast) {
        let artworkUrlString = podcast.creator.avatar
        guard !artworkUrlString.isEmpty, let artworkUrl = URL(string: artworkUrlString) else {
            FlareLog.debug("iOSPodcastManager No valid artwork URL (or empty string) for podcast ID: \(podcast.id)")
            return
        }

        artworkLoadingTasks[podcast.id]?.cancel()

        FlareLog.debug("iOSPodcastManager Starting artwork fetch for: \(artworkUrl)")

        let task = Task {
            do {
                if artworkCache[podcast.id] != nil {
                    FlareLog.debug("iOSPodcastManager Artwork already cached before fetch completed. Skipping update.")
                    artworkLoadingTasks.removeValue(forKey: podcast.id)
                    return
                }

                let (data, response) = try await URLSession.shared.data(from: artworkUrl)

                guard !Task.isCancelled else {
                    FlareLog.debug("iOSPodcastManager Artwork fetch cancelled for ID: \(podcast.id)")
                    artworkLoadingTasks.removeValue(forKey: podcast.id)
                    return
                }

                guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                    FlareLog.error("iOSPodcastManager Failed to download artwork, status code: \((response as? HTTPURLResponse)?.statusCode ?? -1)")
                    artworkLoadingTasks.removeValue(forKey: podcast.id)
                    return
                }

                guard let image = UIImage(data: data) else {
                    FlareLog.error("iOSPodcastManager Failed to create UIImage from downloaded data.")
                    artworkLoadingTasks.removeValue(forKey: podcast.id)
                    return
                }

                let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }

                await MainActor.run {
                    FlareLog.debug("iOSPodcastManager Artwork fetched and cached for ID: \(podcast.id)")
                    self.artworkCache[podcast.id] = artwork
                    self.artworkLoadingTasks.removeValue(forKey: podcast.id)

                    self.updateNowPlayingInfo()
                }

            } catch let error as NSError where error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled {
                FlareLog.debug("iOSPodcastManager Artwork fetch task explicitly cancelled for ID: \(podcast.id).")

                await MainActor.run {
                    _ = self.artworkLoadingTasks.removeValue(forKey: podcast.id)
                }
            } catch {
                FlareLog.error("iOSPodcastManager Error fetching artwork: \(error.localizedDescription)")
                await MainActor.run {
                    _ = self.artworkLoadingTasks.removeValue(forKey: podcast.id)
                }
            }
        }

        artworkLoadingTasks[podcast.id] = task
    }
}
