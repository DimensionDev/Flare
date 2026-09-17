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
    private var loadedURL: URL?
    private var retentionTask: Task<Void, Never>?
    private var handoffTask: Task<Void, Never>?
    private var lifecycleSubscriptions: Set<AnyCancellable> = []
    private var memoryPressure: DispatchSourceMemoryPressure?
    #if os(iOS)
    private var holdsAudioSession = false
    #endif

    private init() {
        #if os(iOS)
        let background = UIApplication.didEnterBackgroundNotification
        NotificationCenter.default.publisher(for: UIApplication.didReceiveMemoryWarningNotification)
            .sink { _ in Task { @MainActor in Self.shared.clearIfIdle() } }
            .store(in: &lifecycleSubscriptions)
        #else
        let background = NSApplication.didResignActiveNotification
        #endif
        NotificationCenter.default.publisher(for: background)
            .sink { _ in Task { @MainActor in Self.shared.suspend() } }
            .store(in: &lifecycleSubscriptions)
        let pressure = DispatchSource.makeMemoryPressureSource(eventMask: [.warning, .critical], queue: .main)
        pressure.setEventHandler { Task { @MainActor in Self.shared.clearIfIdle() } }
        pressure.resume()
        memoryPressure = pressure
    }

    func attach(_ session: VideoPlaybackSession, url: URL, position: Double?, allowsAudio: Bool) -> (AVQueuePlayer, Double) {
        if loadedURL == url { continuePlayback(to: url.absoluteString) }
        owner?.detach()
        owner = session
        updateAudioSession(active: allowsAudio)
        retentionTask?.cancel()
        handoffTask?.cancel()
        handoffTask = nil
        if loadedURL == url, playbackError == nil, player.error == nil,
           player.currentItem?.status != .failed, player.currentItem?.error == nil, !player.items().isEmpty {
            player.isMuted = true
            player.preventsDisplaySleepDuringVideoPlayback = false
            if let position { seek(session, to: position) }
            return (player, self.position(session))
        }
        let position = position ?? MediaPlaybackMemory.shared.position(for: url.absoluteString)
        loadedURL = url
        generation += 1
        stopObserving()
        player.pause()
        looper = nil
        player.removeAllItems()
        player.isMuted = true
        player.preventsDisplaySleepDuringVideoPlayback = false
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

    func isPlaybackRequested(_ session: VideoPlaybackSession) -> Bool {
        owner === session && wantsPlayback
    }

    private func updateAudioSession(active: Bool) {
        #if os(iOS)
        guard holdsAudioSession != active else { return }
        holdsAudioSession = active
        if active {
            AudioSessionManager.shared.beginPlayback()
        } else {
            AudioSessionManager.shared.endPlayback()
        }
        #endif
    }

    func unmute(_ session: VideoPlaybackSession) {
        guard owner === session else { return }
        player.isMuted = false
        player.preventsDisplaySleepDuringVideoPlayback = true
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
        } else if owner == nil, loadedURL?.absoluteString == url {
            // A slider may commit after the old surface has disappeared but
            // before the returning timeline attaches to the retained item.
            seekGeneration += 1
            pendingPosition = seconds
            seeking = false
            player.pause()
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
                    toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] finished in
            Task { @MainActor in
                    guard let self, self.generation == generation,
                      self.seekGeneration == seekGeneration else { return }
                self.seeking = false
                    if finished {
                        self.pendingPosition = nil
                        if self.wantsPlayback { self.player.playImmediately(atRate: self.rate) }
                        self.owner?.refresh()
                }
            }
        }
    }

    func detach(_ session: VideoPlaybackSession) {
        guard owner === session else { return }
        owner = nil
        player.isMuted = true
        player.preventsDisplaySleepDuringVideoPlayback = false
        if handoffTask == nil {
            player.pause()
            wantsPlayback = false
            updateAudioSession(active: false)
        }
        retentionTask?.cancel()
        retentionTask = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(5)) } catch { return }
            self?.clearIfIdle()
        }
    }

    // Only navigation may keep the clock running without a surface. Offscreen
    // scrolling still pauses synchronously; an abandoned handoff is bounded.
    func continuePlayback(to url: String) {
        guard loadedURL?.absoluteString == url else { return }
        player.isMuted = true
        handoffTask?.cancel()
        handoffTask = Task { [weak self] in
            do { try await Task.sleep(for: .milliseconds(500)) } catch { return }
            self?.finishHandoff()
        }
    }

    func finishHandoff() {
        handoffTask?.cancel()
        handoffTask = nil
        if owner == nil, loadedURL != nil {
            player.pause()
            wantsPlayback = false
            updateAudioSession(active: false)
        }
    }

    func suspend() {
        finishHandoff()
        owner?.detach()
        clearIfIdle()
    }

    func clearIfIdle() {
        guard owner == nil, loadedURL != nil else { return }
        finishHandoff()
        retentionTask?.cancel()
        retentionTask = nil
        generation += 1
        stopObserving()
        player.pause()
        looper = nil
        player.removeAllItems()
        loadedURL = nil
        pendingPosition = nil
        seeking = false
        wantsPlayback = false
        playbackError = nil
    }
}

/// Lightweight per-view state. Inactive sessions retain progress, never a player.
@Observable
@MainActor
public final class VideoPlaybackSession {
    @ObservationIgnored private let updateSubject = PassthroughSubject<Void, Never>()
    public var updates: AnyPublisher<Void, Never> { updateSubject.eraseToAnyPublisher() }
    public private(set) var player: AVQueuePlayer?
    public private(set) var state: VideoState = .idle
    public private(set) var position: Double = 0
    public var isPlaying: Bool {
        if case .playing = state { return true }
        return false
    }
    public private(set) var hasRestoredPosition = false
    public private(set) var hasDisplayedFrame = false
    private var url: String?
    private var allowsAudio = false

    var mediaURL: String? { url }
    var isRestoringPosition: Bool { SharedVideoPlayer.shared.isRestoring(self) }

    public init() {}

    public static func setPosition(for url: String, seconds: Double) {
        SharedVideoPlayer.shared.setPosition(for: url, seconds: seconds)
    }

    public static func continuePlayback(to url: String) {
        SharedVideoPlayer.shared.continuePlayback(to: url)
    }

    public static func finishHandoff() {
        SharedVideoPlayer.shared.finishHandoff()
    }

    static func releaseIdleBuffer() {
        SharedVideoPlayer.shared.clearIfIdle()
    }

    public func play(url: String, position: Double? = nil, muted: Bool = true, rate: Float = 1, playing: Bool = true) {
        guard let mediaURL = URL(string: url) else {
            state = .error(URLError(.badURL))
            return
        }
        if player == nil || self.url != url {
            hasRestoredPosition = false
            hasDisplayedFrame = false
            allowsAudio = !muted
            let attachment = SharedVideoPlayer.shared.attach(self, url: mediaURL, position: position, allowsAudio: allowsAudio)
            player = attachment.0
            self.position = attachment.1
            self.url = url
            state = .loading
        }
        setPlaying(playing, rate: rate)
    }

    func surfaceReady() {
        guard player != nil, hasRestoredPosition else { return }
        hasDisplayedFrame = true
        if allowsAudio { SharedVideoPlayer.shared.unmute(self) }
    }

    public func setPlaying(_ playing: Bool, rate: Float = 1) {
        SharedVideoPlayer.shared.update(self, playing: playing, rate: rate)
        refresh()
    }

    public func seek(to seconds: Double) {
        SharedVideoPlayer.shared.seek(self, to: seconds)
        refresh()
    }

    public func refresh() {
        guard let player else { return }
        defer { updateSubject.send() }
        if let error = SharedVideoPlayer.shared.error(self) ?? player.currentItem?.error {
            state = .error(error)
            if player.timeControlStatus != .paused { player.pause() }
            return
        }
        guard let item = player.currentItem else { return }
        SharedVideoPlayer.shared.refresh(self)
        position = SharedVideoPlayer.shared.position(self)
        let duration = item.duration.seconds
        if item.status == .readyToPlay, duration.isFinite {
            if !isRestoringPosition { hasRestoredPosition = true }
            if player.timeControlStatus == .playing {
                state = .playing(duration)
            } else if player.timeControlStatus == .paused, !isRestoringPosition {
                state = .paused(duration)
            } else {
                state = SharedVideoPlayer.shared.isPlaybackRequested(self) ? .loading : .paused(duration)
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
        hasRestoredPosition = false
        hasDisplayedFrame = false
        state = .idle
        updateSubject.send()
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
    private var suspended = false
    private var mediaURLs: [String] = []
    private var selectedMediaURL: String?

    init(arbiter: VideoPlaybackArbiter = .shared) {
        self.arbiter = arbiter
        arbiter.register(self, stop: { [weak self] in self?.stop() },
                         reconsider: { [weak self] in self?.activate() },
                         willHandoff: { VideoPlaybackSession.continuePlayback(to: $0) })
    }

    func begin() {
        presented = true
        arbiter.present(self, selectedMediaURL: selectedMediaURL ?? request?.url)
        activate()
    }

    func end() {
        presented = false
        if let selectedMediaURL { VideoPlaybackSession.continuePlayback(to: selectedMediaURL) }
        stop()
        request = nil
        session = nil
        arbiter.withdraw(self, mediaURLs: mediaURLs, selectedMediaURL: selectedMediaURL)
        VideoPlaybackSession.finishHandoff()
    }

    func setSuspended(_ value: Bool) {
        suspended = value
        if value {
            session?.detach()
        } else {
            activate()
        }
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
        if presented, !suspended, selectedMediaURL == session.mediaURL, let selectedMediaURL {
            VideoPlaybackSession.continuePlayback(to: selectedMediaURL)
        }
        stop()
        self.session = nil
        request = nil
        arbiter.release(self)
    }

    private func activate() {
        guard presented, !suspended, let session, let request, arbiter.acquire(self) else { return }
        self.request?.position = nil
        session.play(url: request.url, position: request.position, muted: false, rate: request.rate, playing: request.playing)
    }

    private func stop() {
        #if os(macOS)
        // macOS also accepts pause commands through AVPlayerView's native controls.
        if let session, let player = session.player, player.currentItem?.status == .readyToPlay,
           player.timeControlStatus == .paused, !session.isRestoringPosition {
            request?.playing = false
        }
        #endif
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
    @Environment(\.scenePhase) private var scenePhase
    @State private var presentation = VideoPlaybackPresentation()
    let mediaURLs: [String]
    let selectedMediaURL: String?

    func body(content: Content) -> some View {
        content
            .environment(\.videoPlaybackPresentation, presentation)
            .onAppear {
                presentation.selectMedia(urls: mediaURLs, selectedURL: selectedMediaURL)
                presentation.setSuspended(scenePhase != .active)
                presentation.begin()
            }
            .onChange(of: mediaURLs) { _, urls in
                presentation.selectMedia(urls: urls, selectedURL: selectedMediaURL)
            }
            .onChange(of: selectedMediaURL) { _, url in
                presentation.selectMedia(urls: mediaURLs, selectedURL: url)
            }
            .onDisappear { presentation.end() }
            .onChange(of: scenePhase) { _, phase in presentation.setSuspended(phase != .active) }
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
    public var onReady: () -> Void = {}
    public var canDisplayFrame = true {
        didSet { updateReadiness() }
    }
    private var observation: NSKeyValueObservation?
    private var reportedReady = false

    public var player: AVPlayer? {
        get { playerLayer.player }
        set {
            guard playerLayer.player !== newValue else { return }
            reportedReady = false
            alpha = 0
            playerLayer.player = newValue
        }
    }

    public override init(frame: CGRect) {
        super.init(frame: frame)
        observeReadiness()
    }

    public required init?(coder: NSCoder) {
        super.init(coder: coder)
        observeReadiness()
    }

    private func observeReadiness() {
        backgroundColor = .black
        alpha = 0
        observation = playerLayer.observe(\.isReadyForDisplay, options: [.initial, .new]) { [weak self] _, _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.updateReadiness()
            }
        }
    }

    private func updateReadiness() {
        let ready = canDisplayFrame && player != nil && playerLayer.isReadyForDisplay
        alpha = ready ? 1 : 0
        if ready, !reportedReady {
            let player = player
            // Readiness can change inside updateUIView; publish it after that update.
            Task { @MainActor [weak self] in
                guard let self, !self.reportedReady, self.player === player,
                      self.player != nil, self.canDisplayFrame, self.playerLayer.isReadyForDisplay else { return }
                self.reportedReady = true
                self.onReady()
            }
        }
    }
}
#endif
