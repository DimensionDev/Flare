import AVFoundation
import Combine
import SwiftUI

public enum VideoState {
    case idle
    case loading
    case playing(Double)
    case paused(Double)
    case error(any Error)
}

/// The only AVPlayer allocation for inline video and the media viewer.
@MainActor
private final class SharedVideoPlayer {
    static let shared = SharedVideoPlayer()
    private lazy var player = AVQueuePlayer()
    private weak var owner: VideoPlaybackSession?
    private var looper: AVPlayerLooper?
    private var playbackSubscription: AnyCancellable?
    private var looperSubscription: AnyCancellable?
    private var timeObserver: Any?
    private var playbackError: (any Error)?
    private var pendingPosition: Double?
    private var seeking = false
    private var generation = 0
    private var seekGeneration = 0
    private var wantsPlayback = false
    private var rate: Float = 1

    func attach(_ session: VideoPlaybackSession, url: URL, position: Double?, muted: Bool) -> (AVQueuePlayer, Double) {
        owner?.detach()
        owner = session
        let position = position ?? MediaPlaybackMemory.shared.position(for: url.absoluteString)
        generation += 1
        stopObserving()
        player.pause()
        looper = nil
        player.removeAllItems()
        player.isMuted = muted
        player.preventsDisplaySleepDuringVideoPlayback = muted ? false : true
        pendingPosition = position > 0 ? position : nil
        seeking = false
        playbackError = nil
        // Observe before queuing the item: a local file can fail immediately.
        observePlayback()
        let looper = AVPlayerLooper(player: player, templateItem: AVPlayerItem(url: url))
        self.looper = looper
        let generation = generation
        looperSubscription = looper.publisher(for: \.status).sink { @Sendable [weak self] status in
            guard status == .failed else { return }
            let error = looper.error ?? URLError(.cannotDecodeContentData)
            Task { @MainActor in
                guard let self, self.generation == generation else { return }
                self.playbackError = error
                self.owner?.refresh()
            }
        }
        return (player, position)
    }

    private enum PlaybackUpdate: Sendable {
        case state
        case itemFailure(AVPlayerItem, any Error)
    }

    private func observePlayback() {
        let generation = generation
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.generation == generation else { return }
                self.owner?.refresh()
            }
        }
        let itemChanges = player.publisher(for: \.currentItem)
            .map { @Sendable item -> AnyPublisher<PlaybackUpdate, Never> in
                guard let item else { return Just(.state).eraseToAnyPublisher() }
                return item.publisher(for: \.status)
                    .combineLatest(item.publisher(for: \.duration), item.publisher(for: \.error))
                    .map { @Sendable status, _, error -> PlaybackUpdate in
                        if status == .failed || error != nil {
                            return .itemFailure(item, error ?? URLError(.cannotDecodeContentData))
                        }
                        return .state
                    }
                    .eraseToAnyPublisher()
            }
            .switchToLatest()
        playbackSubscription = player.publisher(for: \.timeControlStatus)
            .combineLatest(player.publisher(for: \.rate), player.publisher(for: \.status), player.publisher(for: \.error))
            .map { @Sendable _ in PlaybackUpdate.state }
            .merge(with: itemChanges)
            .sink { @Sendable [weak self] update in
                Task { @MainActor in
                    guard let self, self.generation == generation else { return }
                    if case .itemFailure(let failedItem, let error) = update {
                        if let item = self.player.currentItem, item !== failedItem { return }
                        self.playbackError = error
                    }
                    self.owner?.refresh()
                }
            }
    }

    private func stopObserving() {
        playbackSubscription = nil
        looperSubscription = nil
        if let timeObserver {
            player.removeTimeObserver(timeObserver)
            self.timeObserver = nil
        }
    }

    func error(_ session: VideoPlaybackSession) -> (any Error)? {
        guard owner === session else { return nil }
        return playbackError ?? player.error
    }

    func update(_ session: VideoPlaybackSession, playing: Bool, rate: Float) {
        guard owner === session else { return }
        wantsPlayback = playing
        self.rate = rate
        if !playing { player.pause() }
        else if pendingPosition == nil, !seeking { player.playImmediately(atRate: rate) }
    }

    func position(_ session: VideoPlaybackSession) -> Double {
        guard owner === session else { return session.position }
        if let pendingPosition { return pendingPosition }
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? seconds : session.position
    }

    func isRestoring(_ session: VideoPlaybackSession) -> Bool {
        owner === session && (pendingPosition != nil || seeking)
    }

    func seek(_ session: VideoPlaybackSession, to seconds: Double) {
        guard owner === session, seconds.isFinite else { return }
        seekGeneration += 1
        pendingPosition = max(0, seconds)
        seeking = false
        player.pause()
        refresh(session)
    }

    func setPosition(for url: String, seconds: Double) {
        guard seconds.isFinite, seconds >= 0 else { return }
        if let owner, owner.mediaURL == url, abs(owner.position - seconds) > 0.5 {
            owner.seek(to: seconds)
        }
        MediaPlaybackMemory.shared.save(seconds, for: url)
    }

    func refresh(_ session: VideoPlaybackSession) {
        guard owner === session, !seeking, let pendingPosition,
              player.currentItem?.status == .readyToPlay else { return }
        seeking = true
        let generation = generation
        let seekGeneration = seekGeneration
        player.seek(to: CMTime(seconds: pendingPosition, preferredTimescale: 600),
                    toleranceBefore: .zero, toleranceAfter: .zero) { [weak self, weak session] finished in
            Task { @MainActor in
                guard let self, let session, self.owner === session, self.generation == generation,
                      self.seekGeneration == seekGeneration else { return }
                self.seeking = false
                if finished {
                    self.pendingPosition = nil
                    if self.wantsPlayback { self.player.playImmediately(atRate: self.rate) }
                }
            }
        }
    }

    func detach(_ session: VideoPlaybackSession) {
        guard owner === session else { return }
        generation += 1
        stopObserving()
        player.pause()
        looper = nil
        player.removeAllItems()
        pendingPosition = nil
        seeking = false
        wantsPlayback = false
        playbackError = nil
        owner = nil
    }
}

/// Lightweight per-view state. Inactive sessions retain progress, never a player.
@Observable
@MainActor
public final class VideoPlaybackSession {
    @ObservationIgnored let updates = PassthroughSubject<Void, Never>()
    public private(set) var player: AVQueuePlayer?
    public private(set) var state: VideoState = .idle
    public private(set) var position: Double = 0
    public private(set) var isPlaying = false
    private var url: String?

    var mediaURL: String? { url }
    var isRestoringPosition: Bool { SharedVideoPlayer.shared.isRestoring(self) }

    public init() {}

    public static func setPosition(for url: String, seconds: Double) {
        SharedVideoPlayer.shared.setPosition(for: url, seconds: seconds)
    }

    public func play(url: String, position: Double? = nil, muted: Bool = true, rate: Float = 1) {
        guard let mediaURL = URL(string: url) else {
            state = .error(URLError(.badURL))
            return
        }
        if player == nil || self.url != url {
            let attachment = SharedVideoPlayer.shared.attach(self, url: mediaURL, position: position, muted: muted)
            player = attachment.0
            self.position = attachment.1
            self.url = url
            state = .loading
        }
        setPlaying(true, rate: rate)
    }

    public func setPlaying(_ playing: Bool, rate: Float = 1) {
        isPlaying = playing
        SharedVideoPlayer.shared.update(self, playing: playing, rate: rate)
        refresh()
    }

    public func seek(to seconds: Double) {
        SharedVideoPlayer.shared.seek(self, to: seconds)
        refresh()
    }

    public func refresh() {
        guard let player else { return }
        defer { updates.send() }
        if let error = SharedVideoPlayer.shared.error(self) ?? player.currentItem?.error {
            state = .error(error)
            if player.timeControlStatus != .paused { player.pause() }
            isPlaying = false
            return
        }
        guard let item = player.currentItem else { return }
        SharedVideoPlayer.shared.refresh(self)
        position = SharedVideoPlayer.shared.position(self)
        let duration = item.duration.seconds
        if item.status == .readyToPlay, duration.isFinite {
            if player.timeControlStatus == .playing {
                isPlaying = true
                state = .playing(duration)
            } else if player.timeControlStatus == .paused, !isRestoringPosition {
                isPlaying = false
                state = .paused(duration)
            } else {
                state = isPlaying ? .loading : .paused(duration)
            }
        } else {
            state = .loading
        }
    }

    public func detach() {
        guard player != nil else { return }
        position = SharedVideoPlayer.shared.position(self)
        if let url { MediaPlaybackMemory.shared.save(position, for: url) }
        SharedVideoPlayer.shared.detach(self)
        player = nil
        isPlaying = false
        state = .idle
        updates.send()
    }
}

@MainActor
final class VideoPlaybackPresentation {
    private struct Request {
        let url: String
        var position: Double?
        var playing: Bool
        let rate: Float
    }

    private let arbiter: VideoPlaybackArbiter
    private weak var session: VideoPlaybackSession?
    private var request: Request?
    private var presented = false
    private var mediaURLs: [String] = []
    private var selectedMediaURL: String?

    init(arbiter: VideoPlaybackArbiter = .shared) {
        self.arbiter = arbiter
        arbiter.register(self, stop: { [weak self] in self?.stop() },
                         reconsider: { [weak self] in self?.activate() })
    }

    func begin() {
        presented = true
        arbiter.present(self)
        activate()
    }

    func end() {
        presented = false
        stop()
        request = nil
        session = nil
        arbiter.withdraw(self, mediaURLs: mediaURLs, selectedMediaURL: selectedMediaURL)
    }

    func selectMedia(urls: [String], selectedURL: String?) {
        mediaURLs = urls
        selectedMediaURL = selectedURL
    }

    func update(_ session: VideoPlaybackSession, url: String, position: Double? = nil, playing: Bool, rate: Float) {
        if self.session !== session { self.session?.detach() }
        self.session = session
        request = Request(url: url, position: position, playing: playing, rate: rate)
        activate()
    }

    func release(_ session: VideoPlaybackSession) {
        guard self.session === session else { return }
        stop()
        self.session = nil
        request = nil
        arbiter.release(self)
    }

    private func activate() {
        guard presented, let session, let request, arbiter.acquire(self) else { return }
        self.request?.position = nil
        session.play(url: request.url, position: request.position, muted: false, rate: request.rate)
        session.setPlaying(request.playing, rate: request.rate)
    }

    private func stop() {
        if let session, let player = session.player, player.currentItem?.status == .readyToPlay,
           player.timeControlStatus == .paused, !session.isRestoringPosition {
            request?.playing = false
        }
        session?.detach()
    }
}

private struct VideoPlaybackPresentationKey: EnvironmentKey {
    static let defaultValue: VideoPlaybackPresentation? = nil
}

extension EnvironmentValues {
    var videoPlaybackPresentation: VideoPlaybackPresentation? {
        get { self[VideoPlaybackPresentationKey.self] }
        set { self[VideoPlaybackPresentationKey.self] = newValue }
    }
}

private struct VideoPlaybackPresentationModifier: ViewModifier {
    @State private var presentation = VideoPlaybackPresentation()
    let mediaURLs: [String]
    let selectedMediaURL: String?

    func body(content: Content) -> some View {
        content
            .environment(\.videoPlaybackPresentation, presentation)
            .onAppear {
                presentation.selectMedia(urls: mediaURLs, selectedURL: selectedMediaURL)
                presentation.begin()
            }
            .onChange(of: mediaURLs) { _, urls in
                presentation.selectMedia(urls: urls, selectedURL: selectedMediaURL)
            }
            .onChange(of: selectedMediaURL) { _, url in
                presentation.selectMedia(urls: mediaURLs, selectedURL: url)
            }
            .onDisappear { presentation.end() }
    }
}

public extension View {
    func videoPlaybackPresentation(mediaURLs: [String] = [], selectedMediaURL: String? = nil) -> some View {
        modifier(VideoPlaybackPresentationModifier(mediaURLs: mediaURLs, selectedMediaURL: selectedMediaURL))
    }
}

#if os(iOS)
import UIKit

public final class VideoPlaybackSurfaceView: UIView {
    public override class var layerClass: AnyClass { AVPlayerLayer.self }
    public var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
#endif
