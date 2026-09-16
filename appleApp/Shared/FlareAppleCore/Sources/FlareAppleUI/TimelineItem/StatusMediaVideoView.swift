import SwiftUI
import KotlinSharedUI
import AVFoundation
import Combine
import SwiftUIBackports
import FlareAppleCore

#if os(iOS)
import UIKit
#endif

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
        .onDisappear {
            if isSeeking { currentTime = CMTime(seconds: sliderValue, preferredTimescale: 600) }
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
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.videoPlaybackPresentation) private var presentation
    @State private var fallbackPresentation = VideoPlaybackPresentation()
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
    @State private var session = VideoPlaybackSession()
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
        Group {
            #if os(iOS)
            content
            #else
            Color.clear
                .overlay {
                    NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                        .scaledToFit()
                        .allowsHitTesting(false)
                }
                .clipped()
                .overlay { player }
            #endif
        }
        .onAppear {
            if presentation == nil {
                fallbackPresentation.setSuspended(scenePhase != .active)
                fallbackPresentation.begin()
            }
            updatePlayback()
        }
        .onChange(of: play) { _, _ in updatePlayback() }
        .onChange(of: scenePhase) { _, phase in
            if presentation == nil { fallbackPresentation.setSuspended(phase != .active) }
        }
        .onChange(of: playbackRate) { _, _ in updatePlayback() }
        .onChange(of: data.url) { _, _ in
            session.detach()
            updatePlayback()
        }
        .onChange(of: time) { _, target in
            if target == time, session.mediaURL == data.url,
               target.seconds.isFinite, abs(session.position - target.seconds) > 0.5 {
                session.seek(to: target.seconds)
            }
        }
        .onReceive(session.updates) { _ in
            videoState = session.state
            if session.player != nil {
                switch session.state {
                case .playing: if !play { play = true }
                case .paused: if play { play = false }
                default: break
                }
            }
            if abs((time.seconds.isFinite ? time.seconds : 0) - session.position) > 0.05 {
                time = CMTime(seconds: session.position, preferredTimescale: 600)
            }
        }
        .onDisappear {
            endFastPlayback()
            if session.mediaURL == data.url, time.seconds.isFinite,
               abs(session.position - time.seconds) > 0.5 {
                session.seek(to: time.seconds)
            }
            (presentation ?? fallbackPresentation).release(session)
            if presentation == nil { fallbackPresentation.end() }
        }
    }

    private func updatePlayback() {
        (presentation ?? fallbackPresentation).update(
            session, url: data.url,
            playing: play, rate: playbackRate
        )
    }

    #if os(iOS)
    @ViewBuilder
    private var content: some View {
        Color.clear
            .overlay {
                NetworkImage(data: data.thumbnailUrl, customHeader: data.customHeaders)
                    .scaledToFit()
                    .allowsHitTesting(false)
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
        if let player = session.player {
            #if os(iOS)
            InlineAVPlayerView(player: player, videoGravity: .resizeAspect,
                               canDisplayFrame: session.hasRestoredPosition, onReady: session.surfaceReady)
                .id(data.url)
                .allowsHitTesting(false)
            #elseif os(macOS)
            MacAVPlayerView(player: player, videoGravity: .resizeAspect, showsControls: true,
                            canDisplayFrame: session.hasRestoredPosition, onReady: session.surfaceReady)
                .id(data.url)
            #endif
        }
    }

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
        let view = WindowGestureHostView()
        view.backgroundColor = .clear
        view.onWindowChanged = { [weak coordinator = context.coordinator, weak view] in
            coordinator?.installGestures(from: view)
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onDoubleTap = onDoubleTap
        context.coordinator.onLongPressChanged = onLongPressChanged
        DispatchQueue.main.async {
            context.coordinator.installGestures(from: uiView)
        }
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
        private weak var installedWindow: UIWindow?
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

        func installGestures(from view: UIView?) {
            sourceView = view
            guard let window = view?.window, installedWindow !== window else { return }
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

            window.addGestureRecognizer(doubleTap)
            window.addGestureRecognizer(longPress)
            installedWindow = window
            doubleTapRecognizer = doubleTap
            longPressRecognizer = longPress
        }

        func uninstallGestures() {
            if let doubleTapRecognizer {
                installedWindow?.removeGestureRecognizer(doubleTapRecognizer)
            }
            if let longPressRecognizer {
                installedWindow?.removeGestureRecognizer(longPressRecognizer)
            }
            installedWindow = nil
            doubleTapRecognizer = nil
            longPressRecognizer = nil
            longPressBeganInside = false
        }

        @objc func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
            guard recognizer.state == .ended,
                  let window = recognizer.view,
                  let sourceView,
                  let location = localLocation(from: recognizer, in: window, sourceView: sourceView) else { return }
            onDoubleTap(location.x, sourceView.bounds.width)
        }

        @objc func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
            guard let window = recognizer.view,
                  let sourceView else { return }

            switch recognizer.state {
            case .began:
                longPressBeganInside = localLocation(from: recognizer, in: window, sourceView: sourceView) != nil
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

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard let sourceView,
                  let window = installedWindow else { return false }
            let point = touch.location(in: window)
            return sourceView.convert(sourceView.bounds, to: window).contains(point)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        private func localLocation(
            from recognizer: UIGestureRecognizer,
            in windowView: UIView,
            sourceView: UIView
        ) -> CGPoint? {
            let pointInWindow = recognizer.location(in: windowView)
            let sourceFrame = sourceView.convert(sourceView.bounds, to: windowView)
            guard sourceFrame.contains(pointInWindow) else { return nil }
            return sourceView.convert(pointInWindow, from: windowView)
        }
    }
}

private final class WindowGestureHostView: UIView {
    var onWindowChanged: (() -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        onWindowChanged?()
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        false
    }
}
#endif
