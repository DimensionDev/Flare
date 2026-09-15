import SwiftUI
import KotlinSharedUI
import AVFoundation
import Combine
import SwiftUIBackports
import FlareAppleCore

#if os(iOS)
import UIKit
#if canImport(VideoPlayer)
import VideoPlayer
#endif
#endif

public enum VideoState {
    case idle
    case loading
    case playing(Double)
    case paused(Double)
    case error(any Error)
}

public struct VideoControlView: View {
    @Binding private var isPlaying: Bool
    @Binding private var currentTime: CMTime
    private let videoState: VideoState
    private let playbackRate: Float
    @State private var sliderValue: Double = 0
    @State private var isSeeking = false
    @State private var wasPlayingBeforeSeek = false
    @State private var baselineSeconds: Double = 0
    @State private var baselineDate = Date()

    private struct ProgressUpdates: Hashable {
        let isActive: Bool
        let duration: Double
        let playbackRate: Float
    }

    public init(
        isPlaying: Binding<Bool>,
        currentTime: Binding<CMTime>,
        videoState: VideoState,
        playbackRate: Float
    ) {
        self._isPlaying = isPlaying
        self._currentTime = currentTime
        self.videoState = videoState
        self.playbackRate = playbackRate
    }

    public var body: some View {
        #if os(iOS)
        controlContent
        #else
        EmptyView()
        #endif
    }

    @ViewBuilder
    private var controlContent: some View {
        VStack(spacing: 8) {
            if playbackRate > 1 {
                HStack(spacing: 6) {
                    Image(systemName: "forward.fill")
                    Text(verbatim: "\(formatRate(playbackRate))x")
                }
                .font(.caption.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(.black.opacity(0.65), in: .capsule)
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }

            HStack {
                Button {
                    isPlaying.toggle()
                } label: {
                    Image(fontAwesome: isPlaying ? .pause : .play)
                        .font(.title2)
                        .frame(height: 24)
                        .contentTransition(.symbolEffect(.replace))
                }
                .contentShape(Rectangle())
                .accessibilityLabel(
                    Text(
                        isPlaying ? "media_pause" : "media_play",
                        bundle: FlareAppleUILocalization.bundle
                    )
                )
                .backport
                .glassButtonStyle(fallbackStyle: .plain)

                Text(formatTime(sliderValue))
                    .font(.caption)
                    .monospacedDigit()

                Slider(value: $sliderValue, in: 0...max(duration, 0.1)) { editing in
                    isSeeking = editing
                    if editing {
                        wasPlayingBeforeSeek = isPlaying
                        isPlaying = false
                    } else {
                        currentTime = CMTime(seconds: sliderValue, preferredTimescale: 600)
                        baselineSeconds = sliderValue
                        baselineDate = Date()
                        if wasPlayingBeforeSeek {
                            isPlaying = true
                        }
                    }
                }
                .accessibilityLabel(
                    Text("media_playback_position", bundle: FlareAppleUILocalization.bundle)
                )

                Text(formatTime(duration))
                    .font(.caption)
                    .monospacedDigit()
            }
        }
        .onAppear {
            let seconds = currentTime.seconds.isFinite ? currentTime.seconds : 0
            sliderValue = seconds
            baselineSeconds = seconds
            baselineDate = Date()
        }
        .onChange(of: currentTime.seconds) { _, newValue in
            guard !isSeeking, newValue.isFinite else { return }
            baselineSeconds = newValue
            baselineDate = Date()
            sliderValue = newValue
        }
        .onChange(of: isProgressActive) { _, active in
            if active {
                baselineSeconds = sliderValue
                baselineDate = Date()
            }
        }
        .onChange(of: playbackRate) { _, _ in
            baselineSeconds = sliderValue
            baselineDate = Date()
        }
        .onChange(of: duration) { _, newValue in
            if sliderValue > newValue {
                sliderValue = newValue
            }
        }
        .task(id: ProgressUpdates(
            isActive: isProgressActive,
            duration: duration,
            playbackRate: playbackRate
        )) {
            guard isProgressActive else { return }
            while !Task.isCancelled {
                do {
                    try await Task.sleep(for: .milliseconds(100))
                } catch {
                    return
                }
                guard !Task.isCancelled else { return }
                let elapsed = Date().timeIntervalSince(baselineDate)
                let projected = min(baselineSeconds + elapsed * Double(playbackRate), duration)
                if projected != sliderValue {
                    sliderValue = projected
                }
            }
        }
    }

    private var isProgressActive: Bool {
        guard !isSeeking, isPlaying, case .playing(let duration) = videoState else { return false }
        return duration > 0
    }

    private var duration: Double {
        switch videoState {
        case .playing(let duration), .paused(let duration):
            return duration
        default:
            return 0
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        if seconds.isNaN || seconds.isInfinite {
            return "0:00"
        }
        let seconds = Int(seconds)
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }

    private func formatRate(_ rate: Float) -> String {
        if rate.rounded() == rate {
            return String(format: "%.0f", rate)
        }
        return String(format: "%.1f", rate)
    }
}

public struct StatusMediaVideoView: View {
    @Binding private var play: Bool
    @Binding private var videoState: VideoState
    @Binding private var time: CMTime
    @Binding private var playbackRate: Float
    @State private var playbackResumeTask: Task<Void, Never>?
    @State private var fastPlaybackWasPlaying = false
    @State private var isFastPlaybackActive = false
    @State private var seekFeedback: SeekFeedback?
    @State private var seekFeedbackOpacity: Double = 0
    @State private var seekFeedbackTask: Task<Void, Never>?
    #if os(macOS)
    @State private var macPlayer = AVQueuePlayer()
    @State private var macPlayerURL: URL?
    @State private var macPlayerLooper: AVPlayerLooper?
    #endif
    private let data: UiMediaVideo
    private let seekInterval: Double = 5
    private let normalPlaybackRate: Float = 1
    private let fastPlaybackRate: Float = 2

    public init(
        data: UiMediaVideo,
        play: Binding<Bool>,
        videoState: Binding<VideoState>,
        time: Binding<CMTime>,
        playbackRate: Binding<Float>
    ) {
        self.data = data
        self._play = play
        self._videoState = videoState
        self._time = time
        self._playbackRate = playbackRate
    }

    public var body: some View {
        #if os(iOS)
        content
        #elseif os(macOS)
        macContent
        #else
        EmptyView()
        #endif
    }

    #if os(iOS)
    @ViewBuilder
    private var content: some View {
        Color.clear
            .overlay {
                if case .idle = videoState {
                    NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                        .scaledToFit()
                        .allowsHitTesting(false)
                } else {
                    EmptyView()
                }
            }
            .clipped()
            .overlay {
                player
            }
            .overlay {
                VideoGestureOverlay(
                    onDoubleTap: { x, width in
                        if x < width / 2 {
                            seek(by: -seekInterval)
                            showSeekFeedback(.backward)
                        } else {
                            seek(by: seekInterval)
                            showSeekFeedback(.forward)
                        }
                    },
                    onLongPressChanged: { pressing in
                        if pressing {
                            beginFastPlayback()
                        } else {
                            endFastPlayback()
                        }
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityHidden(true)
            }
            .overlay {
                if let seekFeedback {
                    HStack {
                        if seekFeedback == .forward {
                            Spacer()
                        }
                        Image(systemName: seekFeedback.iconName)
                            .font(.system(size: 44, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(20)
                            .background(.black.opacity(0.55), in: .circle)
                        if seekFeedback == .backward {
                            Spacer()
                        }
                    }
                    .padding(.horizontal, 56)
                    .opacity(seekFeedbackOpacity)
                    .allowsHitTesting(false)
                }
            }
            .onDisappear {
                endFastPlayback()
                seekFeedbackTask?.cancel()
            }
    }
    #endif

    @ViewBuilder
    private var player: some View {
        #if os(iOS) && canImport(VideoPlayer)
        if let videoURL = URL(string: data.url) {
            VideoPlayer(url: videoURL, play: $play, time: $time)
                .mute(false)
                .autoReplay(true)
                .speedRate(playbackRate)
                .onStateChanged { state in
                    switch state {
                    case .playing(let duration):
                        videoState = .playing(duration)
                    case .loading:
                        videoState = .loading
                    case .paused:
                        if case .playing(let duration) = videoState {
                            videoState = .paused(duration)
                        } else if case .paused(let duration) = videoState {
                            videoState = .paused(duration)
                        } else {
                            videoState = .idle
                        }
                    case .error(let error):
                        videoState = .error(error)
                    }
                }
                .contentMode(.scaleAspectFit)
                .allowsHitTesting(false)
        }
        #else
        EmptyView()
        #endif
    }

    #if os(macOS)
    @ViewBuilder
    private var macContent: some View {
        Color.clear
            .overlay {
                if case .idle = videoState {
                    NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                        .scaledToFit()
                        .allowsHitTesting(false)
                } else {
                    EmptyView()
                }
            }
            .clipped()
            .overlay {
                if macPlayerURL != nil {
                    MacAVPlayerView(player: macPlayer, videoGravity: .resizeAspect, showsControls: true)
                }
            }
            .onAppear {
                configureMacPlayerIfNeeded()
                updateMacPlayback()
            }
            .onChange(of: play) { _, _ in
                updateMacPlayback()
            }
            .onChange(of: playbackRate) { _, _ in
                updateMacPlayback()
            }
            .onChange(of: time) { _, newValue in
                seekMacPlayerIfNeeded(to: newValue)
            }
            .onChange(of: data.url) { _, _ in
                configureMacPlayerIfNeeded()
                updateMacPlayback()
            }
            .task(id: macPlayerURL) {
                await observeMacPlayback()
            }
            .onDisappear {
                endFastPlayback()
                resetMacPlayer()
            }
    }

    private func configureMacPlayerIfNeeded() {
        guard let videoURL = URL(string: data.url) else {
            resetMacPlayer()
            videoState = .error(URLError(.badURL))
            return
        }

        guard macPlayerURL != videoURL else {
            macPlayer.isMuted = false
            macPlayer.actionAtItemEnd = .advance
            return
        }

        resetMacPlayer()
        macPlayer.isMuted = false
        macPlayer.actionAtItemEnd = .advance
        macPlayerURL = videoURL
        videoState = .loading
    }

    private func updateMacPlayback() {
        configureMacPlayerIfNeeded()
        if play {
            macPlayer.playImmediately(atRate: playbackRate)
        } else {
            macPlayer.pause()
        }
    }

    private func seekMacPlayerIfNeeded(to target: CMTime) {
        guard macPlayerURL != nil, target.seconds.isFinite else {
            return
        }

        let current = macPlayer.currentTime().seconds
        if !current.isFinite || abs(current - target.seconds) > 0.5 {
            macPlayer.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
        }
    }

    private enum MacPlaybackUpdate: Sendable {
        case time
        case state
        case itemFailure(AVPlayerItem, any Error)
        case loopFailure(any Error)
    }

    private func observeMacPlayback() async {
        guard let url = macPlayerURL, !Task.isCancelled else { return }
        let player = macPlayer
        let (updates, continuation) = AsyncStream<MacPlaybackUpdate>.makeStream()
        let timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { _ in
            continuation.yield(.time)
        }
        let itemChanges = player.publisher(for: \.currentItem)
            .map { @Sendable item -> AnyPublisher<MacPlaybackUpdate, Never> in
                guard let item else { return Just(.state).eraseToAnyPublisher() }
                return item.publisher(for: \.status)
                    .combineLatest(item.publisher(for: \.duration), item.publisher(for: \.error))
                    .map { @Sendable status, _, error -> MacPlaybackUpdate in
                        if status == .failed || error != nil {
                            return .itemFailure(item, error ?? URLError(.cannotDecodeContentData))
                        }
                        return .state
                    }
                    .eraseToAnyPublisher()
            }
            .switchToLatest()
        let subscription = player.publisher(for: \.timeControlStatus)
            .combineLatest(
                player.publisher(for: \.rate),
                player.publisher(for: \.status),
                player.publisher(for: \.error)
            )
            .map { @Sendable _ in MacPlaybackUpdate.state }
            .merge(with: itemChanges)
            .sink { @Sendable update in
                continuation.yield(update)
            }
        defer {
            subscription.cancel()
            player.removeTimeObserver(timeObserver)
            continuation.finish()
        }

        // A local item can fail immediately, so observe before adding it to the queue.
        let looper = AVPlayerLooper(player: player, templateItem: AVPlayerItem(url: url))
        macPlayerLooper = looper
        let looperSubscription = looper.publisher(for: \.status)
            .sink { @Sendable status in
                if status == .failed {
                    continuation.yield(.loopFailure(looper.error ?? URLError(.cannotDecodeContentData)))
                }
            }
        defer { looperSubscription.cancel() }
        updateMacPlayback()

        for await update in updates {
            guard !Task.isCancelled, player === macPlayer else { break }
            switch update {
            case .time:
                refreshMacTime()
            case .state:
                refreshMacState()
            case .itemFailure(let failedItem, let error):
                if let item = player.currentItem, item !== failedItem { continue }
                videoState = .error(error)
            case .loopFailure(let error):
                videoState = .error(error)
            }
        }
    }

    private func refreshMacTime() {
        let playerTime = macPlayer.currentTime()
        if playerTime.seconds.isFinite, abs((time.seconds.isFinite ? time.seconds : 0) - playerTime.seconds) > 0.05 {
            time = playerTime
        }
    }

    private func refreshMacState() {
        guard macPlayerURL != nil else { return }
        if macPlayer.status == .failed {
            videoState = .error(macPlayer.error ?? URLError(.cannotDecodeContentData))
            return
        }
        guard let item = macPlayer.currentItem else { return }

        if let error = item.error {
            videoState = .error(error)
            return
        }

        refreshMacTime()

        let rawDuration = item.duration.seconds
        let duration = rawDuration.isFinite ? rawDuration : 0

        switch item.status {
        case .readyToPlay:
            switch macPlayer.timeControlStatus {
            case .playing where macPlayer.rate != 0:
                videoState = .playing(duration)
            case .playing:
                videoState = play ? .loading : .idle
            case .waitingToPlayAtSpecifiedRate:
                videoState = .loading
            case .paused:
                if wasPlayingOrPaused {
                    videoState = .paused(duration)
                } else if play {
                    videoState = .loading
                } else {
                    videoState = .idle
                }
            @unknown default:
                videoState = play ? .loading : .idle
            }
        case .failed:
            videoState = .error(item.error ?? URLError(.cannotDecodeContentData))
        case .unknown:
            videoState = play ? .loading : .idle
        @unknown default:
            videoState = play ? .loading : .idle
        }
    }

    private var wasPlayingOrPaused: Bool {
        switch videoState {
        case .playing, .paused:
            true
        case .idle, .loading, .error:
            false
        }
    }

    private func resetMacPlayer() {
        let player = macPlayer
        player.pause()
        macPlayerLooper = nil
        macPlayer = AVQueuePlayer()
        macPlayerURL = nil
    }
    #endif

    private func seek(by offset: Double) {
        let currentSeconds = time.seconds.isFinite ? time.seconds : 0
        let target: Double
        if let duration {
            target = min(max(currentSeconds + offset, 0), duration)
        } else {
            target = max(currentSeconds + offset, 0)
        }
        time = CMTime(seconds: target, preferredTimescale: 600)
    }

    private func showSeekFeedback(_ feedback: SeekFeedback) {
        seekFeedbackTask?.cancel()
        seekFeedback = feedback
        seekFeedbackOpacity = 0
        withAnimation(.easeOut(duration: 0.12)) {
            seekFeedbackOpacity = 1
        }
        seekFeedbackTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 260_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeIn(duration: 0.22)) {
                seekFeedbackOpacity = 0
            }
            try? await Task.sleep(nanoseconds: 240_000_000)
            guard !Task.isCancelled else { return }
            seekFeedback = nil
        }
    }

    private var duration: Double? {
        switch videoState {
        case .playing(let duration), .paused(let duration):
            return duration > 0 ? duration : nil
        default:
            return nil
        }
    }

    private func beginFastPlayback() {
        guard !isFastPlaybackActive else { return }
        fastPlaybackWasPlaying = play
        playbackResumeTask?.cancel()
        play = false
        playbackRate = fastPlaybackRate
        isFastPlaybackActive = true
        playbackResumeTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 16_000_000)
            guard !Task.isCancelled else { return }
            play = true
        }
    }

    private func endFastPlayback() {
        playbackResumeTask?.cancel()
        playbackResumeTask = nil
        guard isFastPlaybackActive else { return }
        let shouldResume = fastPlaybackWasPlaying
        play = false
        playbackRate = normalPlaybackRate
        if shouldResume {
            playbackResumeTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 16_000_000)
                guard !Task.isCancelled else { return }
                play = true
            }
        }
        isFastPlaybackActive = false
    }
}

private enum SeekFeedback {
    case backward
    case forward

    var iconName: String {
        switch self {
        case .backward:
            return "gobackward.5"
        case .forward:
            return "goforward.5"
        }
    }
}

#if os(iOS)
private struct VideoGestureOverlay: UIViewRepresentable {
    let onDoubleTap: (CGFloat, CGFloat) -> Void
    let onLongPressChanged: (Bool) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        context.coordinator.installGestures(from: view)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onDoubleTap = onDoubleTap
        context.coordinator.onLongPressChanged = onLongPressChanged
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.uninstallGestures()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onDoubleTap: onDoubleTap, onLongPressChanged: onLongPressChanged)
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onDoubleTap: (CGFloat, CGFloat) -> Void
        var onLongPressChanged: (Bool) -> Void
        private weak var sourceView: UIView?
        private var doubleTapRecognizer: UITapGestureRecognizer?
        private var longPressRecognizer: UILongPressGestureRecognizer?
        private var longPressBeganInside = false

        init(
            onDoubleTap: @escaping (CGFloat, CGFloat) -> Void,
            onLongPressChanged: @escaping (Bool) -> Void
        ) {
            self.onDoubleTap = onDoubleTap
            self.onLongPressChanged = onLongPressChanged
        }

        func installGestures(from view: UIView) {
            guard sourceView !== view else { return }
            uninstallGestures()
            sourceView = view

            let doubleTap = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
            doubleTap.numberOfTapsRequired = 2
            doubleTap.cancelsTouchesInView = false
            doubleTap.delegate = self

            let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
            longPress.minimumPressDuration = 0.35
            longPress.allowableMovement = 80
            longPress.cancelsTouchesInView = false
            longPress.delegate = self

            // Let UIKit hit testing exclude controls and panels above the video.
            view.addGestureRecognizer(doubleTap)
            view.addGestureRecognizer(longPress)
            doubleTapRecognizer = doubleTap
            longPressRecognizer = longPress
        }

        func uninstallGestures() {
            if let doubleTapRecognizer {
                sourceView?.removeGestureRecognizer(doubleTapRecognizer)
            }
            if let longPressRecognizer {
                sourceView?.removeGestureRecognizer(longPressRecognizer)
            }
            sourceView = nil
            doubleTapRecognizer = nil
            longPressRecognizer = nil
            longPressBeganInside = false
        }

        @objc func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
            guard recognizer.state == .ended,
                  let sourceView,
                  let location = localLocation(from: recognizer, in: sourceView) else { return }
            onDoubleTap(location.x, sourceView.bounds.width)
        }

        @objc func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
            guard let sourceView else { return }

            switch recognizer.state {
            case .began:
                longPressBeganInside = localLocation(from: recognizer, in: sourceView) != nil
                if longPressBeganInside {
                    onLongPressChanged(true)
                }
            case .ended, .cancelled, .failed:
                if longPressBeganInside {
                    onLongPressChanged(false)
                }
                longPressBeganInside = false
            default:
                break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        private func localLocation(
            from recognizer: UIGestureRecognizer,
            in sourceView: UIView
        ) -> CGPoint? {
            let point = recognizer.location(in: sourceView)
            return sourceView.bounds.contains(point) ? point : nil
        }
    }
}
#endif
